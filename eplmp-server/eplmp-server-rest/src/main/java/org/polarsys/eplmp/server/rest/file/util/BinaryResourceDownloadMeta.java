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

package org.polarsys.eplmp.server.rest.file.util;

import org.polarsys.eplmp.core.common.BinaryResource;
import org.polarsys.eplmp.server.rest.util.FileDownloadTools;

import javax.activation.FileTypeMap;
import javax.activation.MimetypesFileTypeMap;
import javax.ws.rs.core.EntityTag;
import java.util.Date;
import java.util.logging.Logger;

/**
 *
 */
public class BinaryResourceDownloadMeta {
    private static final Logger LOGGER = Logger.getLogger(BinaryResourceDownloadMeta.class.getName());
    private static final String CHARSET = "UTF-8";
    private static MimetypesFileTypeMap fileTypeMap = null;
    private String fullName;
    private String outputFormat;
    private String downloadType;
    private long length;
    private Date lastModified;

    public BinaryResourceDownloadMeta(BinaryResource binaryResource, String outputFormat, String downloadType) {
        this.fullName = binaryResource.getName();
        this.outputFormat = outputFormat;
        this.downloadType = downloadType;
        this.length = binaryResource.getContentLength();
        this.lastModified = binaryResource.getLastModified();
        if (fileTypeMap == null) {
            BinaryResourceDownloadMeta.initFileTypeMap();
        }
    }

    public BinaryResourceDownloadMeta(BinaryResource binaryResource) {
        this(binaryResource, null, null);
    }

    private static void initFileTypeMap() {
        fileTypeMap = new MimetypesFileTypeMap();

        // Additional MIME types
        fileTypeMap.addMimeTypes("application/atom+xml atom");
        fileTypeMap.addMimeTypes("application/msword doc dot");
        fileTypeMap.addMimeTypes("application/mspowerpoint ppt pot");
        fileTypeMap.addMimeTypes("application/msexcel xls");
        fileTypeMap.addMimeTypes("application/pdf pdf");
        fileTypeMap.addMimeTypes("application/rdf+xml rdf rss");
        fileTypeMap.addMimeTypes("application/x-vnd.openxmlformat docx docm dotx dotm");
        fileTypeMap.addMimeTypes("application/x-vnd.openxmlformat xlsx xlsm");
        fileTypeMap.addMimeTypes("application/x-vnd.openxmlformat pptx pptm potx");
        fileTypeMap.addMimeTypes("application/x-javascript js");
        fileTypeMap.addMimeTypes("application/x-rar-compressed rar");
        fileTypeMap.addMimeTypes("application/x-textedit bat cmd");
        fileTypeMap.addMimeTypes("application/zip zip");
        fileTypeMap.addMimeTypes("audio/mpeg mp3");
        fileTypeMap.addMimeTypes("image/bmp bmp");
        fileTypeMap.addMimeTypes("image/gif gif");
        fileTypeMap.addMimeTypes("image/jpeg jpg jpeg jpe");
        fileTypeMap.addMimeTypes("image/png png");
        fileTypeMap.addMimeTypes("text/css css");
        fileTypeMap.addMimeTypes("text/csv csv");
        fileTypeMap.addMimeTypes("text/html htm html");
        fileTypeMap.addMimeTypes("text/xml xml");
        fileTypeMap.addMimeTypes("video/quicktime qt mov moov");
        fileTypeMap.addMimeTypes("video/mpeg mpeg mpg mpe mpv vbs mpegv");
        fileTypeMap.addMimeTypes("video/msvideo avi");
        fileTypeMap.addMimeTypes("video/mp4 mp4");
        fileTypeMap.addMimeTypes("video/ogg ogg");

        FileTypeMap.setDefaultFileTypeMap(fileTypeMap);
    }

    /**
     * Get the full name of the file
     *
     * @return Full name of the file
     */
    public String getFullName() {
        return fullName;
    }

    public boolean isConverted() {
        return outputFormat != null && !outputFormat.isEmpty();
    }

    /**
     * Get the file size
     *
     * @return File size
     */
    public long getLength() {
        return length;
    }

    /**
     * Get the last modification date of the file
     *
     * @return Last modification date
     */
    public Date getLastModified() {
        return (lastModified != null) ? (Date) lastModified.clone() : null;
    }

    /**
     * Get the last modification date of the file
     *
     * @return Last modification date
     */
    public long getLastModifiedTime() {
        return (lastModified != null) ? lastModified.getTime() : 0;
    }

    /**
     * Get file entity tag
     *
     * @return Unique Entity Tag for the file
     */
    public EntityTag getETag() {
        //Todo add iteration and version
        //Todo remove special char from full Name
        return new EntityTag(fullName + "_" + length + "_" + lastModified.getTime());
    }


    /**
     * Get the Content type for this file
     *
     * @return Http Response content type
     */
    public String getContentType() {
        String contentType;
        if (outputFormat != null) {
            contentType = FileTypeMap.getDefaultFileTypeMap().getContentType(fullName + "." + outputFormat);
        } else {
            contentType = FileTypeMap.getDefaultFileTypeMap().getContentType(fullName);
        }


        if (contentType != null && contentType.startsWith("text")) {
            contentType += ";charset=" + CHARSET;
        }

        return (contentType != null) ? contentType : "application/octet-stream";
    }

    /**
     * Get the Content disposition for this file
     *
     * @return Http response 'Content-Disposition' header value
     */
    // Todo check if we can have decoding contentDisposition
    // Todo : check accept header in request
    public String getContentDisposition() {
        String fileName = FileDownloadTools.getFileName(fullName, outputFormat);
        return FileDownloadTools.getContentDisposition(downloadType, fileName);
    }

    public void setLength(int length) {
        this.length = length;
    }
}
