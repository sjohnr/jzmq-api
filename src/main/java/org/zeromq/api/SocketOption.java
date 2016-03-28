package org.zeromq.api;

/**
 * Values for socket options to an underlying ZeroMQ implementation.
 */
public enum SocketOption {
    /*
     * NOTE: Keep in alphabetical order, ordinals do not matter.
     * 
     * TODO: Add socket option codes...
     */

    AFFINITY,
    BACKLOG,
    CONFLATE,
    CURVE_PUBLICKEY,
    CURVE_SECRETKEY,
    CURVE_SERVER,
    CURVE_SERVERKEY,
    DELAY_ATTACH_ON_CONNECT,
    EVENTS,
    FD,
    GSSAPI_PLAINTEXT,
    GSSAPI_PRINCIPAL,
    GSSAPI_SERVER,
    GSSAPI_SERVICE_PRINCIPAL,
    HWM,
    IDENTITY,
    IMMEDIATE,
    IPV4ONLY,
    KEEPALIVE,
    KEEPALIVECNT,
    KEEPALIVEIDLE,
    KEEPALIVEINTVL,
    LAST_ENDPOINT,
    LINGER,
    MAXMSGSIZE,
    MCAST_LOOP,
    MULTICAST_HOPS,
    PLAIN_PASSWORD,
    PLAIN_SERVER,
    PLAIN_USERNAME,
    PROBE_ROUTER,
    RATE,
    RCVBUF,
    RCVHWM,
    RCVMORE,
    RCVTIMEO,
    RECONNECT_IVL,
    RECONNECT_IVL_MAX,
    RECOVERY_IVL,
    REQ_CORRELATE,
    REQ_RELAXED,
    ROUTER_MANDATORY,
    SNDBUF,
    SNDHWM,
    SNDTIMEO,
    SUBSCRIBE,
    TCP_ACCEPT_FILTER,
    TCP_KEEPALIVE_CNT,
    TCP_KEEPALIVE_IDLE,
    TCP_KEEPALIVE_INTVL,
    TCP_KEEPALIVE,
    SWAP,
    TYPE,
    UNSUBSCRIBE,
    XPUB_VERBOSE,
    ZAP_DOMAIN
}
