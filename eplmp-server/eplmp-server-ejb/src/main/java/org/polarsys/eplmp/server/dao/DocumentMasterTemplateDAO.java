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

import org.polarsys.eplmp.core.document.DocumentMasterTemplate;
import org.polarsys.eplmp.core.document.DocumentMasterTemplateKey;
import org.polarsys.eplmp.core.exceptions.CreationException;
import org.polarsys.eplmp.core.exceptions.DocumentMasterTemplateAlreadyExistsException;
import org.polarsys.eplmp.core.exceptions.DocumentMasterTemplateNotFoundException;
import org.polarsys.eplmp.core.meta.ListOfValuesKey;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.*;
import java.util.List;
import java.util.Locale;

@Stateless(name = "DocumentMasterTemplateDAO")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class DocumentMasterTemplateDAO {

    @PersistenceContext
    private EntityManager em;

    private Locale mLocale;

    public DocumentMasterTemplateDAO() {
        mLocale = Locale.getDefault();
    }

    public void updateDocMTemplate(DocumentMasterTemplate pTemplate) {
        em.merge(pTemplate);
    }

    public DocumentMasterTemplate removeDocMTemplate(DocumentMasterTemplateKey pKey) throws DocumentMasterTemplateNotFoundException {
        DocumentMasterTemplate template = loadDocMTemplate(pKey);
        em.remove(template);
        return template;
    }

    public List<DocumentMasterTemplate> findAllDocMTemplates(String pWorkspaceId) {
        TypedQuery<DocumentMasterTemplate> query = em.createQuery("SELECT DISTINCT t FROM DocumentMasterTemplate t WHERE t.workspaceId = :workspaceId", DocumentMasterTemplate.class);
        return query.setParameter("workspaceId", pWorkspaceId).getResultList();
    }

    public DocumentMasterTemplate loadDocMTemplate(DocumentMasterTemplateKey pKey)
            throws DocumentMasterTemplateNotFoundException {
        DocumentMasterTemplate template = em.find(DocumentMasterTemplate.class, pKey);
        if (template == null) {
            throw new DocumentMasterTemplateNotFoundException(mLocale, pKey.getId());
        } else {
            return template;
        }
    }

    public DocumentMasterTemplate loadDocMTemplate(Locale pLocale, DocumentMasterTemplateKey pKey) throws DocumentMasterTemplateNotFoundException {
        mLocale = pLocale;
        return loadDocMTemplate(pKey);
    }

    public void createDocMTemplate(DocumentMasterTemplate pTemplate) throws DocumentMasterTemplateAlreadyExistsException, CreationException {
        try {
            //the EntityExistsException is thrown only when flush occurs
            em.persist(pTemplate);
            em.flush();
        } catch (EntityExistsException pEEEx) {
            throw new DocumentMasterTemplateAlreadyExistsException(mLocale, pTemplate);
        } catch (PersistenceException pPEx) {
            //EntityExistsException is case sensitive
            //whereas MySQL is not thus PersistenceException could be
            //thrown instead of EntityExistsException
            throw new CreationException(mLocale);
        }
    }

    public void createDocMTemplate(Locale pLocale, DocumentMasterTemplate pTemplate) throws DocumentMasterTemplateAlreadyExistsException, CreationException {
        mLocale = pLocale;
        createDocMTemplate(pTemplate);
    }

    public List<DocumentMasterTemplate> findAllDocMTemplatesFromLOV(ListOfValuesKey lovKey){
        return em.createNamedQuery("DocumentMasterTemplate.findWhereLOV", DocumentMasterTemplate.class)
                .setParameter("lovName", lovKey.getName())
                .setParameter("workspace_id", lovKey.getWorkspaceId())
                .getResultList();
    }
}
