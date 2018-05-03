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

package org.polarsys.eplmp.server.importers;

import java.io.Serializable;

/**
 * Minimalistic Attribute Model class
 * Easier to get a name, type, and if LOV(List Of Values) type LOV name
 *
 * @author Laurent Le Van
 * @version 1.0.0
 * @since 04/02/16.
 */
public class AttributeModel implements Serializable {

    private String name;
    private String type;
    private String lovName;

    /**
     * Init of a new empty attribute model
     */
    public AttributeModel() {
        this.name = null;
        this.type = null;
        this.lovName = null;
    }

    /**
     * Constructor that takes name and type
     *
     * @param name
     * @param type can be URL, Date, Boolean, Number, ListOfValues
     */
    public AttributeModel(String name, String type) {
        this.name = name;
        this.type = type;
        this.lovName = null;
    }

    /**
     * Constructor for an ListOfValue Attribute
     *
     * @param name
     * @param type
     * @param lovName
     */
    public AttributeModel(String name, String type, String lovName) {
        this.name = name;
        this.type = type;
        this.lovName = lovName;
    }

    /**
     * To get name
     *
     * @return the name of this attribute
     */
    public String getName() {
        return this.name;
    }

    /**
     * To set name
     *
     * @param name name of this attribute
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the type of this attribute
     */
    public String getType() {
        return this.type;
    }

    /**
     * @param type can be URL, Date, Boolean, Number, ListOfValues
     */
    public void setType(String type) {
        this.type = type;
    }

    public String toString() {
        if (this.lovName != null) {
            return "Nom : " + this.name + "\nType : " + this.type + " " + this.lovName + "\n";
        } else {
            return "Nom : " + this.name + "\nType : " + this.type + "\n ";
        }
    }

    public String getLovName() {
        return this.lovName;
    }

}
