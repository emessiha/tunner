package com.lob.tunner.client;

import com.lob.tunner.BufferUtils;
import com.lob.tunner.OOOException;
import com.lob.tunner.logger.AutoLog;
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
        return _reqSeq ++;
    }

    public void write(short seq, ByteBuffer data) throws OOOException {
        if(_resSeq != seq) {
            AutoLog.ERROR.log("Got response with incorrect sequence - " + _id);
            throw new OOOException(_resSeq, seq);
        }

        _resSeq ++;
        _channel.writeAndFlush(BufferUtils.fromNioBuffer(data));
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
