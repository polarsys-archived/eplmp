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

/**
 * @author Charles Fallourd
 * @version 2.5, 01/06/15
 */
@XmlJavaTypeAdapter(value = DateAdapter.class, type = Date.class) package org.polarsys.eplmp.server.rest.dto.product;

import org.polarsys.eplmp.server.rest.converters.DateAdapter;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Date;
