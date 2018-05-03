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


import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IWorkflowManagerLocal;
import org.polarsys.eplmp.core.workflow.Role;
import org.polarsys.eplmp.core.workflow.RoleKey;
import org.polarsys.eplmp.server.rest.dto.RoleDTO;
import org.polarsys.eplmp.server.rest.dto.UserDTO;
import org.polarsys.eplmp.server.rest.dto.UserGroupDTO;
import io.swagger.annotations.*;
import org.dozer.DozerBeanMapperSingletonWrapper;
import org.dozer.Mapper;

import javax.annotation.PostConstruct;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Morgan Guimard
 */
@RequestScoped
@Api(hidden = true, value = "roles", description = "Operations about roles")
@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
public class RoleResource {

    private static final Logger LOGGER = Logger.getLogger(RoleResource.class.getName());

    @Inject
    private IWorkflowManagerLocal roleService;

    private Mapper mapper;

    public RoleResource() {
    }

    @PostConstruct
    public void init() {
        mapper = DozerBeanMapperSingletonWrapper.getInstance();
    }

    @GET
    @ApiOperation(value = "Get roles in given workspace",
            response = RoleDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of RoleDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRolesInWorkspace(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException {

        Role[] roles = roleService.getRoles(workspaceId);
        List<RoleDTO> rolesDTO = new ArrayList<>();

        for (Role role :roles) {
            rolesDTO.add(mapRoleToDTO(role));
        }

        return Response.ok(new GenericEntity<List<RoleDTO>>((List<RoleDTO>) rolesDTO) {
        }).build();
    }

    @GET
    @ApiOperation(value = "Get roles in use in given workspace",
            response = RoleDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of RoleDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("inuse")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRolesInUseInWorkspace(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException {

        Role[] roles = roleService.getRolesInUse(workspaceId);
        List<RoleDTO> rolesDTO = new ArrayList<>();

        for (Role role :roles) {
            rolesDTO.add(mapRoleToDTO(role));
        }

        return Response.ok(new GenericEntity<List<RoleDTO>>((List<RoleDTO>) rolesDTO) {
        }).build();

    }


    @POST
    @ApiOperation(value = "Create a new role",
            response = RoleDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of created RoleDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createRole(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Role to create") RoleDTO roleDTO)
            throws EntityNotFoundException, EntityAlreadyExistsException, UserNotActiveException, AccessRightException, CreationException {

        List<UserDTO> userDTOs = roleDTO.getDefaultAssignedUsers();
        List<UserGroupDTO> groupDTOs = roleDTO.getDefaultAssignedGroups();
        List<String> userLogins = new ArrayList<>();
        List<String> userGroupIds = new ArrayList<>();
        if (userDTOs != null) {
            for (UserDTO userDTO : userDTOs) {
                userLogins.add(userDTO.getLogin());
            }
        }
        if (groupDTOs != null) {
            for (UserGroupDTO groupDTO : groupDTOs) {
                userGroupIds.add(groupDTO.getId());
            }
        }

        Role roleCreated = roleService.createRole(roleDTO.getName(), roleDTO.getWorkspaceId(), userLogins, userGroupIds);
        RoleDTO roleCreatedDTO = mapRoleToDTO(roleCreated);

        try {
            return Response.created(URI.create(URLEncoder.encode(roleCreatedDTO.getName(), "UTF-8"))).entity(roleCreatedDTO).build();
        } catch (UnsupportedEncodingException ex) {
            LOGGER.log(Level.WARNING, null, ex);
            return Response.ok().entity(roleCreatedDTO).build();
        }
    }

    @PUT
    @ApiOperation(value = "Update a role",
            response = RoleDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated RoleDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{roleName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateRole(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Role name") @PathParam("roleName") String roleName,
            @ApiParam(required = true, value = "Role to update") RoleDTO roleDTO)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException {

        List<UserDTO> userDTOs = roleDTO.getDefaultAssignedUsers();
        List<UserGroupDTO> groupDTOs = roleDTO.getDefaultAssignedGroups();
        List<String> userLogins = new ArrayList<>();
        List<String> userGroupIds = new ArrayList<>();
        if (userDTOs != null) {
            for (UserDTO userDTO : userDTOs) {
                userLogins.add(userDTO.getLogin());
            }
        }
        if (groupDTOs != null) {
            for (UserGroupDTO groupDTO : groupDTOs) {
                userGroupIds.add(groupDTO.getId());
            }
        }

        Role roleUpdated = roleService.updateRole(new RoleKey(roleDTO.getWorkspaceId(), roleName), userLogins, userGroupIds);
        RoleDTO roleUpdatedDTO = mapRoleToDTO(roleUpdated);
        try {
            return Response.created(URI.create(URLEncoder.encode(roleUpdatedDTO.getName(), "UTF-8"))).entity(roleUpdatedDTO).build();
        } catch (UnsupportedEncodingException ex) {
            LOGGER.log(Level.WARNING, null, ex);
            return Response.ok().entity(roleUpdatedDTO).build();
        }
    }

    @DELETE
    @ApiOperation(value = "Delete a role",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion of RoleDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{roleName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteRole(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Role name") @PathParam("roleName") String roleName)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, EntityConstraintException {

        RoleKey roleKey = new RoleKey(workspaceId, roleName);
        roleService.deleteRole(roleKey);
        return Response.noContent().build();
    }

    private RoleDTO mapRoleToDTO(Role role) {
        RoleDTO roleDTO = mapper.map(role, RoleDTO.class);
        roleDTO.setWorkspaceId(role.getWorkspace().getId());
        roleDTO.setId(role.getName());
        return roleDTO;
    }

}
