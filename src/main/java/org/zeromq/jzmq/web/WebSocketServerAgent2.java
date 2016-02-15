package org.zeromq.jzmq.web;

import org.java_websocket.SocketChannelIOHelper;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketAdapter;
import org.java_websocket.WebSocketFactory;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.drafts.Draft_10;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.server.DefaultWebSocketServerFactory;
import org.zeromq.api.Context;
import org.zeromq.api.MessageFlag;
import org.zeromq.api.PollListener;
import org.zeromq.api.Pollable;
import org.zeromq.api.Poller;
import org.zeromq.api.PollerType;
import org.zeromq.api.Socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class WebSocketServerAgent2 extends WebSocketAdapter implements PollListener, Runnable {
    private Context context;
    private Socket socket;
    private Poller poller;

    private WebSocketFactory webSocketFactory = new DefaultWebSocketServerFactory();
    private InetSocketAddress address;
    private Map<WebSocket, SocketChannel> channels = new HashMap<>();
    private Map<SelectableChannel, WebSocketImpl> webSockets = new HashMap<>();
    private Map<byte[], WebSocketImpl> clients = new HashMap<>();

    private List<byte[]> pendingFrames = new LinkedList<>();
    private ByteBuffer buffer = ByteBuffer.allocate(WebSocketImpl.RCVBUF);

    public WebSocketServerAgent2(InetSocketAddress address, Context context, Socket socket) {
        this.address = address;
        this.context = context;
        this.socket = socket;
        this.poller = context.buildPoller().withInPollable(socket, this).build();
    }

    /*
     * WebSocketServer methods.
     */

    //    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Server close: " + code + ", " + reason + ", " + remote);
        SocketChannel channel = channels.remove(conn);
        poller.unregister(channel);
    }

//    @Override
    public void onMessage(WebSocket conn, String message) {
        boolean hasMore = (message.charAt(0) == '1');
        byte[] frame = message.substring(1).getBytes();
        if (hasMore) {
            pendingFrames.add(frame);
        } else {
            if (!pendingFrames.isEmpty()) {
                for (byte[] f : pendingFrames) {
                    socket.send(f, MessageFlag.SEND_MORE);
                }
            }

            socket.send(frame);
        }
    }

    @Override
    public void onWebsocketMessage(WebSocket conn, String message) {
        onMessage(conn, message);
    }

    @Override
    public void onWebsocketMessage(WebSocket conn, ByteBuffer blob) {
        onMessage(conn, new String(blob.array()));
    }

    @Override
    public void onWebsocketOpen(WebSocket conn, Handshakedata handshake) {
        System.out.println("Server open: " + new String(handshake.getContent()));
        clients.put(handshake.getFieldValue("Sec-WebSocket-Key").getBytes(), (WebSocketImpl) conn);
    }

    @Override
    public void onWebsocketClose(WebSocket conn, int code, String reason, boolean remote) {
        onClose(conn, code, reason, remote);
    }

    @Override
    public void onWebsocketClosing(WebSocket ws, int code, String reason, boolean remote) {
    }

    @Override
    public void onWebsocketCloseInitiated(WebSocket ws, int code, String reason) {
    }

    @Override
    public void onWebsocketError(WebSocket conn, Exception ex) {
    }

    @Override
    public void onWriteDemand(WebSocket conn) {
    }

    @Override
    public InetSocketAddress getLocalSocketAddress(WebSocket conn) {
        return null;
    }

    @Override
    public InetSocketAddress getRemoteSocketAddress(WebSocket conn) {
        return null;
    }
/*
     * PollListener methods.
     */

    @Override
    public void handleIn(Pollable pollable) {
        if (pollable.getChannel() != null) {
            onClientMessage(pollable);
        } else {
            onServerMessage();
        }
    }

    private void onServerMessage() {
        byte[] identity = socket.receive();
        WebSocketImpl webSocket = clients.get(identity);
        while (socket.hasMoreToReceive()) {
            webSocket.send(socket.receive());
        }
    }

    private void onClientMessage(Pollable pollable) {
        SelectableChannel channel = pollable.getChannel();
        WebSocketImpl webSocket = webSockets.get(channel);
        try {
            SocketChannelIOHelper.read(buffer, webSocket, (ByteChannel) channel);
            webSocket.decode(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        onMessage(webSocket, new String(buffer.array()));
    }

    @Override
    public void handleOut(Pollable pollable) {
    }

    @Override
    public void handleError(Pollable pollable) {
    }

    @Override
    public void run() {
        ServerSocketChannel server;
        ServerSocket serverSocket;
        Selector selector;
        try {
            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            serverSocket = server.socket();
            serverSocket.setReceiveBufferSize(WebSocketImpl.RCVBUF);
            serverSocket.bind(address);
            selector = Selector.open();
            server.register(selector, server.validOps());
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

        while (!Thread.currentThread().isInterrupted()) {
            try {
                SocketChannel channel = server.accept();
                if (channel != null) {
                    System.out.println("Connecting");
                    channel.configureBlocking(false);
                    WebSocketImpl webSocket = (WebSocketImpl) webSocketFactory.createWebSocket(this, new Draft_10(),  channel.socket());
                    webSocket.key = channel.register(selector, SelectionKey.OP_READ, webSocket);
                    webSocket.channel = channel;

                    poller.register(context.newPollable(channel, PollerType.POLL_IN), this);
                    channels.put(webSocket, channel);
                    webSockets.put(channel, webSocket);
                }

                poller.poll(2000);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    class Client {
        byte[] identity;
        SocketChannel channel;
        WebSocketImpl webSocket;
        ByteBuffer buffer = ByteBuffer.allocate(WebSocketImpl.RCVBUF);

        public Client(byte[] identity, SocketChannel channel, WebSocketImpl webSocket) {
            this.identity = identity;
            this.channel = channel;
            this.webSocket = webSocket;
        }
    }
}
