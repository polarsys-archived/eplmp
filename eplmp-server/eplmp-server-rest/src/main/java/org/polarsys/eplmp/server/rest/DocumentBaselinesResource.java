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
import org.polarsys.eplmp.core.configuration.DocumentBaseline;
import org.polarsys.eplmp.core.configuration.DocumentCollection;
import org.polarsys.eplmp.core.document.DocumentRevisionKey;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IDocumentBaselineManagerLocal;
import org.polarsys.eplmp.server.rest.dto.baseline.BaselinedDocumentDTO;
import org.polarsys.eplmp.server.rest.dto.baseline.DocumentBaselineDTO;
import org.polarsys.eplmp.server.rest.util.DocumentBaselineFileExport;
import org.polarsys.eplmp.server.rest.util.FileDownloadTools;

import javax.annotation.PostConstruct;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Taylor LABEJOF
 */

@RequestScoped
@Api(hidden = true, value = "documentBaseline", description = "Operations about document baselines",
        authorizations = {@Authorization(value = "authorization")})
@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
public class DocumentBaselinesResource {

    @Inject
    private IDocumentBaselineManagerLocal documentBaselineService;

    private Mapper mapper;

    public DocumentBaselinesResource() {
    }

    @PostConstruct
    public void init() {
        mapper = DozerBeanMapperSingletonWrapper.getInstance();
    }

    /**
     * Get all document baselines of a specific workspace
     *
     * @param workspaceId The id of the specific workspace
     * @return The list of baselines
     */
    @GET
    @ApiOperation(value = "Get document baselines",
            response = DocumentBaselineDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of checked out DocumentBaselineDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDocumentBaselines(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        List<DocumentBaseline> documentBaselines = documentBaselineService.getBaselines(workspaceId);
        List<DocumentBaselineDTO> baselinesDTO = new ArrayList<>();
        for (DocumentBaseline documentBaseline : documentBaselines) {
            DocumentBaselineDTO documentBaselineDTO = mapper.map(documentBaseline, DocumentBaselineDTO.class);
            baselinesDTO.add(documentBaselineDTO);
        }
        return Response.ok(new GenericEntity<List<DocumentBaselineDTO>>((List<DocumentBaselineDTO>) baselinesDTO) {
        }).build();
    }

    /**
     * Create a baseline
     *
     * @param workspaceId         The current workspace
     * @param documentBaselineDTO Description of the baseline to create
     * @return the created baseline
     */
    @POST
    @ApiOperation(value = "Create a new document baseline",
            response = DocumentBaselineDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Successful retrieval of created DocumentBaselineDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createDocumentBaseline(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Document baseline to create") DocumentBaselineDTO documentBaselineDTO)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, NotAllowedException, WorkspaceNotEnabledException {

        List<BaselinedDocumentDTO> baselinedDocumentsDTO = documentBaselineDTO.getBaselinedDocuments();
        List<DocumentRevisionKey> documentRevisionKeys = baselinedDocumentsDTO.stream()
                .map(document -> new DocumentRevisionKey(workspaceId, document.getDocumentMasterId(), document.getVersion()))
                .collect(Collectors.toList());

        DocumentBaseline baseline = documentBaselineService.createBaseline(workspaceId, documentBaselineDTO.getName(), documentBaselineDTO.getType(), documentBaselineDTO.getDescription(), documentRevisionKeys);
        DocumentBaselineDTO baselineDTO = getBaseline(workspaceId, baseline.getId());

        return Tools.prepareCreatedResponse(String.valueOf(baselineDTO.getId()), baselineDTO);
    }


    /**
     * Delete a specific document baseline
     *
     * @param workspaceId The workspace of the specific baseline
     * @param baselineId  The id of the specific document baseline
     * @return A response if the baseline was deleted
     */
    @DELETE
    @ApiOperation(value = "Delete a document baseline",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion of DocumentBaselineDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{baselineId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteBaseline(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Baseline id") @PathParam("baselineId") int baselineId)
            throws EntityNotFoundException, AccessRightException, UserNotActiveException, WorkspaceNotEnabledException {

        documentBaselineService.deleteBaseline(workspaceId, baselineId);
        return Response.noContent().build();
    }

    /**
     * Get a specific document baseline ( with documents list )
     *
     * @param workspaceId The workspace of the specific baseline
     * @param baselineId  The id of the specific document baseline
     * @return The specif baseline
     */
    @GET
    @ApiOperation(value = "Get document baseline by id",
            response = DocumentBaselineDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of DocumentBaselineDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{baselineId}")
    @Produces(MediaType.APPLICATION_JSON)
    public DocumentBaselineDTO getBaseline(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Baseline id") @PathParam("baselineId") int baselineId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        DocumentBaseline documentBaseline = documentBaselineService.getBaselineLight(workspaceId, baselineId);
        DocumentCollection documentCollection = documentBaselineService.getACLFilteredDocumentCollection(workspaceId, baselineId);
        List<BaselinedDocumentDTO> baselinedDocumentDTOs = Tools.mapBaselinedDocumentsToBaselinedDocumentDTOs(documentCollection);
        DocumentBaselineDTO baselineDTO = mapper.map(documentBaseline, DocumentBaselineDTO.class);
        baselineDTO.setBaselinedDocuments(baselinedDocumentDTOs);
        return baselineDTO;
    }

    /**
     * Get a specific document baseline
     *
     * @param workspaceId The workspace of the specific baseline
     * @param baselineId  The id of the specific document baseline
     * @return The specif baseline
     */
    @GET
    @ApiOperation(value = "Get document baseline in a light format",
            response = DocumentBaselineDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of DocumentBaselineDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{baselineId}-light")
    @Produces(MediaType.APPLICATION_JSON)
    public DocumentBaselineDTO getBaselineLight(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Baseline id") @PathParam("baselineId") int baselineId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        DocumentBaseline documentBaseline = documentBaselineService.getBaselineLight(workspaceId, baselineId);
        return mapper.map(documentBaseline, DocumentBaselineDTO.class);
    }

    @GET
    @ApiOperation(value = "Export document baseline's files",
            response = File.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful files export, download a zipped file containing requested files."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{baselineId}/export-files")
    public Response exportDocumentFiles(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Baseline id") @PathParam("baselineId") int baselineId)
            throws EntityNotFoundException, WorkspaceNotEnabledException, UserNotActiveException {

        DocumentBaselineFileExport documentBaselineFileExport = new DocumentBaselineFileExport(workspaceId, baselineId);

        DocumentBaseline documentBaseline = documentBaselineService.getBaselineLight(workspaceId, baselineId);
        String fileName = FileDownloadTools.getFileName(documentBaseline.getName() + "-export", "zip");
        String contentDisposition = FileDownloadTools.getContentDisposition("attachment", fileName);

        return Response.ok()
                .header("Content-Type", "application/download")
                .header("Content-Disposition", contentDisposition)
                .entity(documentBaselineFileExport).build();
    }

}
