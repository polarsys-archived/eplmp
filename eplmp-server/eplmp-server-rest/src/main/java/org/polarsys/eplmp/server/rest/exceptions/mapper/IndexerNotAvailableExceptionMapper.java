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
package org.polarsys.eplmp.server.rest.exceptions.mapper;

import org.polarsys.eplmp.core.exceptions.AccessRightException;
import org.polarsys.eplmp.core.exceptions.IndexerNotAvailableException;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Morgan Guimard
 */
@Provider
@RequestScoped
public class IndexerNotAvailableExceptionMapper implements ExceptionMapper<IndexerNotAvailableException> {

    private static final Logger LOGGER = Logger.getLogger(IndexerNotAvailableExceptionMapper.class.getName());

    public IndexerNotAvailableExceptionMapper() {
    }

    @Inject
    private Locale userLocale;

    @Override
    public Response toResponse(IndexerNotAvailableException e) {
        LOGGER.log(Level.WARNING, e.getMessage());
        LOGGER.log(Level.FINE, null, e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .header("Reason-Phrase", e.getMessage(userLocale))
                .entity(e.toString())
                .type(MediaType.TEXT_PLAIN)
                .build();
    }
}
