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
import org.polarsys.eplmp.core.change.ChangeIssue;
import org.polarsys.eplmp.core.document.DocumentIterationKey;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.meta.Tag;
import org.polarsys.eplmp.core.product.PartIterationKey;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IChangeManagerLocal;
import org.polarsys.eplmp.server.rest.dto.*;
import org.polarsys.eplmp.server.rest.dto.change.ChangeIssueDTO;
import org.polarsys.eplmp.server.rest.dto.change.ChangeItemDTO;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RequestScoped
@Api(hidden = true, value = "issues", description = "Operations about issues",
        authorizations = {@Authorization(value = "authorization")})
@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
public class ChangeIssuesResource {

    @Inject
    private IChangeManagerLocal changeManager;

    private Mapper mapper;

    public ChangeIssuesResource() {

    }

    @PostConstruct
    public void init() {
        mapper = DozerBeanMapperSingletonWrapper.getInstance();
    }

    @GET
    @ApiOperation(value = "Get change issues for given parameters",
            response = ChangeIssueDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of ChangeIssueDTOs. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIssues(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        List<ChangeIssue> changeIssues = changeManager.getChangeIssues(workspaceId);
        List<ChangeIssueDTO> changeIssueDTOs = new ArrayList<>();
        for (ChangeIssue issue : changeIssues) {
            ChangeIssueDTO changeIssueDTO = mapper.map(issue, ChangeIssueDTO.class);
            changeIssueDTO.setWritable(changeManager.isChangeItemWritable(issue));
            changeIssueDTOs.add(changeIssueDTO);
        }
        return Response.ok(new GenericEntity<List<ChangeIssueDTO>>((List<ChangeIssueDTO>) changeIssueDTOs) {
        }).build();
    }

    @POST
    @ApiOperation(value = "Create a new change issue",
            response = ChangeIssueDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of created ChangeIssueDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ChangeIssueDTO createIssue(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Change issue to create") ChangeIssueDTO changeIssueDTO)
            throws EntityNotFoundException, AccessRightException, NotAllowedException, WorkspaceNotEnabledException {
        ChangeIssue changeIssue = changeManager.createChangeIssue(workspaceId,
                changeIssueDTO.getName(),
                changeIssueDTO.getDescription(),
                changeIssueDTO.getInitiator(),
                changeIssueDTO.getPriority(),
                changeIssueDTO.getAssignee(),
                changeIssueDTO.getCategory());
        ChangeIssueDTO ret = mapper.map(changeIssue, ChangeIssueDTO.class);
        ret.setWritable(true);
        return ret;
    }

    @GET
    @ApiOperation(value = "Search change issue with given name",
            response = ChangeIssueDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of ChangeIssueDTOs. It can be an empty list"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("link")
    @Produces(MediaType.APPLICATION_JSON)
    public ChangeIssueDTO[] searchIssuesByName(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Query") @QueryParam("q") String name)
            throws EntityNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        int maxResults = 8;
        List<ChangeIssue> issues = changeManager.getIssuesWithName(workspaceId, name, maxResults);
        List<ChangeIssueDTO> issueDTOs = new ArrayList<>();
        for (ChangeIssue issue : issues) {
            ChangeIssueDTO changeIssueDTO = mapper.map(issue, ChangeIssueDTO.class);
            changeIssueDTO.setWritable(changeManager.isChangeItemWritable(issue));
            issueDTOs.add(changeIssueDTO);
        }
        return issueDTOs.toArray(new ChangeIssueDTO[issueDTOs.size()]);
    }

    @GET
    @ApiOperation(value = "Get change issue with given id",
            response = ChangeIssueDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of ChangeIssueDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{issueId}")
    public ChangeIssueDTO getIssue(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Issue id") @PathParam("issueId") int issueId)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {
        ChangeIssue changeIssue = changeManager.getChangeIssue(workspaceId, issueId);
        ChangeIssueDTO changeIssueDTO = mapper.map(changeIssue, ChangeIssueDTO.class);
        changeIssueDTO.setWritable(changeManager.isChangeItemWritable(changeIssue));
        return changeIssueDTO;
    }

    @PUT
    @ApiOperation(value = "Update change issue",
            response = ChangeIssueDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated ChangeIssueDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{issueId}")
    public ChangeIssueDTO updateIssue(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Issue id") @PathParam("issueId") int issueId,
            @ApiParam(required = true, value = "Change issue to update") ChangeIssueDTO pChangeIssueDTO)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, NotAllowedException, WorkspaceNotEnabledException {
        ChangeIssue changeIssue = changeManager.updateChangeIssue(issueId,
                workspaceId,
                pChangeIssueDTO.getDescription(),
                pChangeIssueDTO.getPriority(),
                pChangeIssueDTO.getAssignee(),
                pChangeIssueDTO.getCategory());
        ChangeIssueDTO changeIssueDTO = mapper.map(changeIssue, ChangeIssueDTO.class);
        changeIssueDTO.setWritable(changeManager.isChangeItemWritable(changeIssue));
        return changeIssueDTO;
    }

    @DELETE
    @ApiOperation(value = "Delete change issue",
            response = ChangeIssueDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful deletion"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{issueId}")
    public Response removeIssue(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Issue id") @PathParam("issueId") int issueId)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, EntityConstraintException, WorkspaceNotEnabledException {
        changeManager.deleteChangeIssue(issueId);
        return Response.noContent().build();
    }

    @PUT
    @ApiOperation(value = "Update tags attached to a change issue",
            response = ChangeIssueDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated ChangeIssueDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{issueId}/tags")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ChangeItemDTO saveChangeItemTags(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Issue id") @PathParam("issueId") int issueId,
            @ApiParam(required = true, value = "Tag list to add") TagListDTO tagListDTO)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        List<TagDTO> tagDTOs = tagListDTO.getTags();
        String[] tagsLabel = new String[tagDTOs.size()];
        for (int i = 0; i < tagDTOs.size(); i++) {
            tagsLabel[i] = tagDTOs.get(i).getLabel();
        }

        ChangeIssue changeIssue = changeManager.saveChangeIssueTags(workspaceId, issueId, tagsLabel);
        ChangeIssueDTO changeIssueDTO = mapper.map(changeIssue, ChangeIssueDTO.class);
        changeIssueDTO.setWritable(changeManager.isChangeItemWritable(changeIssue));
        return changeIssueDTO;
    }

    @POST
    @ApiOperation(value = "Attached a new tag to a change issue",
            response = ChangeIssueDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated ChangeIssueDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{issueId}/tags")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ChangeIssueDTO addTagToChangeIssue(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Issue id") @PathParam("issueId") int issueId,
            @ApiParam(required = true, value = "Tag list to add") TagListDTO tagListDTO)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {
        ChangeIssue changeIssue = changeManager.getChangeIssue(workspaceId, issueId);
        Set<Tag> tags = changeIssue.getTags();
        Set<String> tagLabels = new HashSet<>();

        for (TagDTO tagDTO : tagListDTO.getTags()) {
            tagLabels.add(tagDTO.getLabel());
        }

        for (Tag tag : tags) {
            tagLabels.add(tag.getLabel());
        }

        changeIssue = changeManager.saveChangeIssueTags(workspaceId, issueId, tagLabels.toArray(new String[tagLabels.size()]));
        ChangeIssueDTO changeIssueDTO = mapper.map(changeIssue, ChangeIssueDTO.class);
        changeIssueDTO.setWritable(changeManager.isChangeItemWritable(changeIssue));
        return changeIssueDTO;
    }

    @DELETE
    @ApiOperation(value = "Delete a tag attached to a change issue",
            response = ChangeIssueDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful retrieval of updated ChangeIssueDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{issueId}/tags/{tagName}")
    public Response removeTagsFromChangeIssue(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Issue id") @PathParam("issueId") int issueId,
            @ApiParam(required = true, value = "Tag name") @PathParam("tagName") String tagName)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {
        changeManager.removeChangeIssueTag(workspaceId, issueId, tagName);
        return Response.noContent().build();
    }

    @PUT
    @ApiOperation(value = "Attach a document to a change issue",
            response = ChangeIssueDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated ChangeIssueDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{issueId}/affected-documents")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ChangeIssueDTO saveChangeIssueAffectedDocuments(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Issue id") @PathParam("issueId") int issueId,
            @ApiParam(required = true, value = "Document list to save as affected") DocumentIterationListDTO documentListDTO)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        List<DocumentIterationDTO> documentIterationDTOs = documentListDTO.getDocuments();
        DocumentIterationKey[] links = createDocumentIterationKeys(documentIterationDTOs);

        ChangeIssue changeIssue = changeManager.saveChangeIssueAffectedDocuments(workspaceId, issueId, links);
        ChangeIssueDTO changeIssueDTO = mapper.map(changeIssue, ChangeIssueDTO.class);
        changeIssueDTO.setWritable(changeManager.isChangeItemWritable(changeIssue));
        return changeIssueDTO;
    }

    @PUT
    @ApiOperation(value = "Attach a part to a change issue",
            response = ChangeIssueDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated ChangeIssueDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{issueId}/affected-parts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ChangeIssueDTO saveChangeIssueAffectedParts(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String workspaceId,
            @ApiParam(required = true, value = "Issue id") @PathParam("issueId") int issueId,
            @ApiParam(required = true, value = "Part list to save as affected") PartIterationListDTO partIterationListDTO)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        List<PartIterationDTO> partIterationDTOs = partIterationListDTO.getParts();
        PartIterationKey[] links = createPartIterationKeys(partIterationDTOs);

        ChangeIssue changeIssue = changeManager.saveChangeIssueAffectedParts(workspaceId, issueId, links);
        ChangeIssueDTO changeIssueDTO = mapper.map(changeIssue, ChangeIssueDTO.class);
        changeIssueDTO.setWritable(changeManager.isChangeItemWritable(changeIssue));
        return changeIssueDTO;
    }

    @PUT
    @ApiOperation(value = "Update ACL of a change issue",
            response = ChangeIssueDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated ChangeIssueDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("{issueId}/acl")
    @Consumes(MediaType.APPLICATION_JSON)
    public ChangeIssueDTO updateChangeIssueACL(
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") String pWorkspaceId,
            @ApiParam(required = true, value = "Issue id") @PathParam("issueId") int issueId,
            @ApiParam(required = true, value = "ACL rules to set") ACLDTO acl)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        ChangeIssue changeIssue;

        if (acl.hasEntries()) {
            changeIssue = changeManager.updateACLForChangeIssue(pWorkspaceId, issueId, acl.getUserEntriesMap(), acl.getUserGroupEntriesMap());
        } else {
            changeIssue = changeManager.removeACLFromChangeIssue(pWorkspaceId, issueId);
        }

        ChangeIssueDTO changeIssueDTO = mapper.map(changeIssue, ChangeIssueDTO.class);
        changeIssueDTO.setWritable(changeManager.isChangeItemWritable(changeIssue));

        return changeIssueDTO;
    }


    private DocumentIterationKey[] createDocumentIterationKeys(List<DocumentIterationDTO> documentIterationDTOList) {
        DocumentIterationKey[] data = new DocumentIterationKey[documentIterationDTOList.size()];
        int i = 0;
        for (DocumentIterationDTO dto : documentIterationDTOList) {
            data[i++] = new DocumentIterationKey(dto.getWorkspaceId(), dto.getDocumentMasterId(), dto.getVersion(), dto.getIteration());
        }

        return data;
    }

    private PartIterationKey[] createPartIterationKeys(List<PartIterationDTO> partIterationDTOList) {
        PartIterationKey[] data = new PartIterationKey[partIterationDTOList.size()];
        int i = 0;
        for (PartIterationDTO dto : partIterationDTOList) {
            data[i++] = new PartIterationKey(dto.getWorkspaceId(), dto.getNumber(), dto.getVersion(), dto.getIteration());
        }

        return data;
    }
}
