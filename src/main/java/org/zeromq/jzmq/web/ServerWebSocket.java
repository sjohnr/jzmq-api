package org.zeromq.jzmq.web;

import org.zeromq.api.Context;
import org.zeromq.api.Message;
import org.zeromq.api.MessageFlag;
import org.zeromq.api.PollAdapter;
import org.zeromq.api.Poller;
import org.zeromq.api.Socket;
import org.zeromq.api.SocketType;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ServerWebSocket {
    private static final String MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    
    private ExecutorService threadPool = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("WebSocket-" + thread.getId());
            return thread;
        }
    });
    
    private Context context;
    private Socket router;
    
    private InetSocketAddress address;

    public ServerWebSocket(Context context, InetSocketAddress address) {
        this.context = context;
        this.router = context.buildSocket(SocketType.ROUTER).bind(String.format("inproc://websocket-%s", address.toString()));
        this.address = address;
    }

    public void start() {
        threadPool.execute(new ChannelWorker());
    }
    
    public Message receiveMessage() {
        return router.receiveMessage();
    }
    
    public void send(Message message) {
        router.send(message);
    }
    
    public void send(byte[] frame, MessageFlag flag) {
        router.send(frame, flag);
    }
    
    private class ChannelWorker implements Runnable {
        @Override
        public void run() {
            ServerSocketChannel server;
            ServerSocket serverSocket;
            try {
                System.out.println("Opening...");
                server = ServerSocketChannel.open();
                server.configureBlocking(true);
                serverSocket = server.socket();
                serverSocket.setReceiveBufferSize(16384);
                serverSocket.bind(address);
                System.out.println("Opened");
            } catch (IOException ex) {
                ex.printStackTrace();
                return;
            }
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    System.out.println("Accepting...");
                    SocketChannel channel = server.accept();
                    channel.configureBlocking(true);
                    System.out.println("Submitting...");
                    threadPool.execute(new WebSocket(channel));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
    
    private enum WebSocketState {
        READY, CONNECTED
    }
    
    private class WebSocket extends PollAdapter implements Runnable {
        private Socket dealer;
        private SocketChannel channel;
        private List<byte[]> frames = new LinkedList<>();
        private ByteBuffer buffer = ByteBuffer.allocate(16384);
        private WebSocketState state = WebSocketState.READY;
        
        public WebSocket(SocketChannel channel) {
            this.channel = channel;
            this.dealer = context.buildSocket(SocketType.DEALER).connect(String.format("inproc://websocket-%s", address.toString()));
        }
        
        @Override
        public void run() {
            Poller poller = context.buildPoller()
                .withInPollable(channel, this)
                .withInPollable(dealer, this)
                .build();
            while (!Thread.currentThread().isInterrupted()) {
                System.out.println("Polling...");
                poller.poll();
            }
        }
        
        @Override
        protected void handleIn(Socket socket) {
            if (socket == null) return;
            
            System.out.println("Dealer IN");
            boolean hasMore;
            byte[] frame;
            do {
                frame = dealer.receive();
                System.out.println(new String(frame));
                hasMore = dealer.hasMoreToReceive();
                try {
                    buffer.putChar(hasMore ? '1' : '0');
                    buffer.put(frame);
                    buffer.flip();
                    channel.write(buffer);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    buffer.clear();
                }
            } while (hasMore);
        }
        
        @Override
        protected void handleIn(SelectableChannel ch) {
            if (ch == null) return;
            
            System.out.println("Channel IN...");
            try {
                switch (state) {
                    case READY:
                        channel.read(buffer);
                        buffer.flip();
                        onHandshake(buffer.asCharBuffer());
                        state = WebSocketState.CONNECTED;
                        break;
                    case CONNECTED:
                        channel.read(buffer);
                        onMessage(buffer.asCharBuffer());
                        break;
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            } finally {
                buffer.clear();
            }
        }
        
        private void onHandshake(CharBuffer buf) throws IOException {
            String handshake = buf.toString();
            System.out.println("Handshake: " + handshake);
            String clientKey = validateHandshake(handshake.split("(\\r\\n|\\r|\\n)"));
            if (clientKey != null) {
                String acceptKey = generateAcceptKey(clientKey);
                buf.clear();
                buf.put("HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                        "Sec-WebSocket-Protocol: jzmq-api\r\n\r\n");
                buf.flip();
                channel.write(buffer);
            }
        }
        
        private String generateAcceptKey(String clientKey) {
            String data = clientKey + MAGIC_STRING;
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA1");
            } catch (NoSuchAlgorithmException ex) {
                throw new Error("SHA1 not found", ex);
            }
            
            digest.update(data.getBytes(Charset.forName("UTF-8")));
            return new BigInteger(1, digest.digest()).toString(16);
        }
        
        private String validateHandshake(String[] lines) {
            String clientKey = null;
            
            Map<String, String> headers = new HashMap<>();
            for (String line : lines) {
                int index = line.indexOf(":");
                if (index == -1) {
                    continue;
                }
                
                String key = line.substring(0, index).trim();
                String value = line.substring(index + 1).trim();
                headers.put(key, value);
            }
            
            if (!headers.isEmpty()
                    && lines[0].startsWith("GET")
                    && headers.containsKey("Host")
                    && headers.containsKey("Upgrade")
                    && headers.get("Upgrade").equals("websocket")
                    && headers.containsKey("Connection")
                    && headers.get("Connection").contains("Upgrade")
                    && headers.containsKey("Sec-WebSocket-Version")
                    && headers.get("Sec-WebSocket-Version").equals("13")
                    && headers.containsKey("Sec-WebSocket-Key")) {
                clientKey = headers.get("Sec-WebSocket-Key");
            }
            
            return clientKey;
        }
        
        private void onMessage(CharBuffer buf) {
            boolean hasMore = (buf.charAt(0) == '1');
            byte[] frame = buf.toString().substring(1).getBytes();
            if (hasMore) {
                frames.add(frame);
            } else {
                if (!frames.isEmpty()) {
                    for (byte[] f : frames) {
                        dealer.send(f, MessageFlag.SEND_MORE);
                    }
                }
                
                dealer.send(frame);
            }
        }
    }
}
