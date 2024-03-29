/*******************************************************************************
  * Copyright (c) 2017-2019 DocDoku.
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Public License v1.0
  * which accompanies this distribution, and is available at
  * http://www.eclipse.org/legal/epl-v10.html
  *
  * Contributors:
  *    DocDoku - initial API and implementation
  *******************************************************************************/

package org.polarsys.eplmp.core.change;

import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.common.Workspace;
import org.polarsys.eplmp.core.document.DocumentIteration;
import org.polarsys.eplmp.core.meta.Tag;
import org.polarsys.eplmp.core.product.PartIteration;
import org.polarsys.eplmp.core.security.ACL;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Abstract parent class from which change objects are derived.
 *
 * @author Florent Garin
 * @version 2.0, 10/01/14
 * @since V2.0
 */
@MappedSuperclass
public abstract class ChangeItem implements Serializable {


    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    protected int id;

    protected String name;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    protected Workspace workspace;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumns({
            @JoinColumn(name = "AUTHOR_LOGIN", referencedColumnName = "LOGIN"),
            @JoinColumn(name = "AUTHOR_WORKSPACE_ID", referencedColumnName = "WORKSPACE_ID")
    })
    protected User author;

    @ManyToOne(fetch=FetchType.EAGER)
    @JoinColumns({
            @JoinColumn(name="ASSIGNEE_LOGIN", referencedColumnName="LOGIN"),
            @JoinColumn(name="ASSIGNEE_WORKSPACE_ID", referencedColumnName="WORKSPACE_ID")
    })
    protected User assignee;

    @OneToOne(orphanRemoval = true, cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    protected ACL acl;

    @Temporal(TemporalType.TIMESTAMP)
    protected java.util.Date creationDate;

    @Lob
    protected String description;

    protected ChangeItemPriority priority;

    /**
     * An adaptive change maintains functionality for a different platform or
     * environment.
     * A corrective change corrects a defect.
     * A perfective change adds functionality.
     * A preventive change improves maintainability.
     */
    protected ChangeItemCategory category;

    @ManyToMany
    private Set<PartIteration> affectedParts = new HashSet<>();

    @ManyToMany
    private Set<DocumentIteration> affectedDocuments = new HashSet<>();

    @ManyToMany(fetch=FetchType.EAGER)
    private Set<Tag> tags=new HashSet<>();

    public ChangeItem(Workspace pWorkspace, String pName, User pAuthor) {
        workspace=pWorkspace;
        name=pName;
        author=pAuthor;
    }

    protected ChangeItem(String name, Workspace workspace, User author, User assignee, Date creationDate, String description, ChangeItemPriority priority, ChangeItemCategory category) {
        this.name = name;
        this.workspace = workspace;
        this.author = author;
        this.assignee = assignee;
        this.creationDate = creationDate;
        this.description = description;
        this.priority = priority;
        this.category = category;
    }

    public ChangeItem() {
    }

    public int getId() {
        return id;
    }

    public ChangeItemCategory getCategory() {
        return category;
    }
    public void setCategory(ChangeItemCategory category) {
        this.category = category;
    }

    public ChangeItemPriority getPriority() {
        return priority;
    }
    public void setPriority(ChangeItemPriority priority) {
        this.priority = priority;
    }

    public Date getCreationDate() {
        return creationDate;
    }
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public ACL getACL() {
        return acl;
    }
    public void setACL(ACL acl) {
        this.acl = acl;
    }

    public Set<Tag> getTags() {
        return tags;
    }
    public void setTags(Set<Tag> pTags) {
        tags.retainAll(pTags);
        pTags.removeAll(tags);
        tags.addAll(pTags);
    }

    public boolean addTag(Tag pTag){
        return tags.add(pTag);
    }
    public boolean removeTag(Tag pTag){
        return tags.remove(pTag);
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public User getAuthor() {
        return author;
    }
    public void setAuthor(User author) {
        this.author = author;
    }

    public String getAuthorName() {
        return author.getName();
    }

    public Workspace getWorkspace() {
        return workspace;
    }
    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    public User getAssignee() {
        return assignee;
    }
    public void setAssignee(User assignee) {
        this.assignee = assignee;
    }

    public String getAssigneeName() {
        return assignee == null ? null : assignee.getName();
    }

    public Set<DocumentIteration> getAffectedDocuments() {
        return affectedDocuments;
    }
    public void setAffectedDocuments(Set<DocumentIteration> affectedDocuments) {
        this.affectedDocuments = affectedDocuments;
    }

    public Set<PartIteration> getAffectedParts() {
        return affectedParts;
    }
    public void setAffectedParts(Set<PartIteration> affectedParts) {
        this.affectedParts = affectedParts;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public String getWorkspaceId() {
        return workspace == null ? "" : workspace.getId();
    }
}
