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
import org.polarsys.eplmp.core.configuration.ProductConfiguration;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.product.ConfigurationItemKey;
import org.polarsys.eplmp.core.product.PartLink;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IProductBaselineManagerLocal;
import org.polarsys.eplmp.core.services.IProductManagerLocal;
import org.polarsys.eplmp.server.rest.dto.ACLDTO;
import org.polarsys.eplmp.server.rest.dto.LightPartLinkDTO;
import org.polarsys.eplmp.server.rest.dto.LightPartLinkListDTO;
import org.polarsys.eplmp.server.rest.dto.baseline.ProductConfigurationDTO;

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
import java.util.Map;

/**
 * @author Morgan Guimard
 */
@RequestScoped
@Api(hidden = true, value = "productConfigurations", description = "Operations about product configurations",
        authorizations = {@Authorization(value = "authorization")})
@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
public class ProductConfigurationsResource {

    @Inject
    private IProductBaselineManagerLocal productBaselineService;

    @Inject
    private IProductManagerLocal productService;

    private Mapper mapper;

    public ProductConfigurationsResource() {
    }

    @PostConstruct
    public void init() {
        mapper = DozerBeanMapperSingletonWrapper.getInstance();
    }

    @GET
    @ApiOperation(value = "Get all product configurations in workspace",
            response = ProductConfigurationDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of ProductConfigurationDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllConfigurations(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        List<ProductConfiguration> allProductConfigurations = productBaselineService.getAllProductConfigurations(workspaceId);
        return makeList(allProductConfigurations);
    }


    @GET
    @Path("{ciId}/configurations")
    @ApiOperation(value = "Get all product configurations for given product",
            response = ProductConfigurationDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of ProductConfigurationDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfigurationsForProduct(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id filter") @PathParam("ciId") String ciId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, ciId);
        List<ProductConfiguration> allProductConfigurations = productBaselineService.getAllProductConfigurationsByConfigurationItemId(ciKey);
        return makeList(allProductConfigurations);

    }


    @GET
    @ApiOperation(value = "Get product configuration by id",
            response = ProductConfigurationDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of ProductConfigurationDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/configurations/{productConfigurationId}")
    @Produces(MediaType.APPLICATION_JSON)
    public ProductConfigurationDTO getConfiguration(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String ciId,
            @ApiParam(required = true, value = "Product configuration id") @PathParam("productConfigurationId") int productConfigurationId)
            throws EntityNotFoundException, UserNotActiveException, EntityConstraintException, NotAllowedException,
            AccessRightException, WorkspaceNotEnabledException {

        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, ciId);
        ProductConfiguration productConfiguration = productBaselineService.getProductConfiguration(ciKey, productConfigurationId);
        ProductConfigurationDTO productConfigurationDTO = mapper.map(productConfiguration, ProductConfigurationDTO.class);
        productConfigurationDTO.setConfigurationItemId(productConfiguration.getConfigurationItem().getId());

        List<LightPartLinkListDTO> substitutesParts = new ArrayList<>();
        List<LightPartLinkListDTO> optionalParts = new ArrayList<>();

        for (String path : productConfiguration.getSubstituteLinks()) {
            LightPartLinkListDTO partDTOs = new LightPartLinkListDTO();
            for (PartLink partLink : productService.decodePath(ciKey, path)) {
                partDTOs.getPartLinks().add(new LightPartLinkDTO(partLink.getComponent().getNumber(), partLink.getComponent().getName(), partLink.getReferenceDescription(), partLink.getFullId()));
            }
            substitutesParts.add(partDTOs);
        }

        for (String path : productConfiguration.getOptionalUsageLinks()) {
            LightPartLinkListDTO partDTOs = new LightPartLinkListDTO();
            for (PartLink partLink : productService.decodePath(ciKey, path)) {
                partDTOs.getPartLinks().add(new LightPartLinkDTO(partLink.getComponent().getNumber(), partLink.getComponent().getName(), partLink.getReferenceDescription(), partLink.getFullId()));
            }
            optionalParts.add(partDTOs);
        }

        productConfigurationDTO.setSubstitutesParts(substitutesParts);
        productConfigurationDTO.setOptionalsParts(optionalParts);

        return productConfigurationDTO;
    }

    @POST
    @ApiOperation(value = "Create a new product configuration",
            response = ProductConfigurationDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of created ProductConfigurationDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ProductConfigurationDTO createConfiguration(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Product configuration to create") ProductConfigurationDTO pProductConfigurationDTO)
            throws EntityNotFoundException, UserNotActiveException, CreationException, AccessRightException,
            WorkspaceNotEnabledException {

        String ciId = pProductConfigurationDTO.getConfigurationItemId();
        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, ciId);
        String description = pProductConfigurationDTO.getDescription();
        String name = pProductConfigurationDTO.getName();

        ACLDTO acl = pProductConfigurationDTO.getAcl();
        Map<String, String> userEntries = acl != null ? acl.getUserEntriesMap() : null;
        Map<String, String> userGroupEntries = acl != null ? acl.getUserGroupEntriesMap() : null;

        ProductConfiguration productConfiguration = productBaselineService.createProductConfiguration(ciKey, name, description, pProductConfigurationDTO.getSubstituteLinks(), pProductConfigurationDTO.getOptionalUsageLinks(), userEntries, userGroupEntries);
        ProductConfigurationDTO productConfigurationDTO = mapper.map(productConfiguration, ProductConfigurationDTO.class);
        productConfigurationDTO.setConfigurationItemId(productConfiguration.getConfigurationItem().getId());
        return productConfigurationDTO;
    }


    @PUT
    @ApiOperation(value = "Update product configuration ACL",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful ACL update"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/configurations/{productConfigurationId}/acl")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateConfigurationACL(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String pCiId,
            @ApiParam(required = true, value = "Product configuration id") @PathParam("productConfigurationId") int productConfigurationId,
            @ApiParam(required = true, value = "ACL rules to set") ACLDTO acl)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, pCiId);

        if (acl.hasEntries()) {
            productBaselineService.updateACLForConfiguration(ciKey, productConfigurationId, acl.getUserEntriesMap(), acl.getUserGroupEntriesMap());
        } else {
            productBaselineService.removeACLFromConfiguration(ciKey, productConfigurationId);
        }

        return Response.noContent().build();
    }

    @DELETE
    @ApiOperation(value = "Delete product configuration",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion of ProductConfigurationDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/configurations/{productConfigurationId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteProductConfiguration(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String ciId,
            @ApiParam(required = true, value = "Product configuration id") @PathParam("productConfigurationId") int productConfigurationId)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, ciId);
        productBaselineService.deleteProductConfiguration(ciKey, productConfigurationId);

        return Response.noContent().build();
    }


    private Response makeList(List<ProductConfiguration> allProductConfigurations) {
        List<ProductConfigurationDTO> configurationDTOs = new ArrayList<>();
        for (ProductConfiguration productConfiguration : allProductConfigurations) {
            ProductConfigurationDTO productConfigurationDTO = mapper.map(productConfiguration, ProductConfigurationDTO.class);
            productConfigurationDTO.setConfigurationItemId(productConfiguration.getConfigurationItem().getId());
            configurationDTOs.add(productConfigurationDTO);
        }

        return Response.ok(new GenericEntity<List<ProductConfigurationDTO>>((List<ProductConfigurationDTO>) configurationDTOs) {
        }).build();
    }

}
