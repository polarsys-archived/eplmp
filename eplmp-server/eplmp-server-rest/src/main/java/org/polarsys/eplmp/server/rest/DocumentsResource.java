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
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.query.DocumentSearchQuery;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IDocumentManagerLocal;
import org.polarsys.eplmp.core.services.IDocumentWorkflowManagerLocal;
import org.polarsys.eplmp.server.rest.dto.CountDTO;
import org.polarsys.eplmp.server.rest.dto.DocumentRevisionDTO;
import org.polarsys.eplmp.server.rest.util.SearchQueryParser;

import javax.annotation.PostConstruct;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@RequestScoped
@Api(hidden = true, value = "documents", description = "Operations about documents",
        authorizations = {@Authorization(value = "authorization")})
@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
public class DocumentsResource {

    private static final Logger LOGGER = Logger.getLogger(DocumentsResource.class.getName());
    @Inject
    private IDocumentManagerLocal documentService;
    @Inject
    private IDocumentWorkflowManagerLocal documentWorkflowService;
    @Inject
    private DocumentResource documentResource;
    private Mapper mapper;

    public DocumentsResource() {
    }

    @PostConstruct
    public void init() {
        mapper = DozerBeanMapperSingletonWrapper.getInstance();
    }

    @ApiOperation(value = "SubResource : DocumentResource")
    @Path("{documentId: [^/].*}-{documentVersion:[A-Z]+}")
    public DocumentResource getDocumentResource() {
        return documentResource;
    }

    @GET
    @ApiOperation(value = "Get document revisions in workspace",
            response = DocumentRevisionDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of DocumentRevisionDTO. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public DocumentRevisionDTO[] getDocumentsInWorkspace(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = false, value = "Start offset", defaultValue = "0") @QueryParam("start") int start,
            @ApiParam(required = false, value = "Max results", defaultValue = "20") @QueryParam("max") int max)
            throws UserNotActiveException, EntityNotFoundException, WorkspaceNotEnabledException {

        int maxResult = max != 0 ? max : 20;
        DocumentRevision[] docRs = documentService.getFilteredDocumentsInWorkspace(workspaceId, start, maxResult);
        return mapToDTOs(docRs);
    }

    @GET
    @ApiOperation(value = "Search document revisions",
            response = DocumentRevisionDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of searched DocumentRevisionDTO. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("search")
    @Produces(MediaType.APPLICATION_JSON)
    public DocumentRevisionDTO[] searchDocumentRevision(
            @Context UriInfo uri,
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = false, value = "Query") @QueryParam("q") String q,
            @ApiParam(required = false, value = "Document id") @QueryParam("id") String id,
            @ApiParam(required = false, value = "Document title") @QueryParam("title") String title,
            @ApiParam(required = false, value = "Document type") @QueryParam("type") String type,
            @ApiParam(required = false, value = "Document version") @QueryParam("version") String version,
            @ApiParam(required = false, value = "Document author") @QueryParam("author") String author,
            @ApiParam(required = false, value = "Document tags") @QueryParam("tags") String tags,
            @ApiParam(required = false, value = "Document files content") @QueryParam("content") String content,
            @ApiParam(required = false, value = "Document created from date") @QueryParam("createdFrom") String createdFrom,
            @ApiParam(required = false, value = "Document creation to date") @QueryParam("createdTo") String createdTo,
            @ApiParam(required = false, value = "Document modified from date") @QueryParam("modifiedFrom") String modifiedFrom,
            @ApiParam(required = false, value = "Document modified to date") @QueryParam("modifiedTo") String modifiedTo,
            @ApiParam(required = false, value = "Document attributes") @QueryParam("attributes") String attributes,
            @ApiParam(required = false, value = "Folder") @QueryParam("folder") String folder,
            @ApiParam(required = false, value = "Start offset", defaultValue = "0") @QueryParam("from") int from,
            @ApiParam(required = false, value = "Max results", defaultValue = "10") @QueryParam("size") int size,
            @ApiParam(required = false, value = "Search mode (false for history/ true for head only)") @QueryParam("fetchHeadOnly") boolean fetchHeadOnly
    ) throws EntityNotFoundException, UserNotActiveException, NotAllowedException, WorkspaceNotEnabledException, IndexerRequestException, IndexerNotAvailableException {
        // Set default search size
        size = size == 0 ? 10 : size;

        MultivaluedMap<String, String> params = uri.getQueryParameters();
        DocumentSearchQuery documentSearchQuery = SearchQueryParser.parseDocumentStringQuery(workspaceId, params);
        DocumentRevision[] docRs = documentService.searchDocumentRevisions(documentSearchQuery, from, size);
        return mapToDTOs(docRs);
    }

    @GET
    @ApiOperation(value = "Count document revisions",
            response = CountDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of documents count"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("count")
    @Produces(MediaType.APPLICATION_JSON)
    public CountDTO getDocumentsInWorkspaceCount(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        return new CountDTO(documentService.getDocumentsInWorkspaceCount(Tools.stripTrailingSlash(workspaceId)));
    }


    @GET
    @ApiOperation(value = "Get checked out document revisions",
            response = DocumentRevisionDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of checked out documents. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("checkedout")
    @Produces(MediaType.APPLICATION_JSON)
    public DocumentRevisionDTO[] getCheckedOutDocuments(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        DocumentRevision[] checkedOutDocumentRevisions = documentService.getCheckedOutDocumentRevisions(workspaceId);
        return mapToDTOs(checkedOutDocumentRevisions);
    }

    @GET
    @ApiOperation(value = "Count checked out document revisions",
            response = DocumentRevisionDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of checked out documents count"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("countCheckedOut")
    @Produces(MediaType.APPLICATION_JSON)
    public CountDTO countCheckedOutDocs(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        return new CountDTO(documentService.getCheckedOutDocumentRevisions(workspaceId).length);
    }


    @GET
    @ApiOperation(value = "Search document revisions by id and/or name",
            response = DocumentRevisionDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of searched documents. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("doc_revs")
    @Produces(MediaType.APPLICATION_JSON)
    public DocumentRevisionDTO[] searchDocumentRevisionsToLink(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Query") @QueryParam("q") String q,
            @ApiParam(required = false, value = "Max results", defaultValue = "20") @QueryParam("l") int limit)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        int maxResults = limit == 0 ? 20 : limit;
        DocumentRevision[] docRs = documentService.getDocumentRevisionsWithReferenceOrTitle(workspaceId, q, maxResults);

        List<DocumentRevisionDTO> documentRevisionDTOs = new ArrayList<>();
        for (DocumentRevision docR : docRs) {
            DocumentRevisionDTO docRevDTO = new DocumentRevisionDTO(docR.getWorkspaceId(), docR.getDocumentMasterId(), docR.getTitle(), docR.getVersion());
            documentRevisionDTOs.add(docRevDTO);
        }

        return documentRevisionDTOs.toArray(new DocumentRevisionDTO[documentRevisionDTOs.size()]);
    }


    private DocumentRevisionDTO[] mapToDTOs(DocumentRevision[] docRs) throws UserNotFoundException, WorkspaceNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        List<DocumentRevisionDTO> documentRevisionDTOs = new ArrayList<>();

        for (DocumentRevision doc : docRs) {
            DocumentRevisionDTO dto = mapper.map(doc, DocumentRevisionDTO.class);
            dto = Tools.createLightDocumentRevisionDTO(dto);
            dto.setPath(doc.getLocation().getCompletePath());
            dto.setIterationSubscription(documentService.isUserIterationChangeEventSubscribedForGivenDocument(doc.getWorkspaceId(), doc));
            dto.setStateSubscription(documentService.isUserStateChangeEventSubscribedForGivenDocument(doc.getWorkspaceId(), doc));
            documentRevisionDTOs.add(dto);
        }

        return documentRevisionDTOs.toArray(new DocumentRevisionDTO[documentRevisionDTOs.size()]);
    }
}
