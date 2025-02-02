package co.datainsider.bi.service

import co.datainsider.bi.domain.Directory.{Shared, getSharedDirectory}
import co.datainsider.bi.domain.Ids.{DashboardId, DirectoryId, OrganizationId, UserId}
import co.datainsider.bi.domain.request._
import co.datainsider.bi.domain.response.{PaginationResponse, ParentDirectoriesResponse}
import co.datainsider.bi.domain.{Dashboard, Directory, DirectoryType}
import co.datainsider.bi.repository.{DashboardRepository, DeletedDirectoryRepository, DirectoryRepository}
import co.datainsider.bi.util.Implicits._
import co.datainsider.bi.util.ZConfig
import co.datainsider.share.service.{PermissionAssigner, ShareService}
import com.google.inject.Inject
import com.google.inject.name.Named
import com.twitter.util.Future
import com.twitter.util.logging.Logging
import datainsider.client.domain.Implicits.{FutureEnhanceLike, futurePool}
import datainsider.client.domain.user.UserProfile
import datainsider.client.exception.{BadRequestError, InternalError, NotFoundError}
import datainsider.client.service.ProfileClientService
import education.x.commons.SsdbKVS

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global

trait DirectoryService {
  def get(request: GetDirectoryRequest): Future[Directory]

  def get(orgId: Long, dirId: Long): Future[Directory]

  def create(request: CreateDirectoryRequest): Future[Directory]

  def updateDirectory(orgId: Long, id: DirectoryId, data: Option[Map[UserId, Any]]): Future[Directory]

  def getRootDir(request: GetRootDirectoryRequest): Future[Directory]

  def list(request: ListDirectoriesRequest): Future[Array[Directory]]

  def quickList(request: ListDirectoriesRequest): Future[PaginationResponse[Directory]]

  def listShared(request: ListDirectoriesRequest): Future[Array[Directory]]

  def listSharedDirectories(request: ListDirectoriesRequest): Future[Array[Directory]]

  def listSharedRoot(request: ListDirectoriesRequest): Future[Array[Directory]]

  def listParents(request: GetDirectoryRequest): Future[ParentDirectoriesResponse]

  def listParentsShared(request: GetDirectoryRequest): Future[ParentDirectoriesResponse]

  def rename(request: RenameDirectoryRequest): Future[Boolean]

  def move(organizationId: OrganizationId, fromDirId: DirectoryId, toParentId: DirectoryId): Future[Boolean]

  @throws[NotFoundError]("if directory not found")
  @deprecated("use remove instead of delete")
  def hardDelete(organizationId: OrganizationId, dirId: DirectoryId): Future[Boolean]

  def softDelete(organizationId: OrganizationId, dirId: DirectoryId): Future[Boolean]

  @throws[NotFoundError]("if directory not found")
  def transferData(organizationId: OrganizationId, from: UserId, to: UserId): Future[Boolean]

  @throws[NotFoundError]("if directory not found")
  def copy(organizationId: OrganizationId, fromDirId: DirectoryId, toDirId: DirectoryId): Future[Boolean]

  @throws[NotFoundError]("if directory not found")
  def deleteUserData(organizationId: Long, userId: UserId): Future[Boolean]

  def getOwner(organizationId: Long, directoryId: DirectoryId): Future[UserProfile]

  def isOwner(orgId: Long, directoryId: DirectoryId, username: String): Future[Boolean]

  def listParentIds(id: DirectoryId): Future[Array[DirectoryId]]

  def listParentIdsByDashboardId(id: DashboardId): Future[Array[DirectoryId]]

  /**
    * list all children of this directory, it will not include this directory
    */
  def listChildrenIds(id: DirectoryId): Future[Seq[DirectoryId]]

  // Used by dashboard and directory
  def updateUpdatedDate(id: DirectoryId): Future[Boolean]

}

class DirectoryServiceImpl @Inject() (
    directoryRepository: DirectoryRepository,
    dashboardRepository: DashboardRepository,
    profileService: ProfileClientService,
    shareService: ShareService,
    @Named("root_dir") rootDirKvs: SsdbKVS[String, Long],
    deletedDirectoryService: DeletedDirectoryService,
    permissionAssigner: PermissionAssigner
) extends DirectoryService
    with Logging {

  override def get(request: GetDirectoryRequest): Future[Directory] = {
    val orgId: Long = getOrgId(request.currentOrganizationId)
    get(orgId, request.id)
  }

  override def getRootDir(request: GetRootDirectoryRequest): Future[Directory] = {
    val orgId: Long = getOrgId(request.currentOrganizationId)
    val userId = request.currentUser.username
    for {
      id <- getOrCreateRootDir(request.currentOrganizationId.get, userId)
      directory <- get(orgId, id)
    } yield directory
  }

  override def create(request: CreateDirectoryRequest): Future[Directory] = {
    val orgId: Long = getOrgId(request.currentOrganizationId)
    for {
      parentDir <- get(orgId, request.parentId)
      createdId <- directoryRepository.create(request, parentDir.ownerId, request.currentUser.username)
      _ <- shareService.copyPermissionFromParent(
        request.currentOrganizationId.get,
        createdId.toString,
        request.parentId.toString,
        DirectoryType.Directory.toString,
        request.currentUser.username,
        parentDir.ownerId
      )
      directory <- get(orgId, createdId)
    } yield directory
  }

  override def list(request: ListDirectoriesRequest): Future[Array[Directory]] = {
    directoryRepository.list(request)
  }

  override def quickList(request: ListDirectoriesRequest): Future[PaginationResponse[Directory]] = {
    for {
      directories <- directoryRepository.list(request.copy(ownerId = Some(request.currentUser.username)))
      dirCount <- directoryRepository.count(request)
    } yield PaginationResponse(data = directories, total = dirCount)
  }

  private def listSharedDirs(request: ListDirectoriesRequest): Future[Array[Directory]] = {
    for {
      directoryIds <-
        shareService
          .listResourceIdSharing(
            request.currentOrganizationId.get,
            DirectoryType.Directory.toString,
            request.currentUsername,
            Some(request.from),
            Some(request.size)
          )
          .map(_.data.map(_.toLong).toArray)
      directories <- directoryRepository.list(directoryIds)
    } yield directories
  }

  def listDashboardSharedAsDirectories(request: ListDirectoriesRequest): Future[Array[Directory]] = {
    for {
      dashboardIds <-
        shareService
          .listResourceIdSharing(
            request.currentOrganizationId.get,
            DirectoryType.Dashboard.toString,
            request.currentUsername,
            Some(request.from),
            Some(request.size)
          )
          .map(_.data.map(_.toLong).toArray)
      directories <- directoryRepository.listByDashboardIds(dashboardIds)
    } yield directories
  }

  override def listShared(request: ListDirectoriesRequest): Future[Array[Directory]] = {
    // TODO: fix paging here
    for {
      directories <- listSharedDirs(request)
      directories2 <- listDashboardSharedAsDirectories(request)
    } yield (directories ++ directories2)
  }

  override def listParents(request: GetDirectoryRequest): Future[ParentDirectoriesResponse] = {
    val orgId: Long = getOrgId(request.currentOrganizationId)
    val maxDepth = ZConfig.getInt("directory.parent_depth")
    val userId = request.currentUser.username
    for {
      rootId <- getOrCreateRootDir(request.currentOrganizationId.get, userId)
      rootDir <- get(orgId, rootId)
      parents = getParentDirectories(request.id, Some(maxDepth))
      isAll = parents.contains(rootDir)
    } yield ParentDirectoriesResponse(rootDir, isAll, parents)
  }

  private def getParentIdSharedFromRoot(
      parentIds: Array[DirectoryId],
      idsShared: Map[String, Boolean],
      maxDepth: Int = 4
  ): (Boolean, Seq[DirectoryId]) = {
    val ids = parentIds.dropWhile(id => !idsShared.getOrElse(id.toString, false))
    if (ids.length > maxDepth) {
      (false, ids.slice(ids.length - maxDepth, ids.length))
    } else {
      (true, ids)
    }
  }

  private def getDirectories(directoryIds: Seq[DirectoryId]): Future[Array[Directory]] = {
    directoryRepository.list(directoryIds.toArray)
  }

  private def listParentsShared(
      request: GetDirectoryRequest,
      rootDir: Directory,
      maxDepth: Int
  ): Future[ParentDirectoriesResponse] = {
    val username = request.currentUser.username
    for {
      parentIds <- listParentIds(request.id)
      resourceIds = parentIds.map(_.toString)
      idsShared <- shareService.isShared(
        request.currentOrganizationId.get,
        DirectoryType.Directory.toString,
        resourceIds,
        username
      )
      (isAll, parentIdsShared) = getParentIdSharedFromRoot(parentIds, idsShared, maxDepth)
      parents <- getDirectories(parentIdsShared)
    } yield {
      ParentDirectoriesResponse(rootDir, isAll, Array(rootDir) ++ parents)
    }
  }

  override def listParentsShared(request: GetDirectoryRequest): Future[ParentDirectoriesResponse] = {
    val username = request.currentUser.username
    val maxDepth = ZConfig.getInt("directory.parent_depth")
    val rootDir = getSharedDirectory(username)

    request.id match {
      case id if (id >= 0) => listParentsShared(request, rootDir, maxDepth)
      case Shared          => Future.value(ParentDirectoriesResponse(rootDir, true, Array(rootDir)))
      case _               => Future.exception(InternalError(s"can not list parents of id ${request.id}"))
    }

  }

  override def hardDelete(organizationId: OrganizationId, dirId: DirectoryId): Future[Boolean] = {
    Future.False
  }

  override def rename(request: RenameDirectoryRequest): Future[Boolean] = {
    val orgId: Long = getOrgId(request.currentOrganizationId)

    get(orgId, request.id)
      .map(dir => {
        dir.directoryType match {
          case DirectoryType.Dashboard | DirectoryType.Queries =>
            for {
              okDir <- directoryRepository.rename(dir.id, request.toName)
              okDash <- dashboardRepository.rename(dir.dashboardId.get, request.toName)
            } yield okDir && okDash
          case DirectoryType.Directory | DirectoryType.RetentionAnalysis | DirectoryType.FunnelAnalysis |
              DirectoryType.EventAnalysis | DirectoryType.PathExplorer =>
            directoryRepository.rename(dir.id, request.toName)
        }
      })
      .flatten
  }

  override def move(
      organizationId: OrganizationId,
      fromDirId: DirectoryId,
      toParentId: DirectoryId
  ): Future[Boolean] = {

    for {
      fromDir <- get(organizationId, fromDirId)
      toDir <- get(organizationId, toParentId)
      response <- moveDirectory(fromDir, toDir)
    } yield response
  }

  private def moveDirectory(fromDir: Directory, toDir: Directory): Future[Boolean] = {
    for {
      canMove <- isDestinationDirValid(fromDir, toDir)
      isOk <- canMove match {
        case true  => directoryRepository.move(fromDir.id, toDir.id)
        case false => Future.exception(BadRequestError("can not move directory to itself"))
      }
    } yield isOk
  }

  override def softDelete(organizationId: OrganizationId, id: Long): Future[Boolean] = {
    for {
      directory <- get(organizationId, id)
      isDeleted <- moveToTrash(organizationId, directory)
    } yield isDeleted
  }

  /**
    * move directory to trash and all its children, don't hard delete them
    */
  private def moveToTrash(organizationId: OrganizationId, directory: Directory): Future[Boolean] = {
    for {
      subDirectories <- directoryRepository.getSubDirectories(directory.id)
      directories = directory +: subDirectories
      isInserted <- deletedDirectoryService.multiAdd(organizationId, directories)
      isDeleted <- directoryRepository.multiDelete(directories.map(_.id))
    } yield isInserted && isDeleted
  }

  override def copy(organizationId: OrganizationId, fromId: DirectoryId, toId: DirectoryId): Future[Boolean] = {
    for {
      fromDir <- fetch(fromId)
      toDir <- fetch(toId)
      newDirIds <- copyRecursion(fromDir, toDir)
      isOk <- permissionAssigner.assignPermissions(organizationId, newDirIds, toDir.ownerId, Set("*"))
    } yield isOk
  }

  override def transferData(
      organizationId: OrganizationId,
      fromUsername: String,
      toUsername: String
  ): Future[Boolean] = {
    val isTransferSuccess = for {
      isTransferRootDir <- transferRootDir(organizationId, fromUsername, toUsername)
      isUpdatedDirOwner <- directoryRepository.updateOwnerId(fromUsername, toUsername)
      isUpdatedDirCreator <- directoryRepository.updateCreatorId(fromUsername, toUsername)
      isUpdatedDashOwner <- dashboardRepository.updateOwnerId(fromUsername, toUsername)
      isUpdatedDashCreator <- dashboardRepository.updateCreatorId(fromUsername, toUsername)
      _ <- deleteRootDir(organizationId, fromUsername)
    } yield isTransferRootDir || isUpdatedDirOwner || isUpdatedDirCreator || isUpdatedDashOwner || isUpdatedDashCreator
    isTransferSuccess.rescue {
      case ex: Exception =>
        logger.error(s"transfer data from $fromUsername to $toUsername failed", ex)
        Future.False
    }
  }

  private def transferRootDir(
      organizationId: OrganizationId,
      fromUsername: String,
      toUsername: String
  ): Future[Boolean] = {
    for {
      fromDirId <- getOrCreateRootDir(organizationId, fromUsername)
      toDirId <- getOrCreateRootDir(organizationId, toUsername)
      fromDir <- fetch(fromDirId)
      toDir <- fetch(toDirId)
      isMoved <- moveDirectory(fromDir, toDir)
    } yield isMoved
  }

  override def deleteUserData(organizationId: OrganizationId, userId: UserId): Future[Boolean] = {
    for {
      rootDirId <- getOrCreateRootDir(organizationId, userId)
      rootDirectory <- fetch(rootDirId)
      isDeleted <- moveToTrash(organizationId, rootDirectory)
      _ <- deletedDirectoryService.permanentDeleteDirectory(organizationId, rootDirId)
      _ <- deleteRootDir(organizationId, userId)
    } yield isDeleted
  }

  private def copyRecursion(fromDir: Directory, toDir: Directory): Future[Array[DirectoryId]] = {
    for {
      newDirId <- createCopyInstance(fromDir, toDir)
      newDir <- fetch(newDirId)
      subDirectories <-
        directoryRepository.list(ListDirectoriesRequest(parentId = Some(fromDir.id), size = Int.MaxValue))
      newSubDirIds <- Future.collect(subDirectories.map(childDir => copyRecursion(childDir, newDir)))
    } yield Array(newDirId) ++ newSubDirIds.flatten
  }

  @throws[NotFoundError]("when dashboard not found")
  private def createCopyInstance(fromDir: Directory, toDir: Directory): Future[DirectoryId] = {
    fromDir.directoryType match {
      case DirectoryType.Directory | DirectoryType.RetentionAnalysis | DirectoryType.FunnelAnalysis |
          DirectoryType.EventAnalysis | DirectoryType.PathExplorer => {
        val createReq: CreateDirectoryRequest = fromDir.toCreateDirRequest(parentId = toDir.id)
        directoryRepository.create(createReq, toDir.ownerId, toDir.ownerId)
      }
      case DirectoryType.Dashboard | DirectoryType.Queries => {
        for {
          dashboardId <- createCopyDashboardInstance(fromDir, toDir)
          createDirRequest = fromDir.toCreateDirRequest(parentId = toDir.id).copy(dashboardId = Some(dashboardId))
          newDirId <- directoryRepository.create(createDirRequest, toDir.ownerId, toDir.ownerId)
        } yield newDirId
      }
    }
  }

  @throws[NotFoundError]("when dashboard not found")
  private def createCopyDashboardInstance(fromDir: Directory, toDir: Directory): Future[DashboardId] = {
    val dashboardId = fromDir.dashboardId.getOrElseThrow(NotFoundError("dashboard id not found"))
    for {
      dashboardOpt: Option[Dashboard] <- dashboardRepository.get(dashboardId)
      dashboard = dashboardOpt.getOrElseThrow(NotFoundError(s"dashboard ${dashboardId} not found"))
      newDashboardId <- dashboardRepository.create(dashboard.copy(ownerId = toDir.ownerId))
    } yield newDashboardId
  }

  // return order: [root, child1, child2]
  private def getParentDirectories(id: DirectoryId, maxDepth: Option[Int] = None): Array[Directory] = {
    val directories = ArrayBuffer[Directory]()
    var curId = id
    var count = 0
    var isFinish = false

    while (!isFinish) {
      directoryRepository.get(curId).map {
        case Some(x) =>
          directories += x
          count += 1
          curId = x.parentId
          if (curId == -1) isFinish = true
          maxDepth match {
            case Some(x) => if (count > x) isFinish = true
            case None    =>
          }
        case _ => isFinish = true
      }
    }

    directories.toArray.reverse
  }

  /*
   * root directory of user is stored in ssdb in key value pair: (username,dirId)
   * if user already have rootDirId => return
   * else create new directory and save to ssdb
   */
  private def getOrCreateRootDir(organizationId: OrganizationId, userId: UserId): Future[DirectoryId] = {
    rootDirKvs
      .get(userId)
      .asTwitterFuture
      .map {
        case Some(id) => Future.value(id)
        case None     => createRootDir(organizationId, userId)
      }
      .flatten
  }

  private def createRootDir(organizationId: OrganizationId, userId: UserId): Future[DirectoryId] = {
    val createRequest = CreateDirectoryRequest(
      name = s"root-$userId",
      parentId = -1
    )
    for {
      dirId <- directoryRepository.create(createRequest, userId, userId)
      _ <- rootDirKvs.add(userId, dirId).asTwitterFuture
      _ <- permissionAssigner.assignRecurPermissions(organizationId, dirId, userId, Set("*"))
    } yield dirId
  }

  private def deleteRootDir(organizationId: OrganizationId, userId: UserId): Future[Boolean] = {
    rootDirKvs.remove(userId).asTwitterFuture
  }

  private def isDestinationDirValid(toBeMovedDir: Directory, toParentDir: Directory): Future[Boolean] = {
    for {
      childDirs <- listChildrenIds(toBeMovedDir.id)
    } yield {
      val allDirs = childDirs :+ toBeMovedDir.id
      !allDirs.contains(toParentDir.id)
    }
  }

  @deprecated("use fetch with org if possible")
  private def fetch(id: DirectoryId): Future[Directory] = {
    directoryRepository.get(id).map {
      case Some(x) => x
      case None    => throw NotFoundError(s"no directory is found for id $id")
    }
  }

  override def get(orgId: Long, dirId: DirectoryId): Future[Directory] = {
    for {
      dir <- directoryRepository.get(dirId).map {
        case Some(x) => x
        case None    => throw NotFoundError(s"no directory is found for id $dirId")
      }
      checkOrg <- profileService.getUserProfile(orgId, dir.ownerId).map {
        case Some(user) =>
        case None       => throw NotFoundError(s"this directory does not belongs to your organization")
      }
    } yield dir
  }

  override def getOwner(organizationId: Long, directoryId: DirectoryId): Future[UserProfile] = {
    for {
      directory <- get(organizationId, directoryId)
      maybeUserProfile <- profileService.getUserProfile(organizationId, directory.ownerId)
    } yield {
      maybeUserProfile match {
        case Some(ownerProfile) => ownerProfile
        case _                  => throw NotFoundError("fail to load profile of owner")
      }
    }
  }

  override def isOwner(orgId: Long, directoryId: DirectoryId, username: String): Future[Boolean] = {
    for {
      directory <- get(orgId, directoryId)
    } yield username.equals(directory.ownerId)
  }

  // NOTE: [root, child1, child2]
  override def listParentIds(id: DirectoryId): Future[Array[DirectoryId]] =
    Future {
      getParentDirectories(id, None).map(_.id)
    }

  override def listParentIdsByDashboardId(id: DashboardId): Future[Array[DirectoryId]] = {
    list(ListDirectoriesRequest(dashboardId = Some(id)))
      .map(_.headOption.map(_.id))
      .flatMap {
        case Some(directoryId) => listParentIds(directoryId)
        case _                 => Future.value(Array.empty)
      }
  }

  override def listChildrenIds(id: DirectoryId): Future[Seq[DirectoryId]] = {
    for {
      subDirectories <- directoryRepository.getSubDirectories(id)
    } yield subDirectories.map(_.id).distinct
  }

  override def listSharedRoot(request: ListDirectoriesRequest): Future[Array[Directory]] = {
    for {
      directoryIds <-
        shareService
          .listSharedRootIds(
            request.currentOrganizationId.get,
            DirectoryType.Directory.toString,
            request.currentUsername,
            Some(request.from),
            Some(request.size)
          )
          .map(_.data.map(_.toLong).toArray)
      directories <- directoryRepository.list(directoryIds)
    } yield directories
  }

  override def listSharedDirectories(request: ListDirectoriesRequest): Future[Array[Directory]] = {
    for {
      directories <- list(request)
      sharedDirectoryIds <-
        shareService
          .isShared(
            request.currentOrganizationId.get,
            DirectoryType.Directory.toString,
            directories.map(_.id.toString),
            request.currentUsername
          )
          .map(_.filter(_._2.equals(true)).keys.toArray)
      sharedDirectory <- directoryRepository.list(sharedDirectoryIds.map(_.toLong))
    } yield sharedDirectory
  }

  override def updateUpdatedDate(id: DirectoryId): Future[Boolean] = {
    for {
      parentIds <- listParentIds(id)
      isOk <- directoryRepository.refreshUpdatedDate(parentIds)
    } yield isOk
  }

  private def getOrgId(orgId: Option[Long]): Long = {
    orgId match {
      case Some(id) => id
      case None     => throw BadRequestError("Your request has not been authorized.")
    }
  }

  override def updateDirectory(orgId: Long, id: DirectoryId, data: Option[Map[UserId, Any]]): Future[Directory] = {
    for {
      directory <- get(orgId, id)
      newDirectory = directory.copy(data = data, updatedDate = Some(System.currentTimeMillis()))
      _ <- directoryRepository.update(newDirectory)
    } yield newDirectory
  }
}
