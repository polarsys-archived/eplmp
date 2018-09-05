/*******************************************************************************
  * Copyright (c) 2017 DocDoku.
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Public License v1.0
  * which accompanies this distribution, and is available at
  * http://www.eclipse.org/legal/epl-v10.html
  *
  * Contributors:
  *    DocDoku - initial API and implementation
  *******************************************************************************/
package org.polarsys.eplmp.server.rest;

import io.swagger.annotations.*;
import org.dozer.DozerBeanMapperSingletonWrapper;
import org.dozer.Mapper;
import org.polarsys.eplmp.core.change.ModificationNotification;
import org.polarsys.eplmp.core.document.DocumentRevision;
import org.polarsys.eplmp.core.document.DocumentRevisionKey;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.product.PartIterationKey;
import org.polarsys.eplmp.core.product.PartRevision;
import org.polarsys.eplmp.core.product.PartRevisionKey;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.*;
import org.polarsys.eplmp.core.workflow.ActivityKey;
import org.polarsys.eplmp.core.workflow.TaskKey;
import org.polarsys.eplmp.core.workflow.TaskWrapper;
import org.polarsys.eplmp.server.rest.dto.*;

import javax.annotation.PostConstruct;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Morgan Guimard
 */

@RequestScoped
@Api(hidden = true, value = "tasks", description = "Operations about tasks",
        authorizations = {@Authorization(value = "authorization")})
@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
public class TaskResource {

    @Inject
    private IDocumentManagerLocal documentService;

    @Inject
    private IProductManagerLocal productService;

    @Inject
    private IDocumentWorkflowManagerLocal documentWorkflowManager;

    @Inject
    private IPartWorkflowManagerLocal partWorkflowManager;

    @Inject
    private IWorkflowManagerLocal workflowManager;

    @Inject
    private ITaskManagerLocal taskManager;

    private Mapper mapper;

    public TaskResource() {
    }

    @PostConstruct
    public void init() {
        mapper = DozerBeanMapperSingletonWrapper.getInstance();
    }


    @GET
    @ApiOperation(value = "Get assigned tasks for given user",
            response = TaskDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of TaskDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{assignedUserLogin}/assigned")
    @Produces(MediaType.APPLICATION_JSON)
    public TaskDTO[] getAssignedTasksForGivenUser(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Assigned user login") @PathParam("assignedUserLogin") String assignedUserLogin)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        TaskWrapper[] runningTasksForGivenUser = taskManager.getAssignedTasksForGivenUser(workspaceId, assignedUserLogin);
        List<TaskDTO> taskDTOs = new ArrayList<>();
        for (TaskWrapper taskWrapper : runningTasksForGivenUser) {
            TaskDTO taskDTO = mapper.map(taskWrapper.getTask(), TaskDTO.class);
            taskDTO.setHolderType(taskWrapper.getHolderType());
            taskDTO.setWorkspaceId(workspaceId);
            taskDTO.setHolderReference(taskWrapper.getHolderReference());
            taskDTO.setHolderVersion(taskWrapper.getHolderVersion());
            taskDTOs.add(taskDTO);
        }
        return taskDTOs.toArray(new TaskDTO[taskDTOs.size()]);
    }

    @GET
    @ApiOperation(value = "Get task by id",
            response = TaskDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of TaskDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{taskId}")
    @Produces(MediaType.APPLICATION_JSON)
    public TaskDTO getTask(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Task id") @PathParam("taskId") String taskId)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        String[] split = taskId.split("-");

        int workflowId = Integer.parseInt(split[0]);
        int step = Integer.parseInt(split[1]);
        int task = Integer.parseInt(split[2]);

        TaskKey taskKey = new TaskKey(new ActivityKey(workflowId, step), task);
        TaskWrapper taskWrapper = taskManager.getTask(workspaceId, taskKey);

        TaskDTO taskDTO = mapper.map(taskWrapper.getTask(), TaskDTO.class);
        taskDTO.setHolderType(taskWrapper.getHolderType());
        taskDTO.setWorkspaceId(workspaceId);
        taskDTO.setHolderReference(taskWrapper.getHolderReference());
        taskDTO.setHolderVersion(taskWrapper.getHolderVersion());

        return taskDTO;
    }

    @GET
    @ApiOperation(value = "Get document revisions where user has assigned tasks",
            response = DocumentRevisionDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of DocumentRevisionDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{assignedUserLogin}/documents")
    @Produces(MediaType.APPLICATION_JSON)
    public DocumentRevisionDTO[] getDocumentsWhereGivenUserHasAssignedTasks(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Assigned user login") @PathParam("assignedUserLogin") String assignedUserLogin,
            @ApiParam(required = false, value = "Status filter") @QueryParam("filter") String filter)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        DocumentRevision[] docRs;

        if ("in_progress".equals(filter)) {
            docRs = documentService.getDocumentRevisionsWithOpenedTasksForGivenUser(workspaceId, assignedUserLogin);
        } else {
            docRs = documentService.getDocumentRevisionsWithAssignedTasksForGivenUser(workspaceId, assignedUserLogin);
        }

        List<DocumentRevisionDTO> docRsDTOs = new ArrayList<>();

        for (DocumentRevision docR : docRs) {

            DocumentRevisionDTO docDTO = mapper.map(docR, DocumentRevisionDTO.class);
            docDTO.setPath(docR.getLocation().getCompletePath());
            docDTO = Tools.createLightDocumentRevisionDTO(docDTO);
            docDTO.setIterationSubscription(documentService.isUserIterationChangeEventSubscribedForGivenDocument(workspaceId, docR));
            docDTO.setStateSubscription(documentService.isUserStateChangeEventSubscribedForGivenDocument(workspaceId, docR));
            docRsDTOs.add(docDTO);

        }

        return docRsDTOs.toArray(new DocumentRevisionDTO[docRsDTOs.size()]);
    }

    @GET
    @ApiOperation(value = "Get part revisions where user has assigned tasks",
            response = PartRevisionDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of PartRevisionDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{assignedUserLogin}/parts")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPartsWhereGivenUserHasAssignedTasks(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Assigned user login") @PathParam("assignedUserLogin") String assignedUserLogin,
            @ApiParam(required = false, value = "Task status filter") @QueryParam("filter") String filter)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        PartRevision[] withTaskPartRevisions;

        if ("in_progress".equals(filter)) {
            withTaskPartRevisions = productService.getPartRevisionsWithOpenedTasksForGivenUser(workspaceId, assignedUserLogin);
        } else {
            withTaskPartRevisions = productService.getPartRevisionsWithAssignedTasksForGivenUser(workspaceId, assignedUserLogin);
        }

        List<PartRevisionDTO> partRevisionDTOs = new ArrayList<>();

        for (PartRevision partRevision : withTaskPartRevisions) {
            PartRevisionDTO partRevisionDTO = Tools.mapPartRevisionToPartDTO(partRevision);

            PartIterationKey iterationKey = new PartIterationKey(partRevision.getKey(), partRevision.getLastIterationNumber());
            List<ModificationNotification> notifications = productService.getModificationNotifications(iterationKey);
            List<ModificationNotificationDTO> notificationDTOs = Tools.mapModificationNotificationsToModificationNotificationDTO(notifications);
            partRevisionDTO.setNotifications(notificationDTOs);

            partRevisionDTOs.add(partRevisionDTO);
        }

        return Response.ok(new GenericEntity<List<PartRevisionDTO>>((List<PartRevisionDTO>) partRevisionDTOs) {
        }).build();
    }


    @PUT
    @ApiOperation(value = "Approve or reject task on a document or part revision",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful task process"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{taskId}/process")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response processTask(@ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
                                @ApiParam(required = true, value = "Task id") @PathParam("taskId") String taskId,
                                @ApiParam(required = true, value = "Task process data") TaskProcessDTO taskProcessDTO)
            throws EntityNotFoundException, NotAllowedException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        String[] split = taskId.split("-");

        if (split.length != 3) {
            throw new BadRequestException();
        }


        int workflowId = Integer.parseInt(split[0]);
        int step = Integer.parseInt(split[1]);
        int index = Integer.parseInt(split[2]);

        TaskWrapper taskWrapper = taskManager.getTask(workspaceId, new TaskKey(new ActivityKey(workflowId, step), index));

        TaskAction action = taskProcessDTO.getAction();
        TaskKey taskKey = taskWrapper.getTask().getKey();
        String comment = taskProcessDTO.getComment();
        String signature = taskProcessDTO.getSignature();

        switch (taskWrapper.getHolderType()) {
            case "documents":
                DocumentRevisionKey documentRevisionKey = new DocumentRevisionKey(taskWrapper.getWorkspaceId(), taskWrapper.getHolderReference(), taskWrapper.getHolderVersion());
                if (TaskAction.APPROVE.equals(action)) {
                    documentWorkflowManager.approveTaskOnDocument(workspaceId, taskKey, documentRevisionKey, comment, signature);
                } else if (TaskAction.REJECT.equals(action)) {
                    documentWorkflowManager.rejectTaskOnDocument(workspaceId, taskKey, documentRevisionKey, comment, signature);
                } else {
                    throw new BadRequestException();
                }
                break;
            case "parts":
                PartRevisionKey partRevisionKey = new PartRevisionKey(taskWrapper.getWorkspaceId(), taskWrapper.getHolderReference(), taskWrapper.getHolderVersion());
                if (TaskAction.APPROVE.equals(action)) {
                    partWorkflowManager.approveTaskOnPart(workspaceId, taskKey, partRevisionKey, comment, signature);
                } else if (TaskAction.REJECT.equals(action)) {
                    partWorkflowManager.rejectTaskOnPart(workspaceId, taskKey, partRevisionKey, comment, signature);
                }
                break;
            case "workspace-workflows":
                if (TaskAction.APPROVE.equals(action)) {
                    workflowManager.approveTaskOnWorkspaceWorkflow(workspaceId, taskKey, comment, signature);
                } else if (TaskAction.REJECT.equals(action)) {
                    workflowManager.rejectTaskOnWorkspaceWorkflow(workspaceId, taskKey, comment, signature);
                }
                break;
            default:
                throw new ForbiddenException();
        }

        return Response.noContent().build();
    }

}
