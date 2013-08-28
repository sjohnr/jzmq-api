package org.zeromq.api;

import junit.framework.TestCase;

import org.junit.Test;
import org.zeromq.ZMsg;
import org.zeromq.jzmq.patterns.FreelanceClient;
import org.zeromq.jzmq.patterns.FreelanceServer;

public class FreelanceTest extends TestCase {
    @Test
    public void testFreelance() throws Exception {
        FreelanceServer server1 = new FreelanceServer();
        FreelanceServer server2 = new FreelanceServer();
        FreelanceServer server3 = new FreelanceServer();
        
        server1.setIdentity("ipc://server1.ipc");
        server2.setIdentity("ipc://server2.ipc");
        server3.setIdentity("ipc://server3.ipc");
        
        server1.bind("ipc://server1.ipc");
        server2.bind("ipc://server2.ipc");
        server3.bind("ipc://server3.ipc");
        
        FreelanceClient client1 = new FreelanceClient();
        FreelanceClient client2 = new FreelanceClient();
        FreelanceClient client3 = new FreelanceClient();
        
        // 1-2-3
        client1.connect("ipc://server1.ipc");
        client1.connect("ipc://server2.ipc");
        client1.connect("ipc://server3.ipc");
        
        // 2-3-1
        client2.connect("ipc://server2.ipc");
        client2.connect("ipc://server3.ipc");
        client2.connect("ipc://server1.ipc");
        
        // 3-1-2
        client3.connect("ipc://server3.ipc");
        client3.connect("ipc://server1.ipc");
        client3.connect("ipc://server2.ipc");
        
        Thread.sleep(1000);
        
        // ************************************************************ Batch #1
        System.out.println("*** Batch #1 ***");
        
        ZMsg request1 = new ZMsg();
        request1.add("REQUEST1");
        client1.send(request1);
        assertEquals("REQUEST1", server1.receive().popString());
        ZMsg reply1 = new ZMsg();
        reply1.add("REPLY1");
        server1.send(reply1);
        assertEquals("REPLY1", client1.receive().popString());
        
        ZMsg request2 = new ZMsg();
        request2.add("REQUEST2");
        client2.send(request2);
        assertEquals("REQUEST2", server2.receive().popString());
        ZMsg reply2 = new ZMsg();
        reply2.add("REPLY2");
        server2.send(reply2);
        assertEquals("REPLY2", client2.receive().popString());
        
        ZMsg request3 = new ZMsg();
        request3.add("REQUEST3");
        client3.send(request3);
        assertEquals("REQUEST3", server3.receive().popString());
        ZMsg reply3 = new ZMsg();
        reply3.add("REPLY3");
        server3.send(reply3);
        assertEquals("REPLY3", client3.receive().popString());
        
        server1.destroy();
        
        Thread.sleep(10000);
        
        // ************************************************************ Batch #2
        System.out.println("*** Batch #2 ***");
        ZMsg request4 = new ZMsg();
        request4.add("REQUEST4");
        client1.send(request4);
        assertEquals("REQUEST4", server2.receive().popString());
        ZMsg reply4 = new ZMsg();
        reply4.add("REPLY4");
        server2.send(reply4);
        assertEquals("REPLY4", client1.receive().popString());
        
        ZMsg request5 = new ZMsg();
        request5.add("REQUEST5");
        client2.send(request5);
        assertEquals("REQUEST5", server2.receive().popString());
        ZMsg reply5 = new ZMsg();
        reply5.add("REPLY5");
        server2.send(reply5);
        assertEquals("REPLY5", client2.receive().popString());
        
        ZMsg request6 = new ZMsg();
        request6.add("REQUEST6");
        client3.send(request6);
        assertEquals("REQUEST6", server3.receive().popString());
        ZMsg reply6 = new ZMsg();
        reply6.add("REPLY6");
        server3.send(reply6);
        assertEquals("REPLY6", client3.receive().popString());
        
        server2.destroy();
        
        Thread.sleep(10000);
        
        // ************************************************************ Batch #3
        System.out.println("*** Batch #3 ***");
        ZMsg request7 = new ZMsg();
        request7.add("REQUEST7");
        client1.send(request7);
        assertEquals("REQUEST7", server3.receive().popString());
        ZMsg reply7 = new ZMsg();
        reply7.add("REPLY7");
        server3.send(reply7);
        assertEquals("REPLY7", client1.receive().popString());
        
        ZMsg request8 = new ZMsg();
        request8.add("REQUEST8");
        client2.send(request8);
        assertEquals("REQUEST8", server3.receive().popString());
        ZMsg reply8 = new ZMsg();
        reply8.add("REPLY8");
        server3.send(reply8);
        assertEquals("REPLY8", client2.receive().popString());
        
        ZMsg request9 = new ZMsg();
        request9.add("REQUEST9");
        client3.send(request9);
        assertEquals("REQUEST9", server3.receive().popString());
        ZMsg reply9 = new ZMsg();
        reply9.add("REPLY9");
        server3.send(reply9);
        assertEquals("REPLY9", client3.receive().popString());
        
        server3.destroy();
        client1.destroy();
        client2.destroy();
        client3.destroy();
        
        System.out.println("*** EXIT ***");
    }
}
