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
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.product.PartIteration;
import org.polarsys.eplmp.core.product.PartIterationKey;
import org.polarsys.eplmp.core.product.PartRevision;
import org.polarsys.eplmp.core.product.PartRevisionKey;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.*;
import org.polarsys.eplmp.core.sharing.SharedEntity;
import org.polarsys.eplmp.core.sharing.SharedPart;
import org.polarsys.eplmp.core.util.FileIO;
import org.polarsys.eplmp.core.util.HashUtils;
import org.polarsys.eplmp.server.auth.AuthConfig;
import org.polarsys.eplmp.server.auth.jwt.JWTokenFactory;
import org.polarsys.eplmp.server.rest.exceptions.FileConversionException;
import org.polarsys.eplmp.server.rest.exceptions.PreconditionFailedException;
import org.polarsys.eplmp.server.rest.exceptions.RequestedRangeNotSatisfiableException;
import org.polarsys.eplmp.server.rest.file.util.BinaryResourceDownloadMeta;
import org.polarsys.eplmp.server.rest.file.util.BinaryResourceDownloadResponseBuilder;
import org.polarsys.eplmp.server.rest.file.util.BinaryResourceUpload;

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
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Collection;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequestScoped
@Api(hidden = true, value = "partBinary", description = "Operations about part files")
@DeclareRoles({UserGroupMapping.REGULAR_USER_ROLE_ID})
public class PartBinaryResource {

    private static final Logger LOGGER = Logger.getLogger(PartBinaryResource.class.getName());
    private static final String UTF8_ENCODING = "UTF-8";

    @Inject
    private IBinaryStorageManagerLocal storageManager;
    @Inject
    private IProductManagerLocal productService;
    @Inject
    private IContextManagerLocal contextManager;
    @Inject
    private IConverterManagerLocal converterService;
    @Inject
    private IShareManagerLocal shareService;
    @Inject
    private IPublicEntityManagerLocal publicEntityManager;
    @Inject
    private IOnDemandConverterManagerLocal onDemandConverterManager;
    @Inject
    private AuthConfig authConfig;

    public PartBinaryResource() {
    }

    @POST
    @ApiOperation(value = "Upload CAD file",
            response = Response.class)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "upload", paramType = "formData", dataType = "file", required = true)
    })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Upload success"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("/{iteration}/" + PartIteration.NATIVE_CAD_SUBTYPE)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    public Response uploadNativeCADFile(
            @Context HttpServletRequest request,
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") final String workspaceId,
            @ApiParam(required = true, value = "Part number") @PathParam("partNumber") final String partNumber,
            @ApiParam(required = true, value = "Part version") @PathParam("version") final String version,
            @ApiParam(required = true, value = "Part iteration") @PathParam("iteration") final int iteration)
            throws EntityNotFoundException, EntityAlreadyExistsException, UserNotActiveException,
            AccessRightException, NotAllowedException, CreationException, WorkspaceNotEnabledException {

        try {

            PartIterationKey partPK = new PartIterationKey(workspaceId, partNumber, version, iteration);
            Collection<Part> parts = request.getParts();

            if (parts.isEmpty() || parts.size() > 1) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            Part part = parts.iterator().next();
            String fileName = part.getSubmittedFileName();
            BinaryResource binaryResource = productService.saveNativeCADInPartIteration(partPK, fileName, 0);
            OutputStream outputStream = storageManager.getBinaryResourceOutputStream(binaryResource);
            long length = BinaryResourceUpload.uploadBinary(outputStream, part);
            productService.saveNativeCADInPartIteration(partPK, fileName, length);
            converterService.convertCADFileToOBJ(partPK, binaryResource);

            return BinaryResourceUpload.tryToRespondCreated(request.getRequestURI() + URLEncoder.encode(fileName, UTF8_ENCODING));

        } catch (IOException | ServletException | StorageException e) {
            return BinaryResourceUpload.uploadError(e);
        }
    }

    @POST
    @ApiOperation(value = "Upload attached file",
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
    @Path("/{iteration}/" + PartIteration.ATTACHED_FILES_SUBTYPE)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    public Response uploadAttachedFiles(
            @Context HttpServletRequest request,
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") final String workspaceId,
            @ApiParam(required = true, value = "Part number") @PathParam("partNumber") final String partNumber,
            @ApiParam(required = true, value = "Part version") @PathParam("version") final String version,
            @ApiParam(required = true, value = "Part iteration") @PathParam("iteration") final int iteration)
            throws EntityNotFoundException, EntityAlreadyExistsException, UserNotActiveException, AccessRightException,
            NotAllowedException, CreationException, WorkspaceNotEnabledException {

        try {

            PartIterationKey partPK = new PartIterationKey(workspaceId, partNumber, version, iteration);
            Collection<Part> formParts = request.getParts();

            String fileName = null;

            for (Part formPart : formParts) {
                fileName = Normalizer.normalize(formPart.getSubmittedFileName(), Normalizer.Form.NFC);
                BinaryResource binaryResource = productService.saveFileInPartIteration(partPK, fileName, PartIteration.ATTACHED_FILES_SUBTYPE, 0);
                OutputStream outputStream = storageManager.getBinaryResourceOutputStream(binaryResource);
                long length = BinaryResourceUpload.uploadBinary(outputStream, formPart);
                productService.saveFileInPartIteration(partPK, fileName, PartIteration.ATTACHED_FILES_SUBTYPE, length);
            }

            if (formParts.size() == 1) {
                return BinaryResourceUpload.tryToRespondCreated(request.getRequestURI() + URLEncoder.encode(fileName, UTF8_ENCODING));
            }

            return Response.noContent().build();

        } catch (IOException | ServletException | StorageException e) {
            return BinaryResourceUpload.uploadError(e);
        }
    }

    @GET
    @ApiOperation(value = "Download part file without a sub type",
            response = File.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Download success"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("/{iteration}/{fileName}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadDirectPartFile(
            @Context Request request,
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") final String workspaceId,
            @ApiParam(required = true, value = "Part number") @PathParam("partNumber") final String partNumber,
            @ApiParam(required = true, value = "Part version") @PathParam("version") final String version,
            @ApiParam(required = true, value = "Part iteration") @PathParam("iteration") final int iteration,
            @ApiParam(required = true, value = "File name") @PathParam("fileName") final String fileName,
            @ApiParam(required = false, value = "Type") @QueryParam("type") String type,
            @ApiParam(required = false, value = "Output") @QueryParam("output") String output,
            @ApiParam(required = false, value = "Range") @HeaderParam("Range") String range,
            @ApiParam(required = false, value = "Shared entity uuid") @QueryParam("uuid") final String uuid,
            @ApiParam(required = false, value = "Password for private resource") @HeaderParam("password") String password,
            @ApiParam(required = false, value = "Shared entity token") @QueryParam("token") String accessToken)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, NotAllowedException,
            PreconditionFailedException, RequestedRangeNotSatisfiableException, WorkspaceNotEnabledException {
        return downloadPartFile(request, workspaceId, partNumber, version, iteration, null, fileName, type, output, range, uuid, password, accessToken);
    }

    @GET
    @ApiOperation(value = "Download part file with a sub type",
            response = File.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Download success"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Forbidden"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Path("/{iteration}/{subType}/{fileName}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadPartFile(
            @Context Request request,
            @ApiParam(required = true, value = "Workspace id") @PathParam("workspaceId") final String workspaceId,
            @ApiParam(required = true, value = "Part number") @PathParam("partNumber") final String partNumber,
            @ApiParam(required = true, value = "Part version") @PathParam("version") final String version,
            @ApiParam(required = true, value = "Part iteration") @PathParam("iteration") final int iteration,
            @ApiParam(required = true, value = "File sub type") @PathParam("subType") String subType,
            @ApiParam(required = true, value = "File name") @PathParam("fileName") final String fileName,
            @ApiParam(required = false, value = "Type") @QueryParam("type") String type,
            @ApiParam(required = false, value = "Output") @QueryParam("output") String output,
            @ApiParam(required = false, value = "Range") @HeaderParam("Range") String range,
            @ApiParam(required = false, value = "Shared entity uuid") @QueryParam("uuid") final String uuid,
            @ApiParam(required = false, value = "Password for private resource") @HeaderParam("password") String password,
            @ApiParam(required = false, value = "Shared entity token") @QueryParam("token") String accessToken)
            throws EntityNotFoundException, UserNotActiveException, AccessRightException, NotAllowedException,
            PreconditionFailedException, RequestedRangeNotSatisfiableException, WorkspaceNotEnabledException {

        BinaryResource binaryResource;
        String decodedFileName = fileName;
        InputStream binaryContentInputStream;

        boolean isWorkingCopy = false;
        try {
            decodedFileName = URLDecoder.decode(fileName, UTF8_ENCODING);
        } catch (UnsupportedEncodingException e) {
            LOGGER.log(Level.SEVERE, "Cannot decode filename", e);
        }

        String fullName = workspaceId + "/parts/" + FileIO.encode(partNumber) + "/" + version + "/" + iteration + "/";

        if (subType != null && !subType.isEmpty()) {
            fullName = fullName + subType + "/";
        }

        fullName = fullName + decodedFileName;

        if (uuid != null && !uuid.isEmpty()) {

            SharedEntity sharedEntity = shareService.findSharedEntityForGivenUUID(uuid);

            if (accessToken != null && !accessToken.isEmpty()) {
                String decodedUUID = JWTokenFactory.validateSharedResourceToken(authConfig.getJWTKey(), accessToken);
                if (null == decodedUUID || !decodedUUID.equals(sharedEntity.getUuid())) {
                    throw new NotAllowedException("NotAllowedException73");
                }
            } else {
                // Check uuid & access right
                checkUuidValidity(sharedEntity, workspaceId, partNumber, version, iteration, password);
            }

            binaryResource = publicEntityManager.getBinaryResourceForSharedEntity(fullName);
            // sharedEntity is always a SharedPart

            if (sharedEntity instanceof SharedPart) {

                SharedPart part = (SharedPart) sharedEntity;

                PartRevision partRevision = part.getPartRevision();

                PartIteration workingIteration = partRevision.getWorkingCopy();

                isWorkingCopy = partRevision.getLastIteration().equals(workingIteration);
            }

        } else {
            // Check access right

            if (accessToken != null && !accessToken.isEmpty()) {
                String decodedEntityKey = JWTokenFactory.validateEntityToken(authConfig.getJWTKey(), accessToken);
                boolean tokenValid = new PartRevisionKey(workspaceId, partNumber, version).toString().equals(decodedEntityKey);
                if (!tokenValid) {
                    throw new NotAllowedException("NotAllowedException73");
                }
                binaryResource = publicEntityManager.getBinaryResourceForSharedEntity(fullName);
            } else {
                if (!canAccess(new PartIterationKey(workspaceId, partNumber, version, iteration))) {
                    throw new NotAllowedException("NotAllowedException73");
                }
                binaryResource = getBinaryResource(fullName);
                PartRevision partRevision = productService.getPartRevision(new PartRevisionKey(workspaceId, partNumber, version));
                PartIteration workingIteration = partRevision.getWorkingCopy();
                if(workingIteration != null){
                    isWorkingCopy = workingIteration.getIteration() == iteration;
                }
            }
        }

        BinaryResourceDownloadMeta binaryResourceDownloadMeta = new BinaryResourceDownloadMeta(binaryResource, output, type);

        // Check cache precondition
        Response.ResponseBuilder rb = request.evaluatePreconditions(binaryResourceDownloadMeta.getLastModified(), binaryResourceDownloadMeta.getETag());
        if (rb != null) {
            return rb.build();
        }

        boolean isToBeCached = !isWorkingCopy;

        try {
            if (PartIteration.ATTACHED_FILES_SUBTYPE.equals(subType) && output != null && !output.isEmpty()) {
                binaryContentInputStream = getConvertedBinaryResource(binaryResource, output);
                if (range == null || range.isEmpty()) {
                    binaryResourceDownloadMeta.setLength(0);
                }
            } else {
                binaryContentInputStream = storageManager.getBinaryResourceInputStream(binaryResource);
            }
            return BinaryResourceDownloadResponseBuilder.prepareResponse(binaryContentInputStream, binaryResourceDownloadMeta, range, isToBeCached);
        } catch (StorageException | FileConversionException e) {
            return BinaryResourceDownloadResponseBuilder.downloadError(e, fullName);
        }
    }

    /**
     * Try to convert a binary resource to a specific format
     *
     * @param binaryResource The binary resource
     * @param outputFormat   The wanted output
     * @return The binary resource stream in the wanted output
     * @throws FileConversionException
     */

    private InputStream getConvertedBinaryResource(BinaryResource binaryResource, String outputFormat) throws FileConversionException {
        try {
            return onDemandConverterManager.getPartConvertedResource(outputFormat, binaryResource);
        } catch (Exception e) {
            throw new FileConversionException(e);
        }
    }

    private boolean canAccess(PartIterationKey partIKey) throws UserNotActiveException, EntityNotFoundException, WorkspaceNotEnabledException {
        return publicEntityManager.canAccess(partIKey) || contextManager.isCallerInRole(UserGroupMapping.REGULAR_USER_ROLE_ID) && productService.canAccess(partIKey);
    }

    private BinaryResource getBinaryResource(String fullName)
            throws NotAllowedException, AccessRightException, UserNotActiveException, EntityNotFoundException, WorkspaceNotEnabledException {
        BinaryResource publicBinaryResourceForPart = publicEntityManager.getPublicBinaryResourceForPart(fullName);
        if (publicBinaryResourceForPart != null) {
            return publicBinaryResourceForPart;
        }
        return productService.getBinaryResource(fullName);
    }

    private void checkUuidValidity(SharedEntity sharedEntity, String workspaceId, String partNumber, String version, int iteration, String password)
            throws NotAllowedException {
        if (!(sharedEntity instanceof SharedPart)) {
            throw new NotAllowedException("NotAllowedException73");
        }

        checkUuidExpiredDate(sharedEntity);
        checkUuidPassword(sharedEntity, password);

        String shareEntityWorkspaceId = sharedEntity.getWorkspace().getId();
        PartRevision partRevision = ((SharedPart) sharedEntity).getPartRevision();
        PartIteration lastCheckedInIteration = partRevision.getLastCheckedInIteration();
        if (!shareEntityWorkspaceId.equals(workspaceId) ||
                !partRevision.getPartMasterNumber().equals(partNumber) ||
                !partRevision.getVersion().equals(version) ||
                (null != lastCheckedInIteration && lastCheckedInIteration.getIteration() < iteration)) {
            throw new NotAllowedException("NotAllowedException73");
        }
    }

    private void checkUuidPassword(SharedEntity sharedEntity, String password) throws NotAllowedException {
        String entityPassword = sharedEntity.getPassword();
        if (entityPassword != null && !entityPassword.isEmpty()) {
            try {
                if (password == null || password.isEmpty() || !entityPassword.equals(HashUtils.md5Sum(password))) {
                    throw new NotAllowedException("NotAllowedException73");
                }
            } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
                throw new NotAllowedException("NotAllowedException73");
            }
        }
    }

    private void checkUuidExpiredDate(SharedEntity sharedEntity) throws NotAllowedException {
        // Check shared entity expired
        if (sharedEntity.getExpireDate() != null && sharedEntity.getExpireDate().getTime() < new Date().getTime()) {
            shareService.deleteSharedEntityIfExpired(sharedEntity);
            throw new NotAllowedException("NotAllowedException73");
        }
    }

}
