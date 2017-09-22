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

package org.polarsys.eplmp.core.exceptions;

import org.polarsys.eplmp.core.configuration.ProductInstanceIterationKey;

import java.text.MessageFormat;
import java.util.Locale;

/**
 *
 * @author Florent Garin
 */
public class ProductInstanceIterationNotFoundException extends EntityNotFoundException {
    private final ProductInstanceIterationKey mProductInstanceIteration;

    public ProductInstanceIterationNotFoundException(String pMessage) {
        super(pMessage);
        mProductInstanceIteration=null;
    }

    public ProductInstanceIterationNotFoundException(Locale pLocale, ProductInstanceIterationKey pProductInstanceMaster) {
        this(pLocale, pProductInstanceMaster, null);
    }

    public ProductInstanceIterationNotFoundException(Locale pLocale, ProductInstanceIterationKey pProductInstanceMaster, Throwable pCause) {
        super(pLocale, pCause);
        mProductInstanceIteration=pProductInstanceMaster;
    }

    @Override
    public String getLocalizedMessage() {
        String message = getBundleDefaultMessage();
        return MessageFormat.format(message,mProductInstanceIteration);
    }
}
