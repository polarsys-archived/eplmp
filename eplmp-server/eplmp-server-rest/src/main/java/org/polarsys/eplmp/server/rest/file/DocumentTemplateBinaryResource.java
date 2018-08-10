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

import io.swagger.annotations.*;
import org.polarsys.eplmp.core.common.BinaryResource;
import org.polarsys.eplmp.core.document.DocumentMasterTemplateKey;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IBinaryStorageManagerLocal;
import org.polarsys.eplmp.core.services.IDocumentManagerLocal;
import org.polarsys.eplmp.core.services.IOnDemandConverterManagerLocal;
import org.polarsys.eplmp.server.helpers.Streams;
import org.polarsys.eplmp.server.rest.exceptions.FileConversionException;
import org.polarsys.eplmp.server.rest.exceptions.PreconditionFailedException;
import org.polarsys.eplmp.server.rest.exceptions.RequestedRangeNotSatisfiableException;
import org.polarsys.eplmp.server.rest.file.util.BinaryResourceDownloadMeta;
import org.polarsys.eplmp.server.rest.file.util.BinaryResourceDownloadResponseBuilder;
import org.polarsys.eplmp.server.rest.file.util.BinaryResourceUpload;
import org.polarsys.eplmp.server.rest.interceptors.Compress;

import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.Collection;
import java.util.logging.Logger;

@RequestScoped
@Api(hidden = true, value = "documentTemplateBinary", description = "Operations about document templates files",
        authorizations = {@Authorization(value = "authorization")})
@DeclareRoles({UserGroupMapping.REGULAR_USER_ROLE_ID})
@RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
public class DocumentTemplateBinaryResource {

    private static final Logger LOGGER = Logger.getLogger(DocumentTemplateBinaryResource.class.getName());
    @Inject
    private IBinaryStorageManagerLocal storageManager;
    @Inject
    private IDocumentManagerLocal documentService;
    @Inject
    private IOnDemandConverterManagerLocal onDemandConverterManager;


    public DocumentTemplateBinaryResource() {
    }

    @POST
    @ApiOperation(value = "Upload document template file",
            response = Response.class)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "upload", paramType = "formData", dataType = "file", required = true)
    })
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Upload success"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadDocumentTemplateFiles(
            @Context HttpServletRequest request,
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") final String workspaceId,
            @ApiParam(required = true, value = "Template id") @PathParam("templateId") final String templateId)
            throws EntityNotFoundException, EntityAlreadyExistsException, UserNotActiveException, AccessRightException,
            NotAllowedException, CreationException, WorkspaceNotEnabledException {

        try {
            BinaryResource binaryResource;
            String fileName = null;
            long length;
            DocumentMasterTemplateKey templatePK = new DocumentMasterTemplateKey(workspaceId, templateId);
            Collection<Part> formParts = request.getParts();

            for (Part formPart : formParts) {
                fileName = Normalizer.normalize(formPart.getSubmittedFileName(), Normalizer.Form.NFC);
                // Init the binary resource with a null length
                binaryResource = documentService.saveFileInTemplate(templatePK, fileName, 0);
                OutputStream outputStream = storageManager.getBinaryResourceOutputStream(binaryResource);
                length = BinaryResourceUpload.uploadBinary(outputStream, formPart);
                documentService.saveFileInTemplate(templatePK, fileName, length);
            }

            if (fileName == null){
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            if (formParts.size() == 1) {
                return BinaryResourceUpload.tryToRespondCreated(request.getRequestURI() + URLEncoder.encode(fileName, "UTF-8"));
            }

            return Response.noContent().build();

        } catch (IOException | ServletException | StorageException e) {
            return BinaryResourceUpload.uploadError(e);
        }
    }


    @GET
    @ApiOperation(value = "Download document template file",
            response = File.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Download success"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("/{fileName}")
    @Compress
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadDocumentTemplateFile(
            @Context Request request,
            @ApiParam(required = false, value = "Range") @HeaderParam("Range") String range,
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") final String workspaceId,
            @ApiParam(required = true, value = "Template id") @PathParam("templateId") final String templateId,
            @ApiParam(required = true, value = "File name") @PathParam("fileName") final String fileName,
            @ApiParam(required = false, value = "Type") @QueryParam("type") String type,
            @ApiParam(required = false, value = "Output") @QueryParam("output") String output)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, NotAllowedException,
            PreconditionFailedException, RequestedRangeNotSatisfiableException, WorkspaceNotEnabledException {


        String fullName = workspaceId + "/document-templates/" + templateId + "/" + fileName;
        BinaryResource binaryResource = documentService.getTemplateBinaryResource(fullName);
        BinaryResourceDownloadMeta binaryResourceDownloadMeta = new BinaryResourceDownloadMeta(binaryResource);

        // Check cache precondition
        Response.ResponseBuilder rb = request.evaluatePreconditions(binaryResourceDownloadMeta.getLastModified(), binaryResourceDownloadMeta.getETag());
        if (rb != null) {
            return rb.build();
        }

        InputStream binaryContentInputStream = null;

        // Set to false because templates are never historized
        boolean isToBeCached = false;

        try {
            if (output != null && !output.isEmpty()) {
                binaryContentInputStream = getConvertedBinaryResource(binaryResource, output);
                if(range == null || range.isEmpty()){
                    binaryResourceDownloadMeta.setLength(0);
                }
            } else {
                binaryContentInputStream = storageManager.getBinaryResourceInputStream(binaryResource);
            }
            return BinaryResourceDownloadResponseBuilder.prepareResponse(binaryContentInputStream, binaryResourceDownloadMeta, range, isToBeCached);
        } catch (StorageException | FileConversionException e) {
            Streams.close(binaryContentInputStream);
            return BinaryResourceDownloadResponseBuilder.downloadError(e, fullName);
        }
    }

    /**
     * Try to convert a binary resource to a specific format
     *
     * @param binaryResource The binary resource
     * @param output         The wanted output
     * @return The binary resource stream in the wanted output
     * @throws FileConversionException
     */
    private InputStream getConvertedBinaryResource(BinaryResource binaryResource, String output) throws FileConversionException {
        try {
            return onDemandConverterManager.getDocumentConvertedResource(output, binaryResource);
        } catch (Exception e) {
            throw new FileConversionException(e);
        }
    }
}
