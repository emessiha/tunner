package com.lob.tunner.server;

import com.lob.tunner.BlockUtils;
import com.lob.tunner.BufferUtils;
import com.lob.tunner.OOOException;
import com.lob.tunner.common.Block;
import com.lob.tunner.logger.AutoLog;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class Connection {
    private final int _id;
    private volatile Tunnel _tunnel;
    private volatile SocketChannel _channel;

    private short _reqSeq = 0;
    private short _resSeq = 0;

    private final LinkedList<Block> _blocks = new LinkedList<>();

    public Connection(int id) {
        _id = id;
    }

    public final int identifier() {
        return _id;
    }

    public void channel(SocketChannel channel) {
        _channel = channel;
    }

    public final Tunnel tunnel() {
        return _tunnel;
    }

    public final void tunnel(Tunnel tunnel) {
        _tunnel = tunnel;
    }

    public final SocketChannel channel() {
        return _channel;
    }

    public void connect(String address, int port) {
        AutoLog.INFO.log("Try connecting to proxy %s:%d ...", address, port);
        // create a connection ...
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(TunnelManager.CONNWORKERS)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(io.netty.channel.socket.SocketChannel ch) throws Exception {
                        AutoLog.INFO.log("Connection %08x connect to proxy successfully", _id);
                        _channel = ch;
                        ch.pipeline().addLast(new IOHandler());

                        synchronized (_blocks) {
                            // Let's finish queued blocks first!
                            while(_blocks.size() > 0) {
                                Block b = _blocks.remove();
                                _write(b);
                            }
                        }
                    }
                });

        bootstrap.connect(address, port).addListener(future -> {
            if (future.isSuccess()) {
                AutoLog.INFO.log("连接成功: 到代理服务器,允许读浏览器请求: %08x", _id);
                _channel.config().setAutoRead(true);
            }
            else {
                AutoLog.ERROR.log("连接失败:  到代理服务器: %08x", _id);
                shutdown();
            }
        });
    }

    public void shutdown() {
        try {
            _channel.close();
        }
        catch(Exception e) {
            // ignore
        }
    }

    /**
     * Write data ....
     * @param block
     */
    public void write(Block block) {
        if (_channel == null) {
            AutoLog.INFO.log("Connection %08x not started yet. Caching block ...", _id);
            // not ready!!! Let's wait
            synchronized (_blocks) {
                _blocks.add(block);
            }

            return;
        }

        synchronized (_blocks) {
            AutoLog.INFO.log("Connection %08x just started. Writing %d cached block ...", _id, _blocks.size());

            // Let's finish queued blocks first!
            while(_blocks.size() > 0) {
                Block b = _blocks.remove();
                _write(b);
            }
        }

        _write(block);
    }

    private void _write(Block block) {
        short seq = block.sequence();
        if(seq != _reqSeq) {
            throw new OOOException(_reqSeq, seq);
        }

        AutoLog.INFO.log("Writing block %d of %d bytes on connection %08x ...", _reqSeq, block.length(), _id);

        ByteBuffer data = block.data();
        _channel.writeAndFlush(BufferUtils.fromNioBuffer(data));
        _reqSeq ++;
    }

    class IOHandler extends ChannelInboundHandlerAdapter {
        /**
         * Read something on channel, let's write to server ...
         * @param localCtx
         * @param msg
         * @throws Exception
         */
        @Override
        public void channelRead(ChannelHandlerContext localCtx, Object msg) throws Exception {
            ByteBuf buf = (ByteBuf)msg;

            int len = buf.readableBytes();
            if(len > 0xFFFF) {
                AutoLog.ERROR.log("Read packet of too large size! " + _id);
                throw new IOException("Packet size too large - " + len);
            }

            _tunnel.write(new Block(_id, BlockUtils.sequence(_resSeq ++), (short)len, BufferUtils.toNioBuffer(buf)));
            ReferenceCountUtil.release(msg);
        }
    }
}
