package co.datainsider.share.service

import co.datainsider.bi.domain.Ids.DirectoryId
import co.datainsider.bi.domain.request.ListDirectoriesRequest
import co.datainsider.bi.domain.{Directory, PermissionToken, DirectoryType, TokenFullInfo}
import co.datainsider.bi.service.{DashboardService, DirectoryService}
import co.datainsider.bi.util.SchemaImplicits.ActionListEnhanceImplicits
import datainsider.profiler.Profiler
import co.datainsider.share.controller.request._
import co.datainsider.share.domain.response.{PageResult, ResourceSharingInfo, SharingInfo, UserSharingInfo}
import co.datainsider.share.repository.ShareRepository
import com.twitter.util.Future
import com.twitter.util.logging.Logging
import datainsider.authorization.domain.PermissionProviders
import datainsider.client.domain.Implicits.FutureEnhanceLike
import datainsider.client.domain.user.{UserGender, UserProfile}
import datainsider.client.exception.UnsupportedError
import datainsider.client.service.{OrgAuthorizationClientService, ProfileClientService}

import java.util.UUID
import javax.inject.Inject
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

case class IncludeAndExcludePermission(
    includePermissions: Seq[String],
    excludePermissions: Seq[String]
)

object Permissions {
  val LIST_ACTIONS = Seq("view", "edit", "create", "delete", "copy", "share", "*")

  @deprecated
  def getIncludeAndExcludePermissions(
      newPermissions: Seq[String],
      permittedAsMap: Map[String, Boolean]
  ): (Seq[String], Seq[String]) = {
    val oldPermissions = permittedAsMap.filter(_._2).keySet
    val newPermissionsAsSet = newPermissions.toSet
    val includePermissions = newPermissionsAsSet.diff(oldPermissions).toSeq
    val excludePermissions = oldPermissions.diff(newPermissionsAsSet).toSeq
    (includePermissions, excludePermissions)
  }

  def buildIncludeAndExcludePermissions(
      organizationId: Long,
      permissions: Seq[String],
      resourceIds: Seq[String],
      resourceType: String
  ): IncludeAndExcludePermission = {
    val includeActions = getActions(permissions)
    val excludeActions = LIST_ACTIONS.diff(includeActions)
    val includePermissions =
      buildPermissions(organizationId, resourceType, resourceIds, includeActions)
    val excludePermissions =
      buildPermissions(organizationId, resourceType, resourceIds, excludeActions)

    IncludeAndExcludePermission(includePermissions, excludePermissions)
  }

  def getAvailablePermissions(organizationId: Long, resourceType: String, resourceId: String): Seq[String] = {
    LIST_ACTIONS.map { action =>
      PermissionProviders.permissionBuilder.perm(organizationId, resourceType, action, resourceId)
    }
  }

  def buildPermissions(
      organizationId: Long,
      resourceType: String,
      resourceIds: Seq[String],
      actions: Seq[String]
  ): Seq[String] = {
    resourceIds.flatMap(resourceId => actions.toPermissions(organizationId, resourceType, resourceId))
  }

  def copyPermissionsToResourceId(
      organizationId: Long,
      childrenId: String,
      resourceType: String,
      parentPermissions: Seq[String]
  ): Seq[String] = {
    val actions: Seq[String] = getActions(parentPermissions)
    actions.toPermissions(organizationId, resourceType, childrenId)
  }

  def getActions(permissions: Seq[String]): Seq[String] = {
    val actions = ArrayBuffer.empty[String]
    permissions.foreach(permission => {
      val Array(organizationId, domain, action, resourceId) = permission.split(":")
      actions += action
    })
    actions.distinct
  }
}

// TODO: remove share when remove user
// Remove share when remove dashboard/directory
trait ShareService {

  def share(organizationId: Long, request: ShareWithUserRequest): Future[Map[String, Boolean]]

  def share(organizationId: Long, request: ShareAnyoneRequest): Future[PermissionToken]

  def revoke(organizationId: Long, request: RevokeShareRequest): Future[Map[String, Boolean]]

  def revoke(organizationId: Long, request: RevokeShareAnyoneRequest): Future[Boolean]

  def getInfo(organizationId: Long, getRequest: GetResourceSharingInfoRequest): Future[ResourceSharingInfo]

  def getInfo(organizationId: Long, request: GetShareAnyoneInfoRequest): Future[Option[PermissionToken]]

  def getAllInfo(organizationId: Long, resourceId: String, resourceType: String): Future[Seq[UserSharingInfo]]

  def saveSharedInfo(
      organizationId: Long,
      resourceId: String,
      resourceType: String,
      usernames: Seq[String],
      creator: String,
      isRoot: Boolean = false
  ): Future[Boolean]

  def multiUpdate(organizationId: Long, updateRequest: MultiUpdateResourceSharingRequest): Future[Map[String, Boolean]]

  def listResourceIdSharing(
      organizationId: Long,
      resourceType: String,
      userId: String,
      from: Option[Int] = None,
      size: Option[Int] = None
  ): Future[PageResult[String]]

  def listSharedRootIds(
      organizationId: Long,
      resourceType: String,
      userId: String,
      from: Option[Int] = None,
      size: Option[Int] = None
  ): Future[PageResult[String]]

  def update(organizationId: Long, request: UpdateShareAnyoneRequest): Future[Boolean]

  def isShared(
      organizationId: Long,
      resourceType: String,
      resourceId: String,
      usernames: Seq[String]
  ): Future[Map[String, Boolean]]

  def isShared(
      organizationId: Long,
      resourceType: String,
      resourceIds: Seq[String],
      username: String
  ): Future[Map[String, Boolean]]

  def invite(
      organizationId: Long,
      resourceType: String,
      resourceId: String,
      usernames: Seq[String],
      tokenId: String
  ): Future[Map[String, Boolean]]

  def copyPermissionFromParent(
      organizationId: Long,
      childrenId: String,
      parentId: String,
      resourceType: String,
      creator: String,
      owner: String
  ): Future[Map[String, Boolean]]
}

class MockShareService @Inject() (
    directoryService: DirectoryService,
    shareRepository: ShareRepository,
    orgAuthorizationClientService: OrgAuthorizationClientService
) extends ShareService {
  override def getInfo(
      organizationId: Long,
      getRequest: GetResourceSharingInfoRequest
  ): Future[ResourceSharingInfo] =
    Future {
      val data = Seq(
        UserSharingInfo(
          UUID.randomUUID().toString,
          user = UserProfile(
            "thienlan12",
            fullName = Some("Lam Ngoc Lan"),
            lastName = None,
            firstName = Some("Lam"),
            email = Some("lanthien12@gmail.com"),
            mobilePhone = None,
            gender = Some(UserGender.Female),
            dob = Some(1615161959784L),
            avatar = None,
            alreadyConfirmed = true,
            properties = None
          ),
          permissions = Seq("dashboard:98:view"),
          createdAt = Some(System.currentTimeMillis()),
          updatedAt = Some(System.currentTimeMillis()),
          createdBy = Some("tvc12"),
          updatedBy = Some("tvc12")
        ),
        UserSharingInfo(
          UUID.randomUUID().toString,
          user = UserProfile(
            "meomeocf98@gmail.com",
            fullName = Some("thienlan"),
            lastName = None,
            firstName = Some("Lam"),
            email = Some("meowmeow@gmail.com"),
            mobilePhone = None,
            gender = Some(UserGender.Female),
            dob = Some(1615161959784L),
            avatar = Some("https://github.com/tvc12.png"),
            alreadyConfirmed = true,
            properties = None
          ),
          permissions = Seq("dashboard:98:view"),
          createdAt = Some(System.currentTimeMillis()),
          updatedAt = Some(System.currentTimeMillis()),
          createdBy = Some("tvc12"),
          updatedBy = Some("tvc12")
        ),
        UserSharingInfo(
          UUID.randomUUID().toString,
          user = UserProfile(
            "tvc12",
            fullName = Some("Vi Chi Thien"),
            lastName = None,
            firstName = Some("ThienVi"),
            email = Some("tvc12@gmail.com"),
            mobilePhone = None,
            gender = Some(UserGender.Female),
            dob = Some(1615161959784L),
            avatar = None,
            alreadyConfirmed = true,
            properties = None
          ),
          permissions = Seq("dashboard:98:edit"),
          createdAt = Some(System.currentTimeMillis()),
          updatedAt = Some(System.currentTimeMillis()),
          createdBy = Some("tvc12"),
          updatedBy = Some("tvc12")
        )
      )

      ResourceSharingInfo(
        owner = UserProfile(
          "tvc12",
          fullName = None,
          lastName = Some("Thien"),
          firstName = Some("Vi"),
          email = Some("meomeocf98@gmail.com"),
          mobilePhone = None,
          gender = Some(UserGender.Male),
          dob = Some(1615261959784L),
          avatar = Some("https://github.com/tvc12.png"),
          alreadyConfirmed = true,
          properties = None
        ),
        200,
        usersSharing = Range(0, getRequest.size).map(_ => data(Random.nextInt(data.length)))
      )
    }

  override def multiUpdate(
      organizationId: Long,
      updateRequest: MultiUpdateResourceSharingRequest
  ): Future[Map[String, Boolean]] = Future.value(Map("tvc12" -> true))

  override def share(organizationId: Long, request: ShareAnyoneRequest): Future[PermissionToken] = {
    Future.value(PermissionToken("123", "123", Seq("dashboard:view:123"), None))
  }

  override def revoke(organizationId: Long, request: RevokeShareRequest): Future[Map[String, Boolean]] =
    Future.value(Map("tvc12" -> true))

  override def listResourceIdSharing(
      organizationId: Long,
      resourceType: String,
      userId: String,
      from: Option[Int],
      size: Option[Int]
  ): Future[PageResult[String]] = Future.value(PageResult(100, Seq.empty[String]))

  override def share(organizationId: Long, request: ShareWithUserRequest): Future[Map[String, Boolean]] =
    Profiler("[service::ShareService]::share(orgId, shareWithUserRequest)") {
      for {
        listUserAssignedPermissions <-
          assignUserPermissions(organizationId, request.resourceType, request.resourceId, request.userActions)
        _ <- shareRepository.shareWithUsers(
          organizationId,
          request.resourceType,
          request.resourceId,
          listUserAssignedPermissions.filter(_._2).keys.toSeq,
          "test",
          isRoot = false
        )
      } yield {
        listUserAssignedPermissions
      }
    }

  private def assignUserPermissions(
      organizationId: Long,
      resourceType: String,
      resourceId: String,
      userActions: Map[String, Seq[String]]
  ): Future[Map[String, Boolean]] =
    Profiler("[service::ShareService]::assignUserPermissions") {
      val fn = userActions.map {
        case (username, actions) =>
          val permissions = ArrayBuffer.empty[String]
          permissions.appendAll(actions.toPermissions(organizationId, resourceType, resourceId))
          permissions.appendAll(getChildrenPermission(organizationId, resourceId.toInt, actions))
          username -> orgAuthorizationClientService.addPermissions(organizationId, username, permissions).rescue {
            case ex: Throwable =>
              Future.False
          }
      }

      Future.collect(fn)
    }

  private def getChildrenPermission(organizationId: Long, parentId: Int, actions: Seq[String]): Seq[String] = {
    val request = ListDirectoriesRequest(parentId = Some(parentId), isRemoved = Some(false))
    val childrenDir = directoryService.list(request).syncGet()
    toChildrenPermission(organizationId, childrenDir, actions)
  }

  private def toChildrenPermission(
      organizationId: Long,
      directories: Seq[Directory],
      actions: Seq[String]
  ): Seq[String] = {
    val permissions = ArrayBuffer.empty[String]
    directories.foreach(directory => {
      permissions.appendAll(
        actions.toPermissions(organizationId, directory.directoryType.toString, directory.id.toString)
      )
      permissions.appendAll(getChildrenPermission(organizationId, directory.id.toInt, actions))
    })
    permissions
  }

  override def revoke(organizationId: Long, request: RevokeShareAnyoneRequest): Future[Boolean] = Future.True

  override def getInfo(organizationId: Long, request: GetShareAnyoneInfoRequest): Future[Option[PermissionToken]] =
    Future.value(Some(PermissionToken("123", "tvc12", Seq("dashboard:view:123"), None)))

  override def update(organizationId: Long, request: UpdateShareAnyoneRequest): Future[Boolean] = Future.True

  override def isShared(
      organizationId: Long,
      resourceType: String,
      resourceId: String,
      usernames: Seq[String]
  ): Future[Map[String, Boolean]] = Future.value(Map.empty)

  override def invite(
      organizationId: Long,
      resourceType: String,
      resourceId: String,
      usernames: Seq[String],
      tokenId: String
  ): Future[Map[String, Boolean]] = Future.value(Map.empty)

  override def isShared(
      organizationId: Long,
      resourceType: String,
      resourceIds: Seq[String],
      username: String
  ): Future[Map[String, Boolean]] = Future.value(Map.empty)

  override def saveSharedInfo(
      organizationId: Long,
      resourceId: String,
      resourceType: String,
      usernames: Seq[String],
      creator: String,
      isRoot: Boolean = false
  ): Future[Boolean] = Future.True

  override def getAllInfo(
      organizationId: Long,
      resourceId: String,
      resourceType: String
  ): Future[Seq[UserSharingInfo]] = {
    Future {
      Seq(
        UserSharingInfo(
          UUID.randomUUID().toString,
          user = UserProfile(
            "thienlan12",
            fullName = Some("Lam Ngoc Lan"),
            lastName = None,
            firstName = Some("Lam"),
            email = Some("lanthien12@gmail.com"),
            mobilePhone = None,
            gender = Some(UserGender.Female),
            dob = Some(1615161959784L),
            avatar = None,
            alreadyConfirmed = true,
            properties = None
          ),
          permissions = Seq("dashboard:98:view"),
          createdAt = Some(System.currentTimeMillis()),
          updatedAt = Some(System.currentTimeMillis()),
          createdBy = Some("tvc12"),
          updatedBy = Some("tvc12")
        ),
        UserSharingInfo(
          UUID.randomUUID().toString,
          user = UserProfile(
            "meomeocf98@gmail.com",
            fullName = Some("thienlan"),
            lastName = None,
            firstName = Some("Lam"),
            email = Some("meowmeow@gmail.com"),
            mobilePhone = None,
            gender = Some(UserGender.Female),
            dob = Some(1615161959784L),
            avatar = Some("https://github.com/tvc12.png"),
            alreadyConfirmed = true,
            properties = None
          ),
          permissions = Seq("dashboard:98:view"),
          createdAt = Some(System.currentTimeMillis()),
          updatedAt = Some(System.currentTimeMillis()),
          createdBy = Some("tvc12"),
          updatedBy = Some("tvc12")
        ),
        UserSharingInfo(
          UUID.randomUUID().toString,
          user = UserProfile(
            "tvc12",
            fullName = Some("Vi Chi Thien"),
            lastName = None,
            firstName = Some("ThienVi"),
            email = Some("tvc12@gmail.com"),
            mobilePhone = None,
            gender = Some(UserGender.Female),
            dob = Some(1615161959784L),
            avatar = None,
            alreadyConfirmed = true,
            properties = None
          ),
          permissions = Seq("dashboard:98:edit"),
          createdAt = Some(System.currentTimeMillis()),
          updatedAt = Some(System.currentTimeMillis()),
          createdBy = Some("tvc12"),
          updatedBy = Some("tvc12")
        )
      )
    }
  }

  override def copyPermissionFromParent(
      organizationId: Long,
      childrenId: String,
      parentId: String,
      resourceType: String,
      creator: String,
      owner: String
  ): Future[Map[String, Boolean]] =
    Future {
      Map("test1" -> true, "test2" -> true)
    }

  override def listSharedRootIds(
      organizationId: DirectoryId,
      resourceType: String,
      userId: String,
      from: Option[Int],
      size: Option[Int]
  ): Future[PageResult[String]] =
    Future {
      PageResult(2, Seq("1", "2"))
    }
}