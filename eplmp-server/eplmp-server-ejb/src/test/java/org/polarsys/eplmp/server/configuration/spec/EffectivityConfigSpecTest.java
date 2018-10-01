/*******************************************************************************
 * Copyright (c) 2017 DocDoku.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * DocDoku - initial API and implementation
 *******************************************************************************/

package org.polarsys.eplmp.server.configuration.spec;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;
import org.polarsys.eplmp.core.configuration.ProductConfiguration;
import org.polarsys.eplmp.core.product.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/*
 * The tested class is an abstract class with some default business logic,
 * this is why we must check if those business logic are functional.
 *
 * Notice :
 *      In order to avoid troubles at runtime all abstract method we'll be mocked.
 */

@RunWith(MockitoJUnitRunner.class)
public class EffectivityConfigSpecTest {

    private EffectivityConfigSpec effectivityConfigSpec;

    //Utils Mock
    @Mock private ProductConfiguration configuration;
    @Mock private ConfigurationItem configurationItem;
    @Mock private PartMaster pM1;
    @Mock private PartRevision pM1_pR1, pM1_pR2, pM1_pR3;
    @Mock private PartIteration pM1_pI1,pM1_pI2;

    //We want test an abstract class no need to give concrete implementation
    @Mock private Effectivity pM1_eF1;
    @Mock private Effectivity pM1_eF2;
    @Mock private Effectivity pM1_eF3;

    @Mock private PartLink link1,link2, link3;
    @Mock private PartSubstituteLink pSLink1,pSLink2,pSLink3;

    @Before
    public void setUp(){

        effectivityConfigSpec = mock(EffectivityConfigSpec.class, CALLS_REAL_METHODS);
        initMocks(this);
        when(pM1.getPartRevisions()).thenReturn(Arrays.asList(pM1_pR1,pM1_pR2,pM1_pR3));
        Whitebox.setInternalState(effectivityConfigSpec,"retainedPartIterations",new HashSet<>());
        Whitebox.setInternalState(effectivityConfigSpec,"retainedSubstituteLinks",new HashSet<>());
        Whitebox.setInternalState(effectivityConfigSpec,"retainedOptionalUsageLinks",new HashSet<>());
        Whitebox.setInternalState(effectivityConfigSpec,"configuration",configuration);
    }

    @Test
    public void filterPartIterationTest(){

        //------------- TEST : No effectivities on parts revision -------------
        //## BEGIN CONFIGURATION
        when(pM1_pR1.getEffectivities()).thenReturn(Collections.EMPTY_SET);
        when(pM1_pR2.getEffectivities()).thenReturn(Collections.EMPTY_SET);
        when(pM1_pR3.getEffectivities()).thenReturn(Collections.EMPTY_SET);
        //## END CONFIGURATION
        PartIteration result = effectivityConfigSpec.filterPartIteration(pM1);

        //## BEGIN VERIFICATION
        Assert.assertNull(result);
        //## END VERIFICATION

        //------------- TEST : Effectivity for only one part revision -------------
        //## BEGIN CONFIGURATION
        when(pM1_pR1.getEffectivities()).thenReturn(Collections.singleton(pM1_eF1));
        when(pM1_pR1.getLastIteration()).thenReturn(pM1_pI1);
        doReturn(true).when(effectivityConfigSpec).isEffective(pM1_eF1);
        //## END CONFIGURATION

        result = effectivityConfigSpec.filterPartIteration(pM1);

        //## BEGIN VERIFICATION
        Assert.assertNotNull(result);
        Assert.assertEquals(pM1_pI1,result);
        //## END VERIFICATION

        //------------- TEST : Effectivity for all parts revision -------------
        //Notice : According to implementation only last iteration of revision list
        // will be returned. So we only configure just last.
        //## BEGIN CONFIGURATION
        when(pM1_pR3.getEffectivities()).thenReturn(Collections.singleton(pM1_eF3));
        when(pM1_pR3.getLastIteration()).thenReturn(pM1_pI2);
        doReturn(true).when(effectivityConfigSpec).isEffective(pM1_eF3);
        //## END CONFIGURATION

        result = effectivityConfigSpec.filterPartIteration(pM1);

        //## BEGIN VERIFICATION
        Assert.assertNotNull(result);
        Assert.assertEquals(pM1_pI2,result);
        //## END VERIFICATION

        //## BEGIN VERIFICATION : RETAINED LINK COUNT NUMBER
        HashSet<PartIteration> retained = (HashSet) Whitebox.getInternalState(effectivityConfigSpec, "retainedPartIterations");
        Assert.assertTrue(retained.size() == 2);// both iteration must has been retained
        //## END VERIFICATION :  RETAINED LINK COUNT NUMBER

        //## BEGIN VERIFICATION : VISITED METHOD COUNT NUMBER
        //-> Check if we visited Effective(PartRevision) for each parts revision
        //Notice : by doing this we validate part revision list browsing
        verify(effectivityConfigSpec,times(2)).isEffective(pM1_pR1);
        verify(effectivityConfigSpec,times(2)).isEffective(pM1_pR2);
        verify(effectivityConfigSpec,times(3)).isEffective(pM1_pR3);
        //## BEGIN VERIFICATION : VISITED METHOD COUNT NUMBER
    }


    @Test
    public void filterPartLinkTest(){

        //------------- TEST : Work on Product Configuration  -------------
        //~~ Optional link not retained
        //## BEGIN CONFIGURATION
        when(link3.isOptional()).thenReturn(true);// use link3 cause last of list is selected for work
        //## END VERIFICATION

        PartLink result = effectivityConfigSpec.filterPartLink(Arrays.asList(link1,link2,link3));

        //## BEGIN VERIFICATION
        Assert.assertNull(result);
        //## END VERIFICATION

        //~~ Optional link retained with no substitute
        //## BEGIN CONFIGURATION
        when(configuration.isOptionalLinkRetained(anyString())).thenReturn(true);
        when(link3.getSubstitutes()).thenReturn(new ArrayList<>());
        //## END VERIFICATION

        result = effectivityConfigSpec.filterPartLink(Arrays.asList(link1,link2,link3));

        //## BEGIN VERIFICATION
        Assert.assertEquals(link3, result);
        //## END VERIFICATION

        //~~ Optional link retained with substitutes that have substitute link
        //## BEGIN CONFIGURATION
        when(configuration.hasSubstituteLink(anyString())).thenReturn(true);
        when(link2.getSubstitutes()).thenReturn(Arrays.asList(pSLink1,pSLink2,pSLink3));
        when(link2.isOptional()).thenReturn(true);
        //## END VERIFICATION

        result = effectivityConfigSpec.filterPartLink(Arrays.asList(link1,link2));

        //## BEGIN VERIFICATION
        Assert.assertEquals(pSLink1,result);
        //## END VERIFICATION

        //~~ Optional link retained with substitutes that haven't substitute link
        //## BEGIN CONFIGURATION
        when(configuration.hasSubstituteLink(anyString())).thenReturn(false);
        when(link2.getSubstitutes()).thenReturn(Arrays.asList(pSLink2,pSLink3));
        when(link2.isOptional()).thenReturn(true);
        //## END VERIFICATION

        result = effectivityConfigSpec.filterPartLink(Arrays.asList(link1,link2));

        //## BEGIN VERIFICATION
        Assert.assertEquals(link2,result);
        //## END VERIFICATION

        //~~ No optional link retained with no substitute
        //## BEGIN CONFIGURATION
        when(link1.isOptional()).thenReturn(false);
        when(link1.getSubstitutes()).thenReturn(new ArrayList<>());
        //## END CONFIGURATION

        result = effectivityConfigSpec.filterPartLink(Arrays.asList(link1));
        Assert.assertEquals(link1,result);

        //~~ No optional link retained with substitute
        //## BEGIN CONFIGURATION
        when(configuration.hasSubstituteLink(anyString())).thenReturn(true);
        when(link1.getSubstitutes()).thenReturn(Arrays.asList(pSLink3));
        //## END CONFIGURATION

        result = effectivityConfigSpec.filterPartLink(Arrays.asList(link1));

        //## BEGIN VERIFICATION
        Assert.assertEquals(pSLink3,result);
        //## END VERIFICATION

        //## BEGIN VERIFICATION :  RETAINED LINK COUNT NUMBER
        HashSet<String> retainedUsageLinks = (HashSet) Whitebox.getInternalState(effectivityConfigSpec, "retainedOptionalUsageLinks");
        HashSet<String> retainedSubstituteLinks = (HashSet) Whitebox.getInternalState(effectivityConfigSpec, "retainedSubstituteLinks");

        Assert.assertFalse(retainedUsageLinks.isEmpty());
        Assert.assertEquals(2,retainedUsageLinks.size());
        Assert.assertEquals(2,retainedSubstituteLinks.size());
        //## END VERIFICATION :  RETAINED LINK COUNT NUMBER

        //------------- TEST : No Product Configuration  -------------

        //~~ No product configuration with no optional link
        //## BEGIN CONFIGURATION
        Whitebox.setInternalState(effectivityConfigSpec,"configuration",null);
        //## END CONFIGURATION

        result = effectivityConfigSpec.filterPartLink(Arrays.asList(link1));

        //## BEGIN VERIFICATION
        Assert.assertEquals(link1, result);
        //## END VERIFICATION

        //~~ No product configuration with optional link
        //## BEGIN CONFIGURATION
        when(link1.isOptional()).thenReturn(true);
        //## END CONFIGURATION

        result = effectivityConfigSpec.filterPartLink(Arrays.asList(link1));

        //## BEGIN VERIFICATION
        Assert.assertNull(result);
        //## END VERIFICATION
    }
}