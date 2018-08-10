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

package org.polarsys.eplmp.server.storage.filesystem;

import org.apache.commons.io.FileUtils;
import org.polarsys.eplmp.core.common.BinaryResource;
import org.polarsys.eplmp.core.exceptions.FileNotFoundException;
import org.polarsys.eplmp.core.exceptions.StorageException;
import org.polarsys.eplmp.core.util.FileIO;
import org.polarsys.eplmp.core.util.Tools;
import org.polarsys.eplmp.server.storage.StorageProvider;

import java.io.*;
import java.util.Date;
import java.util.logging.Logger;


public class FileStorageProvider implements StorageProvider {

    private final String vaultPath;

    private static final Logger LOGGER = Logger.getLogger(StorageProvider.class.getName());

    public FileStorageProvider(String vaultPath) {
        this.vaultPath = vaultPath;
    }


    @Override
    public InputStream getBinaryResourceInputStream(BinaryResource pBinaryResource) throws StorageException, FileNotFoundException {
        File file = new File(getVirtualPath(pBinaryResource));
        return getInputStream(file);
    }

    @Override
    public File getBinaryResourceFile(BinaryResource pBinaryResource) throws StorageException, FileNotFoundException {
        File file = new File(getVirtualPath(pBinaryResource));
        if (file.exists()) {
            try {
                FileInputStream fileInputStream = new FileInputStream(file);
                fileInputStream.close();
                return file;
            } catch (IOException e) {
                throw new StorageException(e.getMessage(), e);
            }
        } else {
            throw new FileNotFoundException(file.getAbsolutePath() + " not found");
        }
    }

    @Override
    public OutputStream getBinaryResourceOutputStream(BinaryResource pBinaryResource) throws StorageException {
        File file = new File(getVirtualPath(pBinaryResource));
        file.getParentFile().mkdirs();
        try {
            return new BufferedOutputStream(new FileOutputStream(file));
        } catch (java.io.FileNotFoundException e) {
            throw new StorageException(e.getMessage(), e);
        }
    }


    @Override
    public void copyData(BinaryResource pSourceBinaryResource, BinaryResource pTargetBinaryResource) throws StorageException, FileNotFoundException  {
        File source = new File(getVirtualPath(pSourceBinaryResource));
        if (source.exists()) {
            File target = new File(getVirtualPath(pTargetBinaryResource));
            try {
                FileIO.copyFile(source, target);
            } catch (IOException e) {
                throw new StorageException("Error copying " + pSourceBinaryResource.getFullName() + " to " + pTargetBinaryResource.getFullName(), e);
            }
        } else {
            throw new FileNotFoundException("Can't find source file to copy " + pSourceBinaryResource.getFullName());
        }
    }

    @Override
    public boolean exists(BinaryResource binaryResource, String generatedFileName) {
        File generatedFile = new File(getGeneratedFilesFolder(binaryResource), Tools.unAccent(generatedFileName));
        return generatedFile.exists();
    }

    @Override
    public Date getLastModified(BinaryResource binaryResource, String generatedFileName) throws FileNotFoundException {
        File generatedFile = new File(getGeneratedFilesFolder(binaryResource), Tools.unAccent(generatedFileName));
        if (generatedFile.exists()) {
            return new Date(generatedFile.lastModified());
        } else {
            throw new FileNotFoundException("Can't find source file to get last modified date " + binaryResource.getFullName());
        }
    }

    @Override
    public InputStream getGeneratedFileInputStream(BinaryResource pBinaryResource, String generatedFileName) throws StorageException, FileNotFoundException {
        File generatedFile = new File(getGeneratedFilesFolder(pBinaryResource), Tools.unAccent(generatedFileName));
        return getInputStream(generatedFile);
    }

    @Override
    public OutputStream getGeneratedFileOutputStream(BinaryResource binaryResource, String generatedFileName) throws StorageException {
        File generatedFile = new File(getGeneratedFilesFolder(binaryResource), Tools.unAccent(generatedFileName));
        generatedFile.getParentFile().mkdirs();
        try {
            return new BufferedOutputStream(new FileOutputStream(generatedFile));
        } catch (java.io.FileNotFoundException e) {
            throw new StorageException(e.getMessage(), e);
        }
    }

    @Override
    public File copyFile(File source, BinaryResource pTargetBinaryResource) throws StorageException, FileNotFoundException {
        if (source.exists()) {
            File target = new File(getVirtualPath(pTargetBinaryResource));
            try {
                FileIO.copyFile(source, target);
                return target;
            } catch (IOException e) {
                throw new StorageException("Error copying " + source.getAbsolutePath() + " to " + pTargetBinaryResource.getFullName(), e);
            }
        } else {
            throw new FileNotFoundException("Can't find source file to copy " + source.getAbsolutePath());
        }
    }

    @Override
    public void delData(BinaryResource pBinaryResource) {
        File fileToRemove = new File(getVirtualPath(pBinaryResource));
        fileToRemove.delete();
        deleteGeneratedFiles(pBinaryResource);
        cleanRemove(fileToRemove.getParentFile());
    }

    @Override
    public String getExternalResourceURI(BinaryResource binaryResource) {
        return null;
    }

    @Override
    public String getShortenExternalResourceURI(BinaryResource binaryResource) {
        return null;
    }

    @Override
    public void deleteWorkspaceFolder(String workspaceId) throws StorageException {
        if(workspaceId != null && !workspaceId.isEmpty()){
            try{
                File rootFolder = new File(vaultPath + "/" + workspaceId);
                if(rootFolder.exists()){
                    FileUtils.deleteDirectory(rootFolder);
                }
            } catch (IOException e) {
                throw new StorageException("Error deleting directory for workspace " + workspaceId, e);
            }
        }
    }

    @Override
    public void renameData(File src, String pNewName) throws StorageException {
        if(src.exists()){
            src.renameTo(new File(src.getParentFile().getAbsolutePath() + "/" + Tools.unAccent(pNewName)));
        }else{
            throw new StorageException("Error renaming file " + src.getAbsolutePath());
        }
    }

    private String getVirtualPath(BinaryResource pBinaryResource) {
        String normalizedName = Tools.unAccent(pBinaryResource.getFullName());
        return this.vaultPath + "/" + normalizedName;
    }

    private File getGeneratedFilesFolder(BinaryResource pBinaryResource) {
        File binaryResourceFile = new File(getVirtualPath(pBinaryResource));
        return new File(binaryResourceFile.getParentFile(), "_" + binaryResourceFile.getName());
    }

    private void cleanRemove(File pFile) {
        if(!pFile.equals(new File(vaultPath)) && pFile.delete())
            cleanRemove(pFile.getParentFile());
    }

    private InputStream getInputStream(File file) throws StorageException, FileNotFoundException {
        if (file.exists()) {
            try {
                return new BufferedInputStream(new FileInputStream(file));
            } catch (java.io.FileNotFoundException e) {
                throw new StorageException(e.getMessage(), e);
            }
        } else {
            throw new FileNotFoundException(file.getAbsolutePath() + " not found");
        }
    }

    private void deleteGeneratedFiles(BinaryResource binaryResource) {
        File genFolder = getGeneratedFilesFolder(binaryResource);
        if (genFolder.exists()) {
            FileIO.rmDir(genFolder);
        }
    }
}
