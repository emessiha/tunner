package com.lob.tunner.client;

import com.lob.tunner.BlockUtils;
import com.lob.tunner.common.Block;
import com.lob.tunner.common.Config;
import com.lob.tunner.logger.AutoLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tunnel are using SSHJ, which cannot use netty, we'll have to rely on native java Sockets
 *
 * To simplify the implementation, let's create a pair of threads per tunnel to handle the job like
 * 1. encode / decode client data
 * 2. I/O handling
 * 3. background handling like provide faked data, etc.
 */
public class TunnelManager {
    private TunnelManager() {

    }

    private static final TunnelManager _INST = new TunnelManager();
    public static TunnelManager getInstance() {
        return _INST;
    }

    private final ConcurrentHashMap<Integer, Connection> _connections = new ConcurrentHashMap<>();
    private final ArrayList<Tunnel> _tunnels = new ArrayList();

    public void start() {
        AutoLog.INFO.log("Starting Tunnel Manager ...");
    }

    /**
     * 1. assign the connection to one of the tunnel
     * 2. If no tunnel available, let's create one tunnel
     * 3. If tunnel's threshold met, let's create a new tunnel
     * @param conn
     */
    public synchronized void accept(Connection conn) throws IOException {
        Tunnel tunnel = _findTunnel();

        if(tunnel == null) {
            tunnel = _createTunnel();
        }

        if(tunnel == null) {
            throw new IOException("Cannot find tunnel to multiplexing connection - " + conn.getID());
        }

        _connections.put(conn.getID(), conn);
        tunnel.multiplex(conn);
        conn.attach(tunnel);

        // start the connection on this tunnel
        tunnel.write(new Block(conn.getID(), BlockUtils.control(Block.CODE_START)));
    }


    /**
     * Close a connection
     *
     * @param conn
     */
    public void close(Connection conn) {
        int connId = conn.getID();

        _connections.remove(connId);

        Tunnel tunnel = conn.tunnel();

        // notify server ...
        tunnel.write(new Block(connId, BlockUtils.control(Block.CODE_ABORT)));
        tunnel.remove(connId);

        conn.shutdown();
    }

    /**
     *
     * @param tunnel
     * @param connId
     * @param control
     * @param data
     */
    public void handleControl(Tunnel tunnel, int connId, short control, ByteBuffer data) {
        AutoLog.INFO.log("Handling control %04x for connection %08x ...", control, connId);

        Connection conn;
        switch(control) {
            case Block.CODE_ABORT:
                tunnel.remove(connId);

                conn = _connections.remove(connId);
                if(conn == null) {
                    AutoLog.ERROR.log("Closing non-existing connection %08x", connId);
                }
                else {
                    conn.shutdown();
                }

                break;
            case Block.CODE_ECHO:
                // todo: random payload?
                tunnel.write(new Block(connId, BlockUtils.control(Block.CODE_ECHO)));
                break;
            case Block.CODE_RESUME:
                // shall not happen, only used at client side to resume data ...
                break;
            default:
                break;
        }
    }

    private final Tunnel _findTunnel() {
        for(int idx = 0; idx < _tunnels.size(); idx ++) {
            Tunnel tunnel = _tunnels.get(idx);
            if(!tunnel.overloaded()) {
                return tunnel;
            }
        }

        return null;
    }

    private final Tunnel _createTunnel() throws IOException {
        Tunnel tunnel = new Tunnel();

        tunnel.start(Config.getServerAddress(), Config.getServerPort(), Config.getForwardPort());
        _tunnels.add(tunnel);

        return tunnel;
    }
}
