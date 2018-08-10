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
import org.polarsys.eplmp.core.document.DocumentRevision;
import org.polarsys.eplmp.core.document.DocumentRevisionKey;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.meta.Folder;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IDocumentManagerLocal;
import org.polarsys.eplmp.server.rest.dto.*;

import javax.annotation.PostConstruct;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@RequestScoped
@Api(hidden = true, value = "folders", description = "Operations about folders",
        authorizations = {@Authorization(value = "authorization")})
@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
public class FolderResource {

    private static final Logger LOGGER = Logger.getLogger(FolderResource.class.getName());
    @Inject
    private IDocumentManagerLocal documentService;
    private Mapper mapper;

    public FolderResource() {
    }

    @PostConstruct
    public void init() {
        mapper = DozerBeanMapperSingletonWrapper.getInstance();
    }

    @GET
    @Path("{folderId}/documents/")
    @ApiOperation(value = "Get document revisions in given folder",
            response = DocumentRevisionDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of DocumentRevisionDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public DocumentRevisionDTO[] getDocumentsWithGivenFolderIdAndWorkspaceId(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Folder id") @PathParam("folderId") String folderId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        String decodedCompletePath = getPathFromUrlParams(workspaceId, folderId);
        DocumentRevision[] docRs = documentService.findDocumentRevisionsByFolder(decodedCompletePath);
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
    @Path("{folderId}/documents/")
    @ApiOperation(value = "Create a new document revision in given folder",
            response = DocumentRevisionDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Successful retrieval of created DocumentRevisionDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createDocumentMasterInFolder(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Document to create") DocumentCreationDTO docCreationDTO,
            @ApiParam(required = true, value = "Folder id") @PathParam("folderId") String folderId)
            throws EntityNotFoundException, EntityAlreadyExistsException, NotAllowedException, CreationException, AccessRightException, WorkspaceNotEnabledException {

        String pDocMID = docCreationDTO.getReference();
        String pTitle = docCreationDTO.getTitle();
        String pDescription = docCreationDTO.getDescription();

        String decodedCompletePath = getPathFromUrlParams(workspaceId, folderId);

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
        DocumentRevision createdDocRs = documentService.createDocumentMaster(decodedCompletePath, pDocMID, pTitle,
                pDescription, pDocMTemplateId, pWorkflowModelId, userEntries, userGroupEntries, userRoleMapping,
                groupRoleMapping);

        DocumentRevisionDTO docRsDTO = mapper.map(createdDocRs, DocumentRevisionDTO.class);
        docRsDTO.setPath(createdDocRs.getLocation().getCompletePath());
        docRsDTO.setLifeCycleState(createdDocRs.getLifeCycleState());

        return Tools.prepareCreatedResponse(pDocMID + "-" + createdDocRs.getVersion(), docRsDTO);
    }


    /**
     * Retrieves representation of folders located at the root of the given workspace
     *
     * @param workspaceId The current workspace id
     * @return The array of folders
     */
    @GET
    @ApiOperation(value = "Get root folders",
            response = FolderDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of FolderDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public FolderDTO[] getRootFolders(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        String completePath = Tools.stripTrailingSlash(workspaceId);
        return getFolders(workspaceId, completePath, true);
    }

    @GET
    @ApiOperation(value = "Get sub folders of given folder",
            response = FolderDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of FolderDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{completePath}/folders")
    @Produces(MediaType.APPLICATION_JSON)
    public FolderDTO[] getSubFolders(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Folder id") @PathParam("completePath") String folderId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        String decodedCompletePath = FolderDTO.replaceColonWithSlash(folderId);
        String completePath = Tools.stripTrailingSlash(decodedCompletePath);
        return getFolders(workspaceId, completePath, false);
    }

    /**
     * PUT method for updating or creating an instance of FolderResource
     */
    @PUT
    @ApiOperation(value = "Rename a folder",
            response = FolderDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated FolderDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{folderId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public FolderDTO renameFolder(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Folder id") @PathParam("folderId") String folderPath,
            @ApiParam(value = "Folder with new name", required = true) FolderDTO folderDTO)
            throws EntityNotFoundException, EntityAlreadyExistsException, NotAllowedException, AccessRightException, CreationException, WorkspaceNotEnabledException {

        String decodedCompletePath = FolderDTO.replaceColonWithSlash(folderPath);
        String completePath = Tools.stripTrailingSlash(decodedCompletePath);
        String destParentFolder = FolderDTO.extractParentFolder(completePath);
        String folderName = folderDTO.getName();

        documentService.moveFolder(completePath, destParentFolder, folderName);

        String completeRenamedFolderId = destParentFolder + '/' + folderName;
        String encodedRenamedFolderId = FolderDTO.replaceSlashWithColon(completeRenamedFolderId);

        FolderDTO renamedFolderDTO = new FolderDTO();
        renamedFolderDTO.setPath(destParentFolder);
        renamedFolderDTO.setName(folderName);
        renamedFolderDTO.setId(encodedRenamedFolderId);

        return renamedFolderDTO;
    }

    /**
     * PUT method for moving folder into an other
     */
    @PUT
    @ApiOperation(value = "Move a folder to given folder",
            response = FolderDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated FolderDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{folderId}/move")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public FolderDTO moveFolder(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Folder id") @PathParam("folderId") String folderPath,
            @ApiParam(required = true, value = "Folder to move") FolderDTO folderDTO)
            throws EntityNotFoundException, EntityAlreadyExistsException, NotAllowedException, AccessRightException, CreationException, WorkspaceNotEnabledException {

        String decodedCompletePath = FolderDTO.replaceColonWithSlash(folderPath);
        String completePath = Tools.stripTrailingSlash(decodedCompletePath);

        String destParentFolder = FolderDTO.replaceColonWithSlash(folderDTO.getId());
        String folderName = Tools.stripLeadingSlash(FolderDTO.extractName(completePath));

        documentService.moveFolder(completePath, destParentFolder, folderName);

        String completeRenamedFolderId = destParentFolder + '/' + folderName;
        String encodedRenamedFolderId = FolderDTO.replaceSlashWithColon(completeRenamedFolderId);

        FolderDTO renamedFolderDTO = new FolderDTO();
        renamedFolderDTO.setPath(destParentFolder);
        renamedFolderDTO.setName(folderName);
        renamedFolderDTO.setId(encodedRenamedFolderId);

        return renamedFolderDTO;
    }

    @POST
    @ApiOperation(value = "Create a sub folder in a given folder",
            response = FolderDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of created FolderDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{parentFolderPath}/folders")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public FolderDTO createSubFolder(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Parent folder id") @PathParam("parentFolderPath") String parentFolderPath,
            @ApiParam(value = "Folder to create", required = true) FolderDTO folder)
            throws EntityNotFoundException, EntityAlreadyExistsException, NotAllowedException, AccessRightException,
            UserNotActiveException, CreationException, WorkspaceNotEnabledException {

        String decodedCompletePath = FolderDTO.replaceColonWithSlash(parentFolderPath);

        String folderName = folder.getName();
        return createFolder(decodedCompletePath, folderName);
    }

    @POST
    @ApiOperation(value = "Create root folder",
            response = FolderDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of created FolderDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public FolderDTO createRootFolder(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Folder to create") FolderDTO folder)
            throws EntityNotFoundException, EntityAlreadyExistsException, NotAllowedException, AccessRightException,
            UserNotActiveException, CreationException, WorkspaceNotEnabledException {

        String folderName = folder.getName();
        return createFolder(workspaceId, folderName);
    }

    /**
     * DELETE method for deleting an instance of FolderResource
     *
     * @param completePath the folder path
     * @return the array of the documents that have also been deleted
     */
    @DELETE
    @ApiOperation(value = "Delete root folder",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion of FolderDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{folderId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteRootFolder(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Folder id") @PathParam("folderId") String completePath)
            throws EntityNotFoundException, NotAllowedException, AccessRightException, UserNotActiveException,
            EntityConstraintException, WorkspaceNotEnabledException {

        deleteFolder(completePath);
        return Response.noContent().build();
    }

    private String getPathFromUrlParams(String workspaceId, String folderId) {
        return folderId == null ? Tools.stripTrailingSlash(workspaceId) : Tools.stripTrailingSlash(FolderDTO.replaceColonWithSlash(folderId));
    }

    private FolderDTO[] getFolders(String workspaceId, String completePath, boolean rootFolder)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        String[] folderNames = documentService.getFolders(completePath);
        FolderDTO[] folderDTOs = new FolderDTO[folderNames.length];

        for (int i = 0; i < folderNames.length; i++) {
            String completeFolderPath;
            if (rootFolder) {
                completeFolderPath = workspaceId + "/" + folderNames[i];
            } else {
                completeFolderPath = completePath + "/" + folderNames[i];
            }

            String encodedFolderId = FolderDTO.replaceSlashWithColon(completeFolderPath);

            folderDTOs[i] = new FolderDTO();
            folderDTOs[i].setPath(completePath);
            folderDTOs[i].setName(folderNames[i]);
            folderDTOs[i].setId(encodedFolderId);

        }

        return folderDTOs;
    }

    private DocumentRevisionKey[] deleteFolder(String pCompletePath)
            throws EntityNotFoundException, AccessRightException, NotAllowedException,
            EntityConstraintException, UserNotActiveException, WorkspaceNotEnabledException {

        String decodedCompletePath = FolderDTO.replaceColonWithSlash(pCompletePath);
        String completePath = Tools.stripTrailingSlash(decodedCompletePath);
        return documentService.deleteFolder(completePath);
    }

    private FolderDTO createFolder(String pCompletePath, String pFolderName)
            throws EntityNotFoundException, EntityAlreadyExistsException, CreationException, AccessRightException, NotAllowedException, WorkspaceNotEnabledException {
        Folder createdFolder = documentService.createFolder(pCompletePath, pFolderName);

        String completeCreatedFolderPath = createdFolder.getCompletePath() + '/' + createdFolder.getShortName();
        String encodedFolderId = FolderDTO.replaceSlashWithColon(completeCreatedFolderPath);

        FolderDTO createdFolderDTOs = new FolderDTO();
        createdFolderDTOs.setPath(createdFolder.getCompletePath());
        createdFolderDTOs.setName(createdFolder.getShortName());
        createdFolderDTOs.setId(encodedFolderId);

        return createdFolderDTOs;
    }

}
