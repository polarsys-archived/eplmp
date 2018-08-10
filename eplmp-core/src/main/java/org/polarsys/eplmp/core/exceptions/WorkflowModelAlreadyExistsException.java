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

import org.polarsys.eplmp.core.workflow.WorkflowModel;

import java.text.MessageFormat;


/**
 *
 * @author Florent Garin
 */
public class WorkflowModelAlreadyExistsException extends EntityAlreadyExistsException {
    private final WorkflowModel mWorkflowModel;
    
    
    public WorkflowModelAlreadyExistsException(String pMessage) {
        super(pMessage);
        mWorkflowModel=null;
    }
    
    public WorkflowModelAlreadyExistsException(WorkflowModel pWorkflowModel) {
        this(pWorkflowModel, null);
    }

    public WorkflowModelAlreadyExistsException(WorkflowModel pWorkflowModel, Throwable pCause) {
        super( pCause);
        mWorkflowModel=pWorkflowModel;
    }

    @Override
    public String getLocalizedMessage() {
        String message = getBundleDefaultMessage();
        return MessageFormat.format(message,mWorkflowModel);     
    }
}
