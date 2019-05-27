package com.lob.tunner.server;

import com.lob.tunner.BlockUtils;
import com.lob.tunner.BufferUtils;
import com.lob.tunner.common.Block;
import com.lob.tunner.logger.AutoLog;
import com.lob.tunner.logger.HexDump;
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
    private ByteBuffer _data = ByteBuffer.allocate(1024); // 1K for initial size
    private final ByteBuffer _padding = ByteBuffer.allocate(8);
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
    private static final int _RS_PADDING = 3;

    private final ConcurrentHashMap<Integer, Connection> _connections = new ConcurrentHashMap<>();

    private int _readState = _RS_HEADER;
    private Block _block = null;

    public void read(ByteBuffer data) {
        while(data.hasRemaining()) {
            if (_readState == _RS_HEADER) {
                while (_header.hasRemaining() && data.hasRemaining()) {
                    _header.put(data.get());
                }

                if (_header.hasRemaining()) {
                    return; // wait more data!!!
                }

                _header.rewind();
                short typeSeq = _header.getShort();
                short len = _header.getShort();
                int conn = _header.getInt();

                AutoLog.INFO.log(
                        "Read header (typeSeq=%04x, len=%d, conn=%08x) - %s",
                        typeSeq, len, conn,
                        HexDump.dumpHexString(_header.array())
                );

                if (len > 0) {
                    while(len > _data.capacity()) {
                        _data = ByteBuffer.allocate(_data.capacity() << 1);
                    }

                    _data.rewind();
                    _data.limit(len);

                    _readState = _RS_PAYLOAD;
                }
                else {
                    _handleBlock();
                    _readState = _RS_HEADER;
                }
            }
            else if(_readState == _RS_PAYLOAD) {
                while (_data.hasRemaining() && data.hasRemaining()) {
                    _data.put(data.get());
                }

                if(_data.hasRemaining()) {
                    return; // wait more data
                }

                AutoLog.INFO.log("Read data - %s", HexDump.dumpHexString(_data.array(), 0, _data.limit()));
                int len = _data.limit();
                int padding = len % 8;
                if(padding > 0) {
                    _padding.rewind();
                    _padding.limit(8 - padding);

                    _readState = _RS_PADDING;
                }
                else {
                    _handleBlock();
                    _readState = _RS_HEADER;
                }
            }
            else if(_readState == _RS_PADDING) {
                while (_padding.hasRemaining() && data.hasRemaining()) {
                    _padding.put(data.get());
                }

                if(_padding.hasRemaining()) {
                    return; // wait more data
                }

                AutoLog.INFO.log("Read padding - %s", HexDump.dumpHexString(_padding.array(), 0, _padding.limit()));
                _handleBlock();
                _readState = _RS_HEADER;
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

        AutoLog.INFO.log("Write header - %s", HexDump.dumpHexString(_headerOut.array()));

        _headerOut.rewind();
        _channel.write(BufferUtils.fromNioBuffer(_headerOut));

        // 2. if payload, write payload
        if(length > 0) {
            ByteBuffer data = block.data();
            _channel.write(BufferUtils.fromNioBuffer(data));

            AutoLog.INFO.log("Write data - %s", HexDump.dumpHexString(data.array()));
            // 3. if payload and payload size not multiple of 8, write padding bytes
            length %= 8;
            if(length > 0) {
                _channel.write(Unpooled.wrappedBuffer(BlockUtils.PADDING, 0, 8 - length));

                AutoLog.INFO.log("Write padding - %s", HexDump.dumpHexString(BlockUtils.PADDING, 0, 8 - length));
            }
        }

        _channel.flush();
    }

    /**
     * Start multiplexing this connection on tunnel ...
     *
     * All data read on this tunnel for this connection will be handled
     * @param conn
     */
    public void multiplex(Connection conn) {
        if(_connections.containsKey(conn.identifier())) {
            AutoLog.WARN.log("Connection %08x already on tunnel ", conn.identifier());
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

    private void _handleBlock() {
        _header.rewind();
        short typeSeq = _header.getShort();
        short len = _header.getShort();
        int connId = _header.getInt();

        // prepare for next block!!!!
        _header.clear();

        Block block;
        if(len == 0) {
            block = new Block(connId, typeSeq);
        }
        else {
            _data.rewind();
            block = new Block(connId, typeSeq, len, _data);
        }

        if (block.type() == Block.BLOCK_DATA) {
            AutoLog.INFO.log("Processing data block for connection %08x ...", connId);
            final Connection conn = _connections.get(connId);
            if(conn == null) {
                AutoLog.ERROR.log("Receive block for non-existing connection %08x", connId);
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
