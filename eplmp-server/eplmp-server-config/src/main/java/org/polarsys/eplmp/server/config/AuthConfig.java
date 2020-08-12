/*******************************************************************************
  * Copyright (c) 2017-2019 DocDoku.
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Public License v1.0
  * which accompanies this distribution, and is available at
  * http://www.eclipse.org/legal/epl-v10.html
  *
  * Contributors:
  *    DocDoku - initial API and implementation
  *******************************************************************************/

package org.polarsys.eplmp.server.config;

import org.jose4j.keys.HmacKey;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.crypto.KeyGenerator;
import javax.ejb.Singleton;
import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Get auth config from resources
 *
 * @author Morgan Guimard
 */
@Singleton
public class AuthConfig {

    @Resource(lookup="auth.config")
    private Properties properties;

    private Key defaultKey;

    private static final Logger LOGGER = Logger.getLogger(AuthConfig.class.getName());

    @PostConstruct
    private void init() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
            defaultKey = keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, "Cannot generate random JWT default key", e);
        }
    }

    public Boolean isJwtEnabled() {
        return Boolean.parseBoolean(properties.getProperty("jwtEnabled"));
    }

    public Boolean isBasicHeaderEnabled() {
        return Boolean.parseBoolean(properties.getProperty("basicHeaderEnabled"));
    }

    public Boolean isSessionEnabled() {
        return Boolean.parseBoolean(properties.getProperty("sessionEnabled"));
    }

    public Key getJWTKey() {
        String secret = properties.getProperty("jwtKey");

        if (null != secret && !secret.isEmpty()) {
            try {
                return new HmacKey(secret.getBytes("UTF-8"));
            }
            catch (UnsupportedEncodingException e) {
                    LOGGER.log(Level.SEVERE, "Cannot create JWT key", e);
            }
        }

        return defaultKey;
    }
}
