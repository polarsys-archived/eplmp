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

package org.polarsys.eplmp.server.ws.collaborative;


import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.polarsys.eplmp.server.ws.WebSocketMessage;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.websocket.Session;
import java.io.StringReader;
import java.security.Principal;

/**
 * @author Asmae CHADID
 */
@RunWith(MockitoJUnitRunner.class)
public class CollaborativeRoomTest {
    private static Session master = Mockito.mock(Session.class);
    private static Session slave1 = Mockito.mock(Session.class);
    private static Session slave2 = Mockito.mock(Session.class);

    private static Principal principalMaster = Mockito.mock(Principal.class);
    private static Principal principal1 = Mockito.mock(Principal.class);
    private static Principal principal2 = Mockito.mock(Principal.class);


    @BeforeClass
    public static void init() {
        Mockito.when(master.getUserPrincipal()).thenReturn(principalMaster);
        Mockito.when(slave1.getUserPrincipal()).thenReturn(principal1);
        Mockito.when(slave2.getUserPrincipal()).thenReturn(principal2);

        Mockito.when(master.getUserPrincipal().getName()).thenReturn("master1");
        Mockito.when(slave1.getUserPrincipal().getName()).thenReturn("slave1");
        Mockito.when(slave2.getUserPrincipal().getName()).thenReturn("slave2");
    }

    @Test
    public void shouldReturnEmptyMasterName() {
        //Given
        CollaborativeRoom nullCollaborativeRoom = Mockito.spy(new CollaborativeRoom(null));
        //then
        Assert.assertTrue(nullCollaborativeRoom.getMasterName().isEmpty());
    }

    @Test
    public void shouldReturnNotNullMasterName() {
        //Given
        CollaborativeRoom nullCollaborativeRoom = Mockito.spy(new CollaborativeRoom(master));
        //then
        Assert.assertTrue(!nullCollaborativeRoom.getMasterName().isEmpty());
    }

    @Test
    public void shouldReturnNotNullCollaborativeRoom() {
        //Given
        CollaborativeRoom collaborativeRoom = Mockito.spy(new CollaborativeRoom(master));
        //Then
        Assert.assertTrue(CollaborativeRoom.getByKeyName(collaborativeRoom.getKey()) != null);
    }

    @Test
    public void shouldReturnFourRooms() {
        //Given
        CollaborativeRoom collaborativeRoom1 = Mockito.spy(new CollaborativeRoom(master));
        CollaborativeRoom collaborativeRoom2 = Mockito.spy(new CollaborativeRoom(master));
        CollaborativeRoom collaborativeRoom3 = Mockito.spy(new CollaborativeRoom(master));
        CollaborativeRoom collaborativeRoom4 = Mockito.spy(new CollaborativeRoom(master));

        //Then
        Assert.assertEquals(4, CollaborativeRoom.getAllCollaborativeRooms().size());
    }

    @Test
    public void shouldDeleteRooms() {
        //Given
        CollaborativeRoom collaborativeRoom1 = Mockito.spy(new CollaborativeRoom(master));
        CollaborativeRoom collaborativeRoom2 = Mockito.spy(new CollaborativeRoom(master));
        CollaborativeRoom collaborativeRoom3 = Mockito.spy(new CollaborativeRoom(master));
        CollaborativeRoom collaborativeRoom4 = Mockito.spy(new CollaborativeRoom(master));
        //When
        collaborativeRoom1.delete();
        collaborativeRoom2.delete();
        //Then
        Assert.assertTrue(CollaborativeRoom.getAllCollaborativeRooms().size() == 2);
    }

    @Test
    public void shouldReturnMaster() {
        //Given
        CollaborativeRoom collaborativeRoom1 = Mockito.spy(new CollaborativeRoom(master));
        //Then
        Assert.assertTrue("master1".equals(collaborativeRoom1.getLastMaster()));
    }

    @Test
    public void shouldAddSlave() {
        //Given
        CollaborativeRoom collaborativeRoom1 = Mockito.spy(new CollaborativeRoom(master));
        //When
        collaborativeRoom1.addSlave(slave1);
        //Then
        Assert.assertTrue(collaborativeRoom1.getSlaves().size() == 1);

    }

    @Test
    public void shouldRemoveSlave() {
        //Given
        CollaborativeRoom collaborativeRoom1 = Mockito.spy(new CollaborativeRoom(master));
        //When
        collaborativeRoom1.addSlave(slave1);
        collaborativeRoom1.addSlave(slave2);
        collaborativeRoom1.removeSlave(slave1);
        //Then
        Assert.assertTrue(collaborativeRoom1.getSlaves().get(0).equals(slave2));
    }

    @Test
    public void shouldAddPendingUser() {
        //Given
        CollaborativeRoom collaborativeRoom1 = Mockito.spy(new CollaborativeRoom(master));
        //When
        collaborativeRoom1.addPendingUser("user1");
        collaborativeRoom1.addPendingUser("user2");
        collaborativeRoom1.addPendingUser("user2");
        //Then
        Assert.assertTrue(collaborativeRoom1.getPendingUsers().size() == 3);
    }

    @Test
    public void shouldRemovePendingUser() {
        //Given
        CollaborativeRoom collaborativeRoom1 = Mockito.spy(new CollaborativeRoom(master));
        //When
        collaborativeRoom1.addPendingUser("user1");
        collaborativeRoom1.addPendingUser("user2");
        collaborativeRoom1.addPendingUser("user2");
        collaborativeRoom1.addPendingUser("user2");

        collaborativeRoom1.removePendingUser("user2");
        //Then
        Assert.assertTrue(collaborativeRoom1.getPendingUsers().size() == 3);
    }

    @Test
    public void shouldReturnSlave1Slave2AndNull() {
        //Given
        CollaborativeRoom collaborativeRoom1 = Mockito.spy(new CollaborativeRoom(master));
        //When
        collaborativeRoom1.addSlave(slave1);
        collaborativeRoom1.addSlave(slave2);
        //Then
        Assert.assertTrue(collaborativeRoom1.findUserSession("slave1").equals(slave1));
        Assert.assertTrue(collaborativeRoom1.findUserSession("slave2").equals(slave2));
        Assert.assertTrue(collaborativeRoom1.findUserSession("slave3") == null);
    }

    @Test
    public void shouldSaveCommands() {
        //Given
        String msg = "{  \n" +
                "   \"broadcastMessage\":{  \n" +
                "      \"cameraInfos\":{  \n" +
                "         \"target\":{  \n" +
                "            \"x\":0,\n" +
                "            \"y\":0,\n" +
                "            \"z\":0\n" +
                "         },\n" +
                "         \"camPos\":{  \n" +
                "            \"x\":2283.8555345202267,\n" +
                "            \"y\":1742.2368392950543,\n" +
                "            \"z\":306.5925754554133\n" +
                "         },\n" +
                "         \"camOrientation\":{  \n" +
                "            \"x\":-0.16153026619659236,\n" +
                "            \"y\":0.9837903505522302,\n" +
                "            \"z\":0.07787502335635015\n" +
                "         },\n" +
                "         \"layers\":\"create layer\",\n" +
                "         \"colourEditedObjects\":true,\n" +
                "         \"clipping\":\"1\",\n" +
                "         \"explode\":\"3\",\n" +
                "         \"smartPath\":[  \n" +
                "            \"9447-9445-9441\",\n" +
                "            \"9447-9445-9443\",\n" +
                "            \"9447-9445-9444\",\n" +
                "            \"9446\"\n" +
                "         ]\n" +
                "      }\n" +
                "   }\n" +
                "}";


        JsonObject jsObj = Json.createReader(new StringReader(msg)).readObject();
        JsonObject broadcastMessage = jsObj.containsKey("broadcastMessage") ? jsObj.getJsonObject("broadcastMessage") : null;

        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("type", "COLLABORATIVE_COMMANDS")
                .add("key", "key-12545695-7859-458")
                .add("broadcastMessage", broadcastMessage)
                .add("remoteUser", "slave1");


        WebSocketMessage collaborativeMessage = Mockito.spy(new WebSocketMessage(b.build()));

        CollaborativeRoom room = Mockito.spy(new CollaborativeRoom(master));
        //When
        room.addSlave(slave1);
        room.addSlave(slave2);

        JsonObject commands = collaborativeMessage.getJsonObject("broadcastMessage");
        room.saveCommand(commands);
        Assert.assertTrue(room.getCommands().entrySet().size() == 1);
    }


    @After
    public void clearData() {
        for (CollaborativeRoom room : CollaborativeRoom.getAllCollaborativeRooms()) {
            room.delete();
        }
    }


}
