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
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IWorkflowManagerLocal;
import org.polarsys.eplmp.core.workflow.ActivityModel;
import org.polarsys.eplmp.core.workflow.WorkflowModel;
import org.polarsys.eplmp.core.workflow.WorkflowModelKey;
import org.polarsys.eplmp.server.rest.dto.ACLDTO;
import org.polarsys.eplmp.server.rest.dto.ActivityModelDTO;
import org.polarsys.eplmp.server.rest.dto.WorkflowModelDTO;

import javax.annotation.PostConstruct;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Morgan Guimard
 */
@RequestScoped
@Api(hidden = true, value = "workflowModels", description = "Operations about workflow models",
        authorizations = {@Authorization(value = "authorization")})
@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
public class WorkflowModelResource {

    @Inject
    private IWorkflowManagerLocal workflowService;

    private Mapper mapper;

    public WorkflowModelResource() {
    }

    @PostConstruct
    public void init() {
        mapper = DozerBeanMapperSingletonWrapper.getInstance();
    }

    @GET
    @ApiOperation(value = "Get workflow models in given workspace",
            response = WorkflowModelDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of WorkflowModelDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public WorkflowModelDTO[] getWorkflowModelsInWorkspace(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        WorkflowModel[] workflowModels = workflowService.getWorkflowModels(workspaceId);
        WorkflowModelDTO[] workflowModelDTOs = new WorkflowModelDTO[workflowModels.length];

        for (int i = 0; i < workflowModels.length; i++) {
            workflowModelDTOs[i] = mapper.map(workflowModels[i], WorkflowModelDTO.class);
        }

        return workflowModelDTOs;
    }

    @GET
    @ApiOperation(value = "Get workflow model by id",
            response = WorkflowModelDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of WorkflowModelDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{workflowModelId}")
    @Produces(MediaType.APPLICATION_JSON)
    public WorkflowModelDTO getWorkflowModelInWorkspace(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Workflow model id") @PathParam("workflowModelId") String workflowModelId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        WorkflowModel workflowModel = workflowService.getWorkflowModel(new WorkflowModelKey(workspaceId, workflowModelId));
        return mapper.map(workflowModel, WorkflowModelDTO.class);
    }

    @DELETE
    @ApiOperation(value = "Delete a workflow model",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion of WorkflowModelDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{workflowModelId}")
    public Response delWorkflowModel(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Workflow model id") @PathParam("workflowModelId") String workflowModelId)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, EntityConstraintException, WorkspaceNotEnabledException {
        workflowService.deleteWorkflowModel(new WorkflowModelKey(workspaceId, workflowModelId));
        return Response.noContent().build();
    }

    @PUT
    @ApiOperation(value = "Update a workflow model",
            response = WorkflowModelDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated WorkflowModelDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{workflowModelId}")
    @Produces(MediaType.APPLICATION_JSON)
    public WorkflowModelDTO updateWorkflowModel(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Workflow model id") @PathParam("workflowModelId") String workflowModelId,
            @ApiParam(required = true, value = "Workflow model to update") WorkflowModelDTO workflowModelDTOToPersist)
            throws EntityNotFoundException, AccessRightException, EntityAlreadyExistsException,
            CreationException, UserNotActiveException, NotAllowedException, WorkspaceNotEnabledException {

        WorkflowModelKey workflowModelKey = new WorkflowModelKey(workspaceId, workflowModelId);
        List<ActivityModelDTO> activityModelDTOsList = workflowModelDTOToPersist.getActivityModels();
        ActivityModel[] activityModels = extractActivityModelFromDTO(activityModelDTOsList);
        WorkflowModel workflowModel = workflowService.updateWorkflowModel(workflowModelKey,
                workflowModelDTOToPersist.getFinalLifeCycleState(), activityModels);
        return mapper.map(workflowModel, WorkflowModelDTO.class);
    }

    @PUT
    @ApiOperation(value = "Update workflow model ACL",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful update of WorkflowModelDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{workflowModelId}/acl")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateWorkflowModelACL(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String pWorkspaceId,
            @ApiParam(required = true, value = "Workflow model id") @PathParam("workflowModelId") String workflowModelId,
            @ApiParam(required = true, value = "ACL rules to set") ACLDTO acl)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        if (acl.hasEntries()) {
            workflowService.updateACLForWorkflow(pWorkspaceId, workflowModelId, acl.getUserEntriesMap(), acl.getUserGroupEntriesMap());
        } else {
            workflowService.removeACLFromWorkflow(pWorkspaceId, workflowModelId);
        }
        return Response.noContent().build();
    }

    @POST
    @ApiOperation(value = "Create a new workflow model",
            response = WorkflowModelDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of created WorkflowModelDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WorkflowModelDTO createWorkflowModel(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Workflow model to create rules to set") WorkflowModelDTO workflowModelDTOToPersist)
            throws EntityNotFoundException, EntityAlreadyExistsException, UserNotActiveException, NotAllowedException,
            AccessRightException, CreationException, WorkspaceNotEnabledException {

        List<ActivityModelDTO> activityModelDTOsList = workflowModelDTOToPersist.getActivityModels();
        ActivityModel[] activityModels = extractActivityModelFromDTO(activityModelDTOsList);
        WorkflowModel workflowModel = workflowService.createWorkflowModel(workspaceId, workflowModelDTOToPersist.getReference(), workflowModelDTOToPersist.getFinalLifeCycleState(), activityModels);
        return mapper.map(workflowModel, WorkflowModelDTO.class);
    }

    private ActivityModel[] extractActivityModelFromDTO(List<ActivityModelDTO> activityModelDTOsList) throws NotAllowedException {
        Map<Integer, ActivityModel> activityModels = new HashMap<>();

        for (int i = 0; i < activityModelDTOsList.size(); i++) {
            ActivityModelDTO activityModelDTO = activityModelDTOsList.get(i);
            ActivityModel activityModel = mapper.map(activityModelDTO, ActivityModel.class);
            activityModels.put(activityModel.getStep(), activityModel);

            Integer relaunchStep = activityModelDTO.getRelaunchStep();
            if (relaunchStep != null && relaunchStep < i) {
                ActivityModel relaunchActivity = activityModels.get(relaunchStep);
                activityModel.setRelaunchActivity(relaunchActivity);
            }
        }

        return activityModels.values().toArray(new ActivityModel[activityModels.size()]);
    }

}
