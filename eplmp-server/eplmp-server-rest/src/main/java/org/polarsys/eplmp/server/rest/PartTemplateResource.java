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
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.meta.InstanceAttributeTemplate;
import org.polarsys.eplmp.core.product.PartMasterTemplate;
import org.polarsys.eplmp.core.product.PartMasterTemplateKey;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IProductManagerLocal;
import org.polarsys.eplmp.server.rest.dto.*;

import javax.annotation.PostConstruct;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Morgan Guimard
 */
@RequestScoped
@Api(hidden = true, value = "partTemplates", description = "Operations about part templates",
        authorizations = {@Authorization(value = "authorization")})
@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
public class PartTemplateResource {

    @Inject
    private IProductManagerLocal productService;

    private Mapper mapper;

    public PartTemplateResource() {
    }

    @PostConstruct
    public void init() {
        mapper = DozerBeanMapperSingletonWrapper.getInstance();
    }

    @GET
    @ApiOperation(value = "Get part master templates",
            response = PartMasterTemplateDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of PartMasterTemplateDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public PartMasterTemplateDTO[] getPartMasterTemplates(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {


        PartMasterTemplate[] partMasterTemplates = productService.getPartMasterTemplates(workspaceId);
        PartMasterTemplateDTO[] partMasterTemplateDTOs = new PartMasterTemplateDTO[partMasterTemplates.length];

        for (int i = 0; i < partMasterTemplates.length; i++) {
            partMasterTemplateDTOs[i] = mapper.map(partMasterTemplates[i], PartMasterTemplateDTO.class);
        }

        return partMasterTemplateDTOs;
    }

    @GET
    @ApiOperation(value = "Get part master template",
            response = PartMasterTemplateDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of PartMasterTemplateDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{templateId}")
    @Produces(MediaType.APPLICATION_JSON)
    public PartMasterTemplateDTO getPartMasterTemplate(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Template id") @PathParam("templateId") String templateId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        PartMasterTemplate partMasterTemplate = productService.getPartMasterTemplate(new PartMasterTemplateKey(workspaceId, templateId));
        return mapper.map(partMasterTemplate, PartMasterTemplateDTO.class);
    }

    @GET
    @ApiOperation(value = "Generate part master template id",
            response = TemplateGeneratedIdDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of TemplateGeneratedIdDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{templateId}/generate_id")
    @Produces(MediaType.APPLICATION_JSON)
    public TemplateGeneratedIdDTO generatePartMasterTemplateId(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Template id") @PathParam("templateId") String templateId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        String generatedId = productService.generateId(workspaceId, templateId);
        return new TemplateGeneratedIdDTO(generatedId);
    }

    @POST
    @ApiOperation(value = "Crate a new part master template",
            response = PartMasterTemplateDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of created PartMasterTemplateDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PartMasterTemplateDTO createPartMasterTemplate(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Part master template to create") PartTemplateCreationDTO templateCreationDTO)
            throws EntityNotFoundException, EntityAlreadyExistsException, CreationException, AccessRightException,
            NotAllowedException, WorkspaceNotEnabledException {

        String id = templateCreationDTO.getReference();
        String partType = templateCreationDTO.getPartType();
        String workflowModelId = templateCreationDTO.getWorkflowModelId();
        String mask = templateCreationDTO.getMask();
        boolean idGenerated = templateCreationDTO.isIdGenerated();
        boolean attributesLocked = templateCreationDTO.isAttributesLocked();

        List<InstanceAttributeTemplateDTO> attrTemplateDTOs = templateCreationDTO.getAttributeTemplates();
        String[] lovNames = new String[attrTemplateDTOs.size()];
        for (int i = 0; i < attrTemplateDTOs.size(); i++) {
            lovNames[i] = attrTemplateDTOs.get(i).getLovName();
        }

        List<InstanceAttributeTemplateDTO> instanceAttrTemplateDTOs = templateCreationDTO.getAttributeInstanceTemplates();
        String[] instanceLovNames = new String[instanceAttrTemplateDTOs.size()];
        for (int i = 0; i < instanceAttrTemplateDTOs.size(); i++) {
            instanceLovNames[i] = instanceAttrTemplateDTOs.get(i).getLovName();
        }

        List<InstanceAttributeTemplate> attrTemplates = new ArrayList<>();
        for (InstanceAttributeTemplateDTO dto : attrTemplateDTOs) {
            attrTemplates.add(mapper.map(dto, InstanceAttributeTemplate.class));
        }

        List<InstanceAttributeTemplate> instanceAttrTemplates = new ArrayList<>();
        for (InstanceAttributeTemplateDTO dto : instanceAttrTemplateDTOs) {
            instanceAttrTemplates.add(mapper.map(dto, InstanceAttributeTemplate.class));
        }


        PartMasterTemplate template = productService.createPartMasterTemplate(workspaceId, id, partType, workflowModelId, mask, attrTemplates, lovNames, instanceAttrTemplates, instanceLovNames, idGenerated, attributesLocked);
        return mapper.map(template, PartMasterTemplateDTO.class);
    }

    @PUT
    @ApiOperation(value = "Update part master template",
            response = PartMasterTemplateDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated PartMasterTemplateDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{templateId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PartMasterTemplateDTO updatePartMasterTemplate(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Template id") @PathParam("templateId") String templateId,
            @ApiParam(required = true, value = "Part master template to update") PartMasterTemplateDTO partMasterTemplateDTO)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, NotAllowedException, WorkspaceNotEnabledException {

        String partType = partMasterTemplateDTO.getPartType();
        String mask = partMasterTemplateDTO.getMask();
        String workflowModelId = partMasterTemplateDTO.getWorkflowModelId();
        boolean idGenerated = partMasterTemplateDTO.isIdGenerated();
        boolean attributesLocked = partMasterTemplateDTO.isAttributesLocked();

        List<InstanceAttributeTemplateDTO> attrTemplateDTOs = partMasterTemplateDTO.getAttributeTemplates();
        String[] lovNames = new String[attrTemplateDTOs.size()];
        for (int i = 0; i < attrTemplateDTOs.size(); i++) {
            lovNames[i] = attrTemplateDTOs.get(i).getLovName();
        }

        List<InstanceAttributeTemplateDTO> instanceAttrTemplateDTOs = partMasterTemplateDTO.getAttributeInstanceTemplates();
        String[] instanceLovNames = new String[instanceAttrTemplateDTOs.size()];
        for (int i = 0; i < instanceAttrTemplateDTOs.size(); i++) {
            instanceLovNames[i] = instanceAttrTemplateDTOs.get(i).getLovName();
        }


        List<InstanceAttributeTemplate> attrTemplates = new ArrayList<>();
        for (InstanceAttributeTemplateDTO dto : attrTemplateDTOs) {
            attrTemplates.add(mapper.map(dto, InstanceAttributeTemplate.class));
        }

        List<InstanceAttributeTemplate> instanceAttrTemplates = new ArrayList<>();
        for (InstanceAttributeTemplateDTO dto : instanceAttrTemplateDTOs) {
            instanceAttrTemplates.add(mapper.map(dto, InstanceAttributeTemplate.class));
        }

        PartMasterTemplate template = productService.updatePartMasterTemplate(new PartMasterTemplateKey(workspaceId, templateId), partType, workflowModelId, mask, attrTemplates, lovNames, instanceAttrTemplates, instanceLovNames, idGenerated, attributesLocked);
        return mapper.map(template, PartMasterTemplateDTO.class);
    }

    @PUT
    @ApiOperation(value = "Update part master template ACL",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful update of ACL"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{templateId}/acl")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updatePartMasterTemplateACL(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Template id") @PathParam("templateId") String templateId,
            @ApiParam(required = true, value = "ACL rules to set") ACLDTO acl)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, NotAllowedException, WorkspaceNotEnabledException {

        if (acl.hasEntries()) {
            productService.updateACLForPartMasterTemplate(workspaceId, templateId, acl.getUserEntriesMap(), acl.getUserGroupEntriesMap());
        } else {
            productService.removeACLFromPartMasterTemplate(workspaceId, templateId);
        }

        return Response.noContent().build();
    }

    @DELETE
    @ApiOperation(value = "Delete part master template",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion of PartMasterTemplateDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{templateId}")
    public Response deletePartMasterTemplate(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Template id") @PathParam("templateId") String templateId)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, WorkspaceNotEnabledException {

        productService.deletePartMasterTemplate(new PartMasterTemplateKey(workspaceId, templateId));
        return Response.noContent().build();
    }

    @DELETE
    @ApiOperation(value = "Remove attached file from part master template",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful file deletion of PartMasterTemplateDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{templateId}/files/{fileName}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response removeAttachedFileFromPartTemplate(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Template id") @PathParam("templateId") String templateId,
            @ApiParam(required = true, value = "File name") @PathParam("fileName") String fileName)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, WorkspaceNotEnabledException {

        String fileFullName = workspaceId + "/part-templates/" + templateId + "/" + fileName;
        productService.removeFileFromTemplate(fileFullName);
        return Response.noContent().build();
    }

    @PUT
    @ApiOperation(value = "Rename attached file in part master template",
            response = FileDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful file renaming of PartMasterTemplateDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{templateId}/files/{fileName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public FileDTO renameAttachedFileInPartTemplate(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Template id") @PathParam("templateId") String templateId,
            @ApiParam(required = true, value = "File name") @PathParam("fileName") String fileName,
            @ApiParam(required = true, value = "File to rename") FileDTO fileDTO)
            throws UserNotActiveException, EntityNotFoundException, CreationException, AccessRightException,
            EntityAlreadyExistsException, StorageException, NotAllowedException, WorkspaceNotEnabledException {

        String fileFullName = workspaceId + "/part-templates/" + templateId + "/" + fileName;
        BinaryResource binaryResource = productService.renameFileInTemplate(fileFullName, fileDTO.getShortName());
        return new FileDTO(true, binaryResource.getFullName(), binaryResource.getName());
    }

}
