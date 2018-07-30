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

import org.polarsys.eplmp.core.common.Account;
import org.polarsys.eplmp.core.exceptions.CreationException;
import org.polarsys.eplmp.core.exceptions.GCMAccountAlreadyExistsException;
import org.polarsys.eplmp.core.exceptions.GCMAccountNotFoundException;
import org.polarsys.eplmp.core.gcm.GCMAccount;

import javax.enterprise.context.RequestScoped;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import java.util.Locale;

@RequestScoped
public class GCMAccountDAO {

    @PersistenceContext
    private EntityManager em;

    private Locale mLocale;

    public GCMAccountDAO() {
        mLocale=Locale.getDefault();
    }

    public GCMAccount loadGCMAccount(Account pAccount) throws GCMAccountNotFoundException {
        GCMAccount gcmAccount = em.find(GCMAccount.class, pAccount.getLogin());
        if(gcmAccount == null){
            throw new GCMAccountNotFoundException(mLocale,pAccount.getLogin());
        }
        return gcmAccount;
    }
    public boolean hasGCMAccount(Account pAccount) {
        GCMAccount gcmAccount = em.find(GCMAccount.class, pAccount.getLogin());
        return gcmAccount != null;
    }

    public GCMAccount loadGCMAccount(Locale pLocale, Account pAccount) throws GCMAccountNotFoundException {
        mLocale = pLocale;
        return loadGCMAccount(pAccount);
    }

    public void createGCMAccount(GCMAccount pGMCAccount) throws GCMAccountAlreadyExistsException, CreationException {
        try{
            //the EntityExistsException is thrown only when flush occurs
            em.persist(pGMCAccount);
            em.flush();
        }catch(EntityExistsException pEEEx){
            throw new GCMAccountAlreadyExistsException(mLocale, pGMCAccount.getAccount().getLogin());
        }catch(PersistenceException pPEx){
            //EntityExistsException is case sensitive
            //whereas MySQL is not thus PersistenceException could be
            //thrown instead of EntityExistsException
            throw new CreationException(mLocale);
        }
    }
    public void createGCMAccount(Locale pLocale, GCMAccount pGMCAccount) throws GCMAccountAlreadyExistsException, CreationException {
        mLocale = pLocale;
        createGCMAccount(pGMCAccount);
    }

    public void deleteGCMAccount(GCMAccount gcmAccount){
        em.remove(gcmAccount);
        em.flush();
    }

}
