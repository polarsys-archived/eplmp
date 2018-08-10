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
package org.polarsys.eplmp.core.services;

import org.polarsys.eplmp.core.change.*;
import org.polarsys.eplmp.core.document.DocumentIterationKey;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.product.PartIterationKey;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Florent Garin
 */
public interface IChangeManagerLocal {

    ChangeIssue getChangeIssue(String pWorkspaceId, int pId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeIssueNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    List<ChangeIssue> getChangeIssues(String pWorkspaceId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException;

    List<ChangeIssue> getIssuesWithName(String workspaceId, String q, int maxResults) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException;
    ChangeIssue createChangeIssue(String pWorkspaceId, String name, String description, String initiator, ChangeItemPriority priority, String assignee, ChangeItemCategory category) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, WorkspaceNotEnabledException, NotAllowedException, AccountNotFoundException;
    ChangeIssue updateChangeIssue(int pId, String pWorkspaceId, String description, ChangeItemPriority priority, String assignee, ChangeItemCategory category) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeIssueNotFoundException, AccessRightException, WorkspaceNotEnabledException, AccountNotFoundException, NotAllowedException;

    void deleteChangeIssue(int pId) throws ChangeIssueNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, AccessRightException, EntityConstraintException, WorkspaceNotEnabledException;
    ChangeIssue saveChangeIssueAffectedDocuments(String pWorkspaceId, int pId, DocumentIterationKey[] pAffectedDocuments) throws UserNotActiveException, UserNotFoundException, WorkspaceNotFoundException, ChangeIssueNotFoundException, AccessRightException, DocumentRevisionNotFoundException, WorkspaceNotEnabledException;
    ChangeIssue saveChangeIssueAffectedParts(String pWorkspaceId, int pId, PartIterationKey[] pAffectedParts) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeIssueNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeIssue saveChangeIssueTags(String pWorkspaceId, int pId, String[] tagsLabel) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeIssueNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeIssue removeChangeIssueTag(String workspaceId, int pId, String tagName) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeIssueNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeIssue updateACLForChangeIssue(String pWorkspaceId, int pId, Map<String, String> pUserEntries, Map<String, String> pGroupEntries) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeIssueNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeIssue removeACLFromChangeIssue(String pWorkspaceId, int pId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeIssueNotFoundException, AccessRightException, WorkspaceNotEnabledException;

    ChangeRequest getChangeRequest(String pWorkspaceId, int pId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeRequestNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    List<ChangeRequest> getChangeRequests(String pWorkspaceId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException;

    List<ChangeRequest> getRequestsWithName(String workspaceId, String q, int maxResults) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException;
    ChangeRequest createChangeRequest(String pWorkspaceId, String name, String description, int milestone, ChangeItemPriority priority, String assignee, ChangeItemCategory category) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, WorkspaceNotEnabledException, AccountNotFoundException, NotAllowedException;
    ChangeRequest updateChangeRequest(int pId, String pWorkspaceId, String description, int milestoneId, ChangeItemPriority priority, String assignee, ChangeItemCategory category) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeRequestNotFoundException, AccessRightException, WorkspaceNotEnabledException, AccountNotFoundException, NotAllowedException;
    void deleteChangeRequest(String pWorkspaceId, int pId) throws ChangeRequestNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, AccessRightException, EntityConstraintException, WorkspaceNotEnabledException;
    ChangeRequest saveChangeRequestAffectedDocuments(String workspaceId, int requestId, DocumentIterationKey[] links) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeRequestNotFoundException, AccessRightException, DocumentRevisionNotFoundException, WorkspaceNotEnabledException;
    ChangeRequest saveChangeRequestAffectedParts(String workspaceId, int requestId, PartIterationKey[] links) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeRequestNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeRequest saveChangeRequestAffectedIssues(String pWorkspaceId, int pRequestId, int[] pLinkId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeRequestNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeRequest saveChangeRequestTags(String pWorkspaceId, int pId, String[] tagsLabel) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeRequestNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeRequest removeChangeRequestTag(String workspaceId, int pId, String tagName) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeIssueNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeRequest updateACLForChangeRequest(String pWorkspaceId, int pId, Map<String, String> pUserEntries, Map<String, String> pGroupEntries) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeRequestNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeRequest removeACLFromChangeRequest(String pWorkspaceId, int pId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeRequestNotFoundException, AccessRightException, WorkspaceNotEnabledException;

    ChangeOrder getChangeOrder(String pWorkspaceId, int pId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeOrderNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    List<ChangeOrder> getChangeOrders(String pWorkspaceId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException;
    ChangeOrder createChangeOrder(String pWorkspaceId, String name, String description, int milestone, ChangeItemPriority priority, String assignee, ChangeItemCategory category) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, WorkspaceNotEnabledException, AccountNotFoundException, NotAllowedException;
    ChangeOrder updateChangeOrder(int pId, String pWorkspaceId, String description, int milestoneId, ChangeItemPriority priority, String assignee, ChangeItemCategory category) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeOrderNotFoundException, AccessRightException, WorkspaceNotEnabledException, AccountNotFoundException, NotAllowedException;
    void deleteChangeOrder(int pId) throws ChangeOrderNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeOrder saveChangeOrderAffectedDocuments(String workspaceId, int pOrderId, DocumentIterationKey[] links) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeOrderNotFoundException, AccessRightException, DocumentRevisionNotFoundException, WorkspaceNotEnabledException;
    ChangeOrder saveChangeOrderAffectedParts(String workspaceId, int pOrderId, PartIterationKey[] links) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeOrderNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeOrder saveChangeOrderAffectedRequests(String pWorkspaceId, int pOrderId, int[] pLinkId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeOrderNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeOrder saveChangeOrderTags(String pWorkspaceId, int pId, String[] tagsLabel) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeOrderNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeOrder removeChangeOrderTag(String workspaceId, int pId, String tagName) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeIssueNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeOrder updateACLForChangeOrder(String pWorkspaceId, int pId, Map<String, String> pUserEntries, Map<String, String> pGroupEntries) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeOrderNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    ChangeOrder removeACLFromChangeOrder(String pWorkspaceId, int pId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ChangeOrderNotFoundException, AccessRightException, WorkspaceNotEnabledException;

    Milestone getMilestone(String pWorkspaceId, int milestoneId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, MilestoneNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    Milestone getMilestoneByTitle(String pWorkspaceId, String pTitle) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, MilestoneNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    List<Milestone> getMilestones(String pWorkspaceId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException;
    Milestone createMilestone(String pWorkspaceId, String title, String description, Date dueDate) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, MilestoneAlreadyExistsException, WorkspaceNotEnabledException;
    Milestone updateMilestone(int milestoneId, String pWorkspaceId, String title, String description, Date dueDate) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, MilestoneNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    void deleteMilestone(String pWorkspaceId, int milestoneId) throws MilestoneNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, AccessRightException, EntityConstraintException, WorkspaceNotEnabledException;
    List<ChangeRequest> getChangeRequestsByMilestone(String pWorkspaceId, int milestoneId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, MilestoneNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    List<ChangeOrder> getChangeOrdersByMilestone(String pWorkspaceId, int milestoneId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, MilestoneNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    int getNumberOfRequestByMilestone(String pWorkspaceId, int milestoneId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException;
    int getNumberOfOrderByMilestone(String pWorkspaceId, int milestoneId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException;
    Milestone updateACLForMilestone(String pWorkspaceId, int pId, Map<String, String> pUserEntries, Map<String, String> pGroupEntries) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, MilestoneNotFoundException, AccessRightException, WorkspaceNotEnabledException;
    Milestone removeACLFromMilestone(String pWorkspaceId, int pId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, MilestoneNotFoundException, AccessRightException, WorkspaceNotEnabledException;

    boolean isChangeItemWritable(ChangeItem pChangeItem) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException;
    boolean isMilestoneWritable(Milestone pMilestone) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException;
}
