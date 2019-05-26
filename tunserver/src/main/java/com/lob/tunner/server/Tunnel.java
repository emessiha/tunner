package com.lob.tunner.server;

import com.lob.tunner.BlockUtils;
import com.lob.tunner.BufferUtils;
import com.lob.tunner.common.Block;
import com.lob.tunner.logger.AutoLog;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.netty.channel.socket.SocketChannel;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tunnel has two FSMs
 * 1. the request block FSM
 *    1.1. Reading block header
 *    1.2. Reading block payload
 *    1.3. Processing block
 *
 * 2. the response block FSM, simply write out data ....
 */
public class Tunnel {
    private final ByteBuffer _header = ByteBuffer.allocate(8);
    private final ByteBuffer _headerOut = ByteBuffer.allocate(8);

    private final SocketChannel _channel;
    public Tunnel(SocketChannel channel) {
        _channel = channel;
    }

    public final SocketChannel channel() {
        return _channel;
    }

    private static final int _RS_HEADER = 1;
    private static final int _RS_PAYLOAD = 2;

    private final ConcurrentHashMap<Integer, Connection> _connections = new ConcurrentHashMap<>();

    private int _readState = _RS_HEADER;
    private Block _block = null;

    public void read(ByteBuffer data) {
        while(data.hasRemaining()) {
            if (_readState == _RS_HEADER) {
                while (_header.hasRemaining() && data.hasRemaining()) {
                    _header.put(data.get());
                }

                if (!data.hasRemaining()) {
                    return; // wait more data!!!
                }

                _header.rewind();
                short typeSeq = _header.getShort();
                short len = _header.getShort();
                int conn = _header.getInt();

                if (len > 0) {
                    ByteBuffer buffer = ByteBuffer.allocate(BufferUtils.round(len));
                    _block = new Block(conn, typeSeq, len, buffer);
                }
                else {
                    _block = new Block(conn, typeSeq);
                }

                // prepare for next block!!!!
                _header.clear();
                _readState = _RS_PAYLOAD;

                AutoLog.INFO.log("Reading a new block header of %d bytes ...", len);
            }
            else if(_readState == _RS_PAYLOAD) {
                if(_block.read(data)) {
                    _handleBlock(_block);

                    _readState = _RS_HEADER;
                }
            }
        }
    }

    public synchronized void write(Block block) {
        // we a block is here, let's just written it out!
        short length = block.length();

        AutoLog.INFO.log("Writing a block of %d bytes ...", length);

        // 1. write header on channel
        _headerOut.clear();
        _headerOut.putShort(block.getTypeSeq());
        _headerOut.putShort(length);
        _headerOut.putInt(block.connection());

        _channel.writeAndFlush(BufferUtils.fromNioBuffer(_headerOut));

        // 2. if payload, write payload
        if(length > 0) {
            _channel.writeAndFlush(BufferUtils.fromNioBuffer(block.data()));

            // 3. if payload and payload size not multiple of 8, write padding bytes
            length %= 8;
            if(length > 0) {
                _channel.writeAndFlush(Unpooled.wrappedBuffer(BlockUtils.PADDING, 0, 8 - length));
            }
        }
    }

    /**
     * Start multiplexing this connection on tunnel ...
     *
     * All data read on this tunnel for this connection will be handled
     * @param conn
     */
    public void multiplex(Connection conn) {
        if(_connections.containsKey(conn.identifier())) {
            AutoLog.WARN.log("Connection already on tunnel - " + conn.identifier());
        }
        else {
            conn.tunnel(this);
            _connections.put(conn.identifier(), conn);
        }
    }

    public void remove(Connection conn) {
        _connections.remove(conn.identifier());
    }

    /**
     * Shutdown tunnel
     * If there's any connection on it, those connection will also be shutdown
     */
    public void shutdown() {
        for (Connection conn : _connections.values()) {
            conn.shutdown();
        }

        _connections.clear();

        try {
            _channel.close();
        }
        catch(Exception e) {
            // ignore
        }
    }

    private void _handleBlock(Block block) {
        if (block.type() == Block.BLOCK_DATA) {
            int connId = block.connection();

            AutoLog.INFO.log("Processing data block for connection %d ...", connId);
            final Connection conn = _connections.get(connId);
            if(conn == null) {
                AutoLog.ERROR.log("Receive block for non-existing connection " + connId);
                write(new Block(connId, BlockUtils.control(Block.CODE_ABORT)));
            }
            else {
                conn.write(block);
            }
        }
        else {
            TunnelManager.getInstance().handleControlBlock(this, block.connection(), block.control(), block.data());
        }
    }
}
