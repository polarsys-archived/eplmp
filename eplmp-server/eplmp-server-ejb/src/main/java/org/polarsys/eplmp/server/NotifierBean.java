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

import org.polarsys.eplmp.core.admin.WorkspaceBackOptions;
import org.polarsys.eplmp.core.common.Account;
import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.common.Workspace;
import org.polarsys.eplmp.core.document.DocumentRevision;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.hooks.SNSWebhookApp;
import org.polarsys.eplmp.core.hooks.SimpleWebhookApp;
import org.polarsys.eplmp.core.hooks.Webhook;
import org.polarsys.eplmp.core.meta.Tag;
import org.polarsys.eplmp.core.product.PartRevision;
import org.polarsys.eplmp.core.services.INotifierLocal;
import org.polarsys.eplmp.core.services.IPlatformOptionsManagerLocal;
import org.polarsys.eplmp.core.services.IWebhookManagerLocal;
import org.polarsys.eplmp.core.services.IWorkspaceManagerLocal;
import org.polarsys.eplmp.core.util.FileIO;
import org.polarsys.eplmp.core.workflow.Task;
import org.polarsys.eplmp.core.workflow.WorkspaceWorkflow;
import org.polarsys.eplmp.i18n.PropertiesLoader;
import org.polarsys.eplmp.server.hooks.SNSWebhookRunner;
import org.polarsys.eplmp.server.hooks.SimpleWebhookRunner;
import org.polarsys.eplmp.server.hooks.WebhookRunner;

import javax.annotation.Resource;
import javax.ejb.Asynchronous;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Session class NotifierBean
 *
 * @author Florent.Garin
 */
@Local(INotifierLocal.class)
@Stateless(name = "MailerBean")
public class NotifierBean implements INotifierLocal {

    private static final String TEMPLATE_BASE_NAME = "/org/polarsys/eplmp/server/templates/NotificationText";

    @Inject
    private ConfigManager configManager;

    @Inject
    private IPlatformOptionsManagerLocal platformOptionsManager;

    @Inject
    private IWorkspaceManagerLocal workspaceManager;

    @Inject
    private IWebhookManagerLocal webhookManager;

    @Resource(name = "mail/docdokuSMTP")
    private Session mailSession;

    private static final Logger LOGGER = Logger.getLogger(NotifierBean.class.getName());

    @Asynchronous
    @Override
    public void sendStateNotification(String workspaceId, Collection<User> pSubscribers,
                                      DocumentRevision pDocumentRevision) {

        LOGGER.info("Sending state notification emails \n\tfor the document " + pDocumentRevision.getLastIteration());

        try {
            for (User pSubscriber : pSubscribers) {
                sendStateNotification(pSubscriber, pDocumentRevision);
            }
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Asynchronous
    @Override
    public void sendIterationNotification(String workspaceId, Collection<User> pSubscribers,
                                          DocumentRevision pDocumentRevision) {

        LOGGER.info("Sending iteration notification emails \n\tfor the document " + pDocumentRevision.getLastIteration());

        try {
            for (User pSubscriber : pSubscribers) {
                sendIterationNotification(pSubscriber, pDocumentRevision);
            }
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Asynchronous
    @Override
    public void sendTaggedNotification(String workspaceId, Collection<User> pSubscribers, DocumentRevision pDocR, Tag pTag) {

        LOGGER.info("Sending tagged notification emails \n\tfor the document " + pDocR.getLastIteration());

        try {
            for (User pSubscriber : pSubscribers) {
                sendTaggedNotification(pSubscriber, pDocR, pTag);
            }
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Asynchronous
    @Override
    public void sendTaggedNotification(String workspaceId, Collection<User> pSubscribers, PartRevision pPartR, Tag pTag) {

        LOGGER.info("Sending tagged notification emails \n\tfor the part " + pPartR.getLastIteration());

        try {
            for (User pSubscriber : pSubscribers) {
                sendTaggedNotification(pSubscriber, pPartR, pTag);
            }
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Asynchronous
    @Override
    public void sendUntaggedNotification(String workspaceId, Collection<User> pSubscribers, DocumentRevision pDocR, Tag pTag) {

        LOGGER.info("Sending untagged notification emails \n\tfor the document " + pDocR.getLastIteration());

        try {
            for (User pSubscriber : pSubscribers) {
                sendUntaggedNotification(pSubscriber, pDocR, pTag);
            }
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Asynchronous
    @Override
    public void sendUntaggedNotification(String workspaceId, Collection<User> pSubscribers, PartRevision pPartR, Tag pTag) {

        LOGGER.info("Sending untagged notification emails \n\tfor the part " + pPartR.getLastIteration());

        try {
            for (User pSubscriber : pSubscribers) {
                sendUntaggedNotification(pSubscriber, pPartR, pTag);
            }
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Asynchronous
    @Override
    public void sendApproval(String workspaceId, Collection<Task> pRunningTasks,
                             DocumentRevision pDocumentRevision) {

        LOGGER.info("Sending approval emails \n\tfor the document " + pDocumentRevision.getLastIteration());

        try {
            for (Task task : pRunningTasks) {
                sendApproval(task, pDocumentRevision);
            }
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Asynchronous
    @Override
    public void sendApproval(String workspaceId, Collection<Task> pRunningTasks, PartRevision partRevision) {

        LOGGER.info("Sending approval required emails \n\tfor the part " + partRevision.getLastIteration());

        try {
            for (Task task : pRunningTasks) {
                sendApproval(task, partRevision);
            }
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Asynchronous
    @Override
    public void sendApproval(String workspaceId, Collection<Task> pRunningTasks, WorkspaceWorkflow workspaceWorkflow) {

        LOGGER.info("Sending approval required emails \n\tfor the workspace workflow " + workspaceWorkflow.getId());

        try {
            for (Task task : pRunningTasks) {
                sendApproval(task, workspaceWorkflow);
            }
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Asynchronous
    @Override
    public void sendPasswordRecovery(Account account, String recoveryUUID) {

        LOGGER.info("Sending recovery message \n\tfor the user which login is " + account.getLogin());

        Object[] args = {
                getRecoveryUrl(recoveryUUID),
                account.getLogin()
        };

        try {
            sendMessage(account, "Recovery_title", "Recovery_text", args);
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Asynchronous
    @Override
    public void sendWorkspaceDeletionNotification(Account admin, String workspaceId) {

        LOGGER.info("Sending workspace deletion notification message \n\tfor the user which login is " + admin.getLogin());

        Object[] args = {
                workspaceId
        };

        try {
            //User admin does not exist anymore as the workspace has been deleted
            sendMessage(admin, "WorkspaceDeletion_title", "WorkspaceDeletion_text", args);
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Asynchronous
    @Override
    public void sendWorkspaceDeletionErrorNotification(Account admin, String workspaceId) {

        LOGGER.info("Sending workspace deletion error notification message \n\tfor the user which login is " + admin.getLogin());

        Object[] args = {
                workspaceId
        };
        try {
            User adminUser = new User(new Workspace(workspaceId), admin);
            sendMessage(adminUser, "WorkspaceDeletion_title", "WorkspaceDeletionError_text", args);
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }


    @Asynchronous
    @Override
    public void sendPartRevisionWorkflowRelaunchedNotification(String workspaceId, PartRevision partRevision) {

        Workspace workspace = partRevision.getPartMaster().getWorkspace();
        Account admin = workspace.getAdmin();
        User adminUser = new User(workspace, admin);

        User author = partRevision.getAuthor();

        LOGGER.info("Sending workflow relaunch notification email \n\tfor the part " + partRevision.getLastIteration() + " to admin: " + admin.getLogin());

        // Mail both workspace admin and partRevision author
        sendWorkflowRelaunchedNotification(adminUser, partRevision);


        if (!admin.getLogin().equals(author.getLogin())) {
            LOGGER.info("Sending workflow relaunch notification email \n\tfor the part " + partRevision.getLastIteration() + " to user: " + author.getLogin());
            sendWorkflowRelaunchedNotification(author, partRevision);
        }

    }

    @Asynchronous
    @Override
    public void sendDocumentRevisionWorkflowRelaunchedNotification(String workspaceId, DocumentRevision documentRevision) {
        Workspace workspace = documentRevision.getDocumentMaster().getWorkspace();
        Account admin = workspace.getAdmin();
        User author = documentRevision.getAuthor();
        User adminUser = new User(workspace, admin);
        // Mail both workspace admin and documentMaster author
        sendWorkflowRelaunchedNotification(adminUser, documentRevision);

        if (!admin.getLogin().equals(author.getLogin())) {
            sendWorkflowRelaunchedNotification(author, documentRevision);
        }
    }

    @Asynchronous
    @Override
    public void sendWorkspaceWorkflowRelaunchedNotification(String workspaceId, WorkspaceWorkflow workspaceWorkflow) {
        Workspace workspace = workspaceWorkflow.getWorkspace();
        Account admin = workspace.getAdmin();
        User adminUser = new User(workspace, admin);
        // Mail workspace admin
        sendWorkflowRelaunchedNotification(adminUser, workspaceWorkflow);
    }

    @Asynchronous
    @Override
    public void sendWorkspaceIndexationSuccess(Account account, String workspaceId, String extraMessage) {

        Object[] args = {
                workspaceId,
                extraMessage
        };

        try {
            User adminUser = new User(new Workspace(workspaceId), account);
            sendMessage(adminUser, "Indexer_success_title", "Indexer_success_text", args);
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Asynchronous
    @Override
    public void sendWorkspaceIndexationFailure(Account account, String workspaceId, String extraMessage) {

        Object[] args = {
                workspaceId,
                extraMessage
        };

        try {
            User adminUser = new User(new Workspace(workspaceId), account);
            sendMessage(adminUser, "Indexer_failure_title", "Indexer_failure_text", args);
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Override
    public void sendBulkIndexationSuccess(Account account) {
        Object[] args = {};
        try {
            sendMessage(account, "Indexer_bulk_success_title", "Indexer_bulk_success_text", args);
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Override
    public void sendBulkIndexationFailure(Account account, String failureMessage) {
        Object[] args = {
                failureMessage
        };
        try {
            sendMessage(account, "Indexer_bulk_failure_title", "Indexer_bulk_failure_text", args);
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    @Asynchronous
    @Override
    public void sendCredential(Account account) {
        String accountDisabledMessage = "";
        if (!account.isEnabled()) {
            switch (platformOptionsManager.getWorkspaceCreationStrategy()) {
                case ADMIN_VALIDATION:
                    accountDisabledMessage = getString("SignUp_AccountDisabled_text", account.getLocale());
                    break;
            }
        }

        Object[] args = {
                account.getLogin(),
                configManager.getCodebase(),
                accountDisabledMessage
        };

        try {
            sendMessage(account, "SignUp_success_title", "SignUp_success_text", args);
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    private void sendStateNotification(User pSubscriber, DocumentRevision pDocumentRevision) throws MessagingException {

        LOGGER.info("Sending state notification emails \n\tfor the document " + pDocumentRevision.getLastIteration() + " to user " + pSubscriber.getLogin());

        String stateName = pDocumentRevision.getLifeCycleState();
        stateName = (stateName != null && !stateName.isEmpty()) ? stateName : getString("FinalState_name",
                pSubscriber.getLocale());

        Object[] args = {
                pDocumentRevision,
                pDocumentRevision.getLastIteration().getCreationDate(),
                getDocumentRevisionPermalinkURL(pDocumentRevision),
                stateName
        };

        sendMessage(pSubscriber, "StateNotification_title", "StateNotification_text", args);
    }

    private void sendIterationNotification(User pSubscriber,
                                           DocumentRevision pDocumentRevision) throws MessagingException {

        LOGGER.info("Sending iteration notification emails \n\tfor the document " + pDocumentRevision.getLastIteration());

        Object[] args = {
                pDocumentRevision,
                pDocumentRevision.getLastIteration().getCreationDate(),
                pDocumentRevision.getLastIteration().getIteration(),
                pDocumentRevision.getLastIteration().getAuthor(),
                getDocumentRevisionPermalinkURL(pDocumentRevision)
        };
        sendMessage(pSubscriber, "IterationNotification_title", "IterationNotification_text", args);

    }

    private void sendTaggedNotification(User pSubscriber, DocumentRevision pDocumentRevision, Tag pTag) throws MessagingException {
        sendTaggedNotification(pSubscriber, pDocumentRevision, pTag, true);
    }

    private void sendUntaggedNotification(User pSubscriber, DocumentRevision pDocumentRevision, Tag pTag) throws MessagingException {
        sendTaggedNotification(pSubscriber, pDocumentRevision, pTag, false);
    }

    private void sendTaggedNotification(User pSubscriber,
                                        DocumentRevision pDocumentRevision, Tag pTag, boolean tagged) throws MessagingException {
        LOGGER.info("Sending tag notification emails \n\tfor the document " + pDocumentRevision.getLastIteration() + " to subscriber : " + pSubscriber.getLogin());
        Object[] args = {
                pTag,
                pDocumentRevision,
                getDocumentRevisionPermalinkURL(pDocumentRevision)
        };
        sendMessage(pSubscriber, "TagNotification_title", tagged ? "TagNotificationTagged_text" : "TagNotificationUntagged_text", args);
    }

    private void sendTaggedNotification(User pSubscriber, PartRevision pPartRevision, Tag pTag) throws MessagingException {
        sendTaggedNotification(pSubscriber, pPartRevision, pTag, true);
    }

    private void sendUntaggedNotification(User pSubscriber, PartRevision pPartRevision, Tag pTag) throws MessagingException {
        sendTaggedNotification(pSubscriber, pPartRevision, pTag, false);
    }

    private void sendTaggedNotification(User pSubscriber, PartRevision pPartRevision, Tag pTag, boolean tagged) throws MessagingException {
        LOGGER.info("Sending tag notification emails \n\tfor the part " + pPartRevision.getLastIteration() + " to subscriber : " + pSubscriber.getLogin());
        Object[] args = {
                pTag,
                pPartRevision,
                getPartRevisionPermalinkURL(pPartRevision)
        };
        sendMessage(pSubscriber, "TagNotification_title", tagged ? "TagNotificationTagged_text" : "TagNotificationUntagged_text", args);
    }

    private void sendApproval(Task task, DocumentRevision pDocumentRevision) throws MessagingException {

        LOGGER.info("Sending approval required emails \n\tfor the document " + pDocumentRevision.getLastIteration());

        Set<User> workers = new HashSet<>();
        workers.addAll(task.getAssignedUsers());
        task.getAssignedGroups().forEach(g -> workers.addAll(g.getUsers()));

        for (User worker : workers) {
            sendApprovalToUser(worker, task, pDocumentRevision);
        }
    }

    private void sendApproval(Task task, PartRevision partRevision) throws MessagingException {

        LOGGER.info("Sending approval required emails \n\tfor the part " + partRevision.getLastIteration());

        Set<User> workers = new HashSet<>();
        workers.addAll(task.getAssignedUsers());
        task.getAssignedGroups().forEach(g -> workers.addAll(g.getUsers()));

        for (User worker : workers) {
            sendApprovalToUser(worker, task, partRevision);
        }
    }

    private void sendApproval(Task task, WorkspaceWorkflow workspaceWorkflow) throws MessagingException {

        LOGGER.info("Sending approval required emails \n\tfor the workspace workflow " + workspaceWorkflow.getId());

        Set<User> workers = new HashSet<>();
        workers.addAll(task.getAssignedUsers());
        task.getAssignedGroups().forEach(g -> workers.addAll(g.getUsers()));

        for (User worker : workers) {
            sendApprovalToUser(worker, task, workspaceWorkflow);
        }
    }


    private void sendApprovalToUser(User worker, Task task, DocumentRevision pDocumentRevision) throws MessagingException {

        LOGGER.info("Sending approval email \n\tfor the document " + pDocumentRevision.getLastIteration() + " to user: " + worker.getLogin());

        Object[] args = {
                task.getTitle(),
                getDocumentRevisionPermalinkURL(pDocumentRevision),
                pDocumentRevision.getKey(),
                task.getInstructions() == null ? "-" : task.getInstructions(),
                getTaskUrl(task, pDocumentRevision.getWorkspaceId())
        };

        sendMessage(worker, "Approval_title", "Approval_document_text", args);
    }


    private void sendApprovalToUser(User worker, Task pTask, PartRevision partRevision) throws MessagingException {

        LOGGER.info("Sending approval email \n\tfor the part " + partRevision.getLastIteration() + " to user: " + worker.getLogin());

        Object[] args = {
                pTask.getTitle(),
                getPartRevisionPermalinkURL(partRevision),
                partRevision.getKey(),
                pTask.getInstructions() == null ? "-" : pTask.getInstructions(),
                getTaskUrl(pTask, partRevision.getWorkspaceId())
        };

        sendMessage(worker, "Approval_title", "Approval_part_text", args);
    }

    private void sendApprovalToUser(User worker, Task pTask, WorkspaceWorkflow workspaceWorkflow) throws MessagingException {

        LOGGER.info("Sending approval email \n\tfor the workspace workflow " + workspaceWorkflow.getId() + " to user: " + worker.getLogin());

        Object[] args = {
                pTask.getTitle(),
                pTask.getInstructions() == null ? "-" : pTask.getInstructions(),
                getTaskUrl(pTask, workspaceWorkflow.getWorkspaceId())
        };

        sendMessage(worker, "Approval_title", "Approval_workspace_workflow_text", args);
    }


    private void sendWorkflowRelaunchedNotification(User user, PartRevision partRevision) {
        Object[] args = {
                partRevision.getPartNumber() + "-" + partRevision.getVersion(),
                user.getWorkspace().getId(),
                partRevision.getWorkflow().getLifeCycleState()
        };
        try {
            sendMessage(user, "Workflow_relaunched_title", "PartRevision_workflow_relaunched_text", args);
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }


    private void sendWorkflowRelaunchedNotification(User user, DocumentRevision documentRevision) {
        Object[] args = {
                documentRevision.getId() + "-" + documentRevision.getVersion(),
                user.getWorkspace().getId(),
                documentRevision.getWorkflow().getLifeCycleState()
        };
        try {
            sendMessage(user, "Workflow_relaunched_title", "DocumentRevision_workflow_relaunched_text", args);
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }


    private void sendWorkflowRelaunchedNotification(User user, WorkspaceWorkflow workspaceWorkflow) {
        Object[] args = {
                user.getWorkspace().getId(),
                workspaceWorkflow.getWorkflow().getLifeCycleState()
        };
        try {
            sendMessage(user, "Workflow_relaunched_title", "WorkspaceWorkflow_workflow_relaunched_text", args);
        } catch (MessagingException pMEx) {
            logMessagingException(pMEx);
        }
    }

    // URIs
    private String getDocumentRevisionPermalinkURL(DocumentRevision pDocR) {
        return configManager.getCodebase() + "/documents/index.html#" + pDocR.getWorkspaceId() + "/" + FileIO.encode(pDocR.getId()) + "/" + pDocR.getVersion();
    }

    private String getPartRevisionPermalinkURL(PartRevision pPartR) {
        return configManager.getCodebase() + "/parts/index.html#" + pPartR.getWorkspaceId() + "/" + FileIO.encode(pPartR.getPartNumber()) + "/" + pPartR.getVersion();
    }

    private String getTaskUrl(Task pTask, String workspaceId) {
        return configManager.getCodebase() + "/change-management/index.html#" + workspaceId + "/tasks/" + pTask.getWorkflowId() + "-" + pTask.getActivityStep() + "-" + pTask.getNum();
    }

    private String getRecoveryUrl(String uuid) {
        return configManager.getCodebase() + "/index.html#recover/" + uuid;
    }


    // Log shortcuts
    private void logMessagingException(MessagingException pMEx) {
        String logMessage = "Message format error. \n\tMail can't be sent. \n\t" + pMEx.getMessage();
        LOGGER.log(Level.SEVERE, logMessage, pMEx);
    }

    // Template utils methods

    private Properties getProperties(Locale pLocale) {
        return PropertiesLoader.loadLocalizedProperties(pLocale, TEMPLATE_BASE_NAME, getClass());
    }

    private String getString(String string, Locale pLocale) {
        return getProperties(pLocale).getProperty(string).replaceAll("'", "’");
    }

    private String format(String string, Object[] args, Locale pLocale) {
        return MessageFormat.format(getString(string, pLocale).replaceAll("'", "’"), args);
    }

    private String getHTMLBody(String content, Locale pLocale) {
        String mailBodyTemplate = getString("MailBodyTemplate", pLocale);
        return MessageFormat.format(mailBodyTemplate, content);
    }

    private String getSubject(String string, Locale pLocale) {
        String mailSubjectTemplate = getString("MailSubjectTemplate", pLocale);
        return mailSubjectTemplate + " " + getString(string, pLocale);
    }

    // Direct account message
    // Only emails should be sent
    private void sendMessage(Account account, String subjectKey, String contentKey, Object[] contentArgs) throws MessagingException {
        Locale locale = account.getLocale();
        String subject = getSubject(subjectKey, locale);
        String content = format(contentKey, contentArgs, locale);
        String name = account.getName();
        String email = account.getEmail();
        sendEmail(email, name, subject, getHTMLBody(content, locale));
    }

    // User in workspace message
    private void sendMessage(User user, String subjectKey, String contentKey, Object[] contentArgs) throws MessagingException {
        Locale userLocale = user.getLocale();
        String subject = getSubject(subjectKey, userLocale);
        String content = format(contentKey, contentArgs, userLocale);
        String name = user.getName();
        String login = user.getLogin();
        String email = user.getEmail();
        String workspaceId = user.getWorkspaceId();

        WorkspaceBackOptions workspaceBackOptions;
        try {
            workspaceBackOptions = workspaceManager.getWorkspaceBackOptions(workspaceId);
        } catch (WorkspaceNotFoundException | AccountNotFoundException | AccessRightException e) {
            LOGGER.log(Level.SEVERE, null, e);
            return;
        }

        if (workspaceBackOptions.isSendEmails()) {
            sendEmail(email, name, subject, getHTMLBody(content, userLocale));
        }

        List<Webhook> activeWebHooks;

        try {
            activeWebHooks = webhookManager.getActiveWebHooks(workspaceId);
        } catch (UserNotFoundException | WorkspaceNotFoundException | UserNotActiveException | WorkspaceNotEnabledException e) {
            LOGGER.log(Level.SEVERE, null, e);
            return;
        }

        for (Webhook webhook : activeWebHooks) {
            runHook(webhook, login, email, name, subject, content);
        }

    }

    private void sendEmail(String email, String name, String subject, String content) throws MessagingException {

        if (email == null || email.isEmpty()) {
            LOGGER.log(Level.WARNING, "Cannot send mail, email is empty");
            return;
        }

        try {
            InternetAddress emailAddress = new InternetAddress(email, name);
            Message message = new MimeMessage(mailSession);
            message.addRecipient(Message.RecipientType.TO, emailAddress);
            message.setSubject(subject);
            message.setSentDate(new Date());
            message.setContent(content, "text/html; charset=utf-8");
            message.setFrom();
            Transport.send(message);
        } catch (UnsupportedEncodingException e) {
            String logMessage = "Unsupported encoding: " + e.getMessage();
            LOGGER.log(Level.SEVERE, logMessage, e);
        }
    }

    private void runHook(Webhook webhook, String login, String email, String name, String subject, String content) {
        LOGGER.log(Level.INFO, " Running hook " + webhook.getName());
        String appName = webhook.getAppName();
        WebhookRunner runner;

        switch (appName) {
            case SNSWebhookApp.APP_NAME:
                runner = new SNSWebhookRunner();
                break;
            case SimpleWebhookApp.APP_NAME:
                runner = new SimpleWebhookRunner();
                break;
            default:
                LOGGER.log(Level.SEVERE, "Unsupported webhook " + webhook);
                return;
        }

        runner.run(webhook, login, email, name, subject, content);
    }

}
