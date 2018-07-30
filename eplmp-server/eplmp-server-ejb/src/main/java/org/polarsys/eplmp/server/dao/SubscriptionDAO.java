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
package org.polarsys.eplmp.server.dao;

import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.common.UserGroup;
import org.polarsys.eplmp.core.document.*;
import org.polarsys.eplmp.core.gcm.GCMAccount;
import org.polarsys.eplmp.core.meta.Tag;
import org.polarsys.eplmp.core.notification.TagUserGroupSubscription;
import org.polarsys.eplmp.core.notification.TagUserGroupSubscriptionKey;
import org.polarsys.eplmp.core.notification.TagUserSubscription;
import org.polarsys.eplmp.core.notification.TagUserSubscriptionKey;

import javax.enterprise.context.RequestScoped;
import javax.persistence.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@RequestScoped
public class SubscriptionDAO {
    private static final Logger LOGGER = Logger.getLogger(SubscriptionDAO.class.getName());
    public static final String WORKSPACE_ID = "workspaceId";

    @PersistenceContext
    private EntityManager em;

    public TagUserSubscription saveTagUserSubscription(TagUserSubscription pSubscription) {
        return em.merge(pSubscription);
    }

    public TagUserGroupSubscription saveTagUserGroupSubscription(TagUserGroupSubscription pSubscription) {
        return em.merge(pSubscription);
    }

    public void removeTagUserSubscription(TagUserSubscriptionKey pKey) {
        try {
            TagUserSubscription subscription = em.find(TagUserSubscription.class, pKey);
            em.remove(subscription);
            em.flush();
        } catch (NullPointerException pNPEx) {
            //em.getReference throws a NullPointerException when entity
            //doesn't exist. It's probably a bug, as a workaround
            //we silently catch this exception
            LOGGER.log(Level.FINER,null,pNPEx);
        } catch (EntityNotFoundException pENFEx) {
            //not subscribed, no need to unsubscribe
            LOGGER.log(Level.FINER,null,pENFEx);
        }
    }

    public void removeTagUserGroupSubscription(TagUserGroupSubscriptionKey pKey) {
        try {
            TagUserGroupSubscription subscription = em.find(TagUserGroupSubscription.class, pKey);
            em.remove(subscription);
            em.flush();
        } catch (NullPointerException pNPEx) {
            //em.getReference throws a NullPointerException when entity
            //doesn't exist. It's probably a bug, as a workaround
            //we silently catch this exception
            LOGGER.log(Level.FINER,null,pNPEx);
        } catch (EntityNotFoundException pENFEx) {
            //not subscribed, no need to unsubscribe
            LOGGER.log(Level.FINER,null,pENFEx);
        }
    }

    public List<TagUserSubscription> getTagUserSubscriptionsByUser(User pUser){
        return em.createNamedQuery("TagUserSubscription.findTagUserSubscriptionsByUser", TagUserSubscription.class)
                .setParameter("userSubscriber", pUser)
                .getResultList();
    }

    public List<TagUserGroupSubscription> getTagUserGroupSubscriptionsByGroup(UserGroup pUserGroup){
        return em.createNamedQuery("TagUserGroupSubscription.findTagUserGroupSubscriptionsByGroup", TagUserGroupSubscription.class)
                .setParameter("groupSubscriber", pUserGroup)
                .getResultList();
    }


    public StateChangeSubscription createStateChangeSubscription(StateChangeSubscription pSubscription) {
        return em.merge(pSubscription);
    }

    public void removeStateChangeSubscription(SubscriptionKey pKey) {
        try {
            StateChangeSubscription subscription = em.getReference(StateChangeSubscription.class, pKey);
            em.remove(subscription);
            em.flush();
        } catch (NullPointerException pNPEx) {
            //em.getReference throws a NullPointerException when entity
            //doesn't exist. It's probably a bug, as a workaround
            //we silently catch this exception
            LOGGER.log(Level.FINER,null,pNPEx);
        } catch (EntityNotFoundException pENFEx) {
            //not subscribed, no need to unsubscribe
            LOGGER.log(Level.FINER,null,pENFEx);
        }
    }

    public IterationChangeSubscription createIterationChangeSubscription(IterationChangeSubscription pSubscription) {
        return em.merge(pSubscription);
    }

    public void removeIterationChangeSubscription(SubscriptionKey pKey) {
        try {
            IterationChangeSubscription subscription = em.getReference(IterationChangeSubscription.class, pKey);
            em.remove(subscription);
            em.flush();
        } catch (NullPointerException pNPEx) {
            //em.getReference throws a NullPointerException when entity
            //doesn't exist. It's probably a bug, as a workaround
            //we silently catch this exception
            LOGGER.log(Level.FINER,null,pNPEx);
        } catch (EntityNotFoundException pENFEx) {
            //not subscribed, no need to unsubscribe
            LOGGER.log(Level.FINER,null,pENFEx);
        }
    }

    public void removeAllTagSubscriptions(User pUser) {
        Query query = em.createNamedQuery("TagUserSubscription.deleteTagUserSubscriptionsFromUser");
        query.setParameter("userSubscriber", pUser);
        query.executeUpdate();
    }

    public void removeAllTagSubscriptions(UserGroup pGroup) {
        Query query = em.createNamedQuery("TagUserGroupSubscription.deleteTagUserGroupSubscriptionsFromGroup");
        query.setParameter("groupSubscriber", pGroup);
        query.executeUpdate();
    }

    public void removeAllTagSubscriptions(Tag pTag) {
        Query query = em.createNamedQuery("TagUserSubscription.deleteTagUserSubscriptionsFromTag");
        query.setParameter("tag", pTag);
        query.executeUpdate();

        Query query2 = em.createNamedQuery("TagUserGroupSubscription.deleteTagUserGroupSubscriptionsFromTag");
        query2.setParameter("tag", pTag);
        query2.executeUpdate();
    }

    public void removeAllSubscriptions(DocumentRevision pDocR) {
        Query query = em.createQuery("DELETE FROM StateChangeSubscription s WHERE s.observedDocumentRevision = :docR");
        query.setParameter("docR", pDocR);
        query.executeUpdate();

        Query query2 = em.createQuery("DELETE FROM IterationChangeSubscription s WHERE s.observedDocumentRevision = :docR");
        query2.setParameter("docR", pDocR);
        query2.executeUpdate();
    }

    public void removeAllSubscriptions(User pUser) {
        Query query = em.createQuery("DELETE FROM StateChangeSubscription s WHERE s.subscriber = :user");
        query.setParameter("user", pUser);
        query.executeUpdate();

        Query query2 = em.createQuery("DELETE FROM IterationChangeSubscription s WHERE s.subscriber = :user");
        query2.setParameter("user", pUser);
        query2.executeUpdate();
    }

    public DocumentRevisionKey[] getIterationChangeEventSubscriptions(User pUser) {
        DocumentRevisionKey[] docRKeys;
        Query query = em.createQuery("SELECT s.observedDocumentRevisionWorkspaceId, s.observedDocumentRevisionId, s.observedDocumentRevisionVersion FROM IterationChangeSubscription s WHERE s.subscriber = :user");
        List listDocRKeys = query.setParameter("user", pUser).getResultList();
        docRKeys = new DocumentRevisionKey[listDocRKeys.size()];
        for (int i = 0; i < listDocRKeys.size(); i++) {
            Object[] values = (Object[]) listDocRKeys.get(i);
            docRKeys[i] = new DocumentRevisionKey((String) values[0], (String) values[1], (String) values[2]);
        }


        return docRKeys;
    }

    public DocumentRevisionKey[] getStateChangeEventSubscriptions(User pUser) {
        DocumentRevisionKey[] docRKeys;
        Query query = em.createQuery("SELECT s.observedDocumentRevisionWorkspaceId, s.observedDocumentRevisionId, s.observedDocumentRevisionVersion FROM StateChangeSubscription s WHERE s.subscriber = :user");
        List listDocRKeys = query.setParameter("user", pUser).getResultList();
        docRKeys = new DocumentRevisionKey[listDocRKeys.size()];
        for (int i = 0; i < listDocRKeys.size(); i++) {
            Object[] values = (Object[]) listDocRKeys.get(i);
            docRKeys[i] = new DocumentRevisionKey((String) values[0], (String) values[1], (String) values[2]);
        }


        return docRKeys;
    }


    public boolean isUserStateChangeEventSubscribedForGivenDocument(User pUser, DocumentRevision docR) {
        return ! em.createNamedQuery("StateChangeSubscription.findSubscriptionByUserAndDocRevision").
                setParameter("user", pUser).setParameter("docR", docR).getResultList().isEmpty();

    }

    public boolean isUserIterationChangeEventSubscribedForGivenDocument(User pUser, DocumentRevision docR) {
        return ! em.createNamedQuery("IterationChangeSubscription.findSubscriptionByUserAndDocRevision").
                setParameter("user", pUser).setParameter("docR", docR).getResultList().isEmpty();
    }



    public Collection<User> getIterationChangeEventSubscribers(DocumentRevision pDocR) {
        TypedQuery<User> query = em.createQuery("SELECT DISTINCT s.subscriber FROM IterationChangeSubscription s WHERE s.observedDocumentRevision = :docR", User.class);
        List<User> listUsers = query.setParameter("docR", pDocR).getResultList();
        Set<User> users=new HashSet<>();
        users.addAll(listUsers);

        if(pDocR.getTags() !=null && !pDocR.getTags().isEmpty()) {
            listUsers = em.createNamedQuery("TagUserSubscription.findIterationChangeSubscribersByTags", User.class)
                    .setParameter(WORKSPACE_ID, pDocR.getWorkspaceId())
                    .setParameter("tags", pDocR.getTags().stream().map(Tag::getLabel).collect(Collectors.toList()))
                    .getResultList();
            users.addAll(listUsers);

            listUsers = em.createNamedQuery("TagUserGroupSubscription.findIterationChangeSubscribersByTags", User.class)
                    .setParameter(WORKSPACE_ID, pDocR.getWorkspaceId())
                    .setParameter("tags", pDocR.getTags().stream().map(Tag::getLabel).collect(Collectors.toList()))
                    .getResultList();
            users.addAll(listUsers);
        }
        return users;
    }

    public Collection<User> getStateChangeEventSubscribers(DocumentRevision pDocR) {
        TypedQuery<User> query = em.createQuery("SELECT DISTINCT s.subscriber FROM StateChangeSubscription s WHERE s.observedDocumentRevision = :docR", User.class);
        List<User> listUsers = query.setParameter("docR", pDocR).getResultList();
        Set<User> users=new HashSet<>();
        users.addAll(listUsers);

        if(pDocR.getTags() !=null && !pDocR.getTags().isEmpty()) {
            listUsers = em.createNamedQuery("TagUserSubscription.findStateChangeSubscribersByTags", User.class)
                    .setParameter(WORKSPACE_ID, pDocR.getWorkspaceId())
                    .setParameter("tags", pDocR.getTags().stream().map(Tag::getLabel).collect(Collectors.toList()))
                    .getResultList();
            users.addAll(listUsers);

            listUsers = em.createNamedQuery("TagUserGroupSubscription.findStateChangeSubscribersByTags", User.class)
                    .setParameter(WORKSPACE_ID, pDocR.getWorkspaceId())
                    .setParameter("tags", pDocR.getTags().stream().map(Tag::getLabel).collect(Collectors.toList()))
                    .getResultList();
            users.addAll(listUsers);
        }
        return users;
    }

    public GCMAccount[] getIterationChangeEventSubscribersGCMAccount(DocumentRevision pDocR) {
        GCMAccount[] gcmAccounts;
        TypedQuery<GCMAccount> query = em.createQuery("SELECT DISTINCT gcm FROM GCMAccount gcm, IterationChangeSubscription s WHERE gcm.account.login = s.subscriber.login AND s.observedDocumentRevision = :docR", GCMAccount.class);
        List<GCMAccount> gcmAccountsList = query.setParameter("docR", pDocR).getResultList();
        gcmAccounts = new GCMAccount[gcmAccountsList.size()];
        for (int i = 0; i < gcmAccountsList.size(); i++) {
            gcmAccounts[i] = gcmAccountsList.get(i);
        }

        return gcmAccounts;
    }

    public GCMAccount[] getStateChangeEventSubscribersGCMAccount(DocumentRevision pDocR) {
        GCMAccount[] gcmAccounts;
        TypedQuery<GCMAccount> query = em.createQuery("SELECT DISTINCT gcm FROM GCMAccount gcm, StateChangeSubscription s WHERE gcm.account.login = s.subscriber.login AND s.observedDocumentRevision = :docR", GCMAccount.class);
        List<GCMAccount> gcmAccountsList = query.setParameter("docR", pDocR).getResultList();
        gcmAccounts = new GCMAccount[gcmAccountsList.size()];
        for (int i = 0; i < gcmAccountsList.size(); i++) {
            gcmAccounts[i] = gcmAccountsList.get(i);
        }

        return gcmAccounts;
    }

}
