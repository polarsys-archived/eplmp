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

package org.polarsys.eplmp.server.rest.util;

import org.dozer.DozerBeanMapperSingletonWrapper;
import org.dozer.Mapper;
import org.polarsys.eplmp.core.configuration.ProductStructureFilter;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.meta.InstanceAttribute;
import org.polarsys.eplmp.core.product.*;
import org.polarsys.eplmp.core.services.IProductManagerLocal;
import org.polarsys.eplmp.core.util.Tools;
import org.polarsys.eplmp.server.rest.collections.InstanceCollection;
import org.polarsys.eplmp.server.rest.collections.VirtualInstanceCollection;
import org.polarsys.eplmp.server.rest.dto.InstanceAttributeDTO;

import javax.json.stream.JsonGenerator;
import javax.vecmath.Matrix3d;
import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Taylor LABEJOF
 */
public class InstanceBodyWriterTools {

    private static final Logger LOGGER = Logger.getLogger(InstanceBodyWriterTools.class.getName());
    private static Mapper mapper = DozerBeanMapperSingletonWrapper.getInstance();

    public static void generateInstanceStreamWithGlobalMatrix(IProductManagerLocal productService, List<PartLink> currentPath, Matrix4d matrix, InstanceCollection instanceCollection, List<Integer> instanceIds, JsonGenerator jg) {

        try {

            if (currentPath == null) {
                PartLink rootPartUsageLink = productService.getRootPartUsageLink(instanceCollection.getCiKey());
                currentPath = new ArrayList<>();
                currentPath.add(rootPartUsageLink);
            }

            Component component = productService.filterProductStructure(instanceCollection.getCiKey(),
                    instanceCollection.getFilter(), currentPath, 1);

            PartLink partLink = component.getPartLink();
            PartIteration partI = component.getRetainedIteration();

            // Filter ACL on part
            if (!productService.canAccess(partI.getPartRevision().getKey())) {
                return;
            }

            for (CADInstance instance : partLink.getCadInstances()) {

                List<Integer> copyInstanceIds = new ArrayList<>(instanceIds);
                copyInstanceIds.add(instance.getId());
                Vector3d instanceTranslation = new Vector3d(instance.getTx(), instance.getTy(), instance.getTz());
                Matrix4d combinedMatrix;
                switch (instance.getRotationType()) {
                    case ANGLE:
                        Vector3d instanceRotation = new Vector3d(instance.getRx(), instance.getRy(), instance.getRz());
                        combinedMatrix = combineTransformation(matrix, instanceTranslation, instanceRotation);
                        break;
                    case MATRIX:
                        Matrix4d rotationMatrix = new Matrix4d(new Matrix3d(instance.getRotationMatrix().getValues()), instanceTranslation, 1);
                        combinedMatrix = combineTransformation(matrix, rotationMatrix);
                        break;
                    default:
                        LOGGER.log(Level.SEVERE, "Unknown rotation Type, matrix not calculated");
                        combinedMatrix = matrix;
                }


                if (!partI.isAssembly() && !partI.getGeometries().isEmpty() && instanceCollection.isFiltered(currentPath)) {
                    writeLeaf(currentPath, copyInstanceIds, partI, combinedMatrix, jg);
                } else {
                    for (Component subComponent : component.getComponents()) {
                        generateInstanceStreamWithGlobalMatrix(productService, subComponent.getPath(), combinedMatrix, instanceCollection, copyInstanceIds, jg);
                    }
                }
            }

        } catch (PartMasterNotFoundException | PartRevisionNotFoundException | PartUsageLinkNotFoundException | UserNotFoundException | WorkspaceNotFoundException | WorkspaceNotEnabledException | ConfigurationItemNotFoundException e) {
            LOGGER.log(Level.SEVERE, null, e);
        } catch (AccessRightException | EntityConstraintException | NotAllowedException | UserNotActiveException e) {
            LOGGER.log(Level.FINEST, null, e);
        }

    }

    public static void generateInstanceStreamWithGlobalMatrix(IProductManagerLocal productService, List<PartLink> currentPath, Matrix4d matrix, VirtualInstanceCollection virtualInstanceCollection, List<Integer> instanceIds, JsonGenerator jg) {
        try {

            PartLink partLink = currentPath.get(currentPath.size() - 1);
            ProductStructureFilter filter = virtualInstanceCollection.getFilter();
            List<PartIteration> filteredPartIterations = filter.filter(partLink.getComponent());

            if (!filteredPartIterations.isEmpty()) {

                PartIteration partI = filteredPartIterations.iterator().next();

                // Filter ACL on part
                if (!productService.canAccess(partI.getPartRevision().getKey())) {
                    return;
                }

                for (CADInstance instance : partLink.getCadInstances()) {

                    List<Integer> copyInstanceIds = new ArrayList<>(instanceIds);
                    copyInstanceIds.add(instance.getId());
                    Vector3d instanceTranslation = new Vector3d(instance.getTx(), instance.getTy(), instance.getTz());

                    Matrix4d combinedMatrix;

                    switch (instance.getRotationType()) {
                        case ANGLE: {
                            Vector3d instanceRotation = new Vector3d(instance.getRx(), instance.getRy(), instance.getRz());
                            combinedMatrix = combineTransformation(matrix, instanceTranslation, instanceRotation);
                            break;
                        }
                        case MATRIX: {
                            Matrix4d rotationMatrix = new Matrix4d(new Matrix3d(instance.getRotationMatrix().getValues()), instanceTranslation, 1);
                            combinedMatrix = combineTransformation(matrix, rotationMatrix);
                            break;
                        }
                        default: {
                            LOGGER.log(Level.SEVERE, "Unknown rotation Type, matrix not calculated");
                            combinedMatrix = matrix;
                        }
                    }

                    if (!partI.isAssembly() && !partI.getGeometries().isEmpty()) {
                        writeLeaf(currentPath, copyInstanceIds, partI, combinedMatrix, jg);
                    } else {
                        for (PartLink subLink : partI.getComponents()) {
                            List<PartLink> subPath = new ArrayList<>(currentPath);
                            subPath.add(subLink);
                            generateInstanceStreamWithGlobalMatrix(productService, subPath, combinedMatrix, virtualInstanceCollection, copyInstanceIds, jg);
                        }
                    }
                }
            }

        } catch (UserNotFoundException | UserNotActiveException | WorkspaceNotFoundException | WorkspaceNotEnabledException | PartRevisionNotFoundException e) {
            LOGGER.log(Level.SEVERE, null, e);
        }

    }

    static Matrix4d combineTransformation(Matrix4d matrix, Vector3d translation, Vector3d rotation) {
        Matrix4d gM = new Matrix4d(matrix);
        Matrix4d m = new Matrix4d();

        m.setIdentity();
        m.setTranslation(translation);
        gM.mul(m);

        m.setIdentity();
        m.rotZ(rotation.z);
        gM.mul(m);

        m.setIdentity();
        m.rotY(rotation.y);
        gM.mul(m);

        m.setIdentity();
        m.rotX(rotation.x);
        gM.mul(m);

        return gM;
    }

    static Matrix4d combineTransformation(Matrix4d matrix, Matrix4d transformation) {
        Matrix4d gM = new Matrix4d(matrix);
        gM.mul(transformation);

        return gM;
    }

    private static void writeLeaf(List<PartLink> currentPath, List<Integer> copyInstanceIds, PartIteration partI, Matrix4d combinedMatrix, JsonGenerator jg) {
        String partIterationId = partI.toString();
        List<InstanceAttributeDTO> attributes = new ArrayList<>();
        for (InstanceAttribute attr : partI.getInstanceAttributes()) {
            attributes.add(mapper.map(attr, InstanceAttributeDTO.class));
        }

        jg.writeStartObject();
        jg.write("id", Tools.getPathInstanceAsString(currentPath, copyInstanceIds));
        jg.write("partIterationId", partIterationId);
        jg.write("path", Tools.getPathAsString(currentPath));

        writeMatrix(combinedMatrix, jg);
        writeGeometries(partI.getSortedGeometries(), jg);
        writeAttributes(attributes, jg);

        jg.writeEnd();
        jg.flush();
    }

    private static void writeMatrix(Matrix4d matrix, JsonGenerator jg) {
        jg.writeStartArray("matrix");
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                jg.write(matrix.getElement(i, j));
            }
        }
        jg.writeEnd();
    }

    private static void writeGeometries(List<Geometry> files, JsonGenerator jg) {
        jg.write("qualities", files.size());

        if (!files.isEmpty()) {
            Geometry geometry = files.get(0);
            jg.write("xMin", geometry.getxMin());
            jg.write("yMin", geometry.getyMin());
            jg.write("zMin", geometry.getzMin());
            jg.write("xMax", geometry.getxMax());
            jg.write("yMax", geometry.getyMax());
            jg.write("zMax", geometry.getzMax());
        }

        jg.writeStartArray("files");

        for (Geometry g : files) {
            jg.writeStartObject();
            jg.write("fullName", "api/files/" + g.getFullName());
            jg.writeEnd();
        }
        jg.writeEnd();
    }

    private static void writeAttributes(List<InstanceAttributeDTO> attributes, JsonGenerator jg) {
        jg.writeStartArray("attributes");
        for (InstanceAttributeDTO a : attributes) {
            jg.writeStartObject();
            jg.write("name", a.getName());
            jg.write("type", a.getType().toString());
            jg.write("value", a.getValue());
            jg.writeEnd();
        }
        jg.writeEnd();
    }
}
