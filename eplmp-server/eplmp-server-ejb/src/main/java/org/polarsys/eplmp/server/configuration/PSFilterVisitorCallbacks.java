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

package org.polarsys.eplmp.server.configuration;

import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.product.PartIteration;
import org.polarsys.eplmp.core.product.PartLink;
import org.polarsys.eplmp.core.product.PartMaster;

import java.util.List;

public interface PSFilterVisitorCallbacks {
    void onIndeterminateVersion(PartMaster partMaster, List<PartIteration> partIterations) throws NotAllowedException;
    void onUnresolvedVersion(PartMaster partMaster) throws NotAllowedException;
    void onIndeterminatePath(List<PartLink> pCurrentPath, List<PartIteration> pCurrentPathPartIterations) throws NotAllowedException;
    void onUnresolvedPath(List<PartLink> pCurrentPath, List<PartIteration> partIterations) throws NotAllowedException;
    void onBranchDiscovered(List<PartLink> pCurrentPath, List<PartIteration> copyPartIteration);
    void onOptionalPath(List<PartLink> path, List<PartIteration> partIterations);
    boolean onPathWalk(List<PartLink> path, List<PartMaster> parts);
}
