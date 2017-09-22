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

package org.polarsys.eplmp.core.workflow;

import java.io.Serializable;

/**
 * Identity class of {@link WorkflowModel} objects.
 *
 * @author Florent Garin
 */
public class WorkflowModelKey implements Serializable {
    
    private String workspaceId;
    private String id;
    
    public WorkflowModelKey() {
    }
    
    public WorkflowModelKey(String pWorkspaceId, String pId) {
        workspaceId=pWorkspaceId;
        id=pId;
    }
    
    @Override
    public int hashCode() {
        int hash = 1;
        hash = 31 * hash + workspaceId.hashCode();
        hash = 31 * hash + id.hashCode();
        return hash;
    }
    
    @Override
    public boolean equals(Object pObj) {
        if (this == pObj) {
            return true;
        }
        if (!(pObj instanceof WorkflowModelKey)) {
            return false;
        }
        WorkflowModelKey key = (WorkflowModelKey) pObj;
        return key.id.equals(id) && key.workspaceId.equals(workspaceId);
    }
    
    @Override
    public String toString() {
        return workspaceId + "-" + id;
    }
    
    public String getWorkspaceId() {
        return workspaceId;
    }
    
    public void setWorkspaceId(String pWorkspaceId) {
        workspaceId = pWorkspaceId;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String pId) {
        id = pId;
    }
    
}
