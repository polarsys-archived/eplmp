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

import org.polarsys.eplmp.core.exceptions.LayerNotFoundException;
import org.polarsys.eplmp.core.product.ConfigurationItemKey;
import org.polarsys.eplmp.core.product.Layer;

import javax.enterprise.context.RequestScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Locale;

@RequestScoped
public class LayerDAO {

    @PersistenceContext
    private EntityManager em;

    private Locale mLocale;
    
    public LayerDAO() {
        mLocale = Locale.getDefault();
    }

    public List<Layer> findAllLayers(ConfigurationItemKey pKey) {
        TypedQuery<Layer> query = em.createNamedQuery("Layer.findLayersByConfigurationItem", Layer.class);
        query.setParameter("workspaceId", pKey.getWorkspace());
        query.setParameter("configurationItemId", pKey.getId());
        return query.getResultList();
    }
    
    public Layer loadLayer(int pId) throws LayerNotFoundException {
        Layer layer = em.find(Layer.class, pId);
        if (layer == null) {
            throw new LayerNotFoundException(mLocale, pId);
        } else {
            return layer;
        }
    }

    public Layer loadLayer(Locale pLocale, int pId) throws LayerNotFoundException{
        mLocale = pLocale;
        return loadLayer(pId);
    }

    public void createLayer(Layer pLayer) {
        em.persist(pLayer);
        em.flush();
    }

    public void deleteLayer(Layer layer) {
        em.remove(layer);
        em.flush();
    }
}
