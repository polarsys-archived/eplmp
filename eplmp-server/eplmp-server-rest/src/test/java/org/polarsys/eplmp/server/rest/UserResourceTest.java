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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.polarsys.eplmp.core.common.Account;
import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.common.Workspace;
import org.polarsys.eplmp.core.exceptions.ApplicationException;
import org.polarsys.eplmp.core.meta.Tag;
import org.polarsys.eplmp.core.notification.TagUserSubscription;
import org.polarsys.eplmp.core.services.INotificationManagerLocal;
import org.polarsys.eplmp.core.services.IUserManagerLocal;
import org.polarsys.eplmp.core.services.IWorkspaceManagerLocal;
import org.polarsys.eplmp.server.rest.dto.TagSubscriptionDTO;
import org.polarsys.eplmp.server.rest.dto.UserDTO;

import javax.persistence.EntityManager;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;

import static org.mockito.MockitoAnnotations.initMocks;

public class UserResourceTest {

    @InjectMocks
    private UserResource userResource = new UserResource();

    @Mock
    private EntityManager em;

    @Mock
    private IWorkspaceManagerLocal workspaceManager;

    @Mock
    private IUserManagerLocal userManager;

    @Mock
    private INotificationManagerLocal notificationManager;

    private String workspaceId = "wks";
    private Workspace workspace = new Workspace(workspaceId);
    private String login = "foo";
    private Account account = new Account(login);
    private Account admin = new Account("whatever");
    private User user = new User(workspace, account);

    @Before
    public void setup() throws Exception {
        initMocks(this);
        workspace.setAdmin(admin);
        userResource.init();
    }

    @Test
    public void getUsersInWorkspaceTest() throws ApplicationException {
        User[] users = new User[]{user};
        Mockito.when(userManager.getUsers(workspaceId))
                .thenReturn(users);
        UserDTO[] usersInWorkspace = userResource.getUsersInWorkspace(workspaceId);
        Assert.assertEquals(users.length, usersInWorkspace.length);
    }

    @Test
    public void whoAmITest() throws ApplicationException {
        Mockito.when(userManager.whoAmI(workspaceId))
                .thenReturn(user);
        UserDTO userDTO = userResource.whoAmI(workspaceId);
        Assert.assertEquals(user.getLogin(), userDTO.getLogin());
    }

    @Test
    public void getAdminInWorkspaceTest() throws ApplicationException {
        Mockito.when(workspaceManager.getWorkspace(workspaceId))
                .thenReturn(workspace);
        UserDTO adminInWorkspace = userResource.getAdminInWorkspace(workspaceId);
        Assert.assertEquals(admin.getLogin(), adminInWorkspace.getLogin());
    }

    @Test
    public void getTagSubscriptionsForUserTest() throws ApplicationException {
        Tag tag1 = new Tag(workspace, "blah");
        Tag tag2 = new Tag(workspace, "cool");
        TagUserSubscription subscription1 = new TagUserSubscription(tag1, user);
        TagUserSubscription subscription2 = new TagUserSubscription(tag2, user);
        List<TagUserSubscription> subscriptionList = Arrays.asList(subscription1, subscription2);
        Mockito.when(notificationManager.getTagUserSubscriptionsByUser(workspaceId, login))
                .thenReturn(subscriptionList);
        TagSubscriptionDTO[] tagSubscriptionsForUser = userResource.getTagSubscriptionsForUser(workspaceId, login);
        Assert.assertEquals(subscriptionList.size(), tagSubscriptionsForUser.length);
    }

    @Test
    public void updateUserSubscriptionTest() throws ApplicationException {
        String tagName = "blah";
        Tag tag = new Tag(workspace,  tagName);
        TagUserSubscription subscription = new TagUserSubscription(tag, user);
        TagSubscriptionDTO subDTO = new TagSubscriptionDTO();
        subDTO.setOnIterationChange(true);
        subDTO.setOnStateChange(true);

        Mockito.when(notificationManager.createOrUpdateTagUserSubscription(workspaceId,
                login, tagName, subDTO.isOnIterationChange(), subDTO.isOnStateChange()))
                .thenReturn(subscription);

        Response res = userResource.updateUserSubscription(workspaceId, login, tagName, subDTO);
        Assert.assertEquals(Response.Status.CREATED.getStatusCode(), res.getStatus());

    }

    @Test
    public void deleteUserSubscriptionTest() throws ApplicationException {
        String tagName = "blah";
        Response res = userResource.deleteUserSubscription(workspaceId, login, tagName);
        Mockito.verify(notificationManager, Mockito.times(1))
                .removeTagUserSubscription(workspaceId, login, tagName);
        Assert.assertEquals(Response.Status.NO_CONTENT.getStatusCode(), res.getStatus());
    }

}
