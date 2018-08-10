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

import org.dozer.DozerBeanMapperSingletonWrapper;
import org.dozer.Mapper;
import org.polarsys.eplmp.core.change.ModificationNotification;
import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.common.UserGroup;
import org.polarsys.eplmp.core.configuration.*;
import org.polarsys.eplmp.core.document.DocumentIteration;
import org.polarsys.eplmp.core.product.*;
import org.polarsys.eplmp.core.security.ACL;
import org.polarsys.eplmp.core.security.ACLUserEntry;
import org.polarsys.eplmp.core.security.ACLUserGroupEntry;
import org.polarsys.eplmp.server.rest.dto.*;
import org.polarsys.eplmp.server.rest.dto.baseline.*;

import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Florent Garin
 */
public class Tools {

    private static final Logger LOGGER = Logger.getLogger(Tools.class.getName());

    private Tools() {

    }

    public static String stripTrailingSlash(String completePath) {
        if (completePath == null || completePath.isEmpty()) {
            return completePath;
        }
        if (completePath.charAt(completePath.length() - 1) == '/') {
            return completePath.substring(0, completePath.length() - 1);
        } else {
            return completePath;
        }
    }

    public static String stripLeadingSlash(String completePath) {
        if (completePath.charAt(0) == '/') {
            return completePath.substring(1, completePath.length());
        } else {
            return completePath;
        }
    }

    public static DocumentRevisionDTO createLightDocumentRevisionDTO(DocumentRevisionDTO docRsDTO) {

        if (docRsDTO.getLastIteration() != null) {
            DocumentIterationDTO lastIteration = docRsDTO.getLastIteration();
            List<DocumentIterationDTO> iterations = new ArrayList<>();
            iterations.add(lastIteration);
            docRsDTO.setDocumentIterations(iterations);
        }

        docRsDTO.setTags(null);
        docRsDTO.setWorkflow(null);

        return docRsDTO;
    }


    public static ACLDTO mapACLtoACLDTO(ACL acl) {

        ACLDTO aclDTO = new ACLDTO();

        for (Map.Entry<User, ACLUserEntry> entry : acl.getUserEntries().entrySet()) {
            ACLUserEntry aclEntry = entry.getValue();
            aclDTO.addUserEntry(aclEntry.getPrincipalLogin(), aclEntry.getPermission());
        }

        for (Map.Entry<UserGroup, ACLUserGroupEntry> entry : acl.getGroupEntries().entrySet()) {
            ACLUserGroupEntry aclEntry = entry.getValue();
            aclDTO.addGroupEntry(aclEntry.getPrincipalId(), aclEntry.getPermission());
        }

        return aclDTO;

    }

    public static List<ModificationNotificationDTO> mapModificationNotificationsToModificationNotificationDTO(Collection<ModificationNotification> pNotifications) {
        return pNotifications.stream().map(Tools::mapModificationNotificationToModificationNotificationDTO).collect(Collectors.toList());
    }

    public static ModificationNotificationDTO mapModificationNotificationToModificationNotificationDTO(ModificationNotification pNotification) {
        ModificationNotificationDTO dto = new ModificationNotificationDTO();
        Mapper mapper = DozerBeanMapperSingletonWrapper.getInstance();

        UserDTO userDTO = mapper.map(pNotification.getModifiedPart().getAuthor(), UserDTO.class);
        dto.setAuthor(userDTO);

        dto.setId(pNotification.getId());

        dto.setImpactedPartNumber(pNotification.getImpactedPart().getNumber());
        dto.setImpactedPartVersion(pNotification.getImpactedPart().getVersion());

        User ackAuthor = pNotification.getAcknowledgementAuthor();
        if (ackAuthor != null) {
            UserDTO ackDTO = mapper.map(ackAuthor, UserDTO.class);
            dto.setAckAuthor(ackDTO);
        }
        dto.setAcknowledged(pNotification.isAcknowledged());
        dto.setAckComment(pNotification.getAcknowledgementComment());
        dto.setAckDate(pNotification.getAcknowledgementDate());

        dto.setCheckInDate(pNotification.getModifiedPart().getCheckInDate());
        dto.setIterationNote(pNotification.getModifiedPart().getIterationNote());

        dto.setModifiedPartIteration(pNotification.getModifiedPart().getIteration());
        dto.setModifiedPartNumber(pNotification.getModifiedPart().getPartNumber());
        dto.setModifiedPartName(pNotification.getModifiedPart().getPartName());
        dto.setModifiedPartVersion(pNotification.getModifiedPart().getPartVersion());

        return dto;
    }

    public static PartRevisionDTO mapPartRevisionToPartDTO(PartRevision partRevision) {

        Mapper mapper = DozerBeanMapperSingletonWrapper.getInstance();

        PartRevisionDTO partRevisionDTO = mapper.map(partRevision, PartRevisionDTO.class);

        partRevisionDTO.setNumber(partRevision.getPartNumber());
        partRevisionDTO.setPartKey(partRevision.getPartNumber() + "-" + partRevision.getVersion());
        partRevisionDTO.setName(partRevision.getPartMaster().getName());
        partRevisionDTO.setStandardPart(partRevision.getPartMaster().isStandardPart());
        partRevisionDTO.setType(partRevision.getPartMaster().getType());

        if (partRevision.isObsolete()) {
            partRevisionDTO.setObsoleteDate(partRevision.getObsoleteDate());
            UserDTO obsoleteUserDTO = mapper.map(partRevision.getObsoleteAuthor(), UserDTO.class);
            partRevisionDTO.setObsoleteAuthor(obsoleteUserDTO);
        }

        if (partRevision.getReleaseAuthor() != null) {
            partRevisionDTO.setReleaseDate(partRevision.getReleaseDate());
            UserDTO releaseUserDTO = mapper.map(partRevision.getReleaseAuthor(), UserDTO.class);
            partRevisionDTO.setReleaseAuthor(releaseUserDTO);
        }

        List<PartIterationDTO> partIterationDTOs = new ArrayList<>();
        for (PartIteration partIteration : partRevision.getPartIterations()) {
            partIterationDTOs.add(mapPartIterationToPartIterationDTO(partIteration));
        }
        partRevisionDTO.setPartIterations(partIterationDTOs);

        if (partRevision.isCheckedOut()) {
            partRevisionDTO.setCheckOutDate(partRevision.getCheckOutDate());
            UserDTO checkoutUserDTO = mapper.map(partRevision.getCheckOutUser(), UserDTO.class);
            partRevisionDTO.setCheckOutUser(checkoutUserDTO);
        }

        if (partRevision.hasWorkflow()) {
            partRevisionDTO.setLifeCycleState(partRevision.getWorkflow().getLifeCycleState());
            partRevisionDTO.setWorkflow(mapper.map(partRevision.getWorkflow(), WorkflowDTO.class));
        }

        ACL acl = partRevision.getACL();
        if (acl != null) {
            partRevisionDTO.setAcl(Tools.mapACLtoACLDTO(acl));
        } else {
            partRevisionDTO.setAcl(null);
        }


        return partRevisionDTO;
    }

    public static PartIterationDTO mapPartIterationToPartIterationDTO(PartIteration partIteration) {
        Mapper mapper = DozerBeanMapperSingletonWrapper.getInstance();

        List<PartUsageLinkDTO> usageLinksDTO = new ArrayList<>();
        PartIterationDTO partIterationDTO = mapper.map(partIteration, PartIterationDTO.class);

        for (PartUsageLink partUsageLink : partIteration.getComponents()) {
            PartUsageLinkDTO partUsageLinkDTO = mapper.map(partUsageLink, PartUsageLinkDTO.class);
            List<CADInstanceDTO> cadInstancesDTO = new ArrayList<>();
            for (CADInstance cadInstance : partUsageLink.getCadInstances()) {
                CADInstanceDTO cadInstanceDTO = mapper.map(cadInstance, CADInstanceDTO.class);
                cadInstanceDTO.setMatrix(cadInstance.getRotationMatrix().getValues());
                cadInstancesDTO.add(cadInstanceDTO);
            }
            List<PartSubstituteLinkDTO> substituteLinkDTOs = new ArrayList<>();
            for (PartSubstituteLink partSubstituteLink : partUsageLink.getSubstitutes()) {
                PartSubstituteLinkDTO substituteLinkDTO = mapper.map(partSubstituteLink, PartSubstituteLinkDTO.class);
                substituteLinkDTOs.add(substituteLinkDTO);

            }
            partUsageLinkDTO.setCadInstances(cadInstancesDTO);
            partUsageLinkDTO.setSubstitutes(substituteLinkDTOs);
            usageLinksDTO.add(partUsageLinkDTO);
        }
        partIterationDTO.setComponents(usageLinksDTO);
        partIterationDTO.setNumber(partIteration.getPartRevision().getPartNumber());
        partIterationDTO.setVersion(partIteration.getPartRevision().getVersion());

        if (!partIteration.getGeometries().isEmpty()) {
            partIterationDTO.setGeometryFileURI("/api/files/" + partIteration.getSortedGeometries().get(0).getFullName());
        }

        return partIterationDTO;
    }

    public static BaselinedPartDTO mapBaselinedPartToBaselinedPartDTO(BaselinedPart baselinedPart) {
        return mapPartIterationToBaselinedPart(baselinedPart.getTargetPart());
    }

    public static List<BaselinedDocumentDTO> mapBaselinedDocumentsToBaselinedDocumentDTOs(DocumentCollection documentCollection) {
        List<BaselinedDocumentDTO> baselinedDocumentDTOs = new ArrayList<>();
        Map<BaselinedDocumentKey, BaselinedDocument> baselinedDocuments = documentCollection.getBaselinedDocuments();

        for (Map.Entry<BaselinedDocumentKey, BaselinedDocument> map : baselinedDocuments.entrySet()) {
            BaselinedDocument baselinedDocument = map.getValue();
            DocumentIteration targetDocument = baselinedDocument.getTargetDocument();
            baselinedDocumentDTOs.add(new BaselinedDocumentDTO(targetDocument.getDocumentMasterId(), targetDocument.getVersion(), targetDocument.getIteration(), targetDocument.getTitle()));
        }

        return baselinedDocumentDTOs;
    }

    public static BaselinedPartDTO mapPartIterationToBaselinedPart(PartIteration partIteration) {

        BaselinedPartDTO baselinedPartDTO = new BaselinedPartDTO();
        baselinedPartDTO.setNumber(partIteration.getPartNumber());
        baselinedPartDTO.setVersion(partIteration.getVersion());
        baselinedPartDTO.setName(partIteration.getPartRevision().getPartMaster().getName());
        baselinedPartDTO.setIteration(partIteration.getIteration());

        List<BaselinedPartOptionDTO> availableIterations = new ArrayList<>();
        for (PartRevision partRevision : partIteration.getPartRevision().getPartMaster().getPartRevisions()) {
            BaselinedPartOptionDTO option = new BaselinedPartOptionDTO(partRevision.getVersion(),
                    partRevision.getLastIteration().getIteration(),
                    partRevision.isReleased());
            availableIterations.add(option);
        }
        baselinedPartDTO.setAvailableIterations(availableIterations);

        return baselinedPartDTO;
    }

    public static BaselinedPartDTO createBaselinedPartDTOFromPartList(List<PartIteration> availableParts) {
        BaselinedPartDTO baselinedPartDTO = new BaselinedPartDTO();
        PartIteration max = Collections.max(availableParts);
        baselinedPartDTO.setNumber(max.getPartNumber());
        baselinedPartDTO.setVersion(max.getVersion());
        baselinedPartDTO.setName(max.getPartRevision().getPartMaster().getName());
        baselinedPartDTO.setIteration(max.getIteration());
        List<BaselinedPartOptionDTO> availableIterations = new ArrayList<>();
        for (PartIteration partIteration : availableParts) {
            availableIterations.add(new BaselinedPartOptionDTO(partIteration.getVersion(), partIteration.getIteration(), partIteration.getPartRevision().isReleased()));
        }
        baselinedPartDTO.setAvailableIterations(availableIterations);
        return baselinedPartDTO;
    }


    public static PathChoiceDTO mapPathChoiceDTO(PathChoice choice) {
        PathChoiceDTO pathChoiceDTO = new PathChoiceDTO();
        ArrayList<ResolvedPartLinkDTO> resolvedPath = new ArrayList<>();
        for (ResolvedPartLink resolvedPartLink : choice.getResolvedPath()) {
            ResolvedPartLinkDTO resolvedPartLinkDTO = new ResolvedPartLinkDTO();
            PartIteration resolvedIteration = resolvedPartLink.getPartIteration();
            resolvedPartLinkDTO.setPartIteration(new PartIterationDTO(resolvedIteration.getWorkspaceId(), resolvedIteration.getName(), resolvedIteration.getNumber(), resolvedIteration.getVersion(), resolvedIteration.getIteration()));
            PartLink partLink = resolvedPartLink.getPartLink();
            resolvedPartLinkDTO.setPartLink(new LightPartLinkDTO(partLink.getComponent().getNumber(), partLink.getComponent().getName(), partLink.getReferenceDescription(), partLink.getFullId()));
            resolvedPath.add(resolvedPartLinkDTO);
        }
        pathChoiceDTO.setResolvedPath(resolvedPath);
        Mapper mapper = DozerBeanMapperSingletonWrapper.getInstance();
        pathChoiceDTO.setPartUsageLink(mapper.map(choice.getPartUsageLink(), PartUsageLinkDTO.class));
        return pathChoiceDTO;
    }

    public static Response prepareCreatedResponse(String location, Object entity){
        try {
            return Response.created(URI.create(URLEncoder.encode(location, "UTF-8"))).entity(entity).build();
        } catch (UnsupportedEncodingException ex) {
            LOGGER.log(Level.WARNING, null, ex);
            return Response.ok().entity(entity).build();
        }
    }
}
