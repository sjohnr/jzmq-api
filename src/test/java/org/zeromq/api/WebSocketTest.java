package org.zeromq.api;

import static org.junit.Assert.assertEquals;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.Test;
import org.zeromq.jzmq.ManagedContext;
import org.zeromq.jzmq.web.ServerWebSocket;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.LinkedList;
import java.util.Queue;

public class WebSocketTest {
    @Test
    public void testRouterSocket() throws Exception {
        Context context = new ManagedContext();
        ServerWebSocket webSocket = new ServerWebSocket(context, new InetSocketAddress("localhost", 5551));
        webSocket.start();
        
        SimpleWebSocketClient client = new SimpleWebSocketClient(new URI("ws://localhost:5551"));
        client.connect();
        
        Thread.sleep(2500);
        
        client.send("Hello, World");
        Message message = webSocket.receiveMessage();
        byte[] identity = message.popFrame().getData();
        message.popFrame();
        String greeting = message.popString();
        
        assertEquals("Hello, World", greeting);
        webSocket.send(identity, MessageFlag.SEND_MORE);
        webSocket.send(Message.EMPTY_FRAME_DATA, MessageFlag.SEND_MORE);
        webSocket.send("Hello".getBytes(), MessageFlag.NONE);
        
        Thread.sleep(250);
        assertEquals("", client.messages.poll());
        assertEquals("Hello", client.messages.poll());
    }
    
    class SimpleWebSocketClient extends WebSocketClient {
        private Queue<String> messages = new LinkedList<>();

        public SimpleWebSocketClient(URI serverURI) {
            super(serverURI);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            System.out.println("Open...");
            System.out.println(new String(handshakedata.getContent()));
        }

        @Override
        public void onMessage(String message) {
            messages.offer(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.println("Closed: " + code + ", " + reason + ", " + remote);
        }

        @Override
        public void onError(Exception ex) {
            System.out.println("Client error:");
            ex.printStackTrace();
        }
    }
}
