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

package org.polarsys.eplmp.core.document;

import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.common.Version;
import org.polarsys.eplmp.core.meta.Folder;
import org.polarsys.eplmp.core.meta.RevisionStatus;
import org.polarsys.eplmp.core.meta.StatusChange;
import org.polarsys.eplmp.core.meta.Tag;
import org.polarsys.eplmp.core.security.ACL;
import org.polarsys.eplmp.core.workflow.Workflow;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.*;

/**
 * This class stands between {@link DocumentMaster} and {@link DocumentIteration}.
 * It represents a formal revision of a document and can have an attached workflow.
 *
 * @author Florent Garin
 * @version 2.0, 11/01/14
 * @since V2.0
 */
@Table(name = "DOCUMENTREVISION", indexes = {@Index(name = "INDEX_DOC_WKS_ID", columnList = "WORKSPACE_ID, DOCUMENTMASTER_ID")})
@IdClass(DocumentRevisionKey.class)
@Entity
@NamedQueries({
        @NamedQuery(name = "DocumentRevision.findLinks", query = "SELECT l FROM DocumentLink l WHERE l.targetDocument = :target"),
        @NamedQuery(name = "DocumentRevision.findWithAssignedTasksForUser", query = "SELECT d FROM DocumentRevision d, Task t LEFT JOIN t.assignedUsers au LEFT JOIN t.assignedGroups ag LEFT JOIN ag.users agu WHERE t.activity.workflow = d.workflow AND d.workflow IS NOT NULL AND d.documentMasterWorkspaceId = :workspaceId AND ((au.login = :login AND au.workspaceId = :workspaceId) OR (agu.login = :login AND agu.workspaceId = :workspaceId))"),
        @NamedQuery(name = "DocumentRevision.findWithOpenedTasksForUser", query = "SELECT d FROM DocumentRevision d, Task t LEFT JOIN t.assignedUsers au LEFT JOIN t.assignedGroups ag LEFT JOIN ag.users agu WHERE t.activity.workflow = d.workflow AND d.workflow IS NOT NULL AND d.documentMasterWorkspaceId = :workspaceId AND ((au.login = :login AND au.workspaceId = :workspaceId) OR (agu.login = :login AND agu.workspaceId = :workspaceId)) AND t.status = org.polarsys.eplmp.core.workflow.Task.Status.IN_PROGRESS"),
        @NamedQuery(name = "DocumentRevision.findByReferenceOrTitle", query = "SELECT d FROM DocumentRevision d WHERE (d.documentMasterId LIKE :id OR d.title LIKE :title) AND d.documentMasterWorkspaceId = :workspaceId"),
        @NamedQuery(name = "DocumentRevision.countByWorkspace", query = "SELECT COUNT(d) FROM DocumentRevision d WHERE d.documentMasterWorkspaceId = :workspaceId"),
        @NamedQuery(name = "DocumentRevision.findByWorkspace", query = "SELECT dr FROM DocumentRevision dr WHERE dr.documentMasterWorkspaceId = :workspaceId AND dr.location.completePath NOT LIKE :excludedFolders ORDER BY dr.documentMasterId ASC"),
        @NamedQuery(name = "DocumentRevision.findByWorkspace.filterACLEntry", query = "SELECT distinct dr FROM DocumentRevision dr, ACL a WHERE dr.documentMasterWorkspaceId = :workspaceId and (dr.acl is null or exists(SELECT au from ACLUserEntry au WHERE au.principal = :user AND au.permission <> org.polarsys.eplmp.core.security.ACLPermission.FORBIDDEN AND a = dr.acl AND a = au.acl) or exists(SELECT aug from ACLUserGroupEntry aug WHERE :user member of aug.principal.users AND aug.permission <> org.polarsys.eplmp.core.security.ACLPermission.FORBIDDEN AND a = dr.acl AND a = aug.acl)) AND dr.location.completePath NOT LIKE :excludedFolders ORDER BY dr.documentMasterId ASC"),
        @NamedQuery(name = "DocumentRevision.countByWorkspace.filterACLEntry", query = "SELECT count(distinct dr) FROM DocumentRevision dr, ACL a WHERE dr.documentMasterWorkspaceId = :workspaceId and (dr.acl is null or exists(SELECT au from ACLUserEntry au  WHERE au.principal = :user AND au.permission <> org.polarsys.eplmp.core.security.ACLPermission.FORBIDDEN AND a = dr.acl AND a = au.acl) or exists(SELECT aug from ACLUserGroupEntry aug  WHERE :user member of aug.principal.users AND aug.permission <> org.polarsys.eplmp.core.security.ACLPermission.FORBIDDEN AND a = dr.acl AND a = aug.acl)) AND dr.location.completePath NOT LIKE :excludedFolders"),
        @NamedQuery(name = "DocumentRevision.findByWorkflow", query = "SELECT d FROM DocumentRevision d WHERE d.workflow = :workflow")
})
public class DocumentRevision implements Serializable, Comparable<DocumentRevision> {


    @Id
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumns({
            @JoinColumn(name = "DOCUMENTMASTER_ID", referencedColumnName = "ID"),
            @JoinColumn(name = "WORKSPACE_ID", referencedColumnName = "WORKSPACE_ID")
    })
    private DocumentMaster documentMaster;

    @Column(length = 10)
    @Id
    private String version = "";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumns({
            @JoinColumn(name = "AUTHOR_LOGIN", referencedColumnName = "LOGIN"),
            @JoinColumn(name = "AUTHOR_WORKSPACE_ID", referencedColumnName = "WORKSPACE_ID")
    })
    private User author;

    @Temporal(TemporalType.TIMESTAMP)
    private Date creationDate;

    private String title;

    @Lob
    private String description;

    @OneToMany(mappedBy = "documentRevision", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("iteration ASC")
    private List<DocumentIteration> documentIterations = new ArrayList<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumns({
            @JoinColumn(name = "CHECKOUTUSER_LOGIN", referencedColumnName = "LOGIN"),
            @JoinColumn(name = "CHECKOUTUSER_WORKSPACE_ID", referencedColumnName = "WORKSPACE_ID")
    })
    private User checkOutUser;

    @Temporal(TemporalType.TIMESTAMP)
    private Date checkOutDate;

    @OneToOne(orphanRemoval = true, cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Workflow workflow;

    @OrderBy("abortedDate")
    @OneToMany(orphanRemoval = true, cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinTable(name = "DOCUMENT_ABORTED_WORKFLOW",
            inverseJoinColumns = {
                    @JoinColumn(name = "WORKFLOW_ID", referencedColumnName = "ID")
            },
            joinColumns = {
                    @JoinColumn(name = "DOCUMENTMASTER_ID", referencedColumnName = "DOCUMENTMASTER_ID"),
                    @JoinColumn(name = "DOCUMENTREVISION_VERSION", referencedColumnName = "VERSION"),
                    @JoinColumn(name = "DOCUMENTMASTER_WORKSPACE_ID", referencedColumnName = "WORKSPACE_ID")
            })
    private List<Workflow> abortedWorkflows = new ArrayList<>();

    @ManyToOne(fetch = FetchType.EAGER)
    private Folder location;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "DOCUMENTREVISION_TAG",
            inverseJoinColumns = {
                    @JoinColumn(name = "TAG_LABEL", referencedColumnName = "LABEL"),
                    @JoinColumn(name = "TAG_WORKSPACE_ID", referencedColumnName = "WORKSPACE_ID")
            },
            joinColumns = {
                    @JoinColumn(name = "DOCUMENTMASTER_ID", referencedColumnName = "DOCUMENTMASTER_ID"),
                    @JoinColumn(name = "DOCUMENTREVISION_VERSION", referencedColumnName = "VERSION"),
                    @JoinColumn(name = "DOCUMENTMASTER_WORKSPACE_ID", referencedColumnName = "WORKSPACE_ID")
            })
    private Set<Tag> tags = new HashSet<>();


    @Column(name = "DOCUMENTMASTER_ID", nullable = false, insertable = false, updatable = false)
    private String documentMasterId = "";

    @Column(name = "WORKSPACE_ID", nullable = false, insertable = false, updatable = false)
    private String documentMasterWorkspaceId = "";


    @OneToOne(orphanRemoval = true, cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private ACL acl;

    private boolean publicShared;

    private RevisionStatus status = RevisionStatus.WIP;


    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "statusModificationDate",
                    column = @Column(name = "RELEASE_DATE"))
    })
    @AssociationOverrides({
            @AssociationOverride(
                    name = "statusChangeAuthor",
                    joinColumns = {
                            @JoinColumn(name = "RELEASE_USER_LOGIN", referencedColumnName = "LOGIN"),
                            @JoinColumn(name = "RELEASE_USER_WORKSPACE", referencedColumnName = "WORKSPACE_ID")
                    })
    })
    private StatusChange releaseStatusChange;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "statusModificationDate",
                    column = @Column(name = "OBSOLETE_DATE"))
    })
    @AssociationOverrides({
            @AssociationOverride(
                    name = "statusChangeAuthor",
                    joinColumns = {
                            @JoinColumn(name = "OBSOLETE_USER_LOGIN", referencedColumnName = "LOGIN"),
                            @JoinColumn(name = "OBSOLETE_USER_WORKSPACE", referencedColumnName = "WORKSPACE_ID")
                    })
    })
    private StatusChange obsoleteStatusChange;

    public DocumentRevision() {
    }

    public DocumentRevision(DocumentMaster pDocumentMaster,
                            String pStringVersion,
                            User pAuthor) {
        this(pDocumentMaster);
        version = pStringVersion;
        author = pAuthor;
    }

    public DocumentRevision(DocumentMaster pDocumentMaster,
                            Version pVersion,
                            User pAuthor) {
        this(pDocumentMaster);
        version = pVersion.toString();
        author = pAuthor;
    }

    public DocumentRevision(DocumentMaster pDocumentMaster, User pAuthor) {
        this(pDocumentMaster);
        version = new Version().toString();
        author = pAuthor;
    }

    private DocumentRevision(DocumentMaster pDocumentMaster) {
        setDocumentMaster(pDocumentMaster);
    }

    @XmlTransient
    public DocumentMaster getDocumentMaster() {
        return documentMaster;
    }

    public void setDocumentMaster(DocumentMaster documentMaster) {
        this.documentMaster = documentMaster;
        setDocumentMasterId(documentMaster.getId());
        setDocumentMasterWorkspaceId(documentMaster.getWorkspaceId());
    }

    public DocumentRevisionKey getKey() {
        return new DocumentRevisionKey(getDocumentMasterKey(), version);
    }

    public User getCheckOutUser() {
        return checkOutUser;
    }

    public void setCheckOutUser(User pCheckOutUser) {
        checkOutUser = pCheckOutUser;
    }

    public boolean isCheckedOut() {
        return checkOutUser != null;
    }

    public boolean isCheckedOutBy(String pUser) {
        return checkOutUser != null && checkOutUser.getLogin().equals(pUser);
    }

    public Date getCheckOutDate() {
        return (checkOutDate != null) ? (Date) checkOutDate.clone() : null;
    }

    public void setCheckOutDate(Date checkOutDate) {
        this.checkOutDate = (checkOutDate != null) ? (Date) checkOutDate.clone() : null;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User pAuthor) {
        author = pAuthor;
    }

    public Date getCreationDate() {
        return (creationDate != null) ? (Date) creationDate.clone() : null;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = (creationDate != null) ? (Date) creationDate.clone() : null;
    }


    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getType() {
        return documentMaster == null ? "" : documentMaster.getType();
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(Workflow pWorkflow) {
        workflow = pWorkflow;
    }

    public Integer getWorkflowId() {
        return hasWorkflow() ? workflow.getId() : null;
    }

    public boolean hasWorkflow() {
        return workflow != null;
    }

    public String getLifeCycleState() {
        if (workflow != null) {
            return workflow.getLifeCycleState();
        } else {
            return null;
        }
    }

    public ACL getACL() {
        return acl;
    }

    public void setACL(ACL acl) {
        this.acl = acl;
    }

    public List<Workflow> getAbortedWorkflows() {
        return abortedWorkflows;
    }

    public void addAbortedWorkflows(Workflow abortedWorkflow) {
        this.abortedWorkflows.add(abortedWorkflow);
    }

    public List<DocumentIteration> getDocumentIterations() {
        return documentIterations;
    }

    public void setDocumentIterations(List<DocumentIteration> documentIterations) {
        this.documentIterations = documentIterations;
    }

    public DocumentIteration getIteration(int pIteration) {
        return documentIterations.get(pIteration - 1);
    }

    public DocumentIteration createNextIteration(User pUser) {
        DocumentIteration doc = new DocumentIteration(this, pUser);
        documentIterations.add(doc);
        return doc;
    }

    public DocumentIteration getLastIteration() {
        int index = documentIterations.size() - 1;
        if (index < 0) {
            return null;
        } else {
            return documentIterations.get(index);
        }
    }

    public DocumentIteration getLastCheckedInIteration() {
        int index;
        if (isCheckedOut()) {
            index = documentIterations.size() - 2;
        } else {
            index = documentIterations.size() - 1;
        }
        if (index < 0) {
            return null;
        } else {
            return documentIterations.get(index);
        }
    }

    public DocumentIteration getWorkingCopy(){

        if(isCheckedOut()){
            return getLastIteration();
        }else{
            return null;
        }
    }

    public DocumentIteration removeLastIteration() {
        int index = documentIterations.size() - 1;
        if (index < 0) {
            return null;
        } else {
            return documentIterations.remove(index);
        }
    }

    /**
     * Remove the iterations following the lastIterationWanted
     *
     * @param lastIterationWanted The new last iteration number
     */
    public void removeFollowingIterations(int lastIterationWanted) {
        DocumentIteration documentIteration;
        do {
            documentIteration = getLastIteration();
            if (documentIteration.getIteration() > lastIterationWanted) {
                documentIteration = removeLastIteration();
            }
        } while (documentIteration != null && documentIteration.getIteration() > lastIterationWanted);
    }

    public int getNumberOfIterations() {
        return documentIterations.size();
    }

    public void setDescription(String pDescription) {
        description = pDescription;
    }

    public String getDescription() {
        return description;
    }

    public DocumentMasterKey getDocumentMasterKey() {
        return documentMaster == null ? new DocumentMasterKey("", "") : documentMaster.getKey();
    }

    public String getWorkspaceId() {
        return documentMaster == null ? "" : documentMaster.getWorkspaceId();
    }

    public String getId() {
        return documentMaster == null ? "" : documentMaster.getId();
    }


    public void setDocumentMasterId(String pDocumentMasterId) {
        documentMasterId = pDocumentMasterId;
    }

    public void setDocumentMasterWorkspaceId(String pDocumentMasterWorkspaceId) {
        documentMasterWorkspaceId = pDocumentMasterWorkspaceId;
    }

    public String getDocumentMasterId() {
        return documentMasterId;
    }

    public String getDocumentMasterWorkspaceId() {
        return documentMasterWorkspaceId;
    }

    public boolean isPublicShared() {
        return publicShared;
    }

    public void setPublicShared(boolean publicShared) {
        this.publicShared = publicShared;
    }

    public RevisionStatus getStatus() {
        return status;
    }

    public void setStatus(RevisionStatus status) {
        this.status = status;
    }

    public boolean isReleased() {
        return status == RevisionStatus.RELEASED;
    }

    public boolean isObsolete() {
        return status == RevisionStatus.OBSOLETE;
    }

    public boolean release(User user) {
        if (this.status == RevisionStatus.WIP) {
            this.status = RevisionStatus.RELEASED;
            StatusChange statusChange = new StatusChange();
            statusChange.setStatusChangeAuthor(user);
            statusChange.setStatusModificationDate(new Date());
            this.setReleaseStatusChange(statusChange);
            return true;
        } else {
            return false;
        }

    }

    public boolean markAsObsolete(User user) {
        if (this.status == RevisionStatus.RELEASED) {
            this.status = RevisionStatus.OBSOLETE;
            StatusChange statusChange = new StatusChange();
            statusChange.setStatusChangeAuthor(user);
            statusChange.setStatusModificationDate(new Date());
            this.setObsoleteStatusChange(statusChange);
            return true;
        } else {
            return false;
        }

    }

    public void setTitle(String pTitle) {
        title = pTitle;
    }

    public String getTitle() {
        return title;
    }

    public boolean isCheckedOutBy(User pUser) {
        return isCheckedOutBy(pUser.getLogin());
    }

    public Set<Tag> getTags() {
        return tags;
    }

    /**
     * Tags the DocumentRevision with the set of tags.
     * Some of them may already be attached on the document.
     *
     * @param pTags the tag set to attach on the DocumentRevision
     * @return the tags that have actually been added
     */
    public Set<Tag> setTags(Set<Tag> pTags) {
        if (pTags != null) {
            Set<Tag> addedTags = new HashSet<>(pTags);
            tags.retainAll(addedTags);
            addedTags.removeAll(tags);
            tags.addAll(addedTags);
            return addedTags;
        } else
            return Collections.emptySet();
    }

    public boolean addTag(Tag pTag) {
        return tags.add(pTag);
    }

    public boolean removeTag(Tag pTag) {
        return tags.remove(pTag);
    }

    public boolean isAttributesLocked() {
        if (this.documentMaster != null) {
            return this.documentMaster.isAttributesLocked();
        }
        return false;
    }

    public StatusChange getObsoleteStatusChange() {
        return obsoleteStatusChange;
    }

    public void setObsoleteStatusChange(StatusChange statusChange) {
        this.obsoleteStatusChange = statusChange;
    }

    public StatusChange getReleaseStatusChange() {
        return releaseStatusChange;
    }

    public void setReleaseStatusChange(StatusChange statusChange) {
        this.releaseStatusChange = statusChange;
    }

    public User getObsoleteAuthor() {
        return obsoleteStatusChange == null ? null : obsoleteStatusChange.getStatusChangeAuthor();
    }

    public Date getObsoleteDate() {
        return obsoleteStatusChange == null ? null : obsoleteStatusChange.getStatusModificationDate();
    }

    public User getReleaseAuthor() {
        return releaseStatusChange == null ? null : releaseStatusChange.getStatusChangeAuthor();
    }

    public Date getReleaseDate() {
        return releaseStatusChange == null ? null : releaseStatusChange.getStatusModificationDate();
    }

    @Override
    public String toString() {
        return documentMaster.getId() + "-" + version;
    }

    @Override
    public boolean equals(Object pObj) {
        if (this == pObj) {
            return true;
        }
        if (!(pObj instanceof DocumentRevision)) {
            return false;
        }
        DocumentRevision docR = (DocumentRevision) pObj;
        return docR.getId().equals(getId()) &&
                docR.getWorkspaceId().equals(getWorkspaceId()) &&
                docR.version.equals(version);

    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = 31 * hash + getWorkspaceId().hashCode();
        hash = 31 * hash + getId().hashCode();
        hash = 31 * hash + version.hashCode();
        return hash;
    }

    public int compareTo(DocumentRevision pDocR) {
        int wksComp = getWorkspaceId().compareTo(pDocR.getWorkspaceId());
        if (wksComp != 0) {
            return wksComp;
        }
        int idComp = getId().compareTo(pDocR.getId());
        if (idComp != 0) {
            return idComp;
        } else {
            return version.compareTo(pDocR.version);
        }
    }

    public Folder getLocation() {
        return location;
    }

    public void setLocation(Folder pLocation) {
        location = pLocation;
    }
}
