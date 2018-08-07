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

import org.polarsys.eplmp.core.configuration.ProductConfiguration;
import org.polarsys.eplmp.core.exceptions.CreationException;
import org.polarsys.eplmp.core.exceptions.ProductConfigurationNotFoundException;
import org.polarsys.eplmp.core.product.ConfigurationItemKey;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequestScoped
public class ProductConfigurationDAO {

    @Inject
    private EntityManager em;

    private static final Logger LOGGER = Logger.getLogger(ProductConfigurationDAO.class.getName());

    public ProductConfigurationDAO() {
    }

    public void createProductConfiguration(ProductConfiguration pProductConfiguration) throws CreationException {
        try {
            em.persist(pProductConfiguration);
            em.flush();
        } catch (PersistenceException pPEx) {
            LOGGER.log(Level.FINEST, null, pPEx);
            throw new CreationException("");
        }
    }

    public ProductConfiguration getProductConfiguration(int pProductConfigurationId) throws ProductConfigurationNotFoundException {
        ProductConfiguration productConfiguration = em.find(ProductConfiguration.class, pProductConfigurationId);
        if (productConfiguration != null) {
            return productConfiguration;
        } else {
            throw new ProductConfigurationNotFoundException(pProductConfigurationId);
        }
    }

    public List<ProductConfiguration> getAllProductConfigurations(String workspaceId) {
        return em.createNamedQuery("ProductConfiguration.findByWorkspace", ProductConfiguration.class)
                .setParameter("workspaceId", workspaceId)
                .getResultList();
    }

    public List<ProductConfiguration> getAllProductConfigurationsByConfigurationItem(ConfigurationItemKey ciKey) {
        return em.createNamedQuery("ProductConfiguration.findByConfigurationItem", ProductConfiguration.class)
                .setParameter("workspaceId", ciKey.getWorkspace())
                .setParameter("configurationItemId", ciKey.getId())
                .getResultList();
    }

    public void deleteProductConfiguration(ProductConfiguration productConfiguration) {
        em.remove(productConfiguration);
        em.flush();
    }
}
