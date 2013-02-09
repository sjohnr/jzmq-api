package org.zeromq.jzmq.sockets;

import org.zeromq.ZMQ;
import org.zeromq.api.Socket;
import org.zeromq.api.SocketType;
import org.zeromq.jzmq.ManagedContext;
import org.zeromq.jzmq.ManagedSocket;

public class PullSocketBuilder extends SocketBuilder {

    public PullSocketBuilder(ManagedContext context) {
        super(context, SocketType.PULL);
    }

    @Override
    public Socket connect(String url) {
        throw new IllegalArgumentException("PULL socket cannot connect");
    }

    @Override
    public Socket bind(String url, String... additionalUrls) {
        ZMQ.Context zmqContext = context.getZMQContext();
        ZMQ.Socket socket = zmqContext.socket(this.getSocketType().getType());
        socket.setLinger(this.getLinger());
        socket.setRcvHWM(this.getRecvHWM());
        socket.bind(url);
        for (String s : additionalUrls)
            socket.bind(s);
        return new ManagedSocket(context, socket);
    }
}
