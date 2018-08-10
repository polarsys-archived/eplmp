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

import org.polarsys.eplmp.core.exceptions.EntityConstraintException;

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
 * @author Taylor LABEJOF
 */
@Provider
@RequestScoped
public class EntityConstraintExceptionMapper implements ExceptionMapper<EntityConstraintException> {
    private static final Logger LOGGER = Logger.getLogger(EntityConstraintExceptionMapper.class.getName());

    public EntityConstraintExceptionMapper() {
    }

    @Inject
    private Locale userLocale;

    @Override
    public Response toResponse(EntityConstraintException e) {
        LOGGER.log(Level.WARNING, e.getMessage());
        LOGGER.log(Level.FINE, null, e);
        return Response.status(Response.Status.BAD_REQUEST)
                .header("Reason-Phrase", e.getMessage(userLocale))
                .entity(e.toString())
                .type(MediaType.TEXT_PLAIN)
                .build();
    }
}
