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

package org.polarsys.eplmp.server.rest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.polarsys.eplmp.core.exceptions.ApplicationException;
import org.polarsys.eplmp.core.product.ConfigurationItemKey;
import org.polarsys.eplmp.core.product.Layer;
import org.polarsys.eplmp.core.product.Marker;
import org.polarsys.eplmp.core.services.IProductManagerLocal;
import org.polarsys.eplmp.server.rest.dto.LayerDTO;
import org.polarsys.eplmp.server.rest.dto.MarkerDTO;

import javax.ws.rs.core.Response;
import java.util.*;

import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author Morgan Guimard
 */
public class LayerResourceTest {

    @InjectMocks
    private LayerResource layerResource = new LayerResource();

    @Mock
    private IProductManagerLocal productService;

    private String workspaceId = "wks";

    @Before
    public void setup(){
        initMocks(this);
    }

    @Test
    public void getLayersInProductTest() throws ApplicationException{
        String productId = "product";
        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, productId);
        Layer layer1 = new Layer();
        Layer layer2 = new Layer();
        List<Layer> layers = Arrays.asList(layer1, layer2);
        Mockito.when(productService.getLayers(ciKey))
                .thenReturn(layers);
        LayerDTO[] result = layerResource.getLayersInProduct(workspaceId, productId);
        Assert.assertEquals(layers.size(), result.length);
    }

    @Test
    public void createLayerTest() throws ApplicationException{
        String productId = "product";
        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, productId);
        Layer layer = new Layer();
        layer.setName("foo");
        layer.setColor("red");
        Mockito.when(productService.createLayer(ciKey, layer.getName(), layer.getColor()))
                .thenReturn(layer);

        LayerDTO layerDTO = new LayerDTO();
        layerDTO.setName(layer.getName());
        layerDTO.setColor(layer.getColor());
        LayerDTO result = layerResource.createLayer(workspaceId, productId, layerDTO);
        Assert.assertEquals(layerDTO.getName(), result.getName());
        Assert.assertEquals(layerDTO.getColor(), result.getColor());
    }

    @Test
    public void updateLayerTest() throws ApplicationException{
        int layerId = 42;
        String productId = "product";
        ConfigurationItemKey ciKey = new ConfigurationItemKey(workspaceId, productId);
        Layer layer = new Layer();
        layer.setName("foo");
        layer.setColor("red");
        layer.setId(layerId);

        LayerDTO layerDTO = new LayerDTO();
        layerDTO.setId(layer.getId());
        layerDTO.setName("new name");
        layerDTO.setColor("blue");

        Mockito.when(productService.updateLayer(ciKey, layerId, layerDTO.getName(), layerDTO.getColor()))
                .thenReturn(layer);

        LayerDTO result = layerResource.updateLayer(workspaceId, productId, layerId, layerDTO);
        Assert.assertEquals(layer.getName(), result.getName());
        Assert.assertEquals(layer.getColor(), result.getColor());
    }


    @Test
    public void deleteLayerTest() throws ApplicationException{
        int layerId = 42;
        Mockito.doNothing().when(productService)
                .deleteLayer(workspaceId, layerId);
        String productId = "product";
        Response res = layerResource.deleteLayer(workspaceId, productId, layerId);
        Assert.assertEquals(Response.Status.NO_CONTENT.getStatusCode(), res.getStatus());
    }

    @Test
    public void getMarkersInLayerTest() throws ApplicationException{
        String productId = "product";
        int layerId = 42;
        Layer layer = new Layer();
        layer.setName("foo");
        layer.setColor("red");
        layer.setId(layerId);

        Marker marker = new Marker();
        Set<Marker> markers = new HashSet<>();
        markers.add(marker);
        layer.setMarkers(markers);

        Mockito.when(productService.getLayer(layerId))
                .thenReturn(layer);

        MarkerDTO[] markersInLayer = layerResource.getMarkersInLayer(workspaceId, productId, layerId);
        Assert.assertEquals(markers.size(), markersInLayer.length);

    }

    @Test
    public void createMarkerTest() throws ApplicationException{
        String productId = "product";
        int layerId = 42;
        MarkerDTO markerDTO = new MarkerDTO();
        markerDTO.setTitle("title");
        markerDTO.setDescription("description");
        markerDTO.setX(1.0);
        markerDTO.setY(1.0);
        markerDTO.setZ(1.0);

        Marker marker = new Marker();
        marker.setId(37);

        Mockito.when(productService.createMarker(layerId, markerDTO.getTitle(), markerDTO.getDescription(),
                markerDTO.getX(), markerDTO.getY(), markerDTO.getZ()))
                .thenReturn(marker);

        MarkerDTO result = layerResource.createMarker(workspaceId, productId, layerId, markerDTO);
        Assert.assertEquals(marker.getId(), result.getId());

    }

    @Test
    public void deleteMarkerTest() throws ApplicationException{
        String productId = "product";
        int layerId = 42;
        int markerId = 84;
        Mockito.doNothing().when(productService).deleteMarker(layerId, markerId);
        Response res = layerResource.deleteMarker(workspaceId, productId, layerId, markerId);
        Assert.assertEquals(Response.Status.NO_CONTENT.getStatusCode(), res.getStatus());
    }

}
