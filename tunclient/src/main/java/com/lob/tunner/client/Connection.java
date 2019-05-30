package com.lob.tunner.client;

import com.lob.tunner.BlockUtils;
import com.lob.tunner.BufferUtils;
import com.lob.tunner.OOOException;
import com.lob.tunner.common.Block;
import com.lob.tunner.logger.AutoLog;
import io.netty.channel.ChannelFuture;
import io.netty.channel.socket.SocketChannel;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represent a connection from client APP
 */
public class Connection {
    private static final AtomicInteger _COUNTER = new AtomicInteger();

    private final int _id;
    private final SocketChannel _channel;
    private Tunnel _tunnel;

    private short _reqSeq = 0;
    private short _resSeq = 0;

    private long _lastWrite = System.currentTimeMillis();
    private long _lastRead = System.currentTimeMillis();

    public Connection(SocketChannel channel) {
        _id = (int)((System.currentTimeMillis() / 1000) * 1000) + _COUNTER.incrementAndGet() % 1000;
        _channel = channel;

        AutoLog.INFO.log("Creating connection %08x", _id);
    }

    public int getID() {
        return _id;
    }

    public void attach(Tunnel tunnel) {
        _tunnel = tunnel;
    }

    public short nextRequest() {
        short seq = _reqSeq;
        _reqSeq = BlockUtils.nextSeqence(seq);
        return seq;
    }

    public long lastRead() {
        return _lastRead;
    }

    public long lastWrite() {
        return _lastWrite;
    }

    public void timeout(long now) {
        AutoLog.WARN.log(
                "Timing out connection %08x (lastRead=%d secs, lastWrite=%d secs)",
                _id, (now - _lastRead) / 1000, (now - _lastWrite) / 1000
        );

        TunnelManager.getInstance().close(this);
    }

    /**
     * Write response back to client APP
     * @param block
     * @throws OOOException
     */
    public void respond(Block block) throws OOOException {
        short seq = block.sequence();
        if(_resSeq != seq) {
            AutoLog.ERROR.log("Got response with incorrect sequence %d (expecting %d) on connection %08x!!!", seq, _resSeq, _id);
            throw new OOOException(_resSeq, seq);
        }

        _resSeq = BlockUtils.nextSeqence(seq);

        ChannelFuture future = _channel.writeAndFlush(BufferUtils.fromNioBuffer(block.data()));
        if(block.length() >= 0x8FFF) {
            long start = System.currentTimeMillis();
            future.addListener(f -> {
                long end = System.currentTimeMillis();
                if(f.isSuccess()) {
                    AutoLog.INFO.log("#####Block with seq %d written out in %d millis on connection %08x...", seq, end - start, _id);
                }
                else {
                    AutoLog.INFO.exception(f.cause()).log("#####Block with seq %d FAILED after %d millis on connection %08x...", seq, end - start, _id);
                }
            });
        }

        _lastWrite = System.currentTimeMillis();
    }

    public void request(Block block) {
        _tunnel.write(block);
        _lastRead = System.currentTimeMillis();
    }

    public Tunnel tunnel() {
        return _tunnel;
    }

    public SocketChannel channel() {
        return _channel;
    }

    public void shutdown() {
        try {
            /**
             *
             *
             *         if (remoteChannel != null && remoteChannel.isActive())
             *             remoteChannel.writeAndFlush(PooledByteBufAllocator.DEFAULT.buffer()).addListener(future -> {
             *                 remoteChannel.close().addListener((future1 -> {
             *                     AutoLog.INFO.log("返回 0字节：浏览器关闭连接，因此关闭到代理服务器的连接");
             *                 }));
             *             });
             */
            _channel.close();
        }
        catch(Exception e) {
            // ignore
        }
    }
}
