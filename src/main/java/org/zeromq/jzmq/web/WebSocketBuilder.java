package org.zeromq.jzmq.web;

import org.zeromq.ZMQ;
import org.zeromq.api.Socket;
import org.zeromq.api.SocketType;
import org.zeromq.jzmq.ManagedContext;
import org.zeromq.jzmq.sockets.SocketBuilder;

public class WebSocketBuilder extends SocketBuilder {
    public WebSocketBuilder(ManagedContext context, SocketType socketType) {
        super(context, socketType);
    }

    @Override
    protected ZMQ.Socket createBindableSocketWithStandardSettings() {
        return super.createBindableSocketWithStandardSettings();
    }

    @Override
    protected ZMQ.Socket createConnectableSocketWithStandardSettings() {
        return super.createConnectableSocketWithStandardSettings();
    }

    public Socket build() {
        return null;
    }
}
