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
package org.polarsys.eplmp.server;

import org.polarsys.eplmp.core.common.*;
import org.polarsys.eplmp.core.document.DocumentRevision;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.product.PartRevision;
import org.polarsys.eplmp.core.security.ACL;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.INotifierLocal;
import org.polarsys.eplmp.core.services.IUserManagerLocal;
import org.polarsys.eplmp.core.services.IWorkflowManagerLocal;
import org.polarsys.eplmp.core.util.Tools;
import org.polarsys.eplmp.core.workflow.*;
import org.polarsys.eplmp.server.dao.*;
import org.polarsys.eplmp.server.factory.ACLFactory;

import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.*;

@DeclareRoles(UserGroupMapping.REGULAR_USER_ROLE_ID)
@Local(IWorkflowManagerLocal.class)
@Stateless(name = "WorkflowManagerBean")
public class WorkflowManagerBean implements IWorkflowManagerLocal {

    @Inject
    private ACLDAO aclDAO;

    @Inject
    private TaskDAO taskDAO;

    @Inject
    private ACLFactory aclFactory;

    @Inject
    private DocumentRevisionDAO documentRevisionDAO;

    @Inject
    private PartRevisionDAO partRevisionDAO;

    @Inject
    private RoleDAO roleDAO;

    @Inject
    private UserDAO userDAO;

    @Inject
    private UserGroupDAO userGroupDAO;

    @Inject
    private WorkflowDAO workflowDAO;

    @Inject
    private WorkflowModelDAO workflowModelDAO;

    @Inject
    private WorkspaceDAO workspaceDAO;

    @Inject
    private IUserManagerLocal userManager;

    @Inject
    private INotifierLocal notifier;

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void deleteWorkflowModel(WorkflowModelKey pKey) throws WorkspaceNotFoundException, AccessRightException, WorkflowModelNotFoundException, UserNotFoundException, UserNotActiveException, EntityConstraintException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(pKey.getWorkspaceId());

        WorkflowModel workflowModel = workflowModelDAO.loadWorkflowModel(pKey);

        if (workflowModelDAO.isInUseInDocumentMasterTemplate(workflowModel)) {
            throw new EntityConstraintException("EntityConstraintException24");
        }

        if (workflowModelDAO.isInUseInPartMasterTemplate(workflowModel)) {
            throw new EntityConstraintException("EntityConstraintException25");
        }

        checkWorkflowWriteAccess(workflowModel, user);

        workflowModelDAO.removeWorkflowModel(pKey);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public WorkflowModel createWorkflowModel(String pWorkspaceId, String pId, String pFinalLifeCycleState, ActivityModel[] pActivityModels) throws WorkspaceNotFoundException, AccessRightException, UserNotFoundException, WorkflowModelAlreadyExistsException, CreationException, NotAllowedException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceWriteAccess(pWorkspaceId);

        checkWorkflowValidity(pWorkspaceId, pId, pActivityModels);

        WorkflowModel model = new WorkflowModel(user.getWorkspace(), pId, user, pFinalLifeCycleState, pActivityModels);
        Tools.resetParentReferences(model);
        Date now = new Date();
        model.setCreationDate(now);
        workflowModelDAO.createWorkflowModel(model);
        return model;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public WorkflowModel updateWorkflowModel(WorkflowModelKey workflowModelKey, String pFinalLifeCycleState, ActivityModel[] pActivityModels) throws UserNotFoundException, WorkspaceNotFoundException, UserNotActiveException, AccessRightException, WorkflowModelNotFoundException, NotAllowedException, WorkspaceNotEnabledException {
        //remove all activities from workflow model
        //but do not remove it to maintain associated links
        //for instance document or part templates
        //and also ACL, author, creation date...
        User user = userManager.checkWorkspaceReadAccess(workflowModelKey.getWorkspaceId());
        workflowModelDAO.removeAllActivityModels(workflowModelKey);

        WorkflowModel workflowModel = workflowModelDAO.loadWorkflowModel(workflowModelKey);
        checkWorkflowWriteAccess(workflowModel, user);

        checkWorkflowValidity(workflowModelKey.getWorkspaceId(), workflowModelKey.getId(), pActivityModels);
        workflowModel.setFinalLifeCycleState(pFinalLifeCycleState);
        List<ActivityModel> activityModels = new LinkedList<>();
        Collections.addAll(activityModels, pActivityModels);
        workflowModel.setActivityModels(activityModels);
        Tools.resetParentReferences(workflowModel);
        return workflowModel;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public WorkflowModel[] getWorkflowModels(String pWorkspaceId) throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);

        List<WorkflowModel> allWorkflowModels = workflowModelDAO.findAllWorkflowModels(pWorkspaceId);

        allWorkflowModels.removeIf(workflowModel -> !hasWorkflowModelReadAccess(workflowModel, user));
        return allWorkflowModels.toArray(new WorkflowModel[allWorkflowModels.size()]);
    }

    private boolean hasWorkflowModelReadAccess(WorkflowModel workflowModel, User user) {
        return user.isAdministrator() || isACLGrantReadAccess(user, workflowModel);
    }

    private boolean isACLGrantReadAccess(User user, WorkflowModel workflowModel) {
        return workflowModel.getAcl() == null || workflowModel.getAcl().hasReadAccess(user);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public WorkflowModel getWorkflowModel(WorkflowModelKey pKey) throws WorkspaceNotFoundException, WorkflowModelNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(pKey.getWorkspaceId());
        return workflowModelDAO.loadWorkflowModel(pKey);
    }

    @Override
    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    public Role[] getRoles(String pWorkspaceId) throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(pWorkspaceId);
        List<Role> roles = roleDAO.findRolesInWorkspace(pWorkspaceId);
        return roles.toArray(new Role[0]);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public Role[] getRolesInUse(String pWorkspaceId) throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(pWorkspaceId);
        List<Role> roles = roleDAO.findRolesInUseWorkspace(pWorkspaceId);
        return roles.toArray(new Role[0]);
    }

    @Override
    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    public Role createRole(String roleName, String workspaceId, List<String> userLogins, List<String> userGroupIds) throws WorkspaceNotFoundException, UserNotFoundException, AccessRightException, RoleAlreadyExistsException, CreationException, UserGroupNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceWriteAccess(workspaceId);
        Workspace wks = workspaceDAO.loadWorkspace(workspaceId);

        Set<User> users = new HashSet<>();
        Set<UserGroup> groups = new HashSet<>();

        if (userLogins != null) {
            for (String userLogin : userLogins) {
                users.add(userDAO.loadUser(new UserKey(workspaceId, userLogin)));
            }
        }

        if (userGroupIds != null) {
            for (String id : userGroupIds) {
                groups.add(userGroupDAO.loadUserGroup(new UserGroupKey(workspaceId, id)));
            }
        }

        Role role = new Role(roleName, wks, users, groups);
        roleDAO.createRole(role);

        return role;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public Role updateRole(RoleKey roleKey, List<String> userLogins, List<String> userGroupIds) throws WorkspaceNotFoundException, UserNotFoundException, AccessRightException, RoleNotFoundException, UserGroupNotFoundException, WorkspaceNotEnabledException {
         userManager.checkWorkspaceWriteAccess(roleKey.getWorkspace());

        Role role = roleDAO.loadRole(roleKey);

        Set<User> users = new HashSet<>();
        Set<UserGroup> groups = new HashSet<>();

        if (userLogins != null) {
            for (String userLogin : userLogins) {
                users.add(userDAO.loadUser(new UserKey(roleKey.getWorkspace(), userLogin)));
            }
            role.setDefaultAssignedUsers(users);
        }

        if (userGroupIds != null) {
            for (String id : userGroupIds) {
                groups.add(userGroupDAO.loadUserGroup(new UserGroupKey(roleKey.getWorkspace(), id)));
            }
            role.setDefaultAssignedGroups(groups);
        }

        return role;

    }

    @Override
    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    public void deleteRole(RoleKey roleKey) throws WorkspaceNotFoundException, UserNotFoundException, AccessRightException, RoleNotFoundException, EntityConstraintException, WorkspaceNotEnabledException {
         userManager.checkWorkspaceWriteAccess(roleKey.getWorkspace());
        Role role = roleDAO.loadRole(roleKey);

        if (roleDAO.isRoleInUseInWorkflowModel(role)) {
            throw new EntityConstraintException("EntityConstraintException3");
        }

        roleDAO.deleteRole(role);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void removeUserFromAllRoleMappings(User pUser) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceWriteAccess(pUser.getWorkspaceId());
        roleDAO.removeUserFromRoles(pUser);
    }


    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void removeUserGroupFromAllRoleMappings(UserGroup pUserGroup) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceWriteAccess(pUserGroup.getWorkspaceId());
        roleDAO.removeGroupFromRoles(pUserGroup);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void removeACLFromWorkflow(String pWorkspaceId, String workflowModelId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkflowModelNotFoundException, AccessRightException, WorkspaceNotEnabledException {
        // Check the read access to the workspace
        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);
        // Load the workflowModel
        WorkflowModelKey workflowModelKey = new WorkflowModelKey(pWorkspaceId, workflowModelId);
        WorkflowModel workflowModel = workflowModelDAO.loadWorkflowModel(workflowModelKey);
        // Check the access to the workflow
        checkWorkflowWriteAccess(workflowModel, user);

        ACL acl = workflowModel.getAcl();
        if (acl != null) {
            aclDAO.removeACLEntries(acl);
            workflowModel.setAcl(null);
        }
    }


    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public WorkflowModel updateACLForWorkflow(String pWorkspaceId, String workflowModelId, Map<String, String> userEntries, Map<String, String> groupEntries) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkflowModelNotFoundException, AccessRightException, WorkspaceNotEnabledException {
        // Check the read access to the workspace
        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);
        // Load the workflowModel
        WorkflowModelKey workflowModelKey = new WorkflowModelKey(pWorkspaceId, workflowModelId);
        WorkflowModel workflowModel = workflowModelDAO.loadWorkflowModel(workflowModelKey);
        // Check the access to the workflow
        checkWorkflowWriteAccess(workflowModel, user);

        if (workflowModel.getAcl() == null) {
            ACL acl = aclFactory.createACL(pWorkspaceId, userEntries, groupEntries);
            workflowModel.setAcl(acl);
        } else {
            ACL acl = aclFactory.updateACL(pWorkspaceId, workflowModel.getAcl(), userEntries, groupEntries);
            workflowModel.setAcl(acl);
        }

        return workflowModel;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public WorkspaceWorkflow instantiateWorkflow(String workspaceId, String id, String workflowModelId, Map<String, Collection<String>> userRoleMapping, Map<String, Collection<String>> groupRoleMapping) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, RoleNotFoundException, WorkflowModelNotFoundException, NotAllowedException, UserGroupNotFoundException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceWriteAccess(workspaceId);

        Map<Role, Collection<User>> roleUserMap = new HashMap<>();
        for (Map.Entry<String, Collection<String>> pair : userRoleMapping.entrySet()) {
            String roleName = pair.getKey();
            Collection<String> userLogins = pair.getValue();
            Role role = roleDAO.loadRole(new RoleKey(workspaceId, roleName));
            Set<User> users = new HashSet<>();
            roleUserMap.put(role, users);
            for (String login : userLogins) {
                User u = userDAO.loadUser(new UserKey(workspaceId, login));
                users.add(u);
            }
        }

        Map<Role, Collection<UserGroup>> roleGroupMap = new HashMap<>();
        for (Map.Entry<String, Collection<String>> pair : groupRoleMapping.entrySet()) {
            String roleName = pair.getKey();
            Collection<String> groupIds = pair.getValue();
            Role role = roleDAO.loadRole(new RoleKey(workspaceId, roleName));
            Set<UserGroup> groups = new HashSet<>();
            roleGroupMap.put(role, groups);
            for (String groupId : groupIds) {
                UserGroup g = userGroupDAO.loadUserGroup(new UserGroupKey(workspaceId, groupId));
                groups.add(g);
            }
        }

        WorkflowModel workflowModel = workflowModelDAO.loadWorkflowModel(new WorkflowModelKey(user.getWorkspaceId(), workflowModelId));
        Workflow workflow = workflowModel.createWorkflow(roleUserMap, roleGroupMap);

        for (Task task : workflow.getTasks()) {
            if (!task.hasPotentialWorker()) {
                throw new NotAllowedException("NotAllowedException56");
            }
        }

        Collection<Task> runningTasks = workflow.getRunningTasks();

        runningTasks.forEach(Task::start);

        WorkspaceWorkflow workspaceWorkflow = new WorkspaceWorkflow(user.getWorkspace(), id, workflow);
        workflowDAO.createWorkflow(workspaceWorkflow.getWorkflow());
        workflowDAO.createWorkspaceWorkflow(workspaceWorkflow);

        notifier.sendApproval(workspaceId, runningTasks, workspaceWorkflow);

        return workspaceWorkflow;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public Workflow getWorkflow(String workspaceId, int workflowId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, AccessRightException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Workflow workflow = workflowDAO.getWorkflow(workflowId);
        checkWorkflowBelongToWorkspace(user, workspaceId, workflow);
        return workflow;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public WorkspaceWorkflow getWorkspaceWorkflow(String workspaceId, String workspaceWorkflowId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkflowNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(workspaceId);
        WorkspaceWorkflow workspaceWorkflow = workflowDAO.getWorkspaceWorkflow(workspaceId, workspaceWorkflowId);
        if (workspaceWorkflow != null) {
            return workspaceWorkflow;
        } else {
            throw new WorkflowNotFoundException(0);
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public Workflow[] getWorkflowAbortedWorkflowList(String workspaceId, int workflowId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, AccessRightException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Workflow workflow = workflowDAO.getWorkflow(workflowId);
        DocumentRevision documentTarget = workflowDAO.getDocumentTarget(workflow);

        if (documentTarget != null) {
            if (documentTarget.getWorkspaceId().equals(workspaceId)) {
                List<Workflow> abortedWorkflowList = documentTarget.getAbortedWorkflows();
                return abortedWorkflowList.toArray(new Workflow[abortedWorkflowList.size()]);
            } else {
                throw new AccessRightException(user);
            }
        }

        PartRevision partTarget = workflowDAO.getPartTarget(workflow);
        if (partTarget != null) {
            if (partTarget.getWorkspaceId().equals(workspaceId)) {
                List<Workflow> abortedWorkflowList = partTarget.getAbortedWorkflows();
                return abortedWorkflowList.toArray(new Workflow[abortedWorkflowList.size()]);
            } else {
                throw new AccessRightException(user);
            }
        }

        WorkspaceWorkflow workspaceWorkflowTarget = workflowDAO.getWorkspaceWorkflowTarget(workspaceId, workflow);
        if (workspaceWorkflowTarget != null) {
            List<Workflow> abortedWorkflowList = workspaceWorkflowTarget.getAbortedWorkflows();
            return abortedWorkflowList.toArray(new Workflow[abortedWorkflowList.size()]);
        } else {
            throw new AccessRightException(user);
        }

    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void approveTaskOnWorkspaceWorkflow(String workspaceId, TaskKey taskKey, String comment, String signature) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, TaskNotFoundException, WorkflowNotFoundException, NotAllowedException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);

        Task task = taskDAO.loadTask(taskKey);
        Workflow workflow = task.getActivity().getWorkflow();
        task = workflow.getTasks().stream().filter(pTask -> pTask.getKey().equals(taskKey)).findFirst().get();

        WorkspaceWorkflow workspaceWorkflow = checkTaskAccess(user, task);

        task.approve(user, comment, 0, signature);

        Collection<Task> runningTasks = workflow.getRunningTasks();
        for (Task runningTask : runningTasks) {
            runningTask.start();
        }

        notifier.sendApproval(workspaceId, runningTasks, workspaceWorkflow);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void rejectTaskOnWorkspaceWorkflow(String workspaceId, TaskKey taskKey, String comment, String signature) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, TaskNotFoundException, WorkflowNotFoundException, NotAllowedException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);

        Task task = taskDAO.loadTask(taskKey);

        WorkspaceWorkflow workspaceWorkflow = checkTaskAccess(user, task);

        task.reject(user, comment, 0, signature);

        // Relaunch Workflow ?
        Activity currentActivity = task.getActivity();
        Activity relaunchActivity = currentActivity.getRelaunchActivity();

        if (currentActivity.isStopped() && relaunchActivity != null) {
            relaunchWorkflow(workspaceWorkflow, relaunchActivity.getStep());
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public WorkspaceWorkflow[] getWorkspaceWorkflowList(String workspaceId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(workspaceId);
        List<WorkspaceWorkflow> workspaceWorkflowList = workflowDAO.getWorkspaceWorkflowList(workspaceId);
        return workspaceWorkflowList.toArray(new WorkspaceWorkflow[workspaceWorkflowList.size()]);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void deleteWorkspaceWorkflow(String workspaceId, String workspaceWorkflowId) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceWriteAccess(workspaceId);
        WorkspaceWorkflow workspaceWorkflow = workflowDAO.getWorkspaceWorkflow(workspaceId, workspaceWorkflowId);
        if (workspaceWorkflow != null) {
            workflowDAO.deleteWorkspaceWorkflow(workspaceWorkflow);
        } else {
            throw new AccessRightException(user);
        }
    }



    /**
     * Check if a user can approve or reject a task
     *
     * @param user The specific user
     * @param task The specific task
     * @return The part concern by the task
     * @throws WorkflowNotFoundException If no workflow was find for this task
     * @throws NotAllowedException       If you can not make this task
     */
    private WorkspaceWorkflow checkTaskAccess(User user, Task task) throws WorkflowNotFoundException, NotAllowedException {

        Workflow workflow = task.getActivity().getWorkflow();
        WorkspaceWorkflow workspaceWorkflowTarget = workflowDAO.getWorkspaceWorkflowTarget(user.getWorkspaceId(), workflow);

        if (workspaceWorkflowTarget == null) {
            throw new WorkflowNotFoundException(workflow.getId());
        }
        if (!task.isInProgress()) {
            throw new NotAllowedException("NotAllowedException15");
        }
        if (!task.isPotentialWorker(user)) {
            throw new NotAllowedException("NotAllowedException14");
        }
        if (!workflow.getRunningTasks().contains(task)) {
            throw new NotAllowedException("NotAllowedException15");
        }

        return workspaceWorkflowTarget;
    }

    private void relaunchWorkflow(WorkspaceWorkflow workspaceWorkflow, int activityStep) {
        Workflow workflow = workspaceWorkflow.getWorkflow();
        // Clone new workflow
        Workflow relaunchedWorkflow = workflowDAO.duplicateWorkflow(workflow);

        // Move aborted workflow in docR list
        workflow.abort();
        workspaceWorkflow.addAbortedWorkflows(workflow);
        workflowDAO.removeWorkflowConstraints(workflow);
        // Set new workflow on document
        workspaceWorkflow.setWorkflow(relaunchedWorkflow);
        // Reset some properties
        relaunchedWorkflow.relaunch(activityStep);

        String workspaceId = workspaceWorkflow.getWorkspaceId();
        notifier.sendApproval(workspaceId, relaunchedWorkflow.getRunningTasks(), workspaceWorkflow);
        notifier.sendWorkspaceWorkflowRelaunchedNotification(workspaceId, workspaceWorkflow);
    }

    private void checkWorkflowBelongToWorkspace(User user, String workspaceId, Workflow workflow) throws AccessRightException {

        DocumentRevision documentTarget = documentRevisionDAO.getWorkflowHolder(workflow);

        if (documentTarget != null) {
            if (workspaceId.equals(documentTarget.getWorkspaceId())) {
                return;
            } else {
                throw new AccessRightException(user);
            }
        }

        PartRevision partTarget = partRevisionDAO.getWorkflowHolder(workflow);
        if (partTarget != null) {
            if (workspaceId.equals(partTarget.getWorkspaceId())) {
                return;
            } else {
                throw new AccessRightException(user);
            }
        }

        WorkspaceWorkflow workspaceWorkflowTarget = workflowDAO.getWorkspaceWorkflowTarget(workspaceId, workflow);
        if (workspaceWorkflowTarget == null) {
            throw new AccessRightException(user);
        }

    }


    private void checkWorkflowValidity(String workspaceId, String pId, ActivityModel[] pActivityModels) throws NotAllowedException {

        List<Role> roles = roleDAO.findRolesInWorkspace(workspaceId);

        if (pId == null || " ".equals(pId)) {
            throw new NotAllowedException("WorkflowNameEmptyException");
        }

        if (pActivityModels.length == 0) {
            throw new NotAllowedException("NotAllowedException2");
        }

        for (ActivityModel activity : pActivityModels) {
            if (activity.getLifeCycleState() == null || "".equals(activity.getLifeCycleState()) || activity.getTaskModels().isEmpty()) {
                throw new NotAllowedException("NotAllowedException3");
            }
            for (TaskModel taskModel : activity.getTaskModels()) {

                Role modelRole = taskModel.getRole();
                if (modelRole == null) {
                    throw new NotAllowedException("NotAllowedException13");
                }
                String roleName = modelRole.getName();
                for (Role role : roles) {
                    if (role.getName().equals(roleName)) {
                        taskModel.setRole(role);
                        break;
                    }
                }
            }
        }
    }


    private void checkWorkflowWriteAccess(WorkflowModel workflow, User user) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        if (user.isAdministrator()) {
            // Check if it is the workspace's administrator
            return;
        }
        if (workflow.getAcl() == null) {
            // Check if the item haven't ACL
            userManager.checkWorkspaceWriteAccess(workflow.getWorkspaceId());
        } else if (!workflow.getAcl().hasWriteAccess(user)) {
            // Check if there is a write access
            // Else throw a AccessRightException
            throw new AccessRightException(user);
        }
    }

}
