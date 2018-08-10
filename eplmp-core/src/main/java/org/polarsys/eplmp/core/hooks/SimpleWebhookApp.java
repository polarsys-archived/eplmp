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
package org.polarsys.eplmp.core.hooks;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * A {@link WebhookApp} subclass which performs an HTTP request on a given URI.
 * The HTTP method and authorization header can also be customized.
 *
 * @author Morgan Guimard
 * @version 2.5, 14/10/17
 * @see     WebhookApp
 * @see     SNSWebhookApp
 * @since V2.5
 */
@Table(name = "SIMPLEWEBHOOKAPP")
@Entity
public class SimpleWebhookApp extends WebhookApp {

    public static final String APP_NAME = "SIMPLEWEBHOOK";

    private String method;

    private String uri;

    @Column(name="AUTH")
    private String authorization;

    public SimpleWebhookApp(String method, String authorization, String uri) {
        this.method = method;
        this.authorization = authorization;
        this.uri = uri;
    }

    public SimpleWebhookApp() {
    }


    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getAuthorization() {
        return authorization;
    }

    public void setAuthorization(String authorization) {
        this.authorization = authorization;
    }

    @Override
    public String getAppName() {
        return APP_NAME;
    }
}
