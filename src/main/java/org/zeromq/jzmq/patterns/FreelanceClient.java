package org.zeromq.jzmq.patterns;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;
import org.zeromq.ZThread;
import org.zeromq.ZThread.IAttachedRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * <h3>request method</h3>
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
    //  If not a single service replies within this time, give up
    private static final int GLOBAL_TIMEOUT = 2500;   //  msecs
    //  PING interval for servers we think are alive
    private static final int PING_INTERVAL = 2000;    //  msecs
    //  Server considered dead if silent for this long
    private static final int SERVER_TTL = 6000;       //  msecs

    //  Structure of our frontend class
    private ZContext ctx;        //  Our context wrapper
    private Socket pipe;         //  Pipe through to flcliapi agent

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
     * @return The reply message from the backend agent
     */
    public ZMsg request(ZMsg request) {
        request.push("REQUEST");
        request.send(pipe);
        ZMsg reply = ZMsg.recvMsg(pipe);
        if (reply != null) {
            String status = reply.popString();
            if (status.equals("FAILED")) {
                reply.destroy();
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
                ping.send(socket);
                pingAt = System.currentTimeMillis() + PING_INTERVAL;
            }
        }

        public long tickless(long tickless) {
        	long result = -1;
            if (tickless > pingAt) {
                result = pingAt;
            }

            return result;
        }

        /**
         * Nothing.
         */
        public void destroy() {
        	
        }
    }

    /**
     * Simple class for one background agent.
     */
    private static class Agent {
        private Socket pipe;                     //  Socket to talk back to application
        private Socket router;                   //  Socket to talk to servers
        private Map<String, Server> servers;     //  Servers we've connected to
        private List<Server> actives;            //  Servers we know are alive
        private int sequence;                    //  Number of requests ever sent
        private ZMsg request;                    //  Current request if any
        private long expires;                    //  Timeout for request/reply

        /**
         * Construct a background agent.
         * 
         * @param ctx The embedded ZContext of the client
         * @param pipe The inproc pipe socket
         */
        protected Agent(ZContext ctx, Socket pipe) {
            this.pipe = pipe;
            this.router = ctx.createSocket(ZMQ.ROUTER);
            this.servers = new HashMap<String, Server>();
            this.actives = new ArrayList<Server>();
        }

        /**
         * Destroy the server instances this agent is connected to.
         */
        protected void destroy() {
            for (Server server: servers.values()) {
                server.destroy();
            }
        }

        /**
         * Callback when we remove server from agent 'servers' hash table.
         */
        private void controlMessage() {
            ZMsg msg = ZMsg.recvMsg(pipe);
            String command = msg.popString();

            if (command.equals("CONNECT")) {
                String endpoint = msg.popString();
                System.out.printf("I: connecting to %s...\n", endpoint);
                router.connect(endpoint);
                Server server = new Server(endpoint);
                servers.put(endpoint, server);
                actives.add(server);
                server.pingAt = System.currentTimeMillis() + PING_INTERVAL;
                server.expires = System.currentTimeMillis() + SERVER_TTL;
            } else if (command.equals("REQUEST")) {
                assert (request == null);    //  Strict request-reply cycle
                //  Prefix request with sequence number and empty envelope
                String sequenceText = String.format("%d", Integer.valueOf(++sequence));
                msg.push(sequenceText);
                //  Take ownership of request message
                request = msg;
                msg = null;
                //  Request expires after global timeout
                expires = System.currentTimeMillis() + GLOBAL_TIMEOUT;
            }

            if (msg != null) {
                msg.destroy();
            }
        }

        /**
         * Callback to process one message from a connected server.
         */
        private void routerMessage() {
            ZMsg reply = ZMsg.recvMsg(router);

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
            if (Integer.parseInt(sequenceStr) == sequence) {
                reply.push("OK");
                reply.send(pipe);
                request.destroy();
                request = null;
            } else {
                reply.destroy();
            }
        }
    }

    /**
     * Task for background thread.
     */
    private static class FreelanceAgent implements IAttachedRunnable {
        @Override
        public void run(Object[] args, ZContext ctx, Socket pipe) {
            Agent agent = new Agent(ctx, pipe);

            PollItem[] items = {
                    new PollItem(agent.pipe, ZMQ.Poller.POLLIN),
                    new PollItem(agent.router, ZMQ.Poller.POLLIN)
            };
            while (!Thread.currentThread().isInterrupted()) {
                //  Calculate tickless timer, up to 1 hour
                long tickless = System.currentTimeMillis() + 1000 * 3600;
                if (agent.request != null
                        &&  tickless > agent.expires) {
                    tickless = agent.expires;
                }

                for (Server server: agent.servers.values()) {
                    long newTickless = server.tickless(tickless);
                    if (newTickless > 0) {
                        tickless = newTickless;
                    }
                }

                int rc = ZMQ.poll(items,
                        (tickless - System.currentTimeMillis()));
                if (rc == -1) {
                    break;              //  Context has been shut down
                }

                if (items[0].isReadable()) {
                    agent.controlMessage();
                }

                if (items[1].isReadable()) {
                    agent.routerMessage();
                }

                //  If we're processing a request, dispatch to next server
                if (agent.request != null) {
                    if (System.currentTimeMillis() >= agent.expires) {
                        //  Request expired, kill it
                        agent.pipe.send("FAILED");
                        agent.request.destroy();
                        agent.request = null;
                    } else {
                        //  Find server to talk to, remove any expired ones
                        while (!agent.actives.isEmpty()) {
                            Server server = agent.actives.get(0);
                            if (System.currentTimeMillis() >= server.expires) {
                                agent.actives.remove(0);
                                server.alive = false;
                            } else {
                                ZMsg request = agent.request.duplicate();
                                request.push(server.endpoint);
                                request.send(agent.router);
                                break;
                            }
                        }
                    }
                }

                //  Disconnect and delete any expired servers
                //  Send heartbeats to idle servers if needed
                for (Server server: agent.servers.values()) {
                    server.ping(agent.router);
                }
            }
            agent.destroy();
        }
    }
}
