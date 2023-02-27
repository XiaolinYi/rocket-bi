package co.datainsider.bi.controller.http

import co.datainsider.bi.domain.request.UpdateRelationshipRequest
import co.datainsider.bi.service.RelationshipService
import com.google.inject.Inject
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import datainsider.client.filter.PermissionFilter
import datainsider.client.filter.UserContext.UserContextSyntax
import datainsider.tracker.{ActionType, ResourceType, UserActivityTracker}

import scala.concurrent.ExecutionContext.Implicits.global

class RelationshipController @Inject() (relationshipService: RelationshipService, permissionFilter: PermissionFilter)
    extends Controller {

  filter(permissionFilter.require("relationship:view:global"))
    .get(s"/relationships/global") { request: Request =>
      val orgId = request.currentOrganizationId.get
      relationshipService.getGlobal(orgId)
    }

  filter(permissionFilter.require("relationship:create:global"))
    .post(s"/relationships/global") { request: UpdateRelationshipRequest =>
      UserActivityTracker(
        request = request.request,
        actionName = request.getClass.getSimpleName,
        actionType = ActionType.Update,
        resourceType = ResourceType.Relationship,
        resourceId = null,
        description = s"update global table relationships"
      ) {
        val orgId: Long = request.currentOrganizationId.get
        relationshipService
          .createOrUpdateGlobal(orgId, request.toRelationshipInfo)
          .map(toResponse)
      }
    }

  filter(permissionFilter.require("relationship:delete:global"))
    .delete(s"/relationships/global") { request: Request =>
      UserActivityTracker(
        request = request.request,
        actionName = request.getClass.getSimpleName,
        actionType = ActionType.Delete,
        resourceType = ResourceType.Relationship,
        resourceId = null,
        description = s"delete table relationships"
      ) {
        val orgId: Long = request.currentOrganizationId.get
        relationshipService.deleteGlobal(orgId).map(toResponse)
      }
    }

  filter(permissionFilter.require("relationship:view:[dashboard_id]"))
    .get(s"/relationships/:dashboard_id") { request: Request =>
      val orgId = request.currentOrganizationId.get
      val dashboardId: Long = request.getLongParam("dashboard_id")
      relationshipService.get(orgId, dashboardId)
    }

  filter(permissionFilter.require("relationship:create:[dashboard_id]"))
    .post(s"/relationships/:dashboard_id") { request: UpdateRelationshipRequest =>
      UserActivityTracker(
        request = request.request,
        actionName = request.getClass.getSimpleName,
        actionType = ActionType.Update,
        resourceType = ResourceType.Relationship,
        resourceId = request.request.getLongParam("dashboard_id").toString,
        description = s"update dashboard table relationships"
      ) {
        val orgId = request.currentOrganizationId.get
        val dashboardId: Long = request.request.getLongParam("dashboard_id")
        relationshipService
          .createOrUpdate(orgId, dashboardId, request.toRelationshipInfo)
          .map(toResponse)
      }
    }

  filter(permissionFilter.require("relationship:delete:[dashboard_id]"))
    .delete(s"/relationships/:dashboard_id") { request: Request =>
      UserActivityTracker(
        request = request.request,
        actionName = request.getClass.getSimpleName,
        actionType = ActionType.Delete,
        resourceType = ResourceType.Relationship,
        resourceId = request.request.getLongParam("dashboard_id").toString,
        description = s"delete dashboard table relationships"
      ) {
        val orgId = request.currentOrganizationId.get
        val dashboardId: Long = request.getLongParam("dashboard_id")
        relationshipService.delete(orgId, dashboardId).map(toResponse)
      }
    }

  private def toResponse(success: Boolean): Map[String, Any] = {
    Map("success" -> success)
  }

}
