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
import org.polarsys.eplmp.core.product.*;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IEffectivityManagerLocal;
import org.polarsys.eplmp.core.services.IProductManagerLocal;
import org.polarsys.eplmp.server.rest.dto.EffectivityDTO;

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
import java.util.Set;

@RequestScoped
@Api(hidden = true, value = "partEffectivities", description = "Operation about parts effectivities",
        authorizations = {@Authorization(value = "authorization")})
@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
public class PartEffectivityResource {


    @Inject
    private IEffectivityManagerLocal effectivityManager;

    @Inject
    private IProductManagerLocal productManager;

    private Mapper mapper;

    public PartEffectivityResource() {
    }

    @PostConstruct
    public void init() {
        mapper = DozerBeanMapperSingletonWrapper.getInstance();
    }

    @POST
    @ApiOperation(value = "Create an Effectivity for a given PartRevision",
            response = EffectivityDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of created effectivity"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public EffectivityDTO createEffectivity(
            @ApiParam(required = true, value = "Effectivity to create") EffectivityDTO effectivity,
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Part revision number") @PathParam("partNumber") String partNumber,
            @ApiParam(required = true, value = "Part revision version") @PathParam("partVersion") String partVersion)
            throws UserNotActiveException, EntityNotFoundException, AccessRightException, WorkspaceNotEnabledException, EntityAlreadyExistsException, CreationException {

        TypeEffectivity typeEffectivity = effectivity.getTypeEffectivity();
        Effectivity createdEffectivity = null;
        EffectivityDTO returnedEffectivityDTO;

        ConfigurationItemKey configurationItemKey = effectivity.getConfigurationItemKey();
        String productId = configurationItemKey != null ? configurationItemKey.getId() : null;

        if (TypeEffectivity.DATEBASEDEFFECTIVITY.equals(typeEffectivity)) {
            createdEffectivity = effectivityManager.createDateBasedEffectivity(
                    workspaceId, partNumber, partVersion, effectivity.getName(), effectivity.getDescription(), productId, effectivity.getStartDate(), effectivity.getEndDate());
        } else if (TypeEffectivity.SERIALNUMBERBASEDEFFECTIVITY.equals(typeEffectivity)) {
            createdEffectivity = effectivityManager.createSerialNumberBasedEffectivity(
                    workspaceId, partNumber, partVersion, effectivity.getName(), effectivity.getDescription(), productId, effectivity.getStartNumber(),
                    effectivity.getEndNumber());
        } else if (TypeEffectivity.LOTBASEDEFFECTIVITY.equals(typeEffectivity)) {
            createdEffectivity = effectivityManager.createLotBasedEffectivity(
                    workspaceId, partNumber, partVersion, effectivity.getName(), effectivity.getDescription(), productId, effectivity.getStartLotId(),
                    effectivity.getEndLotId());
        }
        // todo : prevent Mapping exception if createdEffectivity is null => throw a bad request status

        returnedEffectivityDTO = mapper.map(createdEffectivity, EffectivityDTO.class);
        returnedEffectivityDTO.setConfigurationItemKey(configurationItemKey);

        returnedEffectivityDTO.setTypeEffectivity(effectivity.getTypeEffectivity());
        return returnedEffectivityDTO;
    }

    @GET
    @ApiOperation(value = "Get effectivities of a given PartRevision",
            response = EffectivityDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of effectivities. It can be an empty list"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEffectivities(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Part revision number") @PathParam("partNumber") String partNumber,
            @ApiParam(required = true, value = "Part revision version") @PathParam("partVersion") String partVersion)
            throws UserNotActiveException, EntityNotFoundException, AccessRightException, WorkspaceNotEnabledException {

        PartRevision partRevision = productManager.getPartRevision(new PartRevisionKey(workspaceId, partNumber, partVersion));

        Set<Effectivity> effectivitySet = partRevision.getEffectivities();
        List<EffectivityDTO> effectivityDTOs = new ArrayList<>();

        for (Effectivity effectivity : effectivitySet) {

            EffectivityDTO effectivityDTO = mapper.map(effectivity, EffectivityDTO.class);
            ConfigurationItem configurationItem = effectivity.getConfigurationItem();
            effectivityDTO.setConfigurationItemKey(configurationItem != null ? configurationItem.getKey() : null);

            if (effectivity.getClass().equals(SerialNumberBasedEffectivity.class)) {
                effectivityDTO.setTypeEffectivity(TypeEffectivity.SERIALNUMBERBASEDEFFECTIVITY);
            } else if (effectivity.getClass().equals(DateBasedEffectivity.class)) {
                effectivityDTO.setTypeEffectivity(TypeEffectivity.DATEBASEDEFFECTIVITY);
            } else if (effectivity.getClass().equals(LotBasedEffectivity.class)) {
                effectivityDTO.setTypeEffectivity(TypeEffectivity.LOTBASEDEFFECTIVITY);
            }

            effectivityDTOs.add(effectivityDTO);
        }

        return Response.ok(new GenericEntity<List<EffectivityDTO>>(effectivityDTOs) {
        }).build();
    }

    @DELETE
    @ApiOperation(value = "Delete effectivity from given part revision",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion of effectivity"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{effectivityId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteEffectivity(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Part revision number") @PathParam("partNumber") String partNumber,
            @ApiParam(required = true, value = "Part revision version") @PathParam("partVersion") String partVersion,
            @ApiParam(required = true, value = "Effectivity id") @PathParam("effectivityId") int effectivityId)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {
        effectivityManager.deleteEffectivity(workspaceId, partNumber, partVersion, effectivityId);
        return Response.noContent().build();
    }

}
