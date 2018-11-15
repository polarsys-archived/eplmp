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

import org.polarsys.eplmp.core.change.ChangeItem;
import org.polarsys.eplmp.core.common.*;
import org.polarsys.eplmp.core.document.*;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.gcm.GCMAccount;
import org.polarsys.eplmp.core.log.DocumentLog;
import org.polarsys.eplmp.core.meta.*;
import org.polarsys.eplmp.core.product.PartRevision;
import org.polarsys.eplmp.core.query.DocumentSearchQuery;
import org.polarsys.eplmp.core.security.ACL;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.*;
import org.polarsys.eplmp.core.sharing.SharedDocument;
import org.polarsys.eplmp.core.sharing.SharedEntityKey;
import org.polarsys.eplmp.core.util.FileIO;
import org.polarsys.eplmp.core.util.NamingConvention;
import org.polarsys.eplmp.core.util.Tools;
import org.polarsys.eplmp.core.workflow.*;
import org.polarsys.eplmp.server.dao.*;
import org.polarsys.eplmp.server.events.*;
import org.polarsys.eplmp.server.factory.ACLFactory;
import org.polarsys.eplmp.server.validation.AttributesConsistencyUtils;

import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.enterprise.event.Event;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import java.text.ParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@DeclareRoles({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
@Local(IDocumentManagerLocal.class)
@Stateless(name = "DocumentManagerBean")
public class DocumentManagerBean implements IDocumentManagerLocal {

    @Inject
    private EntityManager em;

    @Inject
    private AccountDAO accountDAO;

    @Inject
    private ACLDAO aclDAO;

    @Inject
    private TaskDAO taskDAO;

    @Inject
    private ACLFactory aclFactory;

    @Inject
    private BinaryResourceDAO binaryResourceDAO;

    @Inject
    private ChangeItemDAO changeItemDAO;

    @Inject
    private DocumentDAO documentDAO;

    @Inject
    private DocumentBaselineDAO documentBaselineDAO;

    @Inject
    private DocumentLinkDAO documentLinkDAO;

    @Inject
    private DocumentMasterDAO documentMasterDAO;

    @Inject
    private DocumentMasterTemplateDAO documentMasterTemplateDAO;

    @Inject
    private DocumentRevisionDAO documentRevisionDAO;

    @Inject
    private FolderDAO folderDAO;

    @Inject
    private InstanceAttributeDAO instanceAttributeDAO;

    @Inject
    private LOVDAO lovDAO;

    @Inject
    private PartRevisionDAO partRevisionDAO;

    @Inject
    private RoleDAO roleDAO;

    @Inject
    private SharedEntityDAO sharedEntityDAO;

    @Inject
    private SubscriptionDAO subscriptionDAO;

    @Inject
    private TagDAO tagDAO;

    @Inject
    private UserDAO userDAO;

    @Inject
    private UserGroupDAO userGroupDAO;

    @Inject
    private WorkflowModelDAO workflowModelDAO;

    @Inject
    private IUserManagerLocal userManager;

    @Inject
    private IContextManagerLocal contextManager;

    @Inject
    private INotifierLocal mailer;

    @Inject
    private IGCMSenderLocal gcmNotifier;

    @Inject
    private IIndexerManagerLocal indexerManager;

    @Inject
    private IBinaryStorageManagerLocal storageManager;

    @Inject
    private Event<TagEvent> tagEvent;

    @Inject
    private Event<DocumentRevisionEvent> documentRevisionEvent;

    private static final Logger LOGGER = Logger.getLogger(DocumentManagerBean.class.getName());

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public BinaryResource saveFileInTemplate(DocumentMasterTemplateKey pDocMTemplateKey, String pName, long pSize) throws WorkspaceNotFoundException, NotAllowedException, DocumentMasterTemplateNotFoundException, FileAlreadyExistsException, UserNotFoundException, UserNotActiveException, CreationException, AccessRightException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(pDocMTemplateKey.getWorkspaceId());


        checkNameFileValidity(pName);

        DocumentMasterTemplate template = documentMasterTemplateDAO.loadDocMTemplate(pDocMTemplateKey);

        checkDocumentTemplateWriteAccess(template, user);

        BinaryResource binaryResource = null;
        String fullName = template.getWorkspaceId() + "/document-templates/" + template.getId() + "/" + pName;

        for (BinaryResource bin : template.getAttachedFiles()) {
            if (bin.getFullName().equals(fullName)) {
                binaryResource = bin;
                break;
            }
        }

        if (binaryResource == null) {
            binaryResource = new BinaryResource(fullName, pSize, new Date());
            binaryResourceDAO.createBinaryResource(binaryResource);
            template.addFile(binaryResource);
        } else {
            binaryResource.setContentLength(pSize);
            binaryResource.setLastModified(new Date());
        }
        return binaryResource;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public BinaryResource saveFileInDocument(DocumentIterationKey pDocPK, String pName, long pSize) throws WorkspaceNotFoundException, NotAllowedException, DocumentRevisionNotFoundException, FileAlreadyExistsException, UserNotFoundException, UserNotActiveException, CreationException, AccessRightException, WorkspaceNotEnabledException {
        User user = checkDocumentRevisionWriteAccess(new DocumentRevisionKey(pDocPK.getWorkspaceId(), pDocPK.getDocumentMasterId(), pDocPK.getDocumentRevisionVersion()));

        checkNameFileValidity(pName);

        DocumentRevision docR = documentRevisionDAO.loadDocR(new DocumentRevisionKey(pDocPK.getWorkspaceId(), pDocPK.getDocumentMasterId(), pDocPK.getDocumentRevisionVersion()));
        DocumentIteration document = docR.getIteration(pDocPK.getIteration());

        if (isCheckoutByUser(user, docR) && docR.getLastIteration().equals(document)) {
            BinaryResource binaryResource = null;

            String fullName = docR.getWorkspaceId() + "/documents/" + FileIO.encode(docR.getId()) + "/" + docR.getVersion() + "/" + document.getIteration() + "/" + pName;

            for (BinaryResource bin : document.getAttachedFiles()) {
                if (bin.getFullName().equals(fullName)) {
                    binaryResource = bin;
                    break;
                }
            }
            if (binaryResource == null) {
                binaryResource = new BinaryResource(fullName, pSize, new Date());
                binaryResourceDAO.createBinaryResource(binaryResource);
                document.addFile(binaryResource);
            } else {
                binaryResource.setContentLength(pSize);
                binaryResource.setLastModified(new Date());
            }
            return binaryResource;
        } else {
            throw new NotAllowedException("NotAllowedException4");
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void setDocumentPublicShared(DocumentRevisionKey pDocRPK, boolean isPublicShared) throws AccessRightException, NotAllowedException, WorkspaceNotFoundException, UserNotFoundException, DocumentRevisionNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        DocumentRevision documentRevision = getDocumentRevision(pDocRPK);
        documentRevision.setPublicShared(isPublicShared);
    }

    @LogDocument
    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public BinaryResource getBinaryResource(String pFullName) throws WorkspaceNotFoundException, NotAllowedException, FileNotFoundException, UserNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(BinaryResource.parseWorkspaceId(pFullName));

        BinaryResource binaryResource = binaryResourceDAO.loadBinaryResource(pFullName);

        DocumentIteration document = binaryResourceDAO.getDocumentHolder(binaryResource);
        if (document != null) {
            DocumentRevision docR = document.getDocumentRevision();

            if (user.isAdministrator()) {
                return binaryResource;
            }

            if (isACLGrantReadAccess(user, docR)) {
                if ((isInAnotherUserHomeFolder(user, docR) || isCheckoutByAnotherUser(user, docR)) && docR.getLastIteration().equals(document)) {
                    throw new NotAllowedException("NotAllowedException34");
                } else {
                    return binaryResource;
                }
            } else {
                throw new AccessRightException(user);
            }
        } else {
            throw new FileNotFoundException(pFullName);
        }
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public BinaryResource getTemplateBinaryResource(String pFullName) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, FileNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(BinaryResource.parseWorkspaceId(pFullName));
        return binaryResourceDAO.loadBinaryResource(pFullName);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public String[] getFolders(String pCompletePath) throws WorkspaceNotFoundException, FolderNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(Folder.parseWorkspaceId(pCompletePath));

        Folder folder = em.find(Folder.class, pCompletePath);
        if (folder == null) {
            throw new FolderNotFoundException(pCompletePath);
        }

        Folder[] subFolders = folderDAO.getSubFolders(pCompletePath);
        String[] shortNames = new String[subFolders.length];
        int i = 0;
        for (Folder f : subFolders) {
            shortNames[i++] = f.getShortName();
        }
        return shortNames;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision[] findDocumentRevisionsByFolder(String pCompletePath) throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        String workspaceId = Folder.parseWorkspaceId(pCompletePath);
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        List<DocumentRevision> docRs = documentRevisionDAO.findDocRsByFolder(pCompletePath);
        ListIterator<DocumentRevision> ite = docRs.listIterator();
        while (ite.hasNext()) {
            DocumentRevision docR = ite.next();
            if (!hasDocumentRevisionReadAccess(user, docR)) {
                ite.remove();
            } else if (isCheckoutByAnotherUser(user, docR)) {
                em.detach(docR);
                docR.removeLastIteration();
            }
        }
        return docRs.toArray(new DocumentRevision[docRs.size()]);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision[] findDocumentRevisionsByTag(TagKey pKey) throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        String workspaceId = pKey.getWorkspace();
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        List<DocumentRevision> docRs = documentRevisionDAO.findDocRsByTag(new Tag(user.getWorkspace(), pKey.getLabel()));
        ListIterator<DocumentRevision> ite = docRs.listIterator();
        while (ite.hasNext()) {
            DocumentRevision docR = ite.next();
            if (!hasDocumentRevisionReadAccess(user, docR)) {
                ite.remove();
            } else if (isCheckoutByAnotherUser(user, docR)) {
                em.detach(docR);
                docR.removeLastIteration();
            }
        }
        return docRs.toArray(new DocumentRevision[docRs.size()]);
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public DocumentRevision getDocumentRevision(DocumentRevisionKey pDocRPK) throws WorkspaceNotFoundException, DocumentRevisionNotFoundException, NotAllowedException, UserNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(pDocRPK.getDocumentMaster().getWorkspace());

        DocumentRevision docR = documentRevisionDAO.loadDocR(pDocRPK);
        if (isAnotherUserHomeFolder(user, docR.getLocation())) {
            throw new NotAllowedException("NotAllowedException5");
        }

        if (hasDocumentRevisionReadAccess(user, docR)) {
            if (isCheckoutByAnotherUser(user, docR)) {
                em.detach(docR);
                docR.removeLastIteration();
            }
            return docR;

        } else {
            throw new AccessRightException(user);
        }

    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public DocumentIteration findDocumentIterationByBinaryResource(BinaryResource pBinaryResource) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(pBinaryResource.getWorkspaceId());
        return documentRevisionDAO.findDocumentIterationByBinaryResource(pBinaryResource);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void updateDocumentACL(String pWorkspaceId, DocumentRevisionKey docKey, Map<String, String> pACLUserEntries, Map<String, String> pACLUserGroupEntries) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, DocumentRevisionNotFoundException, AccessRightException, NotAllowedException, WorkspaceNotEnabledException {
        User user = checkDocumentRevisionWriteAccess(docKey);


        DocumentRevision docR = documentRevisionDAO.loadDocR(docKey);
        if (user.isAdministrator() || isAuthor(user, docR)) {

            if (docR.getACL() == null) {
                ACL acl = aclFactory.createACL(pWorkspaceId, pACLUserEntries, pACLUserGroupEntries);
                docR.setACL(acl);

            } else {
                aclFactory.updateACL(pWorkspaceId, docR.getACL(), pACLUserEntries, pACLUserGroupEntries);
            }

        } else {
            throw new AccessRightException(user);
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void updateACLForDocumentMasterTemplate(String pWorkspaceId, String pDocMTemplateId, Map<String, String> userEntries, Map<String, String> userGroupEntries) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, AccessRightException, NotAllowedException, DocumentMasterTemplateNotFoundException, WorkspaceNotEnabledException {

        // Check the read access to the workspace
        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);
        // Load the documentTemplateModel
        DocumentMasterTemplate docTemplate = documentMasterTemplateDAO.loadDocMTemplate(new DocumentMasterTemplateKey(user.getWorkspaceId(), pDocMTemplateId));
        // Check the access to the documentTemplate
        checkDocumentTemplateWriteAccess(docTemplate, user);
        if (docTemplate.getAcl() == null) {
            ACL acl = aclFactory.createACL(pWorkspaceId, userEntries, userGroupEntries);
            docTemplate.setAcl(acl);
        } else {
            aclFactory.updateACL(pWorkspaceId, docTemplate.getAcl(), userEntries, userGroupEntries);
        }
    }


    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void removeACLFromDocumentRevision(DocumentRevisionKey documentRevisionKey) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, DocumentRevisionNotFoundException, AccessRightException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(documentRevisionKey.getDocumentMaster().getWorkspace());


        DocumentRevision docR = documentRevisionDAO.getDocRRef(documentRevisionKey);

        if (user.isAdministrator() || isAuthor(user, docR)) {
            ACL acl = docR.getACL();
            if (acl != null) {
                aclDAO.removeACLEntries(acl);
                docR.setACL(null);
            }
        } else {
            throw new AccessRightException(user);
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void removeACLFromDocumentMasterTemplate(String pWorkspaceId, String documentTemplateId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, AccessRightException, DocumentMasterTemplateNotFoundException, WorkspaceNotEnabledException {

        // Check the read access to the workspace
        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);
        // Load the documentTemplateModel
        DocumentMasterTemplate docTemplate = documentMasterTemplateDAO.loadDocMTemplate(new DocumentMasterTemplateKey(user.getWorkspaceId(), documentTemplateId));

        // Check the access to the workflow
        checkDocumentTemplateWriteAccess(docTemplate, user);

        ACL acl = docTemplate.getAcl();
        if (acl != null) {
            aclDAO.removeACLEntries(acl);
            docTemplate.setAcl(null);
        }

    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision[] getAllDocumentsInWorkspace(String workspaceId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        List<DocumentRevision> docRs = documentRevisionDAO.getAllDocumentRevisions(workspaceId);
        List<DocumentRevision> documentRevisions = new ArrayList<>();
        for (DocumentRevision docR : docRs) {
            if (isCheckoutByAnotherUser(user, docR)) {
                em.detach(docR);
                docR.removeLastIteration();
            }
            documentRevisions.add(docR);
        }
        return documentRevisions.toArray(new DocumentRevision[documentRevisions.size()]);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision[] getFilteredDocumentsInWorkspace(String workspaceId, int start, int pMaxResults) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        List<DocumentRevision> docRs = documentRevisionDAO.getDocumentRevisionsFiltered(user, workspaceId, start, pMaxResults);
        List<DocumentRevision> documentRevisions = new ArrayList<>();
        for (DocumentRevision docR : docRs) {
            if (isCheckoutByAnotherUser(user, docR)) {
                em.detach(docR);
                docR.removeLastIteration();
            }
            documentRevisions.add(docR);
        }
        return documentRevisions.toArray(new DocumentRevision[documentRevisions.size()]);
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
    @Override
    public int getDocumentsInWorkspaceCount(String workspaceId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException, AccountNotFoundException {

        int count;

        if (contextManager.isCallerInRole(UserGroupMapping.ADMIN_ROLE_ID)) {
            count = documentRevisionDAO.getTotalNumberOfDocuments(workspaceId);
        } else {
            User user = userManager.checkWorkspaceReadAccess(workspaceId);
            if (user.isAdministrator()) {
                count = documentRevisionDAO.getTotalNumberOfDocuments(workspaceId);
            } else {
                count = documentRevisionDAO.getDocumentRevisionsCountFiltered(user, workspaceId);
            }
        }

        return count;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision[] getCheckedOutDocumentRevisions(String pWorkspaceId) throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);
        List<DocumentRevision> docRs = documentRevisionDAO.findCheckedOutDocRs(user);
        return docRs.toArray(new DocumentRevision[docRs.size()]);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public Task[] getTasks(String pWorkspaceId) throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);
        return taskDAO.findTasks(user);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevisionKey[] getIterationChangeEventSubscriptions(String pWorkspaceId) throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);
        return subscriptionDAO.getIterationChangeEventSubscriptions(user);
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public DocumentRevisionKey[] getStateChangeEventSubscriptions(String pWorkspaceId) throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);
        return subscriptionDAO.getStateChangeEventSubscriptions(user);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public boolean isUserStateChangeEventSubscribedForGivenDocument(String pWorkspaceId, DocumentRevision docR) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);
        return subscriptionDAO.isUserStateChangeEventSubscribedForGivenDocument(user, docR);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public boolean isUserIterationChangeEventSubscribedForGivenDocument(String pWorkspaceId, DocumentRevision docR) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);
        return subscriptionDAO.isUserIterationChangeEventSubscribedForGivenDocument(user, docR);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision[] getDocumentRevisionsWithReferenceOrTitle(String pWorkspaceId, String search, int maxResults) throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(pWorkspaceId);
        List<DocumentRevision> docRs = documentRevisionDAO.findDocsRevisionsWithReferenceOrTitleLike(pWorkspaceId, search, maxResults);
        return docRs.toArray(new DocumentRevision[docRs.size()]);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public String generateId(String pWorkspaceId, String pDocMTemplateId) throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, DocumentMasterTemplateNotFoundException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);
        DocumentMasterTemplate template = documentMasterTemplateDAO.loadDocMTemplate(new DocumentMasterTemplateKey(user.getWorkspaceId(), pDocMTemplateId));

        String newId = null;
        try {
            String latestId = documentRevisionDAO.findLatestDocMId(pWorkspaceId, template.getDocumentType());
            String inputMask = template.getMask();
            String convertedMask = Tools.convertMask(inputMask);
            newId = Tools.increaseId(latestId, convertedMask);
        } catch (NoResultException ex) {
            LOGGER.log(Level.FINER, null, ex);
            //may happen when no document of the specified type has been created
        } catch (ParseException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            //may happen when a different mask has been used for the same document type
        }
        return newId;

    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision[] searchDocumentRevisions(DocumentSearchQuery pQuery, int from, int size)
            throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException, AccountNotFoundException, NotAllowedException, IndexerRequestException, IndexerNotAvailableException {
        User user = userManager.checkWorkspaceReadAccess(pQuery.getWorkspaceId());
        List<DocumentRevision> fetchedDocRs = indexerManager.searchDocumentRevisions(pQuery, from, size);
        List<DocumentRevision> docList = new ArrayList<>();

        if (!fetchedDocRs.isEmpty()) {
            for (DocumentRevision docR : fetchedDocRs) {
                DocumentRevision filteredDocR = applyDocumentRevisionReadAccess(user, docR);
                if (filteredDocR != null) {
                    docList.add(filteredDocR);
                }
            }

            return docList.toArray(new DocumentRevision[docList.size()]);
        }
        return new DocumentRevision[0];
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentMasterTemplate[] getDocumentMasterTemplates(String pWorkspaceId) throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);
        List<DocumentMasterTemplate> templates = documentMasterTemplateDAO.findAllDocMTemplates(pWorkspaceId);

        templates.removeIf(template -> !hasDocumentMasterTemplateReadAccess(template, user));

        return templates.toArray(new DocumentMasterTemplate[templates.size()]);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentMasterTemplate getDocumentMasterTemplate(DocumentMasterTemplateKey pKey)
            throws WorkspaceNotFoundException, DocumentMasterTemplateNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(pKey.getWorkspaceId());
        return documentMasterTemplateDAO.loadDocMTemplate(pKey);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentMasterTemplate updateDocumentMasterTemplate(DocumentMasterTemplateKey pKey, String pDocumentType, String pWorkflowModelId, String pMask, List<InstanceAttributeTemplate> pAttributeTemplates, String[] lovNames, boolean idGenerated, boolean attributesLocked) throws WorkspaceNotFoundException, AccessRightException, DocumentMasterTemplateNotFoundException, UserNotFoundException, WorkflowModelNotFoundException, UserNotActiveException, ListOfValuesNotFoundException, NotAllowedException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(pKey.getWorkspaceId());

        DocumentMasterTemplate template = documentMasterTemplateDAO.loadDocMTemplate(pKey);

        checkDocumentTemplateWriteAccess(template, user);

        Date now = new Date();
        template.setModificationDate(now);
        template.setAuthor(user);
        template.setDocumentType(pDocumentType);
        template.setMask(pMask);
        template.setIdGenerated(idGenerated);
        template.setAttributesLocked(attributesLocked);

        List<InstanceAttributeTemplate> attrs = new ArrayList<>();
        for (int i = 0; i < pAttributeTemplates.size(); i++) {
            pAttributeTemplates.get(i).setLocked(attributesLocked);
            attrs.add(pAttributeTemplates.get(i));
            if (pAttributeTemplates.get(i) instanceof ListOfValuesAttributeTemplate) {
                ListOfValuesAttributeTemplate lovAttr = (ListOfValuesAttributeTemplate) pAttributeTemplates.get(i);
                ListOfValuesKey lovKey = new ListOfValuesKey(user.getWorkspaceId(), lovNames[i]);
                lovAttr.setLov(lovDAO.loadLOV(lovKey));
            }
        }

        if (!AttributesConsistencyUtils.isTemplateAttributesValid(attrs, attributesLocked)) {
            throw new NotAllowedException("NotAllowedException59");
        }
        template.setAttributeTemplates(attrs);

        WorkflowModel workflowModel = null;
        if (pWorkflowModelId != null) {
            workflowModel = workflowModelDAO.loadWorkflowModel(new WorkflowModelKey(user.getWorkspaceId(), pWorkflowModelId));
        }
        template.setWorkflowModel(workflowModel);

        return template;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void deleteTag(TagKey pKey) throws WorkspaceNotFoundException, AccessRightException, TagNotFoundException, UserNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceWriteAccess(pKey.getWorkspace());
        Tag tagToRemove = new Tag(user.getWorkspace(), pKey.getLabel());
        List<DocumentRevision> docRs = documentRevisionDAO.findDocRsByTag(tagToRemove);
        for (DocumentRevision docR : docRs) {
            docR.getTags().remove(tagToRemove);
        }
        List<ChangeItem> changeItems = changeItemDAO.findChangeItemByTag(pKey.getWorkspace(), tagToRemove);
        for (ChangeItem changeItem : changeItems) {
            changeItem.getTags().remove(tagToRemove);
        }

        List<PartRevision> partRevisions = partRevisionDAO.findPartByTag(tagToRemove);
        for (PartRevision partRevision : partRevisions) {
            partRevision.getTags().remove(tagToRemove);
        }

        tagEvent.select(new AnnotationLiteral<Removed>() {
        }).fire(new TagEvent(tagToRemove));

        tagDAO.removeTag(pKey);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void createTag(String pWorkspaceId, String pLabel) throws WorkspaceNotFoundException, AccessRightException, CreationException, TagAlreadyExistsException, UserNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceWriteAccess(pWorkspaceId);
        Tag tag = new Tag(user.getWorkspace(), pLabel);
        tagDAO.createTag(tag);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision createDocumentMaster(String pParentFolder, String pDocMId, String pTitle, String pDescription, String pDocMTemplateId, String pWorkflowModelId, Map<String, String> aclUserEntries, Map<String, String> aclGroupEntries, Map<String, Collection<String>> userRoleMapping, Map<String, Collection<String>> groupRoleMapping) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, NotAllowedException, FolderNotFoundException, DocumentMasterTemplateNotFoundException, FileAlreadyExistsException, CreationException, DocumentRevisionAlreadyExistsException, RoleNotFoundException, WorkflowModelNotFoundException, DocumentMasterAlreadyExistsException, UserGroupNotFoundException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceWriteAccess(Folder.parseWorkspaceId(pParentFolder));

        checkDocumentIdValidity(pDocMId);

        Folder folder = folderDAO.loadFolder(pParentFolder);
        checkFolderWritingRight(user, folder);

        DocumentMaster docM;
        DocumentRevision docR;
        DocumentIteration newDoc;

        if (pDocMTemplateId == null) {
            docM = new DocumentMaster(user.getWorkspace(), pDocMId, user);
            //specify an empty type instead of null
            //so the search will find it with the % character
            docM.setType("");
            documentMasterDAO.createDocM(docM);
            docR = docM.createNextRevision(user);
            newDoc = docR.createNextIteration(user);
        } else {
            DocumentMasterTemplate template = documentMasterTemplateDAO.loadDocMTemplate(new DocumentMasterTemplateKey(user.getWorkspaceId(), pDocMTemplateId));

            if (!Tools.validateMask(template.getMask(), pDocMId)) {
                throw new NotAllowedException("NotAllowedException42");
            }

            docM = new DocumentMaster(user.getWorkspace(), pDocMId, user);
            docM.setType(template.getDocumentType());
            docM.setAttributesLocked(template.isAttributesLocked());

            documentMasterDAO.createDocM(docM);
            docR = docM.createNextRevision(user);
            newDoc = docR.createNextIteration(user);


            List<InstanceAttribute> attrs = new ArrayList<>();
            for (InstanceAttributeTemplate attrTemplate : template.getAttributeTemplates()) {
                InstanceAttribute attr = attrTemplate.createInstanceAttribute();
                attrs.add(attr);
            }
            newDoc.setInstanceAttributes(attrs);

            String encodedDocMId = FileIO.encode(docM.getId());
            for (BinaryResource sourceFile : template.getAttachedFiles()) {
                String fileName = sourceFile.getName();
                long length = sourceFile.getContentLength();
                Date lastModified = sourceFile.getLastModified();
                String fullName = docM.getWorkspaceId() + "/documents/" + encodedDocMId + "/A/1/" + fileName;
                BinaryResource targetFile = new BinaryResource(fullName, length, lastModified);
                binaryResourceDAO.createBinaryResource(targetFile);

                newDoc.addFile(targetFile);
                try {
                    storageManager.copyData(sourceFile, targetFile);
                } catch (StorageException e) {
                    LOGGER.log(Level.INFO, null, e);
                }
            }
        }

        Collection<Task> runningTasks = null;
        if (pWorkflowModelId != null) {

            Map<Role, Collection<User>> roleUserMap = new HashMap<>();
            for (Map.Entry<String, Collection<String>> pair : userRoleMapping.entrySet()) {
                String roleName = pair.getKey();
                Collection<String> userLogins = pair.getValue();
                Role role = roleDAO.loadRole(new RoleKey(Folder.parseWorkspaceId(pParentFolder), roleName));
                Set<User> users = new HashSet<>();
                roleUserMap.put(role, users);
                for (String login : userLogins) {
                    User u = userDAO.loadUser(new UserKey(Folder.parseWorkspaceId(pParentFolder), login));
                    users.add(u);
                }
            }

            Map<Role, Collection<UserGroup>> roleGroupMap = new HashMap<>();
            for (Map.Entry<String, Collection<String>> pair : groupRoleMapping.entrySet()) {
                String roleName = pair.getKey();
                Collection<String> groupIds = pair.getValue();
                Role role = roleDAO.loadRole(new RoleKey(Folder.parseWorkspaceId(pParentFolder), roleName));
                Set<UserGroup> groups = new HashSet<>();
                roleGroupMap.put(role, groups);
                for (String groupId : groupIds) {
                    UserGroup g = userGroupDAO.loadUserGroup(new UserGroupKey(Folder.parseWorkspaceId(pParentFolder), groupId));
                    groups.add(g);
                }
            }

            WorkflowModel workflowModel = workflowModelDAO.loadWorkflowModel(new WorkflowModelKey(user.getWorkspaceId(), pWorkflowModelId));
            Workflow workflow = workflowModel.createWorkflow(roleUserMap, roleGroupMap);
            docR.setWorkflow(workflow);

            for (Task task : workflow.getTasks()) {
                if (!task.hasPotentialWorker()) {
                    throw new NotAllowedException("NotAllowedException56");
                }
            }

            runningTasks = workflow.getRunningTasks();
            runningTasks.forEach(Task::start);
        }

        docR.setTitle(pTitle);
        docR.setDescription(pDescription);

        if (aclUserEntries != null && !aclUserEntries.isEmpty() || aclGroupEntries != null && !aclGroupEntries.isEmpty()) {
            ACL acl = aclFactory.createACL(user.getWorkspace().getId(), aclUserEntries, aclGroupEntries);
            docR.setACL(acl);
        }

        Date now = new Date();
        docM.setCreationDate(now);
        docR.setCreationDate(now);
        docR.setLocation(folder);
        docR.setCheckOutUser(user);
        docR.setCheckOutDate(now);
        newDoc.setCreationDate(now);
        documentRevisionDAO.createDocR(docR);

        if (runningTasks != null) {
            mailer.sendApproval(docR.getWorkspaceId(), runningTasks, docR);
        }
        return docR;
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
    @Override
    public DocumentRevision[] getAllCheckedOutDocumentRevisions(String pWorkspaceId) throws WorkspaceNotFoundException, AccountNotFoundException, AccessRightException {
        userManager.checkAdmin(pWorkspaceId);
        List<DocumentRevision> docRs = documentRevisionDAO.findAllCheckedOutDocRevisions(pWorkspaceId);
        return docRs.toArray(new DocumentRevision[docRs.size()]);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentMasterTemplate createDocumentMasterTemplate(String pWorkspaceId, String pId, String pDocumentType, String pWorkflowModelId,
                                                               String pMask, List<InstanceAttributeTemplate> pAttributeTemplates, String[] lovNames, boolean idGenerated, boolean attributesLocked) throws WorkspaceNotFoundException, AccessRightException, DocumentMasterTemplateAlreadyExistsException, UserNotFoundException, NotAllowedException, CreationException, WorkflowModelNotFoundException, ListOfValuesNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceWriteAccess(pWorkspaceId);

        checkNameValidity(pId);

        //Check pMask
        if (pMask != null && !pMask.isEmpty() && !NamingConvention.correctNameMask(pMask)) {
            throw new NotAllowedException("MaskCreationException");
        }

        DocumentMasterTemplate template = new DocumentMasterTemplate(user.getWorkspace(), pId, user, pDocumentType, pMask);
        Date now = new Date();
        template.setCreationDate(now);
        template.setIdGenerated(idGenerated);
        template.setAttributesLocked(attributesLocked);

        List<InstanceAttributeTemplate> attrs = new ArrayList<>();
        for (int i = 0; i < pAttributeTemplates.size(); i++) {
            attrs.add(pAttributeTemplates.get(i));
            if (pAttributeTemplates.get(i) instanceof ListOfValuesAttributeTemplate) {
                ListOfValuesAttributeTemplate lovAttr = (ListOfValuesAttributeTemplate) pAttributeTemplates.get(i);
                ListOfValuesKey lovKey = new ListOfValuesKey(user.getWorkspaceId(), lovNames[i]);
                lovAttr.setLov(lovDAO.loadLOV(lovKey));
            }
        }
        if (!AttributesConsistencyUtils.isTemplateAttributesValid(attrs, attributesLocked)) {
            throw new NotAllowedException("NotAllowedException59");
        }
        template.setAttributeTemplates(attrs);

        if (pWorkflowModelId != null) {
            WorkflowModel workflowModel = workflowModelDAO.loadWorkflowModel(new WorkflowModelKey(user.getWorkspaceId(), pWorkflowModelId));
            template.setWorkflowModel(workflowModel);
        }

        documentMasterTemplateDAO.createDocMTemplate(template);
        return template;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision moveDocumentRevision(String pParentFolder, DocumentRevisionKey pDocRPK) throws WorkspaceNotFoundException, DocumentRevisionNotFoundException, NotAllowedException, AccessRightException, FolderNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        //TODO security check if both parameter belong to the same workspace
        User user = checkDocumentRevisionWriteAccess(pDocRPK);


        Folder newLocation = folderDAO.loadFolder(pParentFolder);
        checkFolderWritingRight(user, newLocation);
        DocumentRevision docR = documentRevisionDAO.loadDocR(pDocRPK);

        // You cannot move a document to someone else's home directory
        if (isInAnotherUserHomeFolder(user, docR)) {
            throw new NotAllowedException("NotAllowedException6");
        } else {

            docR.setLocation(newLocation);

            if (isCheckoutByAnotherUser(user, docR)) {
                // won't persist newLocation if not flushing
                em.flush();
                em.detach(docR);
                docR.removeLastIteration();
            }

            DocumentIteration lastCheckedInIteration = docR.getLastCheckedInIteration();

            if (null != lastCheckedInIteration) {
                indexerManager.indexDocumentIteration(lastCheckedInIteration);
            }

            return docR;
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public Folder createFolder(String pParentFolder, String pFolder)
            throws WorkspaceNotFoundException, NotAllowedException, AccessRightException, FolderNotFoundException, FolderAlreadyExistsException, UserNotFoundException, CreationException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceWriteAccess(Folder.parseWorkspaceId(pParentFolder));

        checkNameValidity(pFolder);

        Folder folder = folderDAO.loadFolder(pParentFolder);
        checkFoldersStructureChangeRight(user);
        checkFolderWritingRight(user, folder);
        Folder newFolder = new Folder(pParentFolder, pFolder);
        folderDAO.createFolder(newFolder);
        return newFolder;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision checkOutDocument(DocumentRevisionKey pDocRPK) throws WorkspaceNotFoundException, NotAllowedException, DocumentRevisionNotFoundException, AccessRightException, FileAlreadyExistsException, UserNotFoundException, UserNotActiveException, CreationException, WorkspaceNotEnabledException {
        User user = checkDocumentRevisionWriteAccess(pDocRPK);


        DocumentRevision docR = documentRevisionDAO.loadDocR(pDocRPK);
        if (!docR.isLastRevision()) {
            throw new NotAllowedException("NotAllowedException72");
        }

        //Check access rights on docR
        if (docR.isCheckedOut()) {
            throw new NotAllowedException("NotAllowedException37");
        }

        DocumentIteration beforeLastDocument = docR.getLastIteration();

        DocumentIteration newDoc = docR.createNextIteration(user);
        //We persist the doc as a workaround for a bug which was introduced
        //since glassfish 3 that set the DTYPE to null in the instance attribute table
        em.persist(newDoc);
        Date now = new Date();
        newDoc.setCreationDate(now);
        docR.setCheckOutUser(user);
        docR.setCheckOutDate(now);

        if (beforeLastDocument != null) {
            String encodedDocRId = FileIO.encode(docR.getId());
            for (BinaryResource sourceFile : beforeLastDocument.getAttachedFiles()) {
                String fileName = sourceFile.getName();
                long length = sourceFile.getContentLength();
                Date lastModified = sourceFile.getLastModified();
                String fullName = docR.getWorkspaceId() + "/documents/" + encodedDocRId + "/" + docR.getVersion() + "/" + newDoc.getIteration() + "/" + fileName;
                BinaryResource targetFile = new BinaryResource(fullName, length, lastModified);
                binaryResourceDAO.createBinaryResource(targetFile);
                newDoc.addFile(targetFile);
            }

            Set<DocumentLink> links = new HashSet<>();
            for (DocumentLink link : beforeLastDocument.getLinkedDocuments()) {
                DocumentLink newLink = link.clone();
                links.add(newLink);
            }
            newDoc.setLinkedDocuments(links);

            List<InstanceAttribute> attrs = new ArrayList<>();
            for (InstanceAttribute attr : beforeLastDocument.getInstanceAttributes()) {
                InstanceAttribute newAttr = attr.clone();
                //Workaround for the NULL DTYPE bug
                instanceAttributeDAO.createAttribute(newAttr);
                attrs.add(newAttr);
            }
            newDoc.setInstanceAttributes(attrs);
        }

        return docR;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision saveTags(DocumentRevisionKey pDocRPK, String[] pTags) throws WorkspaceNotFoundException, NotAllowedException, DocumentRevisionNotFoundException, AccessRightException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        User user = checkDocumentRevisionWriteAccess(pDocRPK);


        DocumentRevision docR = documentRevisionDAO.loadDocR(pDocRPK);

        Set<Tag> tags = new HashSet<>();
        if (pTags != null) {
            for (String label : pTags) {
                tags.add(new Tag(user.getWorkspace(), label));
            }

            List<Tag> existingTags = Arrays.asList(tagDAO.findAllTags(user.getWorkspaceId()));

            Set<Tag> tagsToCreate = new HashSet<>(tags);
            tagsToCreate.removeAll(existingTags);

            for (Tag t : tagsToCreate) {
                try {
                    tagDAO.createTag(t, true);
                } catch (CreationException | TagAlreadyExistsException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }

            Set<Tag> removedTags = new HashSet<>(docR.getTags());
            removedTags.removeAll(tags);
            Set<Tag> addedTags = docR.setTags(tags);

            for (Tag tag : removedTags) {
                tagEvent.select(new AnnotationLiteral<Untagged>() {
                }).fire(new TagEvent(tag, docR));
            }
            for (Tag tag : addedTags) {
                tagEvent.select(new AnnotationLiteral<Tagged>() {
                }).fire(new TagEvent(tag, docR));
            }
            if (isCheckoutByAnotherUser(user, docR)) {
                em.flush();
                em.detach(docR);
                docR.removeLastIteration();
            }

            docR.getDocumentIterations().forEach(indexerManager::indexDocumentIteration);
        } else {
            throw new IllegalArgumentException("pTags argument must not be null");
        }
        return docR;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision removeTag(DocumentRevisionKey pDocRPK, String pTag)
            throws UserNotFoundException, WorkspaceNotFoundException, UserNotActiveException, AccessRightException, DocumentRevisionNotFoundException, NotAllowedException, WorkspaceNotEnabledException {

        User user = checkDocumentRevisionWriteAccess(pDocRPK);

        DocumentRevision docR = getDocumentRevision(pDocRPK);
        Tag tagToRemove = new Tag(user.getWorkspace(), pTag);
        docR.getTags().remove(tagToRemove);

        tagEvent.select(new AnnotationLiteral<Untagged>() {
        }).fire(new TagEvent(tagToRemove, docR));

        if (isCheckoutByAnotherUser(user, docR)) {
            em.detach(docR);
            docR.removeLastIteration();
        }

        docR.getDocumentIterations().forEach(indexerManager::indexDocumentIteration);
        return docR;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision undoCheckOutDocument(DocumentRevisionKey pDocRPK) throws WorkspaceNotFoundException, DocumentRevisionNotFoundException, NotAllowedException, UserNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {

        User user = checkDocumentRevisionWriteAccess(pDocRPK);

        DocumentRevision docR = documentRevisionDAO.loadDocR(pDocRPK);
        if (isCheckoutByUser(user, docR)) {
            if (docR.getLastIteration().getIteration() <= 1) {
                throw new NotAllowedException("NotAllowedException27");
            }
            DocumentIteration doc = docR.removeLastIteration();
            documentDAO.removeDoc(doc);
            docR.setCheckOutDate(null);
            docR.setCheckOutUser(null);

            for (BinaryResource file : doc.getAttachedFiles()) {
                try {
                    storageManager.deleteData(file);
                } catch (StorageException e) {
                    LOGGER.log(Level.INFO, null, e);
                }
            }

            return docR;
        } else {
            throw new NotAllowedException("NotAllowedException19");
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision checkInDocument(DocumentRevisionKey pDocRPK) throws WorkspaceNotFoundException, NotAllowedException, DocumentRevisionNotFoundException, AccessRightException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {

        User user = checkDocumentRevisionWriteAccess(pDocRPK);


        DocumentRevision docR = documentRevisionDAO.loadDocR(pDocRPK);

        if (isCheckoutByUser(user, docR)) {
            Collection<User> subscribers = subscriptionDAO.getIterationChangeEventSubscribers(docR);
            GCMAccount[] gcmAccounts = subscriptionDAO.getIterationChangeEventSubscribersGCMAccount(docR);

            docR.setCheckOutDate(null);
            docR.setCheckOutUser(null);

            DocumentIteration lastIteration = docR.getLastIteration();
            lastIteration.setCheckInDate(new Date());

            if (!subscribers.isEmpty()) {
                mailer.sendIterationNotification(docR.getWorkspaceId(), subscribers, docR);
            }

            if (gcmAccounts.length != 0) {
                gcmNotifier.sendIterationNotification(gcmAccounts, docR);
            }

            indexerManager.indexDocumentIteration(lastIteration);

            return docR;
        } else {
            throw new NotAllowedException("NotAllowedException20");
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevisionKey[] deleteFolder(String pCompletePath) throws WorkspaceNotFoundException, NotAllowedException, AccessRightException, UserNotFoundException, FolderNotFoundException, EntityConstraintException, UserNotActiveException, DocumentRevisionNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceWriteAccess(Folder.parseWorkspaceId(pCompletePath));


        Folder folder = folderDAO.loadFolder(pCompletePath);
        checkFoldersStructureChangeRight(user);

        if (isAnotherUserHomeFolder(user, folder) || folder.isRoot() || folder.isHome()) {
            throw new NotAllowedException("NotAllowedException21");

        } else {
            return doFolderDeletion(folder);
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevisionKey[] deleteUserFolder(User pUser) throws WorkspaceNotFoundException, NotAllowedException, AccessRightException, UserNotFoundException, FolderNotFoundException, EntityConstraintException, UserNotActiveException, DocumentRevisionNotFoundException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceWriteAccess(pUser.getWorkspaceId());

        String folderCompletePath = pUser.getWorkspaceId() + "/~" + pUser.getLogin();
        Folder folder = folderDAO.loadFolder(folderCompletePath);

        if (!user.isAdministrator()) {
            throw new NotAllowedException("NotAllowedException21");
        }

        return doFolderDeletion(folder);
    }

    private DocumentRevisionKey[] doFolderDeletion(Folder folder) throws EntityConstraintException, NotAllowedException, WorkspaceNotFoundException, AccessRightException, DocumentRevisionNotFoundException, UserNotActiveException, UserNotFoundException, WorkspaceNotEnabledException {
        List<DocumentRevision> allDocRevision = folderDAO.findDocumentRevisionsInFolder(folder);
        List<DocumentRevisionKey> allDocRevisionKey = new ArrayList<>();

        for (DocumentRevision documentRevision : allDocRevision) {
            DocumentRevisionKey documentRevisionKey = documentRevision.getKey();
            deleteDocumentRevision(documentRevisionKey);
            allDocRevisionKey.add(documentRevisionKey);
        }
        folderDAO.removeFolder(folder);
        return allDocRevisionKey.toArray(new DocumentRevisionKey[allDocRevisionKey.size()]);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevisionKey[] moveFolder(String pCompletePath, String pDestParentFolder, String pDestFolder) throws WorkspaceNotFoundException, NotAllowedException, AccessRightException, UserNotFoundException, FolderNotFoundException, CreationException, FolderAlreadyExistsException, WorkspaceNotEnabledException {
        //TODO security check if both parameter belong to the same workspace
        String workspace = Folder.parseWorkspaceId(pCompletePath);
        User user = userManager.checkWorkspaceWriteAccess(workspace);
        
        Folder folder = folderDAO.loadFolder(pCompletePath);
        checkFoldersStructureChangeRight(user);
        if (isAnotherUserHomeFolder(user, folder) || folder.isRoot() || folder.isHome()) {
            throw new NotAllowedException("NotAllowedException21");
        } else if (!workspace.equals(Folder.parseWorkspaceId(pDestParentFolder))) {
            throw new NotAllowedException("NotAllowedException23");
        } else {
            Folder newFolder = createFolder(pDestParentFolder, pDestFolder);
            List<DocumentRevision> docRs = folderDAO.moveFolder(folder, newFolder);
            DocumentRevisionKey[] pks = new DocumentRevisionKey[docRs.size()];
            int i = 0;

            List<DocumentIteration> lastCheckedInIterations = new ArrayList<>();

            for (DocumentRevision docR : docRs) {
                pks[i++] = docR.getKey();
                DocumentIteration lastCheckedInIteration = docR.getLastCheckedInIteration();
                if (null != lastCheckedInIteration) {
                    lastCheckedInIterations.add(lastCheckedInIteration);
                }
            }

            if (!lastCheckedInIterations.isEmpty()) {
                indexerManager.indexDocumentIterations(lastCheckedInIterations);
            }

            return pks;
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void deleteDocumentRevision(DocumentRevisionKey pDocRPK) throws WorkspaceNotFoundException, NotAllowedException, DocumentRevisionNotFoundException, AccessRightException, UserNotFoundException, UserNotActiveException, EntityConstraintException, WorkspaceNotEnabledException {

        User user = checkDocumentRevisionWriteAccess(pDocRPK);


        DocumentRevision docR = documentRevisionDAO.loadDocR(pDocRPK);
        if (!user.isAdministrator() && isInAnotherUserHomeFolder(user, docR)) {
            throw new NotAllowedException("NotAllowedException22");
        }

        if (documentBaselineDAO.existBaselinedDocument(pDocRPK.getWorkspaceId(), pDocRPK.getDocumentMasterId(), pDocRPK.getVersion())) {
            throw new EntityConstraintException("EntityConstraintException6");
        }

        for (DocumentRevision documentRevision : docR.getDocumentMaster().getDocumentRevisions()) {
            if (!documentLinkDAO.getInverseDocumentsLinks(documentRevision).isEmpty()) {
                throw new EntityConstraintException("EntityConstraintException17");
            }
            if (!documentLinkDAO.getInversePartsLinks(documentRevision).isEmpty()) {

                throw new EntityConstraintException("EntityConstraintException18");
            }
            if (!documentLinkDAO.getInverseProductInstanceIteration(documentRevision).isEmpty()) {
                throw new EntityConstraintException("EntityConstraintException19");
            }
            if (!documentLinkDAO.getInversefindPathData(documentRevision).isEmpty()) {
                throw new EntityConstraintException("EntityConstraintException20");
            }

        }
        if (changeItemDAO.hasChangeItems(pDocRPK)) {
            throw new EntityConstraintException("EntityConstraintException7");
        }

        DocumentMaster documentMaster = docR.getDocumentMaster();
        boolean isLastRevision = documentMaster.getDocumentRevisions().size() == 1;
        if (isLastRevision) {
            documentMasterDAO.removeDocM(documentMaster);
        } else {
            documentRevisionDAO.removeRevision(docR);
        }

        for (DocumentIteration doc : docR.getDocumentIterations()) {
            for (BinaryResource file : doc.getAttachedFiles()) {
                try {
                    storageManager.deleteData(file);
                } catch (StorageException e) {
                    LOGGER.log(Level.INFO, null, e);
                }
                indexerManager.removeDocumentIterationFromIndex(doc);
            }
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void deleteDocumentMasterTemplate(DocumentMasterTemplateKey pKey)
            throws WorkspaceNotFoundException, AccessRightException, DocumentMasterTemplateNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(pKey.getWorkspaceId());

        DocumentMasterTemplate documentMasterTemplate = documentMasterTemplateDAO.loadDocMTemplate(pKey);
        checkDocumentTemplateWriteAccess(documentMasterTemplate, user);

        DocumentMasterTemplate template = documentMasterTemplateDAO.removeDocMTemplate(pKey);

        for (BinaryResource file : template.getAttachedFiles()) {
            try {
                storageManager.deleteData(file);
            } catch (StorageException e) {
                LOGGER.log(Level.INFO, null, e);
            }
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision removeFileFromDocument(String pFullName) throws WorkspaceNotFoundException, DocumentRevisionNotFoundException, NotAllowedException, AccessRightException, FileNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(BinaryResource.parseWorkspaceId(pFullName));

        BinaryResource file = binaryResourceDAO.loadBinaryResource(pFullName);

        DocumentIteration document = binaryResourceDAO.getDocumentHolder(file);
        DocumentRevision docR = document.getDocumentRevision();

        //check access rights on docR
        User user = checkDocumentRevisionWriteAccess(docR.getKey());

        if (isCheckoutByUser(user, docR) && docR.getLastIteration().equals(document)) {
            document.removeFile(file);
            binaryResourceDAO.removeBinaryResource(file);

            try {
                storageManager.deleteData(file);
            } catch (StorageException e) {
                LOGGER.log(Level.INFO, null, e);
            }

            return docR;

        } else {
            throw new NotAllowedException("NotAllowedException24");
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public BinaryResource renameFileInDocument(String pFullName, String pNewName) throws WorkspaceNotFoundException, DocumentRevisionNotFoundException, NotAllowedException, AccessRightException, FileNotFoundException, UserNotFoundException, UserNotActiveException, FileAlreadyExistsException, CreationException, StorageException, WorkspaceNotEnabledException {

        userManager.checkWorkspaceReadAccess(BinaryResource.parseWorkspaceId(pFullName));

        checkNameFileValidity(pNewName);

        BinaryResource file = binaryResourceDAO.loadBinaryResource(pFullName);
        if (binaryResourceDAO.exists(file.getNewFullName(pNewName))) {
            throw new FileAlreadyExistsException(pNewName);
        } else {
            DocumentIteration document = binaryResourceDAO.getDocumentHolder(file);
            DocumentRevision docR = document.getDocumentRevision();

            //check access rights on docR
            User user = checkDocumentRevisionWriteAccess(docR.getKey());

            if (isCheckoutByUser(user, docR) && docR.getLastIteration().equals(document)) {
                storageManager.renameFile(file, pNewName);
                document.removeFile(file);
                binaryResourceDAO.removeBinaryResource(file);
                BinaryResource newFile = new BinaryResource(file.getNewFullName(pNewName), file.getContentLength(), file.getLastModified());
                binaryResourceDAO.createBinaryResource(newFile);
                document.addFile(newFile);
                return newFile;
            } else {
                throw new NotAllowedException("NotAllowedException29");
            }
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentMasterTemplate removeFileFromTemplate(String pFullName) throws WorkspaceNotFoundException, DocumentMasterTemplateNotFoundException, AccessRightException, FileNotFoundException, UserNotFoundException, UserNotActiveException, StorageException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(BinaryResource.parseWorkspaceId(pFullName));

        BinaryResource file = binaryResourceDAO.loadBinaryResource(pFullName);

        DocumentMasterTemplate template = binaryResourceDAO.getDocumentTemplateHolder(file);
        checkDocumentTemplateWriteAccess(template, user);

        template.removeFile(file);
        binaryResourceDAO.removeBinaryResource(file);

        try {
            storageManager.deleteData(file);
        } catch (StorageException e) {
            LOGGER.log(Level.INFO, null, e);
        }

        return template;
    }

    @Override
    public BinaryResource renameFileInTemplate(String pFullName, String pNewName) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, NotAllowedException, FileNotFoundException, AccessRightException, FileAlreadyExistsException, CreationException, StorageException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(BinaryResource.parseWorkspaceId(pFullName));

        checkNameFileValidity(pNewName);

        BinaryResource file = binaryResourceDAO.loadBinaryResource(pFullName);

        if (binaryResourceDAO.exists(file.getNewFullName(pNewName))) {
            throw new FileAlreadyExistsException(pNewName);
        } else {
            DocumentMasterTemplate template = binaryResourceDAO.getDocumentTemplateHolder(file);

            checkDocumentTemplateWriteAccess(template, user);

            storageManager.renameFile(file, pNewName);
            template.removeFile(file);
            binaryResourceDAO.removeBinaryResource(file);

            BinaryResource newFile = new BinaryResource(file.getNewFullName(pNewName), file.getContentLength(), file.getLastModified());
            binaryResourceDAO.createBinaryResource(newFile);
            template.addFile(newFile);
            return newFile;
        }

    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision updateDocument(DocumentIterationKey iKey, String pRevisionNote, List<InstanceAttribute> pAttributes, DocumentRevisionKey[] pLinkKeys, String[] documentLinkComments) throws WorkspaceNotFoundException, NotAllowedException, DocumentRevisionNotFoundException, AccessRightException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        DocumentRevisionKey rKey = new DocumentRevisionKey(iKey.getWorkspaceId(), iKey.getDocumentMasterId(), iKey.getDocumentRevisionVersion());
        User user = checkDocumentRevisionWriteAccess(rKey);

        DocumentRevision docR = documentRevisionDAO.loadDocR(rKey);
        //check access rights on docR ?
        if (isCheckoutByUser(user, docR) && docR.getLastIteration().getKey().equals(iKey)) {
            DocumentIteration doc = docR.getLastIteration();

            if (pLinkKeys != null) {
                Set<DocumentLink> currentLinks = new HashSet<>(doc.getLinkedDocuments());

                for (DocumentLink link : currentLinks) {
                    doc.getLinkedDocuments().remove(link);
                }

                int counter = 0;
                for (DocumentRevisionKey link : pLinkKeys) {
                    if (!link.equals(iKey.getDocumentRevision())) {
                        DocumentLink newLink = new DocumentLink(documentRevisionDAO.loadDocR(link));
                        newLink.setComment(documentLinkComments[counter]);
                        documentLinkDAO.createLink(newLink);
                        doc.getLinkedDocuments().add(newLink);
                        counter++;
                    }
                }
            }

            if (pAttributes != null) {
                List<InstanceAttribute> currentAttrs = doc.getInstanceAttributes();
                boolean valid = AttributesConsistencyUtils.hasValidChange(pAttributes, docR.isAttributesLocked(), currentAttrs);
                if (!valid) {
                    throw new NotAllowedException("NotAllowedException59");
                }
                doc.setInstanceAttributes(pAttributes);
            }

            doc.setRevisionNote(pRevisionNote);
            Date now = new Date();
            doc.setModificationDate(now);
            //doc.setLinkedDocuments(links);
            return docR;

        } else {
            throw new NotAllowedException("NotAllowedException25");
        }

    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision[] createDocumentRevision(DocumentRevisionKey pOriginalDocRPK, String pTitle, String pDescription, String pWorkflowModelId, Map<String, String> aclUserEntries, Map<String, String> aclGroupEntries, Map<String, Collection<String>> userRoleMapping, Map<String, Collection<String>> groupRoleMapping) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, NotAllowedException, DocumentRevisionAlreadyExistsException, CreationException, WorkflowModelNotFoundException, RoleNotFoundException, DocumentRevisionNotFoundException, FileAlreadyExistsException, UserGroupNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceWriteAccess(pOriginalDocRPK.getDocumentMaster().getWorkspace());

        DocumentRevision originalDocR = documentRevisionDAO.loadDocR(pOriginalDocRPK);
        DocumentMaster docM = originalDocR.getDocumentMaster();
        Folder folder = originalDocR.getLocation();
        checkFolderWritingRight(user, folder);

        if (originalDocR.isCheckedOut()) {
            throw new NotAllowedException("NotAllowedException26");
        }

        if (originalDocR.getNumberOfIterations() == 0) {
            throw new NotAllowedException("NotAllowedException27");
        }

        DocumentRevision docR = docM.createNextRevision(user);

        //create the first iteration which is a copy of the last one of the original docR
        //of course we duplicate the iteration only if it exists !
        DocumentIteration lastDoc = originalDocR.getLastIteration();
        DocumentIteration firstIte = docR.createNextIteration(user);
        if (lastDoc != null) {
            String encodedDocRId = FileIO.encode(docR.getId());
            for (BinaryResource sourceFile : lastDoc.getAttachedFiles()) {
                String fileName = sourceFile.getName();
                long length = sourceFile.getContentLength();
                Date lastModified = sourceFile.getLastModified();
                String fullName = docR.getWorkspaceId() + "/documents/" + encodedDocRId + "/" + docR.getVersion() + "/1/" + fileName;
                BinaryResource targetFile = new BinaryResource(fullName, length, lastModified);
                binaryResourceDAO.createBinaryResource(targetFile);
                firstIte.addFile(targetFile);
                try {
                    storageManager.copyData(sourceFile, targetFile);
                } catch (StorageException e) {
                    LOGGER.log(Level.INFO, null, e);
                }
            }

            Set<DocumentLink> links = new HashSet<>();
            for (DocumentLink link : lastDoc.getLinkedDocuments()) {
                DocumentLink newLink = link.clone();
                links.add(newLink);
            }
            firstIte.setLinkedDocuments(links);

            List<InstanceAttribute> attrs = new ArrayList<>();
            for (InstanceAttribute attr : lastDoc.getInstanceAttributes()) {
                InstanceAttribute clonedAttribute = attr.clone();
                attrs.add(clonedAttribute);
            }
            firstIte.setInstanceAttributes(attrs);
        }

        Collection<Task> runningTasks = null;
        if (pWorkflowModelId != null) {

            Map<Role, Collection<User>> roleUserMap = new HashMap<>();
            for (Map.Entry<String, Collection<String>> pair : userRoleMapping.entrySet()) {
                String roleName = pair.getKey();
                Collection<String> userLogins = pair.getValue();
                Role role = roleDAO.loadRole(new RoleKey(pOriginalDocRPK.getDocumentMaster().getWorkspace(), roleName));
                Set<User> users = new HashSet<>();
                roleUserMap.put(role, users);
                for (String login : userLogins) {
                    User u = userDAO.loadUser(new UserKey(pOriginalDocRPK.getDocumentMaster().getWorkspace(), login));
                    users.add(u);
                }
            }

            Map<Role, Collection<UserGroup>> roleGroupMap = new HashMap<>();
            for (Map.Entry<String, Collection<String>> pair : groupRoleMapping.entrySet()) {
                String roleName = pair.getKey();
                Collection<String> groupIds = pair.getValue();
                Role role = roleDAO.loadRole(new RoleKey(pOriginalDocRPK.getDocumentMaster().getWorkspace(), roleName));
                Set<UserGroup> groups = new HashSet<>();
                roleGroupMap.put(role, groups);
                for (String groupId : groupIds) {
                    UserGroup g = userGroupDAO.loadUserGroup(new UserGroupKey(pOriginalDocRPK.getDocumentMaster().getWorkspace(), groupId));
                    groups.add(g);
                }
            }

            WorkflowModel workflowModel = workflowModelDAO.loadWorkflowModel(new WorkflowModelKey(user.getWorkspaceId(), pWorkflowModelId));
            Workflow workflow = workflowModel.createWorkflow(roleUserMap, roleGroupMap);
            docR.setWorkflow(workflow);

            for (Task task : workflow.getTasks()) {
                if (!task.hasPotentialWorker()) {
                    throw new NotAllowedException("NotAllowedException56");
                }
            }

            runningTasks = workflow.getRunningTasks();
            runningTasks.forEach(Task::start);
        }

        docR.setTitle(pTitle);
        docR.setDescription(pDescription);

        if (aclUserEntries != null && !aclUserEntries.isEmpty() || aclGroupEntries != null && !aclGroupEntries.isEmpty()) {
            ACL acl = aclFactory.createACL(docR.getWorkspaceId(), aclUserEntries, aclGroupEntries);
            docR.setACL(acl);
        }

        Date now = new Date();
        docR.setCreationDate(now);
        docR.setLocation(folder);
        docR.setCheckOutUser(user);
        docR.setCheckOutDate(now);
        firstIte.setCreationDate(now);
        firstIte.setModificationDate(now);

        documentRevisionDAO.createDocR(docR);

        if (runningTasks != null) {
            mailer.sendApproval(docR.getWorkspaceId(), runningTasks, docR);
        }

        return new DocumentRevision[]{originalDocR, docR};
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void subscribeToStateChangeEvent(DocumentRevisionKey pDocRPK) throws WorkspaceNotFoundException, NotAllowedException, DocumentRevisionNotFoundException, UserNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {
        User user = checkDocumentRevisionReadAccess(pDocRPK);

        DocumentRevision docR = documentRevisionDAO.loadDocR(pDocRPK);
        if (isAnotherUserHomeFolder(user, docR.getLocation())) {
            throw new NotAllowedException("NotAllowedException30");
        }

        subscriptionDAO.createStateChangeSubscription(new StateChangeSubscription(user, docR));
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void unsubscribeToStateChangeEvent(DocumentRevisionKey pDocRPK) throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, AccessRightException, DocumentRevisionNotFoundException, WorkspaceNotEnabledException {
        User user = checkDocumentRevisionReadAccess(pDocRPK);
        SubscriptionKey key = new SubscriptionKey(user.getWorkspaceId(), user.getLogin(), pDocRPK.getDocumentMaster().getWorkspace(), pDocRPK.getDocumentMaster().getId(), pDocRPK.getVersion());
        subscriptionDAO.removeStateChangeSubscription(key);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void subscribeToIterationChangeEvent(DocumentRevisionKey pDocRPK) throws WorkspaceNotFoundException, NotAllowedException, DocumentRevisionNotFoundException, UserNotFoundException, UserNotActiveException, AccessRightException, WorkspaceNotEnabledException {
        User user = checkDocumentRevisionReadAccess(pDocRPK);

        DocumentRevision docR = documentRevisionDAO.getDocRRef(pDocRPK);
        if (isAnotherUserHomeFolder(user, docR.getLocation())) {
            throw new NotAllowedException("NotAllowedException30");
        }

        subscriptionDAO.createIterationChangeSubscription(new IterationChangeSubscription(user, docR));
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void unsubscribeToIterationChangeEvent(DocumentRevisionKey pDocRPK) throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, AccessRightException, DocumentRevisionNotFoundException, WorkspaceNotEnabledException {
        User user = checkDocumentRevisionReadAccess(pDocRPK);
        SubscriptionKey key = new SubscriptionKey(user.getWorkspaceId(), user.getLogin(), pDocRPK.getDocumentMaster().getWorkspace(), pDocRPK.getDocumentMaster().getId(), pDocRPK.getVersion());
        subscriptionDAO.removeIterationChangeSubscription(key);
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
    @Override
    public String[] getTags(String pWorkspaceId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(pWorkspaceId);
        Tag[] tags = tagDAO.findAllTags(pWorkspaceId);

        String[] labels = new String[tags.length];
        int i = 0;
        for (Tag t : tags) {
            labels[i++] = t.getLabel();
        }
        return labels;
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
    @Override
    public long getDiskUsageForDocumentsInWorkspace(String pWorkspaceId) throws WorkspaceNotFoundException, AccessRightException, AccountNotFoundException {
        userManager.checkAdmin(pWorkspaceId);
        return documentRevisionDAO.getDiskUsageForDocumentsInWorkspace(pWorkspaceId);
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
    @Override
    public long getDiskUsageForDocumentTemplatesInWorkspace(String pWorkspaceId) throws WorkspaceNotFoundException, AccessRightException, AccountNotFoundException {
        userManager.checkAdmin(pWorkspaceId);
        return documentRevisionDAO.getDiskUsageForDocumentTemplatesInWorkspace(pWorkspaceId);
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public SharedDocument createSharedDocument(DocumentRevisionKey pDocRPK, String pPassword, Date pExpireDate) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, DocumentRevisionNotFoundException, UserNotActiveException, NotAllowedException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceWriteAccess(pDocRPK.getDocumentMaster().getWorkspace());
        SharedDocument sharedDocument = new SharedDocument(user.getWorkspace(), user, pExpireDate, pPassword, getDocumentRevision(pDocRPK));
        sharedEntityDAO.createSharedDocument(sharedDocument);
        return sharedDocument;
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public void deleteSharedDocument(SharedEntityKey sharedEntityKey) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, SharedEntityNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceWriteAccess(sharedEntityKey.getWorkspace());
        SharedDocument sharedDocument = sharedEntityDAO.loadSharedDocument(sharedEntityKey.getUuid());
        sharedEntityDAO.deleteSharedDocument(sharedDocument);
    }


    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
    @Override
    public boolean canAccess(DocumentRevisionKey docRKey) throws DocumentRevisionNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(docRKey.getDocumentMaster().getWorkspace());
        return canUserAccess(user, docRKey);
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
    @Override
    public boolean canAccess(DocumentIterationKey docIKey) throws DocumentRevisionNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(docIKey.getWorkspaceId());
        return canUserAccess(user, docIKey);
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
    @Override
    public boolean canUserAccess(User user, DocumentRevisionKey docRKey) throws DocumentRevisionNotFoundException {
        DocumentRevision docRevision = documentRevisionDAO.loadDocR(docRKey);
        return hasDocumentRevisionReadAccess(user, docRevision);
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
    @Override
    public boolean canUserAccess(User user, DocumentIterationKey docIKey) throws DocumentRevisionNotFoundException {
        DocumentRevision docRevision = documentRevisionDAO.loadDocR(docIKey.getDocumentRevision());
        return hasDocumentRevisionReadAccess(user, docRevision) &&
                (docRevision.getLastIteration().getIteration() > docIKey.getIteration() ||
                        !isCheckoutByAnotherUser(user, docRevision));
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    @Override
    public List<DocumentIteration> getInverseDocumentsLink(DocumentRevisionKey docKey) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, DocumentRevisionNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(docKey.getWorkspaceId());

        DocumentRevision documentRevision = documentRevisionDAO.loadDocR(docKey);

        List<DocumentIteration> iterations = documentLinkDAO.getInverseDocumentsLinks(documentRevision);

        ListIterator<DocumentIteration> ite = iterations.listIterator();

        while (ite.hasNext()) {
            DocumentIteration next = ite.next();
            if (!canAccess(next.getKey())) {
                ite.remove();
            }
        }

        return iterations;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision[] getDocumentRevisionsWithAssignedTasksForGivenUser(String pWorkspaceId, String assignedUserLogin)
            throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);
        List<DocumentRevision> docRs = documentRevisionDAO.findDocsWithAssignedTasksForGivenUser(pWorkspaceId, assignedUserLogin);

        ListIterator<DocumentRevision> ite = docRs.listIterator();
        while (ite.hasNext()) {
            DocumentRevision docR = ite.next();
            if (!hasDocumentRevisionReadAccess(user, docR)) {
                ite.remove();
            } else if (isCheckoutByAnotherUser(user, docR)) {
                em.detach(docR);
                docR.removeLastIteration();
            }
        }

        return docRs.toArray(new DocumentRevision[docRs.size()]);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision[] getDocumentRevisionsWithOpenedTasksForGivenUser(String pWorkspaceId, String assignedUserLogin)
            throws WorkspaceNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(pWorkspaceId);
        List<DocumentRevision> docRs = documentRevisionDAO.findDocsWithOpenedTasksForGivenUser(pWorkspaceId, assignedUserLogin);

        ListIterator<DocumentRevision> ite = docRs.listIterator();
        while (ite.hasNext()) {
            DocumentRevision docR = ite.next();
            if (!hasDocumentRevisionReadAccess(user, docR)) {
                ite.remove();
            } else if (isCheckoutByAnotherUser(user, docR)) {
                em.detach(docR);
                docR.removeLastIteration();
            }
        }

        return docRs.toArray(new DocumentRevision[docRs.size()]);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void createDocumentLog(DocumentLog log) {
        em.persist(log);
    }


    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision releaseDocumentRevision(DocumentRevisionKey pRevisionKey) throws UserNotFoundException, WorkspaceNotFoundException, UserNotActiveException, DocumentRevisionNotFoundException, AccessRightException, NotAllowedException, WorkspaceNotEnabledException {
        User user = checkDocumentRevisionWriteAccess(pRevisionKey); // Check if the user can write the document

        DocumentRevision documentRevision = documentRevisionDAO.loadDocR(pRevisionKey);

        if (documentRevision.isCheckedOut()) {
            throw new NotAllowedException("NotAllowedException63");
        }

        if (documentRevision.getNumberOfIterations() == 0) {
            throw new NotAllowedException("NotAllowedException27");
        }

        if (documentRevision.isObsolete()) {
            throw new NotAllowedException("NotAllowedException64");
        }

        documentRevision.release(user);
        return documentRevision;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public DocumentRevision markDocumentRevisionAsObsolete(DocumentRevisionKey pRevisionKey) throws UserNotFoundException, WorkspaceNotFoundException, UserNotActiveException, DocumentRevisionNotFoundException, AccessRightException, NotAllowedException, WorkspaceNotEnabledException {
        User user = checkDocumentRevisionWriteAccess(pRevisionKey); // Check if the user can write the document

        DocumentRevision documentRevision = documentRevisionDAO.loadDocR(pRevisionKey);

        if (!documentRevision.isReleased()) {
            throw new NotAllowedException("NotAllowedException65");
        }

        documentRevision.markAsObsolete(user);
        return documentRevision;
    }

    @Override
    public void logDocument(String fullName, String event) throws FileNotFoundException {
        String userLogin = contextManager.getCallerPrincipalLogin();

        BinaryResource file = binaryResourceDAO.loadBinaryResource(fullName);
        DocumentIteration document = binaryResourceDAO.getDocumentHolder(file);

        if (document != null) {
            DocumentLog log = new DocumentLog();
            log.setUserLogin(userLogin);
            log.setLogDate(new Date());
            log.setDocumentWorkspaceId(document.getWorkspaceId());
            log.setDocumentId(document.getId());
            log.setDocumentVersion(document.getVersion());
            log.setDocumentIteration(document.getIteration());
            log.setEvent(event);
            log.setInfo(fullName);
            createDocumentLog(log);
        }

    }

    /**
     * Apply read access policy on a document revision
     *
     * @param user             The user to test
     * @param documentRevision The document revision to test
     * @return The readable document revision or null if the user has no access to it.
     */
    private DocumentRevision applyDocumentRevisionReadAccess(User user, DocumentRevision documentRevision) {
        if (hasDocumentRevisionReadAccess(user, documentRevision)) {
            if (!isCheckoutByAnotherUser(user, documentRevision)) {
                return documentRevision;
            }
            em.detach(documentRevision);
            documentRevision.removeLastIteration();
            return documentRevision;
        }
        return null;
    }


    /**
     * Check if the current account have read access on a document revision.
     *
     * @param documentRevisionKey The key of the document revision.
     * @return The user if he has read access to the document revision.
     * @throws UserNotFoundException             If there are any User matching the Workspace and the current account login.
     * @throws UserNotActiveException            If the user is not actif.
     * @throws WorkspaceNotFoundException        If the workspace doesn't exist.
     * @throws AccessRightException              If the user has no read access to the document revision.
     * @throws DocumentRevisionNotFoundException If the document revision doesn't exist.
     */
    private User checkDocumentRevisionReadAccess(DocumentRevisionKey documentRevisionKey) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, AccessRightException, DocumentRevisionNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(documentRevisionKey.getDocumentMaster().getWorkspace());

        DocumentRevision documentRevision = documentRevisionDAO.loadDocR(documentRevisionKey);

        if (!hasDocumentRevisionReadAccess(user, documentRevision)) {
            throw new AccessRightException(user);
        }

        return user;
    }

    /**
     * Check if the current account user have write access on a document revision.
     *
     * @param documentRevisionKey The key of the document revision.
     * @return The user if he has write access to the document revision.
     * @throws UserNotFoundException             If there are any User matching the Workspace and the current account login.
     * @throws UserNotActiveException            If the user is not actif.
     * @throws WorkspaceNotFoundException        If the workspace doesn't exist.
     * @throws AccessRightException              If the user has no write access to the document revision.
     * @throws DocumentRevisionNotFoundException If the document revision doesn't exist.
     */
    private User checkDocumentRevisionWriteAccess(DocumentRevisionKey documentRevisionKey) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, AccessRightException, DocumentRevisionNotFoundException, NotAllowedException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(documentRevisionKey.getDocumentMaster().getWorkspace());

        if (user.isAdministrator()) {
            return user;
        }
        DocumentRevision documentRevision = documentRevisionDAO.loadDocR(documentRevisionKey);

        if (documentRevision.getACL() == null) {
            return userManager.checkWorkspaceWriteAccess(documentRevisionKey.getDocumentMaster().getWorkspace());
        } else if (hasDocumentRevisionWriteAccess(user, documentRevision)) {
            return user;
        } else if (isInAnotherUserHomeFolder(user, documentRevision)) {
            throw new NotAllowedException("NotAllowedException5");
        } else {
            throw new AccessRightException(user);
        }
    }


    /**
     * Check if a user, which have access to the workspace, have the right of change the folder structure.
     *
     * @param pUser A user which have read access to the workspace.
     * @throws NotAllowedException If access is deny.
     */
    private void checkFoldersStructureChangeRight(User pUser) throws NotAllowedException {
        Workspace wks = pUser.getWorkspace();
        if (wks.isFolderLocked() && !pUser.isAdministrator()) {
            throw new NotAllowedException("NotAllowedException7");
        }
    }

    /**
     * Check if a user, which have access to the workspace, have write access in a folder
     *
     * @param pUser   A user which have read access to the workspace
     * @param pFolder The folder wanted
     * @return The folder access is granted.
     * @throws NotAllowedException If the folder access is deny.
     */
    private Folder checkFolderWritingRight(User pUser, Folder pFolder) throws NotAllowedException {
        if (isAnotherUserHomeFolder(pUser, pFolder)) {
            throw new NotAllowedException("NotAllowedException33");
        }
        return pFolder;
    }


    /**
     * Say if a user, which have access to the workspace, have read access to a document revision
     *
     * @param user             A user which have read access to the workspace
     * @param documentRevision The document revision wanted
     * @return True if access is granted, False otherwise
     */
    private boolean hasDocumentRevisionReadAccess(User user, DocumentRevision documentRevision) {
        return documentRevision.isPublicShared() || hasPrivateDocumentRevisionReadAccess(user, documentRevision);
    }

    private boolean hasPrivateDocumentRevisionReadAccess(User user, DocumentRevision documentRevision) {
        return isInSameWorkspace(user, documentRevision) &&
                (user.isAdministrator() || isACLGrantReadAccess(user, documentRevision)) &&
                !isInAnotherUserHomeFolder(user, documentRevision);
    }

    private boolean hasDocumentMasterTemplateReadAccess(DocumentMasterTemplate template, User user) {
        return isInSameWorkspace(user, template) && (user.isAdministrator() || isACLGrantReadAccess(user, template));
    }

    /**
     * Say if a user, which have access to the workspace, have write access to a document revision
     *
     * @param user             A user which have read access to the workspace
     * @param documentRevision The document revision wanted
     * @return True if access is granted, False otherwise
     */
    private boolean hasDocumentRevisionWriteAccess(User user, DocumentRevision documentRevision) {
        return isInSameWorkspace(user, documentRevision) &&
                (user.isAdministrator() || isACLGrantWriteAccess(user, documentRevision)) &&
                !isInAnotherUserHomeFolder(user, documentRevision);
    }

    private boolean isInSameWorkspace(User user, DocumentRevision documentRevision) {
        return user.getWorkspaceId().equals(documentRevision.getWorkspaceId());
    }

    private boolean isInSameWorkspace(User user, DocumentMasterTemplate template) {
        return user.getWorkspaceId().equals(template.getWorkspaceId());
    }

    private boolean isAuthor(User user, DocumentRevision documentRevision) {
        return documentRevision.getAuthor().getLogin().equals(user.getLogin());
    }

    private boolean isACLGrantReadAccess(User user, DocumentRevision documentRevision) {
        return documentRevision.getACL() == null || documentRevision.getACL().hasReadAccess(user);
    }

    private boolean isACLGrantReadAccess(User user, DocumentMasterTemplate template) {
        return template.getAcl() == null || template.getAcl().hasReadAccess(user);
    }

    private boolean isACLGrantWriteAccess(User user, DocumentRevision documentRevision) {
        return documentRevision.getACL() == null || documentRevision.getACL().hasWriteAccess(user);
    }

    private boolean isAnotherUserHomeFolder(User user, Folder folder) {
        return folder.isPrivate() && !folder.getOwner().equals(user.getLogin());
    }

    private boolean isInAnotherUserHomeFolder(User user, DocumentRevision documentRevision) {
        return isAnotherUserHomeFolder(user, documentRevision.getLocation());
    }

    private boolean isCheckoutByUser(User user, DocumentRevision documentRevision) {
        return documentRevision.isCheckedOut() && documentRevision.getCheckOutUser().equals(user);
    }

    private boolean isCheckoutByAnotherUser(User user, DocumentRevision documentRevision) {
        return documentRevision.isCheckedOut() && !documentRevision.getCheckOutUser().equals(user);
    }


    private void checkNameValidity(String name) throws NotAllowedException {
        if (!NamingConvention.correct(name)) {
            throw new NotAllowedException("NotAllowedException9", name);
        }
    }

    private void checkDocumentIdValidity(String name) throws NotAllowedException {
        if (!NamingConvention.correctDocumentId(name)) {
            throw new NotAllowedException("NotAllowedException69", name);
        }
    }

    private void checkNameFileValidity(String name) throws NotAllowedException {
        if (name != null) {
            name = name.trim();
        }
        if (!NamingConvention.correctNameFile(name)) {
            throw new NotAllowedException("NotAllowedException9", name);
        }
    }

    private User checkDocumentTemplateWriteAccess(DocumentMasterTemplate docTemplate, User user) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        if (user.isAdministrator()) {
            // Check if it is the workspace's administrator
            return user;
        }
        if (docTemplate.getAcl() == null) {
            // Check if the item haven't ACL
            return userManager.checkWorkspaceWriteAccess(docTemplate.getWorkspaceId());
        } else if (docTemplate.getAcl().hasWriteAccess(user)) {
            // Check if there is a write access
            return user;
        } else {
            // Else throw a AccessRightException
            throw new AccessRightException(user);
        }

    }
}
