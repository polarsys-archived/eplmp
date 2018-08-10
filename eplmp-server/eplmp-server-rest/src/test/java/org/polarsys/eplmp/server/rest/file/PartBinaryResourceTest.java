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

package org.polarsys.eplmp.server.rest.file;

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
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.product.*;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.*;
import org.polarsys.eplmp.core.sharing.SharedPart;
import org.polarsys.eplmp.server.rest.file.util.BinaryResourceBinaryStreamingOutput;
import org.polarsys.eplmp.server.util.PartImpl;
import org.polarsys.eplmp.server.util.ResourceUtil;

import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.Part;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class PartBinaryResourceTest {

    @InjectMocks
    private PartBinaryResource partBinaryResource;
    @Mock
    private IBinaryStorageManagerLocal storageManager;
    @Mock
    private IContextManagerLocal contextManager;
    @Mock
    private IProductManagerLocal productService;
    @Mock
    private IConverterManagerLocal converterService;
    @Mock
    private IShareManagerLocal shareService;
    @Mock
    private IPublicEntityManagerLocal publicEntityManager;

    @Before
    public void setup() throws Exception {
        initMocks(this);
    }

    /**
     * Test to upload a file to a part
     *
     * @throws Exception
     */
    @Test
    public void uploadFileToPart() throws Exception {
        //Given
        final File fileToUpload = new File(ResourceUtil.getFilePath(ResourceUtil.SOURCE_PART_STORAGE + ResourceUtil.TEST_PART_FILENAME1));
        File uploadedFile = File.createTempFile(ResourceUtil.TARGET_PART_STORAGE + ResourceUtil.FILENAME_TARGET_PART, ResourceUtil.TEMP_SUFFIX);
        HttpServletRequestWrapper request = Mockito.mock(HttpServletRequestWrapper.class);
        Collection<Part> parts = new ArrayList<>();
        parts.add(new PartImpl(fileToUpload));
        Mockito.when(request.getParts()).thenReturn(parts);
        BinaryResource binaryResource = new BinaryResource(ResourceUtil.FILENAME1, ResourceUtil.PART_SIZE, new Date());
        OutputStream outputStream = new FileOutputStream(uploadedFile);
        Mockito.when(request.getRequestURI()).thenReturn(ResourceUtil.WORKSPACE_ID + "/parts/" + ResourceUtil.PART_TEMPLATE_ID + "/");
        Mockito.when(productService.saveFileInPartIteration(Matchers.any(PartIterationKey.class), Matchers.anyString(), Matchers.anyString(), Matchers.anyInt())).thenReturn(binaryResource);
        Mockito.when(storageManager.getBinaryResourceOutputStream(binaryResource)).thenReturn(outputStream);

        //When
        Response response = partBinaryResource.uploadAttachedFiles(request, ResourceUtil.WORKSPACE_ID, ResourceUtil.PART_NUMBER, ResourceUtil.VERSION, ResourceUtil.ITERATION);
        //Then
        assertNotNull(response);
        assertEquals(response.getStatus(), 201);
        assertEquals(response.getStatusInfo(), Response.Status.CREATED);
        //delete temp file
        uploadedFile.deleteOnExit();

    }

    /**
     * Test to upload a native cad to a part
     */
    @Test
    public void uploadNativeCADToPart() throws Exception {

        //Given
        final File fileToUpload = new File(ResourceUtil.getFilePath(ResourceUtil.SOURCE_PART_STORAGE + ResourceUtil.TEST_PART_FILENAME1));
        File uploadedFile = File.createTempFile(ResourceUtil.TARGET_PART_STORAGE + ResourceUtil.FILENAME_TARGET_PART, ResourceUtil.TEMP_SUFFIX);
        HttpServletRequestWrapper request = Mockito.mock(HttpServletRequestWrapper.class);
        Collection<Part> parts = new ArrayList<>();
        parts.add(new PartImpl(fileToUpload));
        Mockito.when(request.getParts()).thenReturn(parts);
        BinaryResource binaryResource = new BinaryResource(ResourceUtil.FILENAME1, ResourceUtil.PART_SIZE, new Date());
        OutputStream outputStream = new FileOutputStream(uploadedFile);
        Mockito.when(request.getRequestURI()).thenReturn(ResourceUtil.WORKSPACE_ID + "/parts/" + ResourceUtil.PART_TEMPLATE_ID + "/");
        Mockito.when(productService.saveNativeCADInPartIteration(Matchers.any(PartIterationKey.class), Matchers.anyString(), Matchers.anyInt())).thenReturn(binaryResource);
        Mockito.when(storageManager.getBinaryResourceOutputStream(binaryResource)).thenReturn(outputStream);

        //When
        Response response = partBinaryResource.uploadNativeCADFile(request, ResourceUtil.WORKSPACE_ID, ResourceUtil.PART_NUMBER, ResourceUtil.VERSION, ResourceUtil.ITERATION);
        //Then
        assertNotNull(response);
        assertEquals(response.getStatus(), 201);
        assertEquals(response.getStatusInfo(), Response.Status.CREATED);

        //delete temp file
        uploadedFile.deleteOnExit();

    }

    /**
     * Test to upload a file to a part with special characters
     *
     * @throws Exception
     */
    @Test
    public void uploadFileWithSpecialCharactersToPart() throws Exception {
        //Given
        File fileToUpload = new File(ResourceUtil.getFilePath(ResourceUtil.SOURCE_PART_STORAGE) + ResourceUtil.FILENAME_TO_UPLOAD_PART_SPECIAL_CHARACTER);
        File uploadedFile = File.createTempFile(ResourceUtil.TARGET_PART_STORAGE + ResourceUtil.FILENAME_TO_UPLOAD_PART_SPECIAL_CHARACTER, ResourceUtil.TEMP_SUFFIX);
        HttpServletRequestWrapper request = Mockito.mock(HttpServletRequestWrapper.class);
        Collection<Part> parts = new ArrayList<>();
        parts.add(new PartImpl(fileToUpload));
        Mockito.when(request.getParts()).thenReturn(parts);
        BinaryResource binaryResource = new BinaryResource(ResourceUtil.FILENAME_TO_UPLOAD_PART_SPECIAL_CHARACTER, ResourceUtil.PART_SIZE, new Date());

        OutputStream outputStream = new FileOutputStream(uploadedFile);
        Mockito.when(request.getRequestURI()).thenReturn(ResourceUtil.WORKSPACE_ID + "/parts/" + ResourceUtil.PART_TEMPLATE_ID + "/");
        Mockito.when(productService.saveFileInPartIteration(Matchers.any(PartIterationKey.class), Matchers.anyString(), Matchers.anyString(), Matchers.anyInt())).thenReturn(binaryResource);
        Mockito.when(storageManager.getBinaryResourceOutputStream(binaryResource)).thenReturn(outputStream);

        //When
        Response response = partBinaryResource.uploadAttachedFiles(request, ResourceUtil.WORKSPACE_ID, ResourceUtil.PART_NUMBER, ResourceUtil.VERSION, ResourceUtil.ITERATION);
        //Then
        assertNotNull(response);
        assertEquals(response.getStatus(), 201);
        assertEquals(response.getStatusInfo(), Response.Status.CREATED);
        assertEquals(response.getLocation().toString(), (ResourceUtil.WORKSPACE_ID + "/parts/" + ResourceUtil.PART_TEMPLATE_ID + "/" + URLEncoder.encode(ResourceUtil.getFilePath(ResourceUtil.SOURCE_PART_STORAGE) + ResourceUtil.FILENAME_TO_UPLOAD_PART_SPECIAL_CHARACTER, "UTF-8")));
        //delete temp file
        uploadedFile.deleteOnExit();

    }

    /**
     * Test to upload several file to a part
     *
     * @throws Exception
     */

    @Test
    public void uploadSeveralFilesToPart() throws Exception {
        //Given
        File fileToUpload1 = new File(ResourceUtil.getFilePath(ResourceUtil.SOURCE_PART_STORAGE + ResourceUtil.TEST_PART_FILENAME1));
        File fileToUpload2 = new File(ResourceUtil.getFilePath(ResourceUtil.SOURCE_PART_STORAGE + ResourceUtil.TEST_PART_FILENAME2));
        File uploadedFile1 = File.createTempFile(ResourceUtil.TARGET_PART_STORAGE + ResourceUtil.TEST_PART_FILENAME1, ResourceUtil.TEMP_SUFFIX);
        File uploadedFile2 = File.createTempFile(ResourceUtil.TARGET_PART_STORAGE + ResourceUtil.TEST_PART_FILENAME2, ResourceUtil.TEMP_SUFFIX);
        HttpServletRequestWrapper request = Mockito.mock(HttpServletRequestWrapper.class);
        Collection<Part> parts = new ArrayList<>();
        parts.add(new PartImpl(fileToUpload1));
        parts.add(new PartImpl(fileToUpload2));
        Mockito.when(request.getParts()).thenReturn(parts);
        BinaryResource binaryResource1 = new BinaryResource(ResourceUtil.TEST_PART_FILENAME1, ResourceUtil.PART_SIZE, new Date());
        BinaryResource binaryResource2 = new BinaryResource(ResourceUtil.TEST_PART_FILENAME2, ResourceUtil.PART_SIZE, new Date());

        OutputStream outputStream1 = new FileOutputStream(uploadedFile1);
        OutputStream outputStream2 = new FileOutputStream(uploadedFile2);
        Mockito.when(productService.saveFileInPartIteration(Matchers.any(PartIterationKey.class), Matchers.anyString(), Matchers.anyString(), Matchers.anyInt())).thenReturn(binaryResource1, binaryResource1, binaryResource2, binaryResource2);
        Mockito.when(storageManager.getBinaryResourceOutputStream(Mockito.any(BinaryResource.class))).thenReturn(outputStream1, outputStream2);

        //When
        Response response = partBinaryResource.uploadAttachedFiles(request, ResourceUtil.WORKSPACE_ID, ResourceUtil.PART_NUMBER, ResourceUtil.VERSION, ResourceUtil.ITERATION);
        //Then
        assertNotNull(response);
        assertEquals(204, response.getStatus());
        assertEquals(Response.Status.NO_CONTENT, response.getStatusInfo());

        //delete temp files
        uploadedFile1.deleteOnExit();
        uploadedFile2.deleteOnExit();


    }


    /**
     * Test to download a part file as a guest and the part is public
     *
     * @throws Exception
     */
    @Test
    public void downloadPartFileAsGuestPartPublic() throws Exception {

        //Given
        Request request = Mockito.mock(Request.class);
        BinaryResource binaryResource = Mockito.spy(new BinaryResource(ResourceUtil.FILENAME1, ResourceUtil.PART_SIZE, new Date()));
        Mockito.when(storageManager.getBinaryResourceInputStream(binaryResource)).thenReturn(new FileInputStream(new File(ResourceUtil.getFilePath(ResourceUtil.SOURCE_FILE_STORAGE + ResourceUtil.FILENAME1))));
        Mockito.when(contextManager.isCallerInRole(UserGroupMapping.REGULAR_USER_ROLE_ID)).thenReturn(false);
        Mockito.when(publicEntityManager.canAccess(Mockito.any(PartIterationKey.class))).thenReturn(true);
        Mockito.when(productService.canAccess(Matchers.any(PartIterationKey.class))).thenReturn(false);
        Mockito.when(storageManager.getBinaryResourceInputStream(binaryResource)).thenReturn(new FileInputStream(new File(ResourceUtil.getFilePath(ResourceUtil.SOURCE_PART_STORAGE + ResourceUtil.TEST_PART_FILENAME1))));
        Mockito.when(productService.getPartRevision(Matchers.any(PartRevisionKey.class))).thenReturn(new PartRevision());

        //When
        Mockito.when(publicEntityManager.getPublicBinaryResourceForPart(Matchers.anyString())).thenReturn(binaryResource);
        Response response = partBinaryResource.downloadPartFile(request, ResourceUtil.WORKSPACE_ID,
                ResourceUtil.PART_NUMBER, ResourceUtil.VERSION, ResourceUtil.ITERATION, "attached-files",
                ResourceUtil.TEST_PART_FILENAME1, ResourceUtil.FILE_TYPE, null, ResourceUtil.RANGE, null, null, null);
        //Then
        assertNotNull(response);
        assertEquals(response.getStatus(), 206);
        assertNotNull(response.getEntity());
        assertTrue(response.getEntity() instanceof BinaryResourceBinaryStreamingOutput);
    }

    /**
     * Test to download a part file as a guest, the part is shared private mode
     *
     * @throws Exception
     */
    @Test
    public void downloadPartFilePrivateShare() throws Exception {

        //Given
        Request request = Mockito.mock(Request.class);
        Account account = Mockito.spy(new Account("user2", "user2", "user2@docdoku.com", "en", new Date(), null));
        Workspace workspace = new Workspace(ResourceUtil.WORKSPACE_ID, account, "pDescription", false);
        User user = new User(workspace, new Account("user1", "user1", "user1@docdoku.com", "en", new Date(), null));
        PartMaster partMaster = new PartMaster(workspace, ResourceUtil.PART_NUMBER, user);
        PartRevision partRevision = new PartRevision(partMaster, ResourceUtil.VERSION, user);
        List<PartIteration> iterations = new ArrayList<>();
        PartIteration partIteration = new PartIteration(partRevision, ResourceUtil.ITERATION, user);
        PartIteration partIteration2 = new PartIteration(partRevision, 2, user);
        iterations.add(partIteration);
        partRevision.setPartIterations(iterations);


        SharedPart sharedPart = Mockito.spy(new SharedPart(workspace, user, ResourceUtil.getFutureDate(), "password", partRevision));

        BinaryResource binaryResource = Mockito.spy(new BinaryResource(ResourceUtil.FILENAME1, ResourceUtil.PART_SIZE, new Date()));
        Mockito.when(storageManager.getBinaryResourceInputStream(binaryResource)).thenReturn(new FileInputStream(new File(ResourceUtil.getFilePath(ResourceUtil.SOURCE_FILE_STORAGE + ResourceUtil.FILENAME1))));
        Mockito.when(contextManager.isCallerInRole(UserGroupMapping.REGULAR_USER_ROLE_ID)).thenReturn(false);
        Mockito.when(publicEntityManager.canAccess(Mockito.any(PartIterationKey.class))).thenReturn(true);
        Mockito.when(productService.canAccess(Matchers.any(PartIterationKey.class))).thenReturn(false);
        File file = File.createTempFile(ResourceUtil.getFilePath(ResourceUtil.SOURCE_PART_STORAGE + ResourceUtil.TEST_PART_FILENAME1), ResourceUtil.TEMP_SUFFIX);
        Mockito.when(storageManager.getBinaryResourceInputStream(binaryResource)).thenReturn(new FileInputStream(file));
        Mockito.when(publicEntityManager.getBinaryResourceForSharedEntity(Matchers.anyString())).thenReturn(binaryResource);
        Mockito.when(shareService.findSharedEntityForGivenUUID(ResourceUtil.SHARED_PART_ENTITY_UUID)).thenReturn(sharedPart);
        //When
        Response response = partBinaryResource.downloadPartFile(request, ResourceUtil.WORKSPACE_ID,
                ResourceUtil.PART_NUMBER, ResourceUtil.VERSION, ResourceUtil.ITERATION, "attached-files",
                ResourceUtil.TEST_PART_FILENAME1, ResourceUtil.FILE_TYPE, null, ResourceUtil.RANGE, ResourceUtil.SHARED_PART_ENTITY_UUID, "password", null);
        //Then
        assertNotNull(response);
        assertEquals(response.getStatus(), 206);
        assertNotNull(response.getEntity());
        assertTrue(response.getEntity() instanceof BinaryResourceBinaryStreamingOutput);

        //Delete temp file
        file.deleteOnExit();

    }

    /**
     * Test to download a part file as a regular user who has read access
     *
     * @throws Exception
     */
    @Test
    public void downloadPartFileAsRegularUserReadAccess() throws Exception {
        //Given
        Request request = Mockito.mock(Request.class);
        BinaryResource binaryResource = Mockito.spy(new BinaryResource(ResourceUtil.FILENAME1, ResourceUtil.PART_SIZE, new Date()));
        Mockito.when(storageManager.getBinaryResourceInputStream(binaryResource)).thenReturn(new FileInputStream(new File(ResourceUtil.getFilePath(ResourceUtil.SOURCE_FILE_STORAGE + ResourceUtil.FILENAME1))));
        Mockito.when(contextManager.isCallerInRole(UserGroupMapping.REGULAR_USER_ROLE_ID)).thenReturn(true);
        Mockito.when(productService.getBinaryResource(Matchers.anyString())).thenReturn(binaryResource);
        Mockito.when(productService.canAccess(Matchers.any(PartIterationKey.class))).thenReturn(true);
        Mockito.when(productService.getPartRevision(Matchers.any(PartRevisionKey.class))).thenReturn(new PartRevision());
        Mockito.when(storageManager.getBinaryResourceInputStream(binaryResource)).thenReturn(new FileInputStream(new File(ResourceUtil.getFilePath(ResourceUtil.SOURCE_PART_STORAGE + ResourceUtil.TEST_PART_FILENAME1))));
        Mockito.when(publicEntityManager.getPublicBinaryResourceForPart(Matchers.anyString())).thenReturn(binaryResource);
        //When
        Response response = partBinaryResource.downloadPartFile(request, ResourceUtil.WORKSPACE_ID,
                ResourceUtil.PART_NUMBER, ResourceUtil.VERSION, ResourceUtil.ITERATION, "attached-files",
                ResourceUtil.TEST_PART_FILENAME1, ResourceUtil.FILE_TYPE, null, ResourceUtil.RANGE, null, null, null);
        //Then
        assertNotNull(response);
        assertEquals(response.getStatus(), 206);
        assertNotNull(response.getEntity());
        assertTrue(response.getEntity() instanceof BinaryResourceBinaryStreamingOutput);

    }

    /**
     * Test to download a part file as a regular user who has no access
     *
     * @throws Exception
     */
    @Test
    public void downloadPartFileAsRegularUserNoReadAccess() throws Exception {

        //Given
        Request request = Mockito.mock(Request.class);
        BinaryResource binaryResource = Mockito.spy(new BinaryResource(ResourceUtil.FILENAME1, ResourceUtil.PART_SIZE, new Date()));
        File file = File.createTempFile(ResourceUtil.getFilePath(ResourceUtil.SOURCE_FILE_STORAGE + ResourceUtil.FILENAME1), ResourceUtil.TEMP_SUFFIX);
        Mockito.when(storageManager.getBinaryResourceInputStream(binaryResource)).thenReturn(new FileInputStream(file));
        Mockito.when(contextManager.isCallerInRole(UserGroupMapping.REGULAR_USER_ROLE_ID)).thenReturn(true);
        Mockito.when(productService.getBinaryResource(Matchers.anyString())).thenReturn(binaryResource);
        Mockito.when(productService.canAccess(Matchers.any(PartIterationKey.class))).thenReturn(false);
        File file1 = File.createTempFile(ResourceUtil.getFilePath(ResourceUtil.SOURCE_PART_STORAGE + ResourceUtil.TEST_PART_FILENAME1), ResourceUtil.TEMP_SUFFIX);
        Mockito.when(storageManager.getBinaryResourceInputStream(binaryResource)).thenReturn(new FileInputStream(file1));
        Mockito.when(publicEntityManager.getPublicBinaryResourceForPart(Matchers.anyString())).thenReturn(binaryResource);
        //When
        try {
            partBinaryResource.downloadPartFile(request, ResourceUtil.WORKSPACE_ID,
                    ResourceUtil.PART_NUMBER, ResourceUtil.VERSION, ResourceUtil.ITERATION, "attached-files",
                    ResourceUtil.TEST_PART_FILENAME1, ResourceUtil.FILE_TYPE, null, ResourceUtil.RANGE, null, null, null);
            assertTrue(false);
        } catch (NotAllowedException e) {
            assertTrue(true);
        }

        //delete tem files
        file.deleteOnExit();
        file1.deleteOnExit();

    }

}
