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

package org.polarsys.eplmp.core.workflow;


import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.common.UserGroup;
import org.polarsys.eplmp.core.common.Workspace;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Role describes the profile needed to complete a task.
 * It acts as a place holder that will be filled with real users
 * (or user groups) when the {@link Workflow} will be started.
 *
 * {@code defaultAssignedUsers} and {@code defaultAssignedGroups} provide
 * default mappings that could however be overridden.
 *
 * Roles are created at workspace level.
 *
 * @author Morgan Guimard
 */
@Table(name="ROLE")
@IdClass(RoleKey.class)
@javax.persistence.Entity
@NamedQueries({
        @NamedQuery(name="Role.findByWorkspace", query="SELECT r FROM Role r WHERE r.workspace.id = :workspaceId"),
        @NamedQuery(name="Role.findRolesWhereUserIsAssigned", query="SELECT r FROM Role r WHERE :user member of r.defaultAssignedUsers"),
        @NamedQuery(name="Role.findRolesWhereGroupIsAssigned", query="SELECT r FROM Role r WHERE :userGroup member of r.defaultAssignedGroups")
})
public class Role implements Serializable {

    @Column(length = 100)
    @Id
    private String name = "";

    @Id
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Workspace workspace;

    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(name="ROLE_USER",
    inverseJoinColumns={
            @JoinColumn(name="USER_LOGIN", referencedColumnName="LOGIN"),
            @JoinColumn(name="USER_WORKSPACE_ID", referencedColumnName="WORKSPACE_ID")
    },
    joinColumns={
            @JoinColumn(name="ROLE_NAME", referencedColumnName="NAME"),
            @JoinColumn(name="ROLE_WORKSPACE_ID", referencedColumnName="WORKSPACE_ID"),
    })
    private Set<User> defaultAssignedUsers=new HashSet<>();

    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(name="ROLE_USERGROUP",
    inverseJoinColumns={
            @JoinColumn(name="USERGROUP_ID", referencedColumnName="ID"),
            @JoinColumn(name="USERGROUP_WORKSPACE_ID", referencedColumnName="WORKSPACE_ID")
    },
    joinColumns={
            @JoinColumn(name="ROLE_NAME", referencedColumnName="NAME"),
            @JoinColumn(name="ROLE_WORKSPACE_ID", referencedColumnName="WORKSPACE_ID")
    })
    private Set<UserGroup> defaultAssignedGroups=new HashSet<>();

    public Role(){
    }

    public Role(String name, Workspace workspace){
        this.name = name;
        this.workspace = workspace;
    }

    public Role(String name, Workspace workspace, Set<User> defaultAssignedUsers, Set<UserGroup> defaultAssignedGroups){
        this.name = name;
        this.workspace = workspace;
        this.defaultAssignedUsers = defaultAssignedUsers;
        this.defaultAssignedGroups = defaultAssignedGroups;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    public Set<User> getDefaultAssignedUsers() {
        return defaultAssignedUsers;
    }

    public void setDefaultAssignedUsers(Set<User> defaultAssignedUsers) {
        this.defaultAssignedUsers = defaultAssignedUsers;
    }

    public Set<UserGroup> getDefaultAssignedGroups() {
        return defaultAssignedGroups;
    }

    public void setDefaultAssignedGroups(Set<UserGroup> defaultAssignedGroups) {
        this.defaultAssignedGroups = defaultAssignedGroups;
    }

    public void addUser(User user){
        this.defaultAssignedUsers.add(user);
    }

    public void removeUser(User user){
        this.defaultAssignedUsers.remove(user);
    }

    public void addUserGroup(UserGroup userGroup){
        this.defaultAssignedGroups.add(userGroup);
    }

    public void removeUserGroup(UserGroup userGroup){
        this.defaultAssignedGroups.remove(userGroup);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Role role = (Role) o;

        if (name != null ? !name.equals(role.name) : role.name != null) return false;
        return !(workspace != null ? !workspace.equals(role.workspace) : role.workspace != null);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (workspace != null ? workspace.hashCode() : 0);
        return result;
    }
}
