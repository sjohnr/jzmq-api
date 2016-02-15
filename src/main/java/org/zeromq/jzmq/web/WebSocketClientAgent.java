package org.zeromq.jzmq.web;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.zeromq.api.Socket;

import java.net.URI;

public class WebSocketClientAgent extends WebSocketClient {
    private Socket socket;

    public WebSocketClientAgent(URI serverURI, Socket socket) {
        super(serverURI);
        this.socket = socket;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
    }

    @Override
    public void onMessage(String message) {
        
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
    }

    @Override
    public void onError(Exception ex) {
    }
}
