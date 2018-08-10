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
import org.polarsys.eplmp.core.common.BinaryResource;
import org.polarsys.eplmp.core.configuration.*;
import org.polarsys.eplmp.core.document.DocumentRevisionKey;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.meta.InstanceAttribute;
import org.polarsys.eplmp.core.meta.InstanceAttributeTemplate;
import org.polarsys.eplmp.core.product.*;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IImporterManagerLocal;
import org.polarsys.eplmp.core.services.IPSFilterManagerLocal;
import org.polarsys.eplmp.core.services.IProductInstanceManagerLocal;
import org.polarsys.eplmp.core.services.IProductManagerLocal;
import org.polarsys.eplmp.server.rest.dto.*;
import org.polarsys.eplmp.server.rest.dto.baseline.ProductBaselineDTO;
import org.polarsys.eplmp.server.rest.dto.product.ProductInstanceCreationDTO;
import org.polarsys.eplmp.server.rest.dto.product.ProductInstanceIterationDTO;
import org.polarsys.eplmp.server.rest.dto.product.ProductInstanceMasterDTO;
import org.polarsys.eplmp.server.rest.file.util.BinaryResourceUpload;

import javax.annotation.PostConstruct;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Taylor LABEJOF
 */
@RequestScoped
@Api(hidden = true, value = "productInstances", description = "Operations about product-instances",
        authorizations = {@Authorization(value = "authorization")})
@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
public class ProductInstancesResource {

    private static final Logger LOGGER = Logger.getLogger(ProductInstancesResource.class.getName());
    @Inject
    private IProductInstanceManagerLocal productInstanceService;
    @Inject
    private IProductManagerLocal productService;
    @Inject
    private IPSFilterManagerLocal psFilterService;
    @Inject
    private IImporterManagerLocal importerService;
    private Mapper mapper;

    public ProductInstancesResource() {
    }

    @PostConstruct
    public void init() {
        mapper = DozerBeanMapperSingletonWrapper.getInstance();
    }

    @GET
    @ApiOperation(value = "Get product instances in given workspace",
            response = ProductInstanceMasterDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of ProductInstanceMasterDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllProductInstances(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        List<ProductInstanceMaster> productInstanceMasterList = productInstanceService.getProductInstanceMasters(workspaceId);
        return makeList(productInstanceMasterList);
    }

    @GET
    @Path("{ciId}/instances")
    @ApiOperation(value = "Get product-instance with given configuration item",
            response = ProductInstanceMasterDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of ProductInstanceMasterDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProductInstances(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String ciId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        ConfigurationItemKey configurationItemKey = new ConfigurationItemKey(workspaceId, ciId);
        List<ProductInstanceMaster> productInstanceMasterList = productInstanceService.getProductInstanceMasters(configurationItemKey);
        return makeList(productInstanceMasterList);
    }

    @POST
    @ApiOperation(value = "Create a new product-instance",
            response = ProductInstanceMasterDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of ProductInstanceMasterDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ProductInstanceMasterDTO createProductInstanceMaster(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Product instance master to create") ProductInstanceCreationDTO productInstanceCreationDTO)
            throws EntityNotFoundException, EntityAlreadyExistsException, AccessRightException, CreationException,
            NotAllowedException, EntityConstraintException, UserNotActiveException, WorkspaceNotEnabledException {


        List<InstanceAttributeDTO> instanceAttributeDTOs = productInstanceCreationDTO.getInstanceAttributes();
        List<InstanceAttribute> attributes = new ArrayList<>();

        if (instanceAttributeDTOs != null) {
            for (InstanceAttributeDTO dto : instanceAttributeDTOs) {
                dto.setWorkspaceId(workspaceId);
                attributes.add(mapper.map(dto, InstanceAttribute.class));
            }
        }

        ACLDTO acl = productInstanceCreationDTO.getAcl();
        Map<String, String> userEntries = acl != null ? acl.getUserEntriesMap() : null;
        Map<String, String> userGroupEntries = acl != null ? acl.getUserGroupEntriesMap() : null;

        Set<DocumentRevisionDTO> linkedDocs = productInstanceCreationDTO.getLinkedDocuments();
        DocumentRevisionKey[] links = null;
        String[] documentLinkComments = null;
        if (linkedDocs != null) {
            documentLinkComments = new String[linkedDocs.size()];
            links = createDocumentRevisionKeys(linkedDocs);
            int i = 0;
            for (DocumentRevisionDTO docRevisionForLink : linkedDocs) {
                String comment = docRevisionForLink.getCommentLink();
                if (comment == null) {
                    comment = "";
                }
                documentLinkComments[i++] = comment;
            }
        }
        ProductInstanceMaster productInstanceMaster = productInstanceService.createProductInstance(workspaceId, new ConfigurationItemKey(workspaceId, productInstanceCreationDTO.getConfigurationItemId()), productInstanceCreationDTO.getSerialNumber(), productInstanceCreationDTO.getBaselineId(), userEntries, userGroupEntries, attributes, links, documentLinkComments);

        return mapper.map(productInstanceMaster, ProductInstanceMasterDTO.class);
    }

    @PUT
    @ApiOperation(value = "Update product-instance",
            response = ProductInstanceMasterDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated ProductInstanceMasterDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/iterations/{iteration}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ProductInstanceMasterDTO updateProductInstanceMaster(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Product instance iteration") @PathParam("iteration") int iteration,
            @ApiParam(required = true, value = "Product instance master to update") ProductInstanceIterationDTO productInstanceCreationDTO)
            throws EntityNotFoundException, EntityAlreadyExistsException, AccessRightException, CreationException,
            UserNotActiveException, WorkspaceNotEnabledException {

        List<InstanceAttributeDTO> instanceAttributes = productInstanceCreationDTO.getInstanceAttributes();
        List<InstanceAttribute> attributes = new ArrayList<>();

        if (instanceAttributes != null) {
            for (InstanceAttributeDTO dto : instanceAttributes) {
                dto.setWorkspaceId(workspaceId);
                attributes.add(mapper.map(dto, InstanceAttribute.class));
            }
        }

        Set<DocumentRevisionDTO> linkedDocs = productInstanceCreationDTO.getLinkedDocuments();
        DocumentRevisionKey[] links = null;
        String[] documentLinkComments = null;
        if (linkedDocs != null) {
            documentLinkComments = new String[linkedDocs.size()];
            links = createDocumentRevisionKeys(linkedDocs);
            int i = 0;
            for (DocumentRevisionDTO docRevisionForLink : linkedDocs) {
                String comment = docRevisionForLink.getCommentLink();
                if (comment == null) {
                    comment = "";
                }
                documentLinkComments[i++] = comment;
            }
        }

        ProductInstanceMaster productInstanceMaster =
                productInstanceService.updateProductInstance(workspaceId, iteration,
                        productInstanceCreationDTO.getIterationNote(),
                        new ConfigurationItemKey(workspaceId, configurationItemId),
                        productInstanceCreationDTO.getSerialNumber(),
                        productInstanceCreationDTO.getBasedOn().getId(), attributes, links, documentLinkComments);

        return mapper.map(productInstanceMaster, ProductInstanceMasterDTO.class);
    }

    @GET
    @ApiOperation(value = "Get product-instance by serial number",
            response = ProductInstanceMasterDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of ProductInstanceMasterDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}")
    @Produces(MediaType.APPLICATION_JSON)
    public ProductInstanceMasterDTO getProductInstance(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        ProductInstanceMaster productInstanceMaster = productInstanceService.getProductInstanceMaster(new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));
        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, configurationItemId);
        ProductInstanceMasterDTO dto = mapper.map(productInstanceMaster, ProductInstanceMasterDTO.class);


        List<ProductInstanceIterationDTO> productInstanceIterationsDTO = dto.getProductInstanceIterations();
        List<ProductInstanceIteration> productInstanceIterations = productInstanceMaster.getProductInstanceIterations();
        Iterator<ProductInstanceIteration> iterationIterator = productInstanceIterations.iterator();
        for (ProductInstanceIterationDTO productInstanceIterationDTO : productInstanceIterationsDTO) {

            List<LightPartLinkListDTO> substitutesParts = new ArrayList<>();
            List<LightPartLinkListDTO> optionalParts = new ArrayList<>();
            try {
                productInstanceIterationDTO.setPathToPathLinks(getPathToPathLinksForGivenProductInstance(iterationIterator.next()));
            } catch (AccessRightException e) {
                LOGGER.log(Level.FINEST, null, e);
            }
            for (String path : productInstanceIterationDTO.getSubstituteLinks()) {
                LightPartLinkListDTO lightPartLinkDTO = new LightPartLinkListDTO();
                for (PartLink partLink : productService.decodePath(ciKey, path)) {
                    lightPartLinkDTO.getPartLinks().add(new LightPartLinkDTO(partLink.getComponent().getNumber(), partLink.getComponent().getName(), partLink.getReferenceDescription(), partLink.getFullId()));
                }
                substitutesParts.add(lightPartLinkDTO);
            }
            for (String path : productInstanceIterationDTO.getOptionalUsageLinks()) {
                LightPartLinkListDTO lightPartLinkDTO = new LightPartLinkListDTO();
                for (PartLink partLink : productService.decodePath(ciKey, path)) {
                    lightPartLinkDTO.getPartLinks().add(new LightPartLinkDTO(partLink.getComponent().getNumber(), partLink.getComponent().getName(), partLink.getReferenceDescription(), partLink.getFullId()));
                }
                optionalParts.add(lightPartLinkDTO);
            }

            productInstanceIterationDTO.setSubstitutesParts(substitutesParts);
            productInstanceIterationDTO.setOptionalsParts(optionalParts);

            List<LightPartLinkListDTO> pathDataPaths = new ArrayList<>();
            for (PathDataMasterDTO pathDataMasterDTO : productInstanceIterationDTO.getPathDataMasterList()) {
                LightPartLinkListDTO lightPartLinkListDTO = new LightPartLinkListDTO();
                for (PartLink partLink : productService.decodePath(ciKey, pathDataMasterDTO.getPath())) {
                    lightPartLinkListDTO.getPartLinks().add(new LightPartLinkDTO(partLink.getComponent().getNumber(), partLink.getComponent().getName(), partLink.getReferenceDescription(), partLink.getFullId()));
                }
                pathDataPaths.add(lightPartLinkListDTO);
            }
            productInstanceIterationDTO.setPathDataPaths(pathDataPaths);
        }

        return dto;
    }

    @DELETE
    @ApiOperation(value = "Remove attached file from product-instance",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion of file of ProductInstanceMasterDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{ciId}/instances/{serialNumber}/iterations/{iteration}/files/{fileName}")
    public Response removeAttachedFileFromProductInstance(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Product instance iteration") @PathParam("iteration") int iteration,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "File name") @PathParam("fileName") String fileName)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, WorkspaceNotEnabledException {

        String fullName = workspaceId + "/product-instances/" + serialNumber + "/iterations/" + iteration + "/" + fileName;
        productInstanceService.removeFileFromProductInstanceIteration(workspaceId, iteration, fullName, new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));
        return Response.noContent().build();
    }


    @PUT
    @ApiOperation(value = "Update product-instance's ACL",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful update of ProductInstanceMasterDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/acl")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateProductInstanceACL(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "ACL to set") ACLDTO acl)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, NotAllowedException, WorkspaceNotEnabledException {

        if (acl.hasEntries()) {
            productInstanceService.updateACLForProductInstanceMaster(workspaceId, configurationItemId, serialNumber,
                    acl.getUserEntriesMap(), acl.getUserGroupEntriesMap());
        } else {
            productInstanceService.removeACLFromProductInstanceMaster(workspaceId, configurationItemId, serialNumber);
        }

        return Response.noContent().build();
    }

    @DELETE
    @ApiOperation(value = "Delete product-instance",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion of ProductInstanceMasterDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteProductInstanceMaster(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, WorkspaceNotEnabledException {

        productInstanceService.deleteProductInstance(workspaceId, configurationItemId, serialNumber);
        return Response.noContent().build();
    }

    @GET
    @ApiOperation(value = "Get product-instance's iterations",
            response = ProductInstanceIterationDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of ProductInstanceMasterDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/iterations")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProductInstanceIterations(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        List<ProductInstanceIteration> productInstanceIterationList = productInstanceService.getProductInstanceIterations(new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));
        List<ProductInstanceIterationDTO> productInstanceIterationDTOs = new ArrayList<>();
        for (ProductInstanceIteration productInstanceIteration : productInstanceIterationList) {
            ProductInstanceIterationDTO productInstanceIterationDTO = mapper.map(productInstanceIteration, ProductInstanceIterationDTO.class);
            productInstanceIterationDTOs.add(productInstanceIterationDTO);
        }
        return Response.ok(new GenericEntity<List<ProductInstanceIterationDTO>>((List<ProductInstanceIterationDTO>) productInstanceIterationDTOs) {
        }).build();
    }


    @GET
    @ApiOperation(value = "Get product-instance's iteration",
            response = ProductInstanceIterationDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of ProductInstanceIterationDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/iterations/{iteration}")
    @Produces(MediaType.APPLICATION_JSON)
    public ProductInstanceIterationDTO getProductInstanceIteration(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Product instance iteration") @PathParam("iteration") int iteration)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        ProductInstanceIteration productInstanceIteration = productInstanceService.getProductInstanceIteration(new ProductInstanceIterationKey(serialNumber, workspaceId, configurationItemId, iteration));
        return mapper.map(productInstanceIteration, ProductInstanceIterationDTO.class);
    }

    @PUT
    @ApiOperation(value = "Rebase product-instance with given baseline",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful rebase of ProductInstanceIterationDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{ciId}/instances/{serialNumber}/rebase")
    public Response rebaseProductInstance(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Product baseline to rebase with") ProductBaselineDTO baselineDTO)
            throws UserNotActiveException, EntityNotFoundException, AccessRightException, NotAllowedException,
            EntityAlreadyExistsException, CreationException, EntityConstraintException, WorkspaceNotEnabledException {

        productInstanceService.rebaseProductInstance(workspaceId, serialNumber, new ConfigurationItemKey(workspaceId, configurationItemId), baselineDTO.getId());
        return Response.noContent().build();
    }

    @PUT
    @ApiOperation(value = "Rename attached file in product instance iteration",
            response = FileDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of renamed FileDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{ciId}/instances/{serialNumber}/iterations/{iteration}/files/{fileName}")
    public FileDTO renameAttachedFileInProductInstance(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Product instance iteration") @PathParam("iteration") int iteration,
            @ApiParam(required = true, value = "File name") @PathParam("fileName") String fileName,
            @ApiParam(required = true, value = "Renamed file") FileDTO fileDTO)
            throws UserNotActiveException, EntityNotFoundException, CreationException, NotAllowedException,
            EntityAlreadyExistsException, StorageException, AccessRightException, WorkspaceNotEnabledException {

        String fullName = workspaceId + "/product-instances/" + serialNumber + "/iterations/" + iteration + "/" + fileName;
        BinaryResource binaryResource = productInstanceService.renameFileInProductInstance(fullName, fileDTO.getShortName(), serialNumber, configurationItemId, iteration);
        return new FileDTO(true, binaryResource.getFullName(), binaryResource.getName());
    }

    @GET
    @ApiOperation(value = "Get product-instance's last iteration path-data",
            response = PathDataMasterDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of PathDataMasterDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/pathdata/{path}")
    @Produces(MediaType.APPLICATION_JSON)
    public PathDataMasterDTO getPathData(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Complete path in context") @PathParam("path") String pathAsString)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        PathDataMaster pathDataMaster = productInstanceService.getPathDataByPath(workspaceId, configurationItemId, serialNumber, pathAsString);

        PathDataMasterDTO dto = pathDataMaster == null ? new PathDataMasterDTO(pathAsString) : mapper.map(pathDataMaster, PathDataMasterDTO.class);

        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, configurationItemId);

        LightPartLinkListDTO partLinksList = new LightPartLinkListDTO();
        List<PartLink> path = productService.decodePath(ciKey, pathAsString);
        for (PartLink partLink : path) {
            partLinksList.getPartLinks().add(new LightPartLinkDTO(partLink.getComponent().getNumber(), partLink.getComponent().getName(), partLink.getReferenceDescription(), partLink.getFullId()));
        }
        dto.setPartLinksList(partLinksList);

        List<InstanceAttributeDTO> attributesDTO = new ArrayList<>();
        List<InstanceAttributeTemplateDTO> attributeTemplatesDTO = new ArrayList<>();
        PartLink partLink = path.get(path.size() - 1);
        ProductStructureFilter filter = psFilterService.getPSFilter(ciKey, "pi-" + serialNumber, false);
        List<PartIteration> partIterations = filter.filter(partLink.getComponent());
        PartIteration partIteration = partIterations.get(0);

        if (partIteration != null) {
            for (InstanceAttribute instanceAttribute : partIteration.getInstanceAttributes()) {
                attributesDTO.add(mapper.map(instanceAttribute, InstanceAttributeDTO.class));
            }
            dto.setPartAttributes(attributesDTO);
            for (InstanceAttributeTemplate instanceAttributeTemplate : partIteration.getInstanceAttributeTemplates()) {
                attributeTemplatesDTO.add(mapper.map(instanceAttributeTemplate, InstanceAttributeTemplateDTO.class));
            }
            dto.setPartAttributeTemplates(attributeTemplatesDTO);
        }

        return dto;
    }


    @PUT
    @ApiOperation(value = "Rename product-instance's attached file",
            response = FileDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of renamed FileDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{ciId}/instances/{serialNumber}/pathdata/{pathDataId}/iterations/{iteration}/files/{fileName}")
    public FileDTO renameAttachedFileInPathData(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Path data master id") @PathParam("pathDataId") int pathDataId,
            @ApiParam(required = true, value = "Product instance iteration") @PathParam("iteration") int iteration,
            @ApiParam(required = true, value = "File name") @PathParam("fileName") String fileName,
            @ApiParam(required = true, value = "Renamed file") FileDTO fileDTO)
            throws UserNotActiveException, CreationException, NotAllowedException, EntityAlreadyExistsException,
            EntityNotFoundException, AccessRightException, StorageException, WorkspaceNotEnabledException {

        String fullName = workspaceId + "/product-instances/" + serialNumber + "/pathdata/" + pathDataId + "/iterations/" + iteration + "/" + fileName;
        BinaryResource binaryResource = productInstanceService.renameFileInPathData(workspaceId, configurationItemId, serialNumber, pathDataId, iteration, fullName, fileDTO.getShortName());
        return new FileDTO(true, binaryResource.getFullName(), binaryResource.getName());
    }

    @DELETE
    @ApiOperation(value = "Delete product-instance's attached file",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful file deletion"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{ciId}/instances/{serialNumber}/pathdata/{pathDataId}/iterations/{iteration}/files/{fileName}")
    public Response deleteAttachedFileInPathData(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Path data master id") @PathParam("pathDataId") int pathDataId,
            @ApiParam(required = true, value = "Product instance iteration") @PathParam("iteration") int iteration,
            @ApiParam(required = true, value = "File name") @PathParam("fileName") String fileName
    ) throws UserNotActiveException, CreationException, EntityNotFoundException, NotAllowedException,
            EntityAlreadyExistsException, AccessRightException, WorkspaceNotEnabledException {

        String fullName = workspaceId + "/product-instances/" + serialNumber + "/pathdata/" + pathDataId + "/iterations/" + iteration + "/" + fileName;
        ProductInstanceMaster productInstanceMaster = productInstanceService.getProductInstanceMaster(new ProductInstanceMasterKey(serialNumber, workspaceId, configurationItemId));
        productInstanceService.removeFileFromPathData(workspaceId, configurationItemId, serialNumber, pathDataId, iteration, fullName, productInstanceMaster);
        return Response.noContent().build();
    }

    @DELETE
    @ApiOperation(value = "Delete product-instance's path-data",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion of PathDataMaster"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/pathdata/{pathDataId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deletePathData(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Path data master id") @PathParam("pathDataId") int pathDataId)
            throws UserNotActiveException, EntityNotFoundException, AccessRightException, NotAllowedException,
            WorkspaceNotEnabledException {

        productInstanceService.deletePathData(workspaceId, configurationItemId, serialNumber, pathDataId);
        return Response.noContent().build();
    }

    @POST
    @ApiOperation(value = "Add new path-data iteration",
            response = PathDataMasterDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated PathDataMasterDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/pathdata/{pathDataId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PathDataMasterDTO addNewPathDataIteration(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Path data master id") @PathParam("pathDataId") int pathDataId,
            @ApiParam(required = true, value = "Path data iteration to create") PathDataIterationCreationDTO pathDataIterationCreationDTO)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, NotAllowedException,
            EntityAlreadyExistsException, CreationException, WorkspaceNotEnabledException {

        List<InstanceAttributeDTO> instanceAttributeDTOs = pathDataIterationCreationDTO.getInstanceAttributes();
        List<InstanceAttribute> attributes = new ArrayList<>();

        if (instanceAttributeDTOs != null) {
            for (InstanceAttributeDTO dto : instanceAttributeDTOs) {
                dto.setWorkspaceId(workspaceId);
                attributes.add(mapper.map(dto, InstanceAttribute.class));
            }
        }

        Set<DocumentRevisionDTO> linkedDocs = pathDataIterationCreationDTO.getLinkedDocuments();
        DocumentRevisionKey[] links = null;
        String[] documentLinkComments = null;
        if (linkedDocs != null) {
            documentLinkComments = new String[linkedDocs.size()];
            links = createDocumentRevisionKeys(linkedDocs);
            int i = 0;
            for (DocumentRevisionDTO docRevisionForLink : linkedDocs) {
                String comment = docRevisionForLink.getCommentLink();
                if (comment == null) {
                    comment = "";
                }
                documentLinkComments[i++] = comment;
            }
        }


        PathDataMaster pathDataMaster = productInstanceService.addNewPathDataIteration(workspaceId, configurationItemId, serialNumber, pathDataId, attributes, pathDataIterationCreationDTO.getIterationNote(), links, documentLinkComments);
        PathDataMasterDTO dto = mapper.map(pathDataMaster, PathDataMasterDTO.class);

        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, configurationItemId);

        LightPartLinkListDTO partLinksList = new LightPartLinkListDTO();
        List<PartLink> path = productService.decodePath(ciKey, pathDataIterationCreationDTO.getPath());
        for (PartLink partLink : path) {
            partLinksList.getPartLinks().add(new LightPartLinkDTO(partLink.getComponent().getNumber(), partLink.getComponent().getName(), partLink.getReferenceDescription(), partLink.getFullId()));
        }
        dto.setPartLinksList(partLinksList);

        List<InstanceAttributeDTO> attributesDTO = new ArrayList<>();
        PartLink partLink = path.get(path.size() - 1);
        ProductStructureFilter filter = psFilterService.getPSFilter(ciKey, "pi-" + serialNumber, false);
        List<PartIteration> partIterations = filter.filter(partLink.getComponent());
        PartIteration partIteration = partIterations.get(0);

        if (partIteration != null) {
            for (InstanceAttribute instanceAttribute : partIteration.getInstanceAttributes()) {
                attributesDTO.add(mapper.map(instanceAttribute, InstanceAttributeDTO.class));
            }
            dto.setPartAttributes(attributesDTO);
        }

        return dto;
    }

    @POST
    @ApiOperation(value = "Create a new path-data in product-instance last iteration",
            response = PathDataMasterDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of created PathDataMaster"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/pathdata/{path}/new")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PathDataMasterDTO createPathDataMaster(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Complete path in context") @PathParam("path") String pathAsString,
            @ApiParam(required = true, value = "Path data iteration create") PathDataIterationCreationDTO pathDataIterationCreationDTO)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException,
            NotAllowedException, EntityAlreadyExistsException, CreationException, WorkspaceNotEnabledException {

        List<InstanceAttributeDTO> instanceAttributeDTOs = pathDataIterationCreationDTO.getInstanceAttributes();
        List<InstanceAttribute> attributes = new ArrayList<>();

        if (instanceAttributeDTOs != null) {
            for (InstanceAttributeDTO dto : instanceAttributeDTOs) {
                dto.setWorkspaceId(workspaceId);
                attributes.add(mapper.map(dto, InstanceAttribute.class));
            }
        }


        PathDataMaster pathDataMaster = productInstanceService.createPathDataMaster(workspaceId, configurationItemId, serialNumber, pathAsString, attributes, pathDataIterationCreationDTO.getIterationNote());

        PathDataMasterDTO dto = mapper.map(pathDataMaster, PathDataMasterDTO.class);

        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, configurationItemId);

        LightPartLinkListDTO partLinksList = new LightPartLinkListDTO();
        List<PartLink> path = productService.decodePath(ciKey, pathAsString);
        for (PartLink partLink : path) {
            partLinksList.getPartLinks().add(new LightPartLinkDTO(partLink.getComponent().getNumber(), partLink.getComponent().getName(), partLink.getReferenceDescription(), partLink.getFullId()));
        }
        dto.setPartLinksList(partLinksList);

        return dto;
    }

    @PUT
    @ApiOperation(value = "Update path-data",
            response = PathDataMasterDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated PathDataMaster"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/pathdata/{pathDataId}/iterations/{iteration}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PathDataMasterDTO updatePathData(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Path data master id") @PathParam("pathDataId") int pathDataId,
            @ApiParam(required = true, value = "Product instance iteration") @PathParam("iteration") int iteration,
            @ApiParam(required = true, value = "Path data iteration to update") PathDataIterationCreationDTO pathDataIterationCreationDTO)
            throws AccessRightException, UserNotActiveException, EntityNotFoundException, NotAllowedException,
            EntityAlreadyExistsException, WorkspaceNotEnabledException {

        List<InstanceAttributeDTO> instanceAttributeDTOs = pathDataIterationCreationDTO.getInstanceAttributes();
        List<InstanceAttribute> attributes = new ArrayList<>();

        if (instanceAttributeDTOs != null) {
            for (InstanceAttributeDTO dto : instanceAttributeDTOs) {
                dto.setWorkspaceId(workspaceId);
                attributes.add(mapper.map(dto, InstanceAttribute.class));
            }
        }

        Set<DocumentRevisionDTO> linkedDocs = pathDataIterationCreationDTO.getLinkedDocuments();
        DocumentRevisionKey[] links = null;
        String[] documentLinkComments = null;
        if (linkedDocs != null) {
            documentLinkComments = new String[linkedDocs.size()];
            links = createDocumentRevisionKeys(linkedDocs);
            int i = 0;
            for (DocumentRevisionDTO docRevisionForLink : linkedDocs) {
                String comment = docRevisionForLink.getCommentLink();
                if (comment == null) {
                    comment = "";
                }
                documentLinkComments[i++] = comment;
            }
        }

        PathDataMaster pathDataMaster = productInstanceService.updatePathData(workspaceId, configurationItemId, serialNumber, pathDataIterationCreationDTO.getId(), iteration, attributes, pathDataIterationCreationDTO.getIterationNote(), links, documentLinkComments);

        PathDataMasterDTO dto = mapper.map(pathDataMaster, PathDataMasterDTO.class);

        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, configurationItemId);

        LightPartLinkListDTO partLinksList = new LightPartLinkListDTO();
        List<PartLink> path = productService.decodePath(ciKey, dto.getPath());
        for (PartLink partLink : path) {
            partLinksList.getPartLinks().add(new LightPartLinkDTO(partLink.getComponent().getNumber(), partLink.getComponent().getName(), partLink.getReferenceDescription(), partLink.getFullId()));
        }
        dto.setPartLinksList(partLinksList);

        return dto;
    }

    @GET
    @ApiOperation(value = "Get path-to-path link types",
            response = LightPathToPathLinkDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of LightPathToPathLinkDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/path-to-path-links-types")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPathToPathLinkTypesInProductInstance(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        List<String> pathToPathLinkTypes = productInstanceService.getPathToPathLinkTypes(workspaceId, configurationItemId, serialNumber);
        List<LightPathToPathLinkDTO> pathToPathLinkDTOs = new ArrayList<>();
        for (String type : pathToPathLinkTypes) {
            LightPathToPathLinkDTO pathToPathLinkDTO = new LightPathToPathLinkDTO();
            pathToPathLinkDTO.setType(type);
            pathToPathLinkDTOs.add(pathToPathLinkDTO);
        }
        return Response.ok(new GenericEntity<List<LightPathToPathLinkDTO>>((List<LightPathToPathLinkDTO>) pathToPathLinkDTOs) {
        }).build();
    }

    @GET
    @ApiOperation(value = "Get part from path-to-path link",
            response = LightPartMasterDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of LightPathToPathLinkDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/link-path-part/{pathPart}")
    @Produces(MediaType.APPLICATION_JSON)
    public LightPartMasterDTO getPartFromPathLink(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Complete path to the part") @PathParam("pathPart") String partPath)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        PartMaster partMaster = productService.getPartMasterFromPath(workspaceId, configurationItemId, partPath);
        LightPartMasterDTO lightPartMasterDTO = new LightPartMasterDTO();
        lightPartMasterDTO.setPartName(partMaster.getName());
        lightPartMasterDTO.setPartNumber(partMaster.getNumber());
        return lightPartMasterDTO;
    }

    @GET
    @ApiOperation(value = "Get path-to-path links",
            response = LightPathToPathLinkDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of LightPathToPathLinkDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/path-to-path-links")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPathToPathLinks(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        List<PathToPathLink> pathToPathLinkTypes = productInstanceService.getPathToPathLinks(workspaceId, configurationItemId, serialNumber);
        List<LightPathToPathLinkDTO> pathToPathLinkDTOs = new ArrayList<>();
        for (PathToPathLink pathToPathLink : pathToPathLinkTypes) {
            pathToPathLinkDTOs.add(mapper.map(pathToPathLink, LightPathToPathLinkDTO.class));

        }
        return Response.ok(new GenericEntity<List<LightPathToPathLinkDTO>>((List<LightPathToPathLinkDTO>) pathToPathLinkDTOs) {
        }).build();
    }

    @GET
    @ApiOperation(value = "Get path-to-path link",
            response = LightPathToPathLinkDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of LightPathToPathLinkDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/path-to-path-links/{pathToPathLinkId}")
    @Produces(MediaType.APPLICATION_JSON)
    public LightPathToPathLinkDTO getPathToPathLink(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Path to path link id") @PathParam("pathToPathLinkId") int pathToPathLinkId)
            throws UserNotActiveException, EntityNotFoundException, AccessRightException, WorkspaceNotEnabledException {

        PathToPathLink pathToPathLink = productInstanceService.getPathToPathLink(workspaceId, configurationItemId, serialNumber, pathToPathLinkId);
        return mapper.map(pathToPathLink, LightPathToPathLinkDTO.class);
    }

    @GET
    @ApiOperation(value = "Get path-to-path link for given source and target",
            response = LightPathToPathLinkDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of LightPathToPathLinkDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/path-to-path-links/source/{sourcePath}/target/{targetPath}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPathToPathLinksForGivenSourceAndTarget(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Complete source path") @PathParam("sourcePath") String sourcePathAsString,
            @ApiParam(required = true, value = "Complete target path") @PathParam("targetPath") String targetPathAsString)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        List<PathToPathLink> pathToPathLinks = productInstanceService.getPathToPathLinkFromSourceAndTarget(workspaceId, configurationItemId, serialNumber, sourcePathAsString, targetPathAsString);
        List<PathToPathLinkDTO> pathToPathLinkDTOs = new ArrayList<>();
        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, configurationItemId);

        for (PathToPathLink pathToPathLink : pathToPathLinks) {
            PathToPathLinkDTO pathToPathLinkDTO = mapper.map(pathToPathLink, PathToPathLinkDTO.class);
            List<LightPartLinkDTO> sourceLightPartLinkDTOs = new ArrayList<>();

            List<PartLink> sourcePath = productService.decodePath(ciKey, pathToPathLink.getSourcePath());
            List<PartLink> targetPath = productService.decodePath(ciKey, pathToPathLink.getTargetPath());

            for (PartLink partLink : sourcePath) {
                LightPartLinkDTO lightPartLinkDTO = new LightPartLinkDTO(partLink.getComponent().getNumber(), partLink.getComponent().getName(), partLink.getReferenceDescription(), partLink.getFullId());
                sourceLightPartLinkDTOs.add(lightPartLinkDTO);
            }

            List<LightPartLinkDTO> targetLightPartLinkDTOs = new ArrayList<>();
            for (PartLink partLink : targetPath) {
                LightPartLinkDTO lightPartLinkDTO = new LightPartLinkDTO(partLink.getComponent().getNumber(), partLink.getComponent().getName(), partLink.getReferenceDescription(), partLink.getFullId());
                targetLightPartLinkDTOs.add(lightPartLinkDTO);
            }

            pathToPathLinkDTO.setSourceComponents(sourceLightPartLinkDTOs);
            pathToPathLinkDTO.setTargetComponents(targetLightPartLinkDTOs);
            pathToPathLinkDTOs.add(pathToPathLinkDTO);
        }

        return Response.ok(new GenericEntity<List<PathToPathLinkDTO>>((List<PathToPathLinkDTO>) pathToPathLinkDTOs) {
        }).build();
    }

    @GET
    @ApiOperation(value = "Get root path-to-path links",
            response = LightPathToPathLinkDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of LightPathToPathLinkDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{ciId}/instances/{serialNumber}/path-to-path-links-roots/{type}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRootPathToPathLinks(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Configuration item id") @PathParam("ciId") String configurationItemId,
            @ApiParam(required = true, value = "Serial number") @PathParam("serialNumber") String serialNumber,
            @ApiParam(required = true, value = "Link type") @PathParam("type") String type)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        List<PathToPathLink> pathToPathLinks = productInstanceService.getRootPathToPathLinks(workspaceId, configurationItemId, serialNumber, type);
        List<LightPathToPathLinkDTO> lightPathToPathLinkDTOs = new ArrayList<>();
        for (PathToPathLink pathToPathLink : pathToPathLinks) {
            lightPathToPathLinkDTOs.add(mapper.map(pathToPathLink, LightPathToPathLinkDTO.class));
        }
        return Response.ok(new GenericEntity<List<LightPathToPathLinkDTO>>((List<LightPathToPathLinkDTO>) lightPathToPathLinkDTOs) {
        }).build();
    }

    @POST
    @ApiOperation(value = "Import attribute into product-instance",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful import"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importProductInstanceAttributes(
            @Context HttpServletRequest request,
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = false, value = "Auto freeze after update flag") @QueryParam("autoFreezeAfterUpdate") boolean autoFreezeAfterUpdate,
            @ApiParam(required = false, value = "Permissive update flag") @QueryParam("permissiveUpdate") boolean permissiveUpdate,
            @ApiParam(required = false, value = "Revision note to set") @QueryParam("revisionNote") String revisionNote)
            throws IOException, ServletException {

        Collection<Part> parts = request.getParts();

        if (parts.isEmpty() || parts.size() > 1) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Part part = parts.iterator().next();

        String fileName = URLDecoder.decode(part.getSubmittedFileName(), "UTF-8");
        String tempFolderName = UUID.randomUUID().toString();

        File importFile = Files.createTempFile(tempFolderName, fileName).toFile();
        BinaryResourceUpload.uploadBinary(new BufferedOutputStream(new FileOutputStream(importFile)), part);
        importerService.importIntoPathData(workspaceId, importFile, fileName, revisionNote, autoFreezeAfterUpdate, permissiveUpdate);

        importFile.deleteOnExit();

        return Response.noContent().build();

    }


    private DocumentRevisionKey[] createDocumentRevisionKeys(Set<DocumentRevisionDTO> documentRevisionDTOs) {
        DocumentRevisionKey[] data = new DocumentRevisionKey[documentRevisionDTOs.size()];
        int i = 0;
        for (DocumentRevisionDTO dto : documentRevisionDTOs) {
            data[i++] = new DocumentRevisionKey(dto.getWorkspaceId(), dto.getDocumentMasterId(), dto.getVersion());
        }
        return data;
    }

    private List<PathToPathLinkDTO> getPathToPathLinksForGivenProductInstance(ProductInstanceIteration productInstanceIteration) throws UserNotFoundException, WorkspaceNotFoundException, UserNotActiveException, AccessRightException, ProductInstanceMasterNotFoundException, PartUsageLinkNotFoundException, ConfigurationItemNotFoundException, WorkspaceNotEnabledException {
        List<PathToPathLink> pathToPathLinkTypes = productInstanceIteration.getPathToPathLinks();
        List<PathToPathLinkDTO> pathToPathLinkDTOs = new ArrayList<>();

        for (PathToPathLink pathToPathLink : pathToPathLinkTypes) {
            PathToPathLinkDTO pathToPathLinkDTO = mapper.map(pathToPathLink, PathToPathLinkDTO.class);
            List<PartLink> sourcePath = productService.decodePath(productInstanceIteration.getBasedOn().getConfigurationItem().getKey(), pathToPathLink.getSourcePath());
            List<PartLink> targetPath = productService.decodePath(productInstanceIteration.getBasedOn().getConfigurationItem().getKey(), pathToPathLink.getTargetPath());

            List<LightPartLinkDTO> sourceLightPartLinkDTOs = new ArrayList<>();
            for (PartLink partLink : sourcePath) {
                LightPartLinkDTO lightPartLinkDTO = new LightPartLinkDTO(partLink.getComponent().getNumber(), partLink.getComponent().getName(), partLink.getReferenceDescription(), partLink.getFullId());
                sourceLightPartLinkDTOs.add(lightPartLinkDTO);
            }

            List<LightPartLinkDTO> targetLightPartLinkDTOs = new ArrayList<>();
            for (PartLink partLink : targetPath) {
                LightPartLinkDTO lightPartLinkDTO = new LightPartLinkDTO(partLink.getComponent().getNumber(), partLink.getComponent().getName(), partLink.getReferenceDescription(), partLink.getFullId());
                targetLightPartLinkDTOs.add(lightPartLinkDTO);
            }

            pathToPathLinkDTO.setSourceComponents(sourceLightPartLinkDTOs);
            pathToPathLinkDTO.setTargetComponents(targetLightPartLinkDTOs);
            pathToPathLinkDTOs.add(pathToPathLinkDTO);
        }
        return pathToPathLinkDTOs;
    }

    private Response makeList(List<ProductInstanceMaster> productInstanceMasterList) {
        List<ProductInstanceMasterDTO> productInstanceMasterDTOs = new ArrayList<>();
        for (ProductInstanceMaster productInstanceMaster : productInstanceMasterList) {
            ProductInstanceMasterDTO productInstanceMasterDTO = mapper.map(productInstanceMaster, ProductInstanceMasterDTO.class);
            productInstanceMasterDTO.setConfigurationItemId(productInstanceMaster.getInstanceOf().getId());
            productInstanceMasterDTOs.add(productInstanceMasterDTO);
        }
        return Response.ok(new GenericEntity<List<ProductInstanceMasterDTO>>((List<ProductInstanceMasterDTO>) productInstanceMasterDTOs) {
        }).build();
    }

}
