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

package org.polarsys.eplmp.core.util;

import java.io.*;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class for file manipulations and information extraction.
 *
 * @author Florent Garin
 */
public class FileIO {

    private static final int CHUNK_SIZE = 1024 * 8;
    private static final int BUFFER_CAPACITY = 1024 * 16;

    private static final List<String> DOC_EXTENSIONS = Arrays.asList("odt", "html", "sxw", "swf", "sxc", "doc", "docx", "xls", "xlsx", "rtf", "txt", "ppt", "pptx", "odp", "wpd", "tsv", "sxi", "csv", "pdf");
    private static final String ENCODING_CHARSET = "UTF-8";

    private static final Logger LOGGER = Logger.getLogger(FileIO.class.getName());

    private FileIO() {
    }

    public static void rmDir(File pDir) {
        if (pDir.isDirectory()) {
            File[] files = pDir.listFiles();
            if (files != null) {
                for (File subFile : files) {
                    if (subFile.isDirectory()) {
                        rmDir(subFile);
                    } else {
                        subFile.delete();
                    }
                }
                pDir.delete();
            }
        }
    }

    public static void copyFile(File pIn, File pOut) throws IOException {
        pOut.getParentFile().mkdirs();
        pOut.createNewFile();
        try (InputStream in = new BufferedInputStream(new FileInputStream(pIn), BUFFER_CAPACITY);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(pOut), BUFFER_CAPACITY)) {
            FileIO.copyBufferedStream(in, out);
        }
    }

    public static void copyBufferedStream(InputStream in, OutputStream out) throws IOException {
        byte[] data = new byte[CHUNK_SIZE];
        int length;
        while ((length = in.read(data)) != -1) {
            out.write(data, 0, length);
        }
    }

    public static String getExtension(String fileName) {
        String ext = null;
        int i = fileName.lastIndexOf('.');
        if (i > 0 && i < fileName.length() - 1) {
            ext = fileName.substring(i + 1).toLowerCase();
        }
        return ext;
    }

    public static String getFileNameWithoutExtension(String fileName) {
        int index = fileName.lastIndexOf(".");
        if (index != -1) {
            return fileName.substring(0, index);
        } else {
            return fileName;
        }
    }


    public static boolean isDocFile(String fileName) {
        String ext = getExtension(fileName);
        return DOC_EXTENSIONS.contains(ext);
    }

    public static String encode(String toEncode) {
        try {
            return URLEncoder.encode(toEncode, ENCODING_CHARSET);
        } catch (UnsupportedEncodingException e) {
            LOGGER.log(Level.SEVERE, "Cannot encode string " + toEncode, e);
            return toEncode;
        }
    }


    public static long copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[CHUNK_SIZE];
        long totalRead = 0L;
        int len;

        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
            totalRead += len;
        }

        return totalRead;
    }

}
