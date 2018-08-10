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
import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.common.Workspace;
import org.polarsys.eplmp.core.exceptions.AccessRightException;
import org.polarsys.eplmp.core.exceptions.EntityNotFoundException;
import org.polarsys.eplmp.core.exceptions.UserNotActiveException;
import org.polarsys.eplmp.core.exceptions.WorkspaceNotEnabledException;
import org.polarsys.eplmp.core.notification.TagUserSubscription;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.INotificationManagerLocal;
import org.polarsys.eplmp.core.services.IUserManagerLocal;
import org.polarsys.eplmp.core.services.IWorkspaceManagerLocal;
import org.polarsys.eplmp.server.rest.dto.TagSubscriptionDTO;
import org.polarsys.eplmp.server.rest.dto.UserDTO;

import javax.annotation.PostConstruct;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.logging.Logger;

@RequestScoped
@Api(hidden = true, value = "users", description = "Operations about users",
        authorizations = {@Authorization(value = "authorization")})
@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
public class UserResource {


    @Inject
    private IWorkspaceManagerLocal workspaceManager;

    @Inject
    private IUserManagerLocal userManager;

    @Inject
    private INotificationManagerLocal notificationManager;

    private Mapper mapper;

    private static final Logger LOGGER = Logger.getLogger(UserResource.class.getName());

    public UserResource() {
    }

    @PostConstruct
    public void init() {
        mapper = DozerBeanMapperSingletonWrapper.getInstance();
    }

    @GET
    @ApiOperation(value = "Get users in workspace",
            response = UserDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of UserDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public UserDTO[] getUsersInWorkspace(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, WorkspaceNotEnabledException {

        User[] users = userManager.getUsers(workspaceId);
        UserDTO[] userDTOs = new UserDTO[users.length];

        for (int i = 0; i < users.length; i++) {
            userDTOs[i] = mapper.map(users[i], UserDTO.class);
        }

        return userDTOs;
    }

    @GET
    @ApiOperation(value = "Get authenticated user details",
            response = UserDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of UserDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    public UserDTO whoAmI(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        User user = userManager.whoAmI(workspaceId);
        return mapper.map(user, UserDTO.class);
    }

    @GET
    @ApiOperation(value = "Get admin for workspace",
            response = UserDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of UserDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("admin")
    @Produces(MediaType.APPLICATION_JSON)
    public UserDTO getAdminInWorkspace(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        Workspace workspace = workspaceManager.getWorkspace(workspaceId);
        UserDTO userDTO = mapper.map(workspace.getAdmin(), UserDTO.class);
        userDTO.setWorkspaceId(workspaceId);
        return userDTO;
    }


    @GET
    @ApiOperation(value = "Get tag subscriptions of user",
            response = TagSubscriptionDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of TagSubscriptionDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{login}/tag-subscriptions")
    @Produces(MediaType.APPLICATION_JSON)
    public TagSubscriptionDTO[] getTagSubscriptionsForUser(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "User login") @PathParam("login") String login)
            throws  AccessRightException, UserNotActiveException, EntityNotFoundException, WorkspaceNotEnabledException {

        List<TagUserSubscription> subs = notificationManager.getTagUserSubscriptionsByUser(workspaceId, login);

        TagSubscriptionDTO[] subDTOs = new TagSubscriptionDTO[subs.size()];
        for (int i = 0; i < subs.size(); i++) {
            subDTOs[i] = mapper.map(subs.get(i), TagSubscriptionDTO.class);
        }
        return subDTOs;
    }

    @PUT
    @ApiOperation(value = "Update or create tag subscription of user",
            response = TagSubscriptionDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Successful retrieval of created/updated TagSubscriptionDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{login}/tag-subscriptions/{tagName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateUserSubscription(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "User login") @PathParam("login") String login,
            @ApiParam(required = true, value = "Tag name") @PathParam("tagName") String tagName,
            @ApiParam(required = true, value = "Tag subscription to update or create") TagSubscriptionDTO subDTO)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, WorkspaceNotEnabledException {

        notificationManager.createOrUpdateTagUserSubscription(workspaceId,
                login,
                tagName,
                subDTO.isOnIterationChange(),
                subDTO.isOnStateChange());
        subDTO.setTag(tagName);

        return Tools.prepareCreatedResponse(tagName, subDTO);
    }

    @DELETE
    @ApiOperation(value = "Delete tag subscription of user",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion of TagSubscriptionDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{login}/tag-subscriptions/{tagName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteUserSubscription(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "User login") @PathParam("login") String login,
            @ApiParam(required = true, value = "Tag name") @PathParam("tagName") String tagName)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, WorkspaceNotEnabledException {

        notificationManager.removeTagUserSubscription(workspaceId, login, tagName);
        return Response.noContent().build();
    }
}

