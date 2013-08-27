package org.zeromq.jzmq.patterns;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;
import org.zeromq.ZThread;
import org.zeromq.ZThread.IAttachedRunnable;

/**
 * FreelanceClient class - Freelance Pattern agent class.
 * Implements the Freelance Protocol at
 *   <a href="http://rfc.zeromq.org/spec:10">rfc.zeromq.org</a>.
 * <p>
 * <h2>API structure</h2>
 * <p>
 * This API works in two halves, a common pattern for APIs that need to
 * run in the background. One half is an frontend object our application
 * creates and works with; the other half is a backend "agent" that runs
 * in a background thread. The frontend talks to the backend over an
 * inproc pipe socket.
 * <p>
 * <h3>connect method</h3>
 * <p>
 * To implement the connect method, the frontend object sends a multipart
 * message to the backend agent. The first part is a string "CONNECT", and
 * the second part is the endpoint. It waits 100msec for the connection to
 * come up, which isn't pretty, but saves us from sending all requests to a
 * single server, at startup time.
 * <p>
 * <h3>request methods - send->receive</h3>
 * <p>
 * To implement the request method, the frontend object sends a message
 * to the backend, specifying a command "REQUEST" and the request message.
 * <p>
 * <h2>backend agent</h2>
 * <p>
 * Then we see the backend agent. It runs as an attached thread, talking
 * to its parent over a pipe socket. It is a fairly complex piece of work
 * so we'll break it down into pieces. First, the agent manages a set of
 * servers, using our familiar class approach.
 * <p>
 * <h3>backend agent class</h3>
 * <p>
 * We build the agent as a class that's capable of processing messages
 * coming in from its various sockets.
 * <p>
 * <h3>control messages</h3>
 * <p>
 * This method processes one message from our frontend class
 * (it's going to be CONNECT or REQUEST).
 * <p>
 * <h3>router messages</h3>
 * <p>
 * This method processes one message from a connected server.
 * <p>
 * <h3>backend agent implementation</h3>
 * <p>
 * Finally, this is the agent task itself, which polls its two sockets
 * and processes incoming messages.
 */
public class FreelanceClient {
    private static final Logger log = LoggerFactory.getLogger(FreelanceClient.class);
    
    //  If not a single service replies within this time, give up
    private static final int GLOBAL_TIMEOUT = 2500;   //  msecs
    //  PING interval for servers we think are alive
    private static final int PING_INTERVAL = 2000;    //  msecs
    //  Server considered dead if silent for this long
    private static final int SERVER_TTL = 6000;       //  msecs
    
    //  Structure of our frontend class
    private ZContext ctx;        //  Our context wrapper
    private Socket pipe;         //  Pipe through to backend agent
    private ZMsg request;        //  Enforce strict request-reply sequence
    
    /**
     * Construct a Freelance Pattern agent with an embedded ZContext.
     */
    public FreelanceClient() {
        FreelanceAgent agent = new FreelanceAgent();
        this.ctx = new ZContext();
        this.pipe = ZThread.fork(ctx, agent);
    }
    
    /**
     * Send a CONNECT message to the backend agent.
     * 
     * @param endpoint The endpoint to connect to
     */
    public void connect(String endpoint) {
        ZMsg msg = new ZMsg();
        msg.add("CONNECT");
        msg.add(endpoint);
        
        msg.send(pipe);
        try {
            Thread.sleep(100);   //  Allow connection to come up
        } catch (InterruptedException e) {
        }
    }
    
    /**
     * Send a REQUEST message to the backend agent.
     * 
     * @param request The message to send along with the request
     */
    public void send(ZMsg message) {
        assert (request == null);
        request = message;
        
        message.push("REQUEST");
        message.send(pipe);
    }
    
    /**
     * Receive a reply message from the backend agent.
     * 
     * @return The reply message from the backend agent
     */
    public ZMsg receive() {
        assert (request != null);
        request = null;
        
        ZMsg reply = ZMsg.recvMsg(pipe);
        if (reply != null) {
            String status = reply.popString();
            if (status.equals("FAILED")) {
                reply = null;
            }
        }
        
        return reply;
    }
    
    /**
     * Destroy the embedded context.
     */
    public void destroy() {
        ctx.destroy();
    }
    
    /**
     * Simple class for one server we talk to.
     */
    private static class Server {
        private String endpoint;        //  Server identity/endpoint
        private boolean alive;          //  true if known to be alive
        private long pingAt;            //  Next ping at this time
        private long expires;           //  Expires at this time
        
        /**
         * Construct an internal server.
         * 
         * @param endpoint The endpoint to connect to
         */
        public Server(String endpoint) {
            this.endpoint = endpoint;
            this.alive = false;
            this.pingAt = System.currentTimeMillis() + PING_INTERVAL;
            this.expires = System.currentTimeMillis() + SERVER_TTL;
        }
        
        /**
         * Send a PING message to the ROUTER socket.
         * 
         * @param socket The ROUTER socket
         */
        public void ping(Socket socket) {
            if (System.currentTimeMillis() >= pingAt) {
                ZMsg ping = new ZMsg();
                ping.add(endpoint);
                ping.add("PING");
                if (log.isDebugEnabled()) {
                    log.debug("Sending ping:\n" + ping.toString());
                }
                
                ping.send(socket);
                pingAt = System.currentTimeMillis() + PING_INTERVAL;
            }
        }
    }
    
    /**
     * Task for background thread.
     */
    private static class FreelanceAgent implements IAttachedRunnable {
        private Map<String, Server> servers;     //  Servers we've connected to
        private Queue<Server> actives;           //  Servers we know are alive
        private int sequence;                    //  Number of requests ever sent
        private ZMsg request;                    //  Current request if any
        private long expires;                    //  Timeout for request/reply
        
        /**
         * Construct a background agent.
         */
        public FreelanceAgent() {
            this.servers = new HashMap<String, Server>();
            this.actives = new ArrayDeque<Server>();
        }
        
        @Override
        public void run(Object[] args, ZContext ctx, Socket pipe) {
            Socket router = ctx.createSocket(ZMQ.ROUTER);
            
            PollItem[] items = {
                    new PollItem(pipe, ZMQ.Poller.POLLIN),
                    new PollItem(router, ZMQ.Poller.POLLIN)
            };
            while (!Thread.currentThread().isInterrupted()) {
                //  Calculate tickless timer, up to 1 hour
                long tickless = System.currentTimeMillis() + 1000 * 3600;
                if (request != null) {
                    tickless = Math.min(tickless,  expires);
                }
                
                for (Server server: servers.values()) {
                    tickless = Math.min(tickless, server.pingAt);
                }
                
                long timeout = tickless - System.currentTimeMillis();
                int rc = ZMQ.poll(items, timeout);
                if (rc == -1) {
                    break;              //  Context has been shut down
                }
                
                boolean newRequest = false;
                if (items[0].isReadable()) {
                    pipeMessage(pipe, router);
                    newRequest = true;
                }
                
                if (items[1].isReadable()) {
                    routerMessage(pipe, router);
                }
                
                //  If we're processing a request, dispatch to next server
                if (request != null) {
                    if (System.currentTimeMillis() >= expires) {
                        //  Request expired, kill it
                        ZMsg message = new ZMsg();
                        message.add("FAILED");
                        message.send(pipe);
                        request = null;
                    } else {
                        //  Find server to talk to, remove any expired ones
                        boolean newServer = false;
                        while (!actives.isEmpty()) {
                            Server server = actives.peek();
                            if (System.currentTimeMillis() >= server.expires) {
                                actives.remove();
                                server.alive = false;
                                newServer = true;
                            } else {
                                break;
                            }
                        }
                        
                        Server server = actives.peek();
                        if (newRequest || newServer && server != null) {
                            ZMsg message = request.duplicate();
                            message.push(server.endpoint);
                            if (log.isDebugEnabled()) {
                                log.debug("Sending request:\n" + message.toString());
                            }
                            
                            message.send(router);
                        }
                    }
                }
                
                //  Disconnect and delete any expired servers
                //  Send heartbeats to idle servers if needed
                for (Server server: servers.values()) {
                    server.ping(router);
                }
            }
            
            router.close();
        }
        
        /**
         * Callback when we receive a message from frontend thread.
         * 
         * @param pipe The inproc pipe socket
         * @param socket The router socket
         */
        private void pipeMessage(Socket pipe, Socket router) {
            ZMsg message = ZMsg.recvMsg(pipe);
            if (log.isDebugEnabled()) {
                log.debug("Received pipe message:\n" + message.toString());
            }
            
            String command = message.popString();
            if (command.equals("CONNECT")) {
                String endpoint = message.popString();
                System.out.printf("I: connecting to %s...\n", endpoint);
                router.connect(endpoint);
                
                Server server = new Server(endpoint);
                servers.put(endpoint, server);
                actives.add(server);
            } else if (command.equals("REQUEST")) {
                //  Prefix request with sequence number and empty envelope
                message.push(String.valueOf(++sequence));
                //  Take ownership of request message
                request = message;
                //  Request expires after global timeout
                expires = System.currentTimeMillis() + GLOBAL_TIMEOUT;
            }
        }
        
        /**
         * Callback to process one message from a connected server.
         * 
         * @param pipe The inproc pipe socket
         * @param socket The router socket
         */
        private void routerMessage(Socket pipe, Socket router) {
            ZMsg reply = ZMsg.recvMsg(router);
            if (log.isDebugEnabled()) {
                log.debug("Received router message:\n" + reply.toString());
            }
            
            //  Frame 0 is server that replied
            String endpoint = reply.popString();
            Server server = servers.get(endpoint);
            assert (server != null);
            if (!server.alive) {
                actives.add(server);
                server.alive = true;
            }
            
            server.pingAt = System.currentTimeMillis() + PING_INTERVAL;
            server.expires = System.currentTimeMillis() + SERVER_TTL;
            
            //  Frame 1 may be sequence number for reply
            String sequenceStr = reply.popString();
            if (!sequenceStr.equals("PONG")) {
                assert (Integer.parseInt(sequenceStr) == sequence);
                reply.push("OK");
                reply.send(pipe);
                request = null;
            }
        }
    }
}
