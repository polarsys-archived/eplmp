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
package org.polarsys.eplmp.server.dao;

import org.polarsys.eplmp.core.exceptions.MarkerNotFoundException;
import org.polarsys.eplmp.core.product.Marker;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;


@RequestScoped
public class MarkerDAO {

    @Inject
    private EntityManager em;

    public MarkerDAO() {
    }

    public void createMarker(Marker pMarker) {
        em.persist(pMarker);
        em.flush();
    }

    public Marker loadMarker(int pId) throws MarkerNotFoundException {
        Marker marker = em.find(Marker.class, pId);
        if (marker == null) {
            throw new MarkerNotFoundException(pId);
        } else {
            return marker;
        }
    }

    public void removeMarker(int pId) throws MarkerNotFoundException {
        Marker marker = loadMarker(pId);
        em.remove(marker);
    }

}
