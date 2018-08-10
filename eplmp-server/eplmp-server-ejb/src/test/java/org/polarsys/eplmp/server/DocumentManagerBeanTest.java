/*******************************************************************************
 * Copyright (c) 2017 DocDoku.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p/>
 * Contributors:
 * DocDoku - initial API and implementation
 *******************************************************************************/

package org.polarsys.eplmp.server;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.polarsys.eplmp.core.common.Account;
import org.polarsys.eplmp.core.common.BinaryResource;
import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.common.Workspace;
import org.polarsys.eplmp.core.document.*;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.meta.Folder;
import org.polarsys.eplmp.core.meta.InstanceAttribute;
import org.polarsys.eplmp.core.meta.InstanceDateAttribute;
import org.polarsys.eplmp.core.meta.InstanceTextAttribute;
import org.polarsys.eplmp.core.security.ACL;
import org.polarsys.eplmp.core.security.ACLPermission;
import org.polarsys.eplmp.core.services.IUserManagerLocal;
import org.polarsys.eplmp.server.dao.ACLDAO;
import org.polarsys.eplmp.server.dao.BinaryResourceDAO;
import org.polarsys.eplmp.server.dao.DocumentMasterTemplateDAO;
import org.polarsys.eplmp.server.dao.DocumentRevisionDAO;
import org.polarsys.eplmp.server.util.DocumentUtil;

import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.mockito.MockitoAnnotations.initMocks;

public class DocumentManagerBeanTest {

    @InjectMocks
    private
    DocumentManagerBean documentManagerBean = new DocumentManagerBean();

    @Mock
    private IUserManagerLocal userManager;
    @Mock
    private TypedQuery<DocumentIteration> documentIterationQuery;
    @Mock
    private TypedQuery<ACL> aclTypedQuery;
    @Mock
    private BinaryResourceDAO binaryResourceDAO;
    @Mock
    private DocumentMasterTemplateDAO documentMasterTemplateDAO;
    @Mock
    private DocumentRevisionDAO documentRevisionDAO;
    @Mock
    private ACLDAO aclDAO;

    private Workspace workspace;
    private User user;
    private DocumentMasterTemplate documentMasterTemplate;
    private BinaryResource binaryResource;
    private DocumentIteration documentIteration;
    private DocumentRevision documentRevision;
    private ACL acl;

    @Before
    public void setup() {
        initMocks(this);
        Account account = new Account(DocumentUtil.USER_2_LOGIN, DocumentUtil.USER_2_NAME, DocumentUtil.USER2_MAIL, DocumentUtil.LANGUAGE, new Date(), null);
        workspace = new Workspace(DocumentUtil.WORKSPACE_ID, account, DocumentUtil.WORKSPACE_DESCRIPTION, false);
        user = new User(workspace, new Account(DocumentUtil.USER_1_LOGIN, DocumentUtil.USER_1_NAME, DocumentUtil.USER1_MAIL, DocumentUtil.LANGUAGE, new Date(), null));
        documentMasterTemplate = new DocumentMasterTemplate(workspace, DocumentUtil.DOCUMENT_TEMPLATE_ID, user, "", "");
        binaryResource = new BinaryResource(DocumentUtil.FULL_NAME, DocumentUtil.DOCUMENT_SIZE, new Date());
        documentIteration = new DocumentIteration();
        acl = new ACL();
        acl.addEntry(user, ACLPermission.READ_ONLY);

        Folder folder = new Folder(DocumentUtil.WORKSPACE_ID + "/" + user.getName() + "/folders/" + DocumentUtil.FOLDER);
        documentRevision = new DocumentRevision();
        documentRevision.setLocation(folder);
        documentIteration.setDocumentRevision(documentRevision);
        documentRevision.setACL(acl);
    }

    /**
     * test the upload of file in document template's
     * @throws Exception
     */
    @Test
    public void saveFileInTemplate() throws Exception {
        //Given
        DocumentMasterTemplateKey pDocMTemplateKey = Mockito.spy(new DocumentMasterTemplateKey(DocumentUtil.WORKSPACE_ID, DocumentUtil.DOCUMENT_ID));

        Mockito.when(userManager.checkWorkspaceWriteAccess(DocumentUtil.WORKSPACE_ID)).thenReturn(user);
        Mockito.when(userManager.checkWorkspaceReadAccess(DocumentUtil.WORKSPACE_ID)).thenReturn(user);
        Mockito.when(documentMasterTemplateDAO.loadDocMTemplate(pDocMTemplateKey)).thenReturn(documentMasterTemplate);

        //When
        BinaryResource binaryResource = documentManagerBean.saveFileInTemplate(pDocMTemplateKey, DocumentUtil.FILE1_NAME, DocumentUtil.DOCUMENT_SIZE);

        //Then
        Assert.assertNotNull(binaryResource.getLastModified());
        Assert.assertEquals(DocumentUtil.DOCUMENT_SIZE, binaryResource.getContentLength());
        Assert.assertTrue(!binaryResource.getFullName().isEmpty());
        Assert.assertEquals(binaryResource.getFullName(), DocumentUtil.WORKSPACE_ID + "/document-templates/" + DocumentUtil.DOCUMENT_TEMPLATE_ID + "/" + DocumentUtil.FILE1_NAME);
    }

    /**
     * test the upload of file in documents
     * @throws Exception
     */
    @Test
    public void saveFileInDocument() throws Exception {
        //Given
        DocumentMaster documentMaster = new DocumentMaster(workspace, DocumentUtil.DOCUMENT_ID, user);
        DocumentRevision documentRevision = new DocumentRevision(documentMaster, DocumentUtil.VERSION, user);
        ArrayList<DocumentIteration> iterations = new ArrayList<>();
        iterations.add(new DocumentIteration(documentRevision, user));
        documentRevision.setDocumentIterations(iterations);
        documentRevision.setCheckOutUser(user);
        DocumentRevisionKey documentRevisionKey = new DocumentRevisionKey(DocumentUtil.WORKSPACE_ID, DocumentUtil.DOCUMENT_ID, DocumentUtil.VERSION);
        DocumentIterationKey iterationKey = new DocumentIterationKey(DocumentUtil.WORKSPACE_ID, DocumentUtil.DOCUMENT_ID, DocumentUtil.VERSION, DocumentUtil.ITERATION);

        Mockito.when(userManager.checkWorkspaceWriteAccess(DocumentUtil.WORKSPACE_ID)).thenReturn(user);
        Mockito.when(userManager.checkWorkspaceReadAccess(DocumentUtil.WORKSPACE_ID)).thenReturn(user);
        Mockito.when(documentRevisionDAO.loadDocR(documentRevisionKey)).thenReturn(documentRevision);

        //When
        BinaryResource binaryResource = documentManagerBean.saveFileInDocument(iterationKey, DocumentUtil.FILE1_NAME, DocumentUtil.DOCUMENT_SIZE);

        //Then
        Assert.assertNotNull(binaryResource.getLastModified());
        Assert.assertEquals(DocumentUtil.DOCUMENT_SIZE, binaryResource.getContentLength());
        Assert.assertTrue(!binaryResource.getFullName().isEmpty());
        Assert.assertEquals(binaryResource.getFullName(), DocumentUtil.WORKSPACE_ID + "/documents/" + DocumentUtil.DOCUMENT_ID + "/" + DocumentUtil.VERSION + "/" + DocumentUtil.ITERATION + "/" + DocumentUtil.FILE1_NAME);

    }

    /**
     * test the upload of file  with special characters in documents
     * @throws Exception
     */
    @Test
    public void saveFileWithSpecialCharactersInDocument() throws Exception {
        //Given
        DocumentMaster documentMaster = new DocumentMaster(workspace, DocumentUtil.DOCUMENT_ID, user);
        DocumentRevision documentRevision = new DocumentRevision(documentMaster, DocumentUtil.VERSION, user);
        ArrayList<DocumentIteration> iterations = new ArrayList<>();
        iterations.add(new DocumentIteration(documentRevision, user));
        documentRevision.setDocumentIterations(iterations);
        documentRevision.setCheckOutUser(user);
        DocumentRevisionKey documentRevisionKey = new DocumentRevisionKey(DocumentUtil.WORKSPACE_ID, DocumentUtil.DOCUMENT_ID, DocumentUtil.VERSION);
        DocumentIterationKey iterationKey = new DocumentIterationKey(DocumentUtil.WORKSPACE_ID, DocumentUtil.DOCUMENT_ID, DocumentUtil.VERSION, DocumentUtil.ITERATION);

        Mockito.when(userManager.checkWorkspaceWriteAccess(DocumentUtil.WORKSPACE_ID)).thenReturn(user);
        Mockito.when(userManager.checkWorkspaceReadAccess(DocumentUtil.WORKSPACE_ID)).thenReturn(user);
        Mockito.when(documentRevisionDAO.loadDocR(documentRevisionKey)).thenReturn(documentRevision);

        //When
        BinaryResource binaryResource = documentManagerBean.saveFileInDocument(iterationKey, DocumentUtil.FILE2_NAME, DocumentUtil.DOCUMENT_SIZE);

        //Then
        Assert.assertNotNull(binaryResource.getLastModified());
        Assert.assertEquals(DocumentUtil.DOCUMENT_SIZE, binaryResource.getContentLength());
        Assert.assertTrue(!binaryResource.getFullName().isEmpty());
        Assert.assertEquals(binaryResource.getFullName(), DocumentUtil.WORKSPACE_ID + "/documents/" + DocumentUtil.DOCUMENT_ID + "/" + DocumentUtil.VERSION + "/" + DocumentUtil.ITERATION + "/" + DocumentUtil.FILE2_NAME);

    }

    /**
     * test the upload of file  with forbidden characters in documents
     * @throws Exception
     */
    @Test(expected = NotAllowedException.class)
    public void saveFileWithForbiddenCharactersInDocument() throws Exception {
        //Given
        DocumentMaster documentMaster = new DocumentMaster(workspace, DocumentUtil.DOCUMENT_ID, user);
        DocumentRevision documentRevision = new DocumentRevision(documentMaster, DocumentUtil.VERSION, user);
        ArrayList<DocumentIteration> iterations = new ArrayList<>();
        iterations.add(new DocumentIteration(documentRevision, user));
        documentRevision.setDocumentIterations(iterations);
        documentRevision.setCheckOutUser(user);
        DocumentRevisionKey documentRevisionKey = new DocumentRevisionKey(DocumentUtil.WORKSPACE_ID, DocumentUtil.DOCUMENT_ID, DocumentUtil.VERSION);
        DocumentIterationKey iterationKey = new DocumentIterationKey(DocumentUtil.WORKSPACE_ID, DocumentUtil.DOCUMENT_ID, DocumentUtil.VERSION, DocumentUtil.ITERATION);

        Mockito.when(userManager.checkWorkspaceWriteAccess(DocumentUtil.WORKSPACE_ID)).thenReturn(user);
        Mockito.when(userManager.checkWorkspaceReadAccess(DocumentUtil.WORKSPACE_ID)).thenReturn(user);
        Mockito.when(documentRevisionDAO.loadDocR(documentRevisionKey)).thenReturn(documentRevision);


        //When
        documentManagerBean.saveFileInDocument(iterationKey, DocumentUtil.FILE3_NAME, DocumentUtil.DOCUMENT_SIZE);

    }

    /**
     *
     *  Test to download a document file
     *  */
    @Test
    public void getBinaryResourceTest() throws Exception {
        //Given
        Mockito.when(userManager.checkWorkspaceReadAccess(BinaryResource.parseWorkspaceId(DocumentUtil.FULL_NAME))).thenReturn(user);
        Mockito.when(binaryResourceDAO.loadBinaryResource(DocumentUtil.FULL_NAME)).thenReturn(binaryResource);
        Mockito.when(documentIterationQuery.getSingleResult()).thenReturn(documentIteration);
        Mockito.when(documentIterationQuery.setParameter("binaryResource", binaryResource)).thenReturn(documentIterationQuery);
        Mockito.when(binaryResourceDAO.getDocumentHolder(binaryResource)).thenReturn(documentIteration);

        //When
        BinaryResource binaryResource = documentManagerBean.getBinaryResource(DocumentUtil.FULL_NAME);

        //Then
        Assert.assertNotNull(binaryResource);
        Assert.assertEquals(DocumentUtil.DOCUMENT_SIZE, binaryResource.getContentLength());
    }

    /**
     *
     * This test will check if the attributes is well manages if the documents has a template with freeze attributes
     *
     */
    @Test
    public void changeAttributesWithLockedTemplate() throws Exception {

        DocumentMaster documentMaster = new DocumentMaster(workspace, DocumentUtil.DOCUMENT_ID, user);

        documentRevision = new DocumentRevision(documentMaster, "A", user);
        documentIteration = new DocumentIteration(documentRevision, user);
        documentRevision.setCheckOutUser(user);
        documentRevision.setCheckOutDate(new Date());
        ArrayList<DocumentIteration> iterations = new ArrayList<>();
        iterations.add(documentIteration);
        documentRevision.setDocumentIterations(iterations);

        DocumentRevisionKey documentRevisionKey = new DocumentRevisionKey(workspace.getId(), documentMaster.getId(), documentRevision.getVersion());

        //Creation of current attributes of the iteration
        InstanceAttribute attribute = new InstanceTextAttribute("Nom", "Testeur", false);
        List<InstanceAttribute> attributesOfIteration = new ArrayList<>();
        attributesOfIteration.add(attribute);
        documentIteration.setInstanceAttributes(attributesOfIteration);

        documentMaster.setAttributesLocked(true);

        Mockito.when(userManager.checkWorkspaceReadAccess(workspace.getId())).thenReturn(user);
        Mockito.when(userManager.checkWorkspaceWriteAccess(workspace.getId())).thenReturn(user);
        Mockito.when(documentRevisionDAO.loadDocR(documentRevisionKey)).thenReturn(documentRevision);

        try {
            //Test to remove attribute
            documentManagerBean.updateDocument(documentIteration.getKey(), "test", Arrays.asList(new InstanceAttribute[]{}), new DocumentRevisionKey[]{}, null);
            Assert.fail("updateDocument should have raise an exception because we have removed attributes");
        } catch (NotAllowedException notAllowedException) {
            try {
                //Test with a swipe of attribute
                documentManagerBean.updateDocument(documentIteration.getKey(), "test", Arrays.asList(new InstanceAttribute[]{new InstanceDateAttribute("Nom", new Date(), false)}), new DocumentRevisionKey[]{}, null);
                Assert.fail("updateDocument should have raise an exception because we have changed the attribute type attributes");
            } catch (NotAllowedException notAllowedException2) {
                try {
                    //Test without modifying the attribute
                    documentManagerBean.updateDocument(documentIteration.getKey(), "test", Arrays.asList(new InstanceAttribute[]{attribute}), new DocumentRevisionKey[]{}, null);
                    //Test with a new value of the attribute
                    documentManagerBean.updateDocument(documentIteration.getKey(), "test", Arrays.asList(new InstanceAttribute[]{new InstanceTextAttribute("Nom", "Testeur change", false)}), new DocumentRevisionKey[]{}, null);
                } catch (NotAllowedException notAllowedException3) {
                    Assert.fail("updateDocument shouldn't have raised an exception because we haven't change the number of attribute or the type");
                }
            }
        }
    }

    /**
     *
     * This test will check if the attributes is well manages if the documents has a template with freeze attributes
     *
     */
    @Test
    public void changeAttributesWithUnlockedTemplate() throws Exception {

        DocumentMaster documentMaster = new DocumentMaster(workspace, DocumentUtil.DOCUMENT_ID, user);

        documentRevision = new DocumentRevision(documentMaster, "A", user);
        documentIteration = new DocumentIteration(documentRevision, user);
        documentRevision.setCheckOutUser(user);
        documentRevision.setCheckOutDate(new Date());
        ArrayList<DocumentIteration> iterations = new ArrayList<>();
        iterations.add(documentIteration);
        documentRevision.setDocumentIterations(iterations);

        DocumentRevisionKey documentRevisionKey = new DocumentRevisionKey(workspace.getId(), documentMaster.getId(), documentRevision.getVersion());

        //Creation of current attributes of the iteration
        InstanceAttribute attribute = new InstanceTextAttribute("Nom", "Testeur", false);
        List<InstanceAttribute> attributesOfIteration = new ArrayList<>();
        attributesOfIteration.add(attribute);
        documentIteration.setInstanceAttributes(attributesOfIteration);

        documentMaster.setAttributesLocked(false);

        Mockito.when(userManager.checkWorkspaceReadAccess(workspace.getId())).thenReturn(user);
        Mockito.when(userManager.checkWorkspaceWriteAccess(workspace.getId())).thenReturn(user);
        Mockito.when(documentRevisionDAO.loadDocR(documentRevisionKey)).thenReturn(documentRevision);

        try {
            //Test to remove attribute
            documentManagerBean.updateDocument(documentIteration.getKey(), "test", Arrays.asList(new InstanceAttribute[]{}), new DocumentRevisionKey[]{}, null);
            //Add the attribute
            documentManagerBean.updateDocument(documentIteration.getKey(), "test", Arrays.asList(new InstanceAttribute[]{attribute}), new DocumentRevisionKey[]{}, null);
            //Change the value of the attribute
            documentManagerBean.updateDocument(documentIteration.getKey(), "test", Arrays.asList(new InstanceAttribute[]{new InstanceTextAttribute("Nom", "Testeur change", false)}), new DocumentRevisionKey[]{}, null);
            //Change the type of the attribute
            documentManagerBean.updateDocument(documentIteration.getKey(), "test", Arrays.asList(new InstanceAttribute[]{new InstanceDateAttribute("Nom", new Date(), false)}), new DocumentRevisionKey[]{}, null);
        } catch (NotAllowedException notAllowedException3) {
            Assert.fail("updateDocument shouldn't have raised an exception because the attribute are not frozen");
        }
    }

    /**
     *
     * This test will check if the ACL is null when removing it from a document
     *
     */

    @Test
    public void removeACLFromDocument() throws Exception {

        user = new User(workspace, new Account(DocumentUtil.USER_1_LOGIN, DocumentUtil.USER_1_NAME, DocumentUtil.USER1_MAIL, DocumentUtil.LANGUAGE, new Date(), null));

        DocumentMaster documentMaster = new DocumentMaster(workspace, DocumentUtil.DOCUMENT_ID, user);
        documentRevision = new DocumentRevision(documentMaster, "A", user);
        documentIteration = new DocumentIteration(documentRevision, user);
        documentRevision.setCheckOutUser(user);
        documentRevision.setCheckOutDate(new Date());
        acl = new ACL();
        acl.addEntry(user, ACLPermission.READ_ONLY);
        documentRevision.setACL(acl);

        DocumentRevisionKey documentRevisionKey = new DocumentRevisionKey(workspace.getId(), documentMaster.getId(), documentRevision.getVersion());

        Mockito.when(userManager.checkWorkspaceReadAccess(workspace.getId())).thenReturn(user);
        Mockito.when(aclTypedQuery.setParameter(Matchers.anyString(), Matchers.any())).thenReturn(aclTypedQuery);
        Mockito.when(documentRevisionDAO.getDocRRef(documentRevisionKey)).thenReturn(documentRevision);

        documentManagerBean.removeACLFromDocumentRevision(documentRevision.getKey());
        Assert.assertNull(documentRevision.getACL());
    }

}
