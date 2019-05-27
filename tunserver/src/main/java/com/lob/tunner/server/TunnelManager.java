package com.lob.tunner.server;

import com.lob.tunner.BlockUtils;
import com.lob.tunner.common.Block;
import com.lob.tunner.common.Config;
import com.lob.tunner.logger.AutoLog;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

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

    public final static EventLoopGroup CONNWORKERS = new NioEventLoopGroup(1);

    private static final TunnelManager _INST = new TunnelManager();
    public static TunnelManager getInstance() {
        return _INST;
    }

    private final ArrayList<Tunnel> _tunnels = new ArrayList();
    private final ConcurrentHashMap<Integer, Connection> _connections = new ConcurrentHashMap<>();

    public void start() {
        AutoLog.INFO.log("Starting Tunnel Manager ...");
    }

    /**
     * 1. assign the connection to one of the tunnel
     * 2. If no tunnel available, let's create one tunnel
     * 3. If tunnel's threshold met, let's create a new tunnel
     * @param tunnel
     */
    public synchronized void accept(Tunnel tunnel) throws IOException {
        _tunnels.add(tunnel);
    }

    public void close(Connection conn) {
        _connections.remove(conn.identifier());

        Tunnel tunnel = conn.tunnel();
        tunnel.write(new Block(conn.identifier(), BlockUtils.control(Block.CODE_ABORT)));
        tunnel.remove(conn);

        conn.shutdown();
    }

    /**
     * Handle all control blocks received!!!
     *
     * @param tunnel
     * @param conId
     * @param control
     * @param data
     */
    public void handleControlBlock(Tunnel tunnel, int conId, short control, ByteBuffer data) {
        AutoLog.INFO.log("Handling control %04x for connection %08x ...", control, conId);

        switch(control) {
            case Block.CODE_START:
                /**
                 * A new connection be started on this tunnel!!!
                 */
                if(_connections.containsKey(conId)) {
                    // dame!
                    AutoLog.ERROR.log("Connection with ID already exists %08x", conId);
                    throw new RuntimeException(String.format("Found already existing connection %08x", conId));
                }
                else {
                    Connection conn = new Connection(conId);
                    _connections.put(conId, conn);

                    // try connect to target
                    conn.connect(Config.getProxyAddress(), Config.getProxyPort());

                    // and allow multiplexing on this tunnel
                    tunnel.multiplex(conn);
                }
                break;
            case Block.CODE_RESUME:
                if(!_connections.containsKey(conId)) {
                    AutoLog.ERROR.log("Resuming non-existing connection %08x", conId);
                    throw new RuntimeException(String.format("Try resuming non-existing connection %08x", conId));
                }

                // we need to abort from old tunnel if there's and associate it with new one
                // TODO
                AutoLog.ERROR.log("Resuming connection on different tunnel not supported!!!");
                tunnel.write(new Block(conId, BlockUtils.control(Block.CODE_ABORT)));
                break;
            case Block.CODE_ABORT:
                if(!_connections.containsKey(conId)) {
                    AutoLog.WARN.log("Abort non-existing connection %08x", conId);
                    break;
                }
                else {
                    Connection conn = _connections.remove(conId);
                    tunnel.remove(conn);
                    conn.shutdown();
                }
                break;
            case Block.CODE_ECHO:
                // todo: random payload?
                tunnel.write(new Block(0, BlockUtils.control(Block.CODE_ECHO)));
                break;
            default:
                break;
        }
    }
}
