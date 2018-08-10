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
package org.polarsys.eplmp.server.auth;

import org.polarsys.eplmp.server.auth.modules.*;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.config.ServerAuthConfig;
import javax.security.auth.message.config.ServerAuthContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Authentication config, returns authentication context to provider
 *
 * @author Morgan Guimard
 */
public class CustomServerAuthConfig implements ServerAuthConfig {

    private static final Logger LOGGER = Logger.getLogger(CustomServerAuthConfig.class.getName());

    private String layer;
    private String appContext;

    /**
     * Declare modules to use (highest priority first)
     */
    private final List<CustomSAM> serverAuthModules;

    public CustomServerAuthConfig(AuthConfig authConfig, String layer, String appContext, CallbackHandler handler) {

        List<CustomSAM> customSAMs = new ArrayList<>();

        if (authConfig.isJwtEnabled()) {
            customSAMs.add(new JWTSAM(authConfig.getJWTKey()));
        }

        if (authConfig.isBasicHeaderEnabled()) {
            customSAMs.add(new BasicHeaderSAM());
        }

        if (authConfig.isSessionEnabled()) {
            customSAMs.add(new SessionSAM());
        }

        customSAMs.add(new GuestSAM());

        serverAuthModules = customSAMs;

        this.layer = layer;
        this.appContext = appContext;

        LOGGER.log(Level.INFO, "Initializing modules");

        serverAuthModules.forEach(serverAuthModule -> {
            try {
                serverAuthModule.initialize(null, null, handler, Collections.<String, String>emptyMap());
            } catch (AuthException e) {
                LOGGER.log(Level.SEVERE, "Cannot initialize SAM : " + serverAuthModule.getClass().getName(), e);
            }
        });

    }

    @Override
    public ServerAuthContext getAuthContext(String authContextID, Subject serviceSubject, Map properties) throws AuthException {
        return new CustomServerAuthContext(serverAuthModules);
    }

    @Override
    public String getMessageLayer() {
        return layer;
    }

    @Override
    public String getAppContext() {
        return appContext;
    }

    @Override
    public String getAuthContextID(MessageInfo messageInfo) {
        return appContext;
    }

    @Override
    public void refresh() {

    }

    @Override
    public boolean isProtected() {
        return false;
    }
}
