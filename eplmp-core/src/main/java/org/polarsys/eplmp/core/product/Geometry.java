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


package org.polarsys.eplmp.core.product;

import org.polarsys.eplmp.core.common.BinaryResource;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

/**
 * Wraps a CAD file providing quality information.
 * Higher quality means that more faces are defined.
 *
 * This CAD file is not the native one used by the authoring tool but the generated one
 * used by the web browser viewer.
 * 
 * @author Florent Garin
 * @version 1.1, 20/07/12
 * @since   V1.1
 */
@Table(name="GEOMETRY")
@Entity
public class Geometry extends BinaryResource{

    /**
     * Starts at 0, smaller is greater.
     */
    private int quality;


    //Bounding box
    @Column(name="X_MIN")
    private double xMin = 0;

    @Column(name="Y_MIN")
    private double yMin = 0;

    @Column(name="Z_MIN")
    private double zMin = 0;

    @Column(name="X_MAX")
    private double xMax = 0;

    @Column(name="Y_MAX")
    private double yMax = 0;

    @Column(name="Z_MAX")
    private double zMax = 0;

    public Geometry() {
    }
    
    public Geometry(int pQuality, String pFullName, long pContentLength, Date pLastModified) {
        super(pFullName, pContentLength, pLastModified);
        this.quality=pQuality;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int quality) {
        this.quality = quality;
    }

    public void setBox(double x1, double y1, double z1, double x2, double y2, double z2) {
        xMin = Math.min(x1, x2);
        xMax = Math.max(x1, x2);
        yMin = Math.min(y1, y2);
        yMax = Math.max(y1, y2);
        zMin = Math.min(z1, z2);
        zMax = Math.max(z1, z2);
    }

    public double getxMin() {
        return xMin;
    }

    public void setxMin(double xMin) {
        this.xMin = xMin;
    }

    public double getyMin() {
        return yMin;
    }

    public void setyMin(double yMin) {
        this.yMin = yMin;
    }

    public double getzMin() {
        return zMin;
    }

    public void setzMin(double zMin) {
        this.zMin = zMin;
    }

    public double getxMax() {
        return xMax;
    }

    public void setxMax(double xMax) {
        this.xMax = xMax;
    }

    public double getyMax() {
        return yMax;
    }

    public void setyMax(double yMax) {
        this.yMax = yMax;
    }

    public double getzMax() {
        return zMax;
    }

    public void setzMax(double zMax) {
        this.zMax = zMax;
    }

    @Override
    public int compareTo(BinaryResource pBinaryResource) {
        if (!(pBinaryResource instanceof Geometry)) {
            return super.compareTo(pBinaryResource);
        }

        int ret = quality - ((Geometry) pBinaryResource).quality;
        if(ret == 0){
            return  fullName.compareTo(((Geometry) pBinaryResource).fullName);
        }
        return ret;

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Geometry)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        Geometry geometry = (Geometry) o;
        return quality == geometry.quality;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + quality;
        return result;
    }
}
