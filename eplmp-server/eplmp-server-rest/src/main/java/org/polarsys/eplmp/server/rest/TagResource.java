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

import org.polarsys.eplmp.core.document.DocumentRevision;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.meta.TagKey;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IDocumentManagerLocal;
import org.polarsys.eplmp.server.rest.dto.*;
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
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Yassine Belouad
 */
@RequestScoped
@Api(hidden = true, value = "tags", description = "Operations about tags")
@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
public class TagResource {

    private final static Logger LOGGER = Logger.getLogger(TagResource.class.getName());

    @Inject
    private IDocumentManagerLocal documentService;
    private Mapper mapper;

    public TagResource() {
    }

    @PostConstruct
    public void init() {
        mapper = DozerBeanMapperSingletonWrapper.getInstance();
    }

    @GET
    @ApiOperation(value = "Get tags in workspace",
            response = TagDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of TagDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTagsInWorkspace(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException {

        String[] tagsName = documentService.getTags(workspaceId);
        List<TagDTO> tagsDTO = new ArrayList<>();
        for (String tagName : tagsName) {
            tagsDTO.add(new TagDTO(tagName, workspaceId));
        }
        return Response.ok(new GenericEntity<List<TagDTO>>((List<TagDTO>) tagsDTO) {
        }).build();
    }

    @POST
    @ApiOperation(value = "Create tag in workspace",
            response = TagDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of TagDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TagDTO createTag(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(value = "Tag to create", required = true) TagDTO tag)
            throws EntityNotFoundException, EntityAlreadyExistsException, UserNotActiveException, AccessRightException, CreationException {

        documentService.createTag(workspaceId, tag.getLabel());
        return new TagDTO(tag.getLabel());
    }

    @POST
    @ApiOperation(value = "Create tags in workspace",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful creation of TagDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("/multiple")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createTags(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(value = "Tag list to create", required = true) TagListDTO tagList)
            throws EntityNotFoundException, EntityAlreadyExistsException, UserNotActiveException,
            AccessRightException, CreationException {

        for (TagDTO tagDTO : tagList.getTags()) {
            documentService.createTag(workspaceId, tagDTO.getLabel());
        }
        return Response.noContent().build();
    }

    @DELETE
    @ApiOperation(value = "Delete tag in workspace",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion of TagDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{tagId}")
    public Response deleteTag(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Tag id") @PathParam("tagId") String tagId)
            throws EntityNotFoundException, AccessRightException {

        documentService.deleteTag(new TagKey(workspaceId, tagId));
        return Response.noContent().build();
    }

    @GET
    @ApiOperation(value = "Get documents from given tag id",
            response = DocumentRevisionDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of DocumentRevisionDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{tagId}/documents/")
    @Produces(MediaType.APPLICATION_JSON)
    public DocumentRevisionDTO[] getDocumentsWithGivenTagIdAndWorkspaceId(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Tag id") @PathParam("tagId") String tagId)
            throws EntityNotFoundException, UserNotActiveException {

        TagKey tagKey = new TagKey(workspaceId, tagId);
        DocumentRevision[] docRs = documentService.findDocumentRevisionsByTag(tagKey);
        DocumentRevisionDTO[] docRsDTOs = new DocumentRevisionDTO[docRs.length];

        for (int i = 0; i < docRs.length; i++) {
            docRsDTOs[i] = mapper.map(docRs[i], DocumentRevisionDTO.class);
            docRsDTOs[i].setPath(docRs[i].getLocation().getCompletePath());
            docRsDTOs[i] = Tools.createLightDocumentRevisionDTO(docRsDTOs[i]);
            docRsDTOs[i].setLifeCycleState(docRs[i].getLifeCycleState());
            docRsDTOs[i].setIterationSubscription(documentService.isUserIterationChangeEventSubscribedForGivenDocument(workspaceId, docRs[i]));
            docRsDTOs[i].setStateSubscription(documentService.isUserStateChangeEventSubscribedForGivenDocument(workspaceId, docRs[i]));
        }

        return docRsDTOs;
    }

    @POST
    @Path("{tagId}/documents/")
    @ApiOperation(value = "Create document",
            response = DocumentRevisionDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of DocumentRevisionDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createDocumentMasterInRootFolderWithTag(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Document to create") DocumentCreationDTO docCreationDTO,
            @ApiParam(required = true, value = "Tag id") @PathParam("tagId") String tagId)
            throws CreationException, FileAlreadyExistsException, DocumentRevisionAlreadyExistsException, WorkspaceNotFoundException, UserNotFoundException, NotAllowedException, DocumentMasterAlreadyExistsException, RoleNotFoundException, FolderNotFoundException, WorkflowModelNotFoundException, AccessRightException, DocumentMasterTemplateNotFoundException, DocumentRevisionNotFoundException, UserNotActiveException, UserGroupNotFoundException, WorkspaceNotEnabledException {

        String pDocMID = docCreationDTO.getReference();
        String pTitle = docCreationDTO.getTitle();
        String pDescription = docCreationDTO.getDescription();

        String decodedCompletePath = getPathFromUrlParams(workspaceId, workspaceId);

        String pWorkflowModelId = docCreationDTO.getWorkflowModelId();
        RoleMappingDTO[] roleMappingDTOs = docCreationDTO.getRoleMapping();
        String pDocMTemplateId = docCreationDTO.getTemplateId();

        ACLDTO acl = docCreationDTO.getAcl();

        Map<String, String> userEntries = acl != null ? acl.getUserEntriesMap() : null;
        Map<String, String> userGroupEntries = acl != null ? acl.getUserGroupEntriesMap() : null;

        Map<String, Collection<String>> userRoleMapping = new HashMap<>();
        Map<String, Collection<String>> groupRoleMapping = new HashMap<>();

        if (roleMappingDTOs != null) {
            for (RoleMappingDTO roleMappingDTO : roleMappingDTOs) {
                userRoleMapping.put(roleMappingDTO.getRoleName(), roleMappingDTO.getUserLogins());
                groupRoleMapping.put(roleMappingDTO.getRoleName(), roleMappingDTO.getGroupIds());
            }
        }


        DocumentRevision createdDocRs = documentService.createDocumentMaster(decodedCompletePath, pDocMID, pTitle, pDescription, pDocMTemplateId, pWorkflowModelId, userEntries, userGroupEntries, userRoleMapping, groupRoleMapping);
        documentService.saveTags(createdDocRs.getKey(), new String[]{tagId});

        DocumentRevisionDTO docRsDTO = mapper.map(createdDocRs, DocumentRevisionDTO.class);
        docRsDTO.setPath(createdDocRs.getLocation().getCompletePath());
        docRsDTO.setLifeCycleState(createdDocRs.getLifeCycleState());

        try {
            return Response.created(URI.create(URLEncoder.encode(pDocMID + "-" + createdDocRs.getVersion(), "UTF-8"))).entity(docRsDTO).build();
        } catch (UnsupportedEncodingException ex) {
            LOGGER.log(Level.WARNING, null, ex);
            return Response.ok().entity(docRsDTO).build();
        }
    }

    private String getPathFromUrlParams(String workspaceId, String folderId) {
        return folderId == null ? Tools.stripTrailingSlash(workspaceId) : Tools.stripTrailingSlash(FolderDTO.replaceColonWithSlash(folderId));
    }

}
