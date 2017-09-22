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

import org.polarsys.eplmp.core.exceptions.EntityAlreadyExistsException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Taylor LABEJOF
 */
@Provider
public class AlreadyExistsExceptionMapper implements ExceptionMapper<EntityAlreadyExistsException> {
    private static final Logger LOGGER = Logger.getLogger(AlreadyExistsExceptionMapper.class.getName());

    public AlreadyExistsExceptionMapper() {
    }

    @Override
    public Response toResponse(EntityAlreadyExistsException e) {
        LOGGER.log(Level.WARNING, e.getMessage());
        LOGGER.log(Level.FINE, null, e);
        return Response.status(Response.Status.CONFLICT)
                .header("Reason-Phrase", e.getMessage())
                .entity(e.toString())
                .type(MediaType.TEXT_PLAIN)
                .build();
    }
}
