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

package org.polarsys.eplmp.server.configuration.filter;

import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.product.PartIteration;
import org.polarsys.eplmp.core.product.PartMaster;
import org.polarsys.eplmp.core.product.PartRevision;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * @author kelto on 13/01/16.
 */
@RunWith(MockitoJUnitRunner.class)
public class WIPPSFilterTest {

    private WIPPSFilter filter;
    private PartMaster partMaster;
    private List<PartRevision> partRevisions;
    private User user;

    @Before
    public void setup() {
        user = Mockito.spy(new User());
        Mockito.doReturn("test").when(user).getLogin();
        filter = new WIPPSFilter(user);
        partMaster = Mockito.spy(new PartMaster());
        partRevisions = new ArrayList<>();
        Mockito.doReturn(partRevisions).when(partMaster).getPartRevisions();
    }


    @Test
    public void testFilterNoIterationAccessible() throws Exception {
        PartRevision partRevision = Mockito.spy(new PartRevision());
        Mockito.doReturn(null).when(partRevision).getLastAccessibleIteration(Mockito.any());
        partRevisions.add(partRevision);


        //Should return empty list of partIteration if only one partRevision with no accessible iteration
        Assert.assertTrue(filter.filter(partMaster).isEmpty());

        partRevision = Mockito.spy(new PartRevision());
        Mockito.doReturn(null).when(partRevision).getLastAccessibleIteration(Mockito.any());
        partRevisions.add(partRevision);
        partRevision = Mockito.spy(new PartRevision());
        Mockito.doReturn(null).when(partRevision).getLastAccessibleIteration(Mockito.any());
        partRevisions.add(partRevision);
        partRevision = Mockito.spy(new PartRevision());
        Mockito.doReturn(null).when(partRevision).getLastAccessibleIteration(Mockito.any());
        partRevisions.add(partRevision);

        //Should still return empty list of part iteration with multiple partRevision
        Assert.assertTrue(filter.filter(partMaster).isEmpty());
    }

    @Test
    public void testFilterLastRevisionNoIteration() throws Exception {
        PartRevision partRevision = Mockito.spy(new PartRevision());
        Mockito.doReturn(Mockito.mock(PartIteration.class)).when(partRevision).getLastAccessibleIteration(Mockito.any());
        partRevisions.add(partRevision);

        Assert.assertTrue(filter.filter(partMaster).size() == 1);

        partRevision = Mockito.spy(new PartRevision());
        Mockito.doReturn(null).when(partRevision).getLastAccessibleIteration(Mockito.any());
        partRevisions.add(partRevision);

        Assert.assertTrue(filter.filter(partMaster).size() == 1);

        partRevision = Mockito.spy(new PartRevision());
        Mockito.doReturn(null).when(partRevision).getLastAccessibleIteration(Mockito.any());
        partRevisions.add(partRevision);

        Assert.assertTrue(filter.filter(partMaster).size() == 1);
    }
}
