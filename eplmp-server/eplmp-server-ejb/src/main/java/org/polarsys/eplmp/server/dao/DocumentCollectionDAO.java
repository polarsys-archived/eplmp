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

import org.polarsys.eplmp.core.configuration.DocumentCollection;

import javax.enterprise.context.RequestScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequestScoped
public class DocumentCollectionDAO {

    @PersistenceContext
    private EntityManager em;

    private static final Logger LOGGER = Logger.getLogger(DocumentCollectionDAO.class.getName());

    public void createDocumentCollection(DocumentCollection documentCollection){
        try {
            em.persist(documentCollection);
            em.flush();
        }catch (Exception e){
            LOGGER.log(Level.SEVERE,"Fail to create a collection of documents",e);
        }
    }
}
