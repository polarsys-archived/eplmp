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
package org.polarsys.eplmp.server;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.polarsys.eplmp.core.admin.OperationSecurityStrategy;
import org.polarsys.eplmp.core.common.Account;
import org.polarsys.eplmp.core.common.Organization;
import org.polarsys.eplmp.core.exceptions.AccessRightException;
import org.polarsys.eplmp.core.exceptions.AccountAlreadyExistsException;
import org.polarsys.eplmp.core.exceptions.AccountNotFoundException;
import org.polarsys.eplmp.core.exceptions.CreationException;
import org.polarsys.eplmp.core.exceptions.GCMAccountAlreadyExistsException;
import org.polarsys.eplmp.core.exceptions.GCMAccountNotFoundException;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.exceptions.OrganizationNotFoundException;
import org.polarsys.eplmp.core.gcm.GCMAccount;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IAccountManagerLocal;
import org.polarsys.eplmp.core.services.IContextManagerLocal;
import org.polarsys.eplmp.core.services.INotifierLocal;
import org.polarsys.eplmp.core.services.IPlatformOptionsManagerLocal;
import org.polarsys.eplmp.i18n.PropertiesLoader;
import org.polarsys.eplmp.server.dao.AccountDAO;
import org.polarsys.eplmp.server.dao.GCMAccountDAO;
import org.polarsys.eplmp.server.dao.OrganizationDAO;

@DeclareRoles({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
@Local(IAccountManagerLocal.class)
@Stateless(name = "AccountManagerBean")
public class AccountManagerBean implements IAccountManagerLocal {

    @Inject
    private EntityManager em;

    @Inject
    private AccountDAO accountDAO;

    @Inject
    private GCMAccountDAO gcmAccountDAO;

    @Inject
    private OrganizationDAO organizationDAO;

    @Inject
    private IContextManagerLocal contextManager;

    @Inject
    private INotifierLocal mailer;

    @Inject
    private IPlatformOptionsManagerLocal platformOptionsManager;

    @Inject
    private ConfigManager configManager;

    public AccountManagerBean() {
    }

    @Override
    public Account authenticateAccount(String login, String password) {
        Account account = null;

        if (accountDAO.authenticate(login, password, configManager.getDigestAlgorithm())) {

            try {
                account = getAccount(login);
            } catch (AccountNotFoundException e) {
                return null;
            }
        }

        return account;
    }

    @Override
    public UserGroupMapping getUserGroupMapping(String login) {
        return em.find(UserGroupMapping.class, login);
    }

    @Override
    public Account createAccount(String pLogin, String pName, String pEmail, String pLanguage, String pPassword, String pTimeZone) throws AccountAlreadyExistsException, CreationException {
        OperationSecurityStrategy registrationStrategy = platformOptionsManager.getRegistrationStrategy();
        Date now = new Date();
        Account account = new Account(pLogin, pName, pEmail, pLanguage, now, pTimeZone);
        account.setEnabled(registrationStrategy.equals(OperationSecurityStrategy.NONE));
        accountDAO.createAccount(account, pPassword, configManager.getDigestAlgorithm());
        mailer.sendCredential(account);
        return account;
    }

    @Override
    public Account getAccount(String pLogin) throws AccountNotFoundException {
        return accountDAO.loadAccount(pLogin);
    }

    public String getRole(String login) {
        UserGroupMapping userGroupMapping = em.find(UserGroupMapping.class, login);
        if (userGroupMapping == null) {
            return null;
        } else {
            return userGroupMapping.getGroupName();
        }
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
    @Override
    public Account updateAccount(String pName, String pEmail, String pLanguage, String pPassword, String pTimeZone) throws AccountNotFoundException, NotAllowedException {
        
        if(!isLanguageSupported(pLanguage)){
            throw new NotAllowedException("NotAllowedException74");
        }
        if(!isTimeZoneAvailable(pTimeZone)) {
            throw new NotAllowedException("NotAllowedException75");
        }
        Account account = accountDAO.loadAccount(contextManager.getCallerPrincipalLogin());
        account.setName(pName);
        account.setEmail(pEmail);
        account.setLanguage(pLanguage);
        account.setTimeZone(pTimeZone);
        if (pPassword != null) {
            accountDAO.updateCredential(account.getLogin(), pPassword, configManager.getDigestAlgorithm());
        }
        return account;
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
    @Override
    public Account checkAdmin(Organization pOrganization) throws AccessRightException, AccountNotFoundException {
        Account account = accountDAO.loadAccount(contextManager.getCallerPrincipalLogin());

        if (!contextManager.isCallerInRole(UserGroupMapping.ADMIN_ROLE_ID) && !pOrganization.getOwner().equals(account)) {
            throw new AccessRightException(account);
        }

        return account;
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
    @Override
    public Account checkAdmin(String pOrganizationName)
            throws AccessRightException, AccountNotFoundException, OrganizationNotFoundException {

        Account account = accountDAO.loadAccount(contextManager.getCallerPrincipalLogin());
        Organization organization = organizationDAO.loadOrganization(pOrganizationName);

        if (!contextManager.isCallerInRole(UserGroupMapping.ADMIN_ROLE_ID) && !organization.getOwner().equals(account)) {
            throw new AccessRightException(account);
        }

        return account;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void setGCMAccount(String gcmId) throws AccountNotFoundException, GCMAccountAlreadyExistsException, CreationException, GCMAccountNotFoundException {
        String callerLogin = contextManager.getCallerPrincipalLogin();
        Account account = getAccount(callerLogin);
        if (gcmAccountDAO.hasGCMAccount(account)) {
            GCMAccount gcmAccount = gcmAccountDAO.loadGCMAccount(account);
            gcmAccount.setGcmId(gcmId);
        } else {
            gcmAccountDAO.createGCMAccount(new GCMAccount(account, gcmId));
        }

    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
    @Override
    public void deleteGCMAccount() throws AccountNotFoundException, GCMAccountNotFoundException {
        String callerLogin = contextManager.getCallerPrincipalLogin();
        Account account = getAccount(callerLogin);
        GCMAccount gcmAccount = gcmAccountDAO.loadGCMAccount(account);
        gcmAccountDAO.deleteGCMAccount(gcmAccount);
    }

    @Override
    public boolean isAccountEnabled(String pLogin) throws AccountNotFoundException {
        Account account = getAccount(pLogin);
        return account.isEnabled();
    }

    @Override
    @RolesAllowed(UserGroupMapping.ADMIN_ROLE_ID)
    public List<Account> getAccounts() {
        return accountDAO.getAccounts();
    }

    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
    @Override
    public Account getMyAccount() throws AccountNotFoundException {
        return getAccount(contextManager.getCallerPrincipalName());
    }

    @Override
    @RolesAllowed(UserGroupMapping.ADMIN_ROLE_ID)
    public Account enableAccount(String login, boolean enabled) throws AccountNotFoundException, NotAllowedException {
        String callerPrincipalLogin = contextManager.getCallerPrincipalLogin();
        if (!callerPrincipalLogin.equals(login)) {
            Account account = getAccount(login);
            account.setEnabled(enabled);
            return account;
        } else {
            throw new NotAllowedException("NotAllowedException67");
        }
    }

    @Override
    @RolesAllowed(UserGroupMapping.ADMIN_ROLE_ID)
    public Account updateAccount(String pLogin, String pName, String pEmail, String pLanguage, String pPassword, String pTimeZone) throws AccountNotFoundException, NotAllowedException {
        if(!isLanguageSupported(pLanguage)){
            throw new NotAllowedException("NotAllowedException74");
        }
        if(!isTimeZoneAvailable(pTimeZone)) {
            throw new NotAllowedException("NotAllowedException75");
        }
        Account otherAccount = getAccount(pLogin);
        otherAccount.setName(pName);
        otherAccount.setEmail(pEmail);
        otherAccount.setLanguage(pLanguage);
        otherAccount.setTimeZone(pTimeZone);
        if (pPassword != null) {
            accountDAO.updateCredential(otherAccount.getLogin(), pPassword, configManager.getDigestAlgorithm());
        }
        return otherAccount;
    }
    private Boolean  isTimeZoneAvailable(String value) {
        return Arrays.asList(TimeZone.getAvailableIDs()).contains(value);
    }
    private Boolean isLanguageSupported(String value) {
        return PropertiesLoader.getSupportedLanguages().contains(value);
    }
}
