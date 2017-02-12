package org.zeromq.api;

/**
 * Callback from within a {@link CloneClient} agent for subscription events.
 */
public interface CloneClientHandler {
    /**
     * Handle a CHP update, which could be a new, removed, or updated value.
     *
     * @param message The CHP message
     */
    void onUpdate(CloneMessage message);

    /**
     * Handle the start of a snapshot request.
     */
    void onSnapshotStart();

    /**
     * Handle a CHP message <code>KTHXBAI</code> which comes at the end of a
     * snapshot request to signify the end of the stream.
     *
     * @param message The CHP message
     */
    void onSnapshotEnd(CloneMessage message);

    /**
     * Handle a CHP message <code>HUGZ</code> which comes every two seconds to
     * signify that the server is still available.
     * <p>
     * Note: The CHP protocol does not include an identification frame for which
     * server sent the HUGZ command. Therefore, it is assumed that the message
     * came from whichever server is currently primary.
     *
     * @param message The CHP message
     */
    void onHeartbeat(CloneMessage message);
}
