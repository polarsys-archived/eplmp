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
import org.polarsys.eplmp.core.exceptions.AccessRightException;
import org.polarsys.eplmp.core.exceptions.EntityNotFoundException;
import org.polarsys.eplmp.core.exceptions.UserNotActiveException;
import org.polarsys.eplmp.core.exceptions.WorkspaceNotEnabledException;
import org.polarsys.eplmp.core.product.ConfigurationItemKey;
import org.polarsys.eplmp.core.product.Layer;
import org.polarsys.eplmp.core.product.Marker;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IProductManagerLocal;
import org.polarsys.eplmp.server.rest.dto.LayerDTO;
import org.polarsys.eplmp.server.rest.dto.MarkerDTO;

import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Set;

/**
 * @author Florent Garin
 */

@RequestScoped
@Api(hidden = true, value = "layers", description = "Operations about layers",
        authorizations = {@Authorization(value = "authorization")})
@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
public class LayerResource {

    @Inject
    private IProductManagerLocal productService;

    public LayerResource() {
    }

    @GET
    @ApiOperation(value = "Get layers for given product",
            response = LayerDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of LayerDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public LayerDTO[] getLayersInProduct(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String ciId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, ciId);
        List<Layer> layers = productService.getLayers(ciKey);
        LayerDTO[] layerDTOs = new LayerDTO[layers.size()];
        for (int i = 0; i < layers.size(); i++) {
            layerDTOs[i] = new LayerDTO(layers.get(i).getId(), layers.get(i).getName(), layers.get(i).getColor());
        }
        return layerDTOs;
    }

    @POST
    @ApiOperation(value = "Create a new layer for given product",
            response = LayerDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of created LayerDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public LayerDTO createLayer(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String ciId,
            @ApiParam(required = true, value = "Layer to create") LayerDTO layer)
            throws EntityNotFoundException, AccessRightException, WorkspaceNotEnabledException {

        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, ciId);
        Layer l = productService.createLayer(ciKey, layer.getName(), layer.getColor());
        return new LayerDTO(l.getId(), l.getName(), l.getColor());
    }

    @PUT
    @ApiOperation(value = "Update layer",
            response = LayerDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated LayerDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{layerId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public LayerDTO updateLayer(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String ciId,
            @ApiParam(required = true, value = "Layer id") @PathParam("layerId") int layerId,
            @ApiParam(required = true, value = "Layer to update") LayerDTO layer)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, WorkspaceNotEnabledException {

        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, ciId);
        Layer l = productService.updateLayer(ciKey, layerId, layer.getName(), layer.getColor());
        return new LayerDTO(l.getId(), l.getName(), l.getColor());
    }

    @DELETE
    @ApiOperation(value = "Delete layer", response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion of LayerDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{layerId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteLayer(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String ciId,
            @ApiParam(required = true, value = "Layer id") @PathParam("layerId") int layerId)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, WorkspaceNotEnabledException {

        productService.deleteLayer(workspaceId, layerId);
        return Response.noContent().build();
    }

    @GET
    @ApiOperation(value = "Get markers of a given layer",
            response = MarkerDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of MarkerDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{layerId}/markers")
    @Produces(MediaType.APPLICATION_JSON)
    public MarkerDTO[] getMarkersInLayer(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String ciId,
            @ApiParam(required = true, value = "Layer id") @PathParam("layerId") int layerId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        Layer layer = productService.getLayer(layerId);
        Set<Marker> markers = layer.getMarkers();
        Marker[] markersArray = markers.toArray(new Marker[markers.size()]);
        MarkerDTO[] markersDTO = new MarkerDTO[markers.size()];
        for (int i = 0; i < markersArray.length; i++) {
            markersDTO[i] = new MarkerDTO(markersArray[i].getId(), markersArray[i].getTitle(), markersArray[i].getDescription(), markersArray[i].getX(), markersArray[i].getY(), markersArray[i].getZ());
        }
        return markersDTO;
    }

    @POST
    @ApiOperation(value = "Create a new marker in a given layer",
            response = MarkerDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of created MarkerDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{layerId}/markers")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public MarkerDTO createMarker(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String ciId,
            @ApiParam(required = true, value = "Layer id") @PathParam("layerId") int layerId,
            @ApiParam(required = true, value = "Marker to create") MarkerDTO markerDTO)
            throws EntityNotFoundException, AccessRightException, WorkspaceNotEnabledException {

        Marker marker = productService.createMarker(layerId, markerDTO.getTitle(), markerDTO.getDescription(), markerDTO.getX(), markerDTO.getY(), markerDTO.getZ());
        return new MarkerDTO(marker.getId(), marker.getTitle(), marker.getDescription(), marker.getX(), marker.getY(), marker.getZ());
    }

    @DELETE
    @ApiOperation(value = "Delete marker",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion of LayerDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{layerId}/markers/{markerId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteMarker(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String ciId,
            @ApiParam(required = true, value = "Layer id") @PathParam("layerId") int layerId,
            @ApiParam(required = true, value = "Marker id") @PathParam("markerId") int markerId)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, WorkspaceNotEnabledException {

        productService.deleteMarker(layerId, markerId);
        return Response.noContent().build();
    }
}
