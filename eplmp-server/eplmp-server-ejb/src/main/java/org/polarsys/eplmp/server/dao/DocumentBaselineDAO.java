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

import org.polarsys.eplmp.core.configuration.DocumentBaseline;
import org.polarsys.eplmp.core.exceptions.BaselineNotFoundException;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;


/**
 * Data access object for DocumentBaseline
 *
 * @author Taylor LABEJOF
 * @version 2.0, 28/08/14
 * @since   V2.0
 */
@RequestScoped
public class DocumentBaselineDAO {

    @Inject
    private EntityManager em;

    public DocumentBaselineDAO() {}

    public void createBaseline(DocumentBaseline documentBaseline) {
        em.persist(documentBaseline);
        em.flush();
    }

    public List<DocumentBaseline> findBaselines(String workspaceId) {
        return em.createQuery("SELECT b FROM DocumentBaseline b WHERE b.author.workspace.id = :workspaceId", DocumentBaseline.class)
                .setParameter("workspaceId",workspaceId)
                .getResultList();
    }

    public DocumentBaseline loadBaseline(int pBaselineId) throws BaselineNotFoundException {
        DocumentBaseline documentBaseline = em.find(DocumentBaseline.class, pBaselineId);
        if(documentBaseline == null){
            throw new BaselineNotFoundException(pBaselineId);
        }else{
            return documentBaseline;
        }
    }

    public void deleteBaseline(DocumentBaseline documentBaseline) {
        em.remove(documentBaseline);
        em.flush();
    }

    public boolean existBaselinedDocument(String workspaceId, String documentId, String documentVersion) {
        return em.createNamedQuery("BaselinedDocument.existBaselinedDocument", Long.class)
                .setParameter("documentId", documentId)
                .setParameter("documentVersion", documentVersion)
                .setParameter("workspaceId", workspaceId)
                .getSingleResult() > 0;
    }
}
