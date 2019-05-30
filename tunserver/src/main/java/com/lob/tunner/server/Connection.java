package com.lob.tunner.server;

import com.lob.tunner.BlockUtils;
import com.lob.tunner.BufferUtils;
import com.lob.tunner.OOOException;
import com.lob.tunner.common.Block;
import com.lob.tunner.logger.AutoLog;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class Connection {
    private final int _id;
    private volatile Tunnel _tunnel;
    private volatile SocketChannel _channel;

    private short _reqSeq = 0;
    private short _resSeq = 0;

    private long _lastWrite = System.currentTimeMillis();
    private long _lastRead = System.currentTimeMillis();

    private boolean _connected = false;
    private final LinkedList<Block> _blocks = new LinkedList<>();

    public Connection(int id) {
        _id = id;
    }

    public long lastRead() {
        return _lastRead;
    }

    public long lastWrite() {
        return _lastWrite;
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
        AutoLog.DEBUG.log("Connection %08x try connecting to proxy %s:%d ...", _id, address, port);
        // create a connection ...
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(TunnelManager.CONNWORKERS)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(io.netty.channel.socket.SocketChannel ch) throws Exception {
                        _channel = ch;
                        ch.pipeline().addLast(new IOHandler());
                    }
                });

        bootstrap.connect(address, port).addListener(future -> {
            if (future.isSuccess()) {
                AutoLog.INFO.log("Connection %08x connect to proxy successfully", _id);

                synchronized (_blocks) {
                    // Let's finish queued blocks first!
                    while(_blocks.size() > 0) {
                        Block b = _blocks.remove();
                        _write(b);
                    }

                    _connected = true;
                }

                _channel.config().setAutoRead(true);
            }
            else {
                AutoLog.ERROR.log("Connection %08x failed to connect proxy!", _id);
                shutdown();
            }
        });
    }

    public void timeout(long now) {
        AutoLog.WARN.log(
                "Timing out connection %08x (lastRead=%d secs, lastWrite=%d secs)",
                _id, (now - _lastRead) / 1000, (now - _lastWrite) / 1000
        );

        TunnelManager.getInstance().close(this);
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
        synchronized (_blocks) {
            if(!_connected) {
                AutoLog.INFO.log("Connection %08x not started yet. Caching block ...", _id);
                // not ready!!! Let's wait
                synchronized (_blocks) {
                    _blocks.add(new Block(block));
                }

                return;
            }

            if(_blocks.size() > 0) {
                AutoLog.DEBUG.log("Connection %08x just started. Writing %d cached block ...", _id, _blocks.size());

                // Let's finish queued blocks first!
                while (_blocks.size() > 0) {
                    Block b = _blocks.remove();
                    _write(b);
                }
            }
        }

        _write(block);
    }

    private void _write(Block block) {
        short seq = block.sequence();
        if(seq != _reqSeq) {
            throw new OOOException(_reqSeq, seq);
        }

        AutoLog.INFO.log("Connection %08x write %d bytes (seq=%d) to proxy server", _id, block.length(), _reqSeq);

        ByteBuffer data = block.data();
        ChannelFuture future = _channel.writeAndFlush(BufferUtils.fromNioBuffer(data));

        /*
        future.addListener(future1 -> {
            if (future1.isSuccess()) {
                AutoLog.INFO.log("Write data suceeded!");
            }
            else {
                AutoLog.ERROR.exception(future.cause()).log("Write data failed!!!");
            }
        });
        */
        _reqSeq = BlockUtils.nextSeqence(seq);

        _lastWrite = System.currentTimeMillis();
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

            /*
            int len = buf.readableBytes();
            if(len >= 0xFFFF) {
                AutoLog.ERROR.log("Read packet of too large size! " + _id);
                throw new IOException("Packet size too large - " + len);
            }

            AutoLog.INFO.log("Connection %08x read %d bytes (seq=%d) from proxy ...", _id, len, _resSeq);

            _tunnel.write(new Block(_id, BlockUtils.sequence(_resSeq ++), (short)len, BufferUtils.toNioBuffer(buf)));
            */

            //
            // Create a block from the buffer and send it to tunnel
            int bytes = buf.readableBytes();

            AutoLog.INFO.log("Connection %08x read %d bytes (seq=%d) from proxy ...", _id, bytes, _resSeq);

            for(;;) {
                // we need to split to smaller packets!!! Unlucky!!!!
                if(bytes > Block.MAX_LENGTH) {
                    ByteBuffer data = ByteBuffer.allocate(Block.MAX_NO_PADDING_LENGTH);
                    buf.readBytes(data);
                    data.rewind();

                    Block block = new Block(
                            _id, BlockUtils.sequence(_resSeq),
                            Block.MAX_NO_PADDING_LENGTH, data
                    );

                    _tunnel.write(block);

                    bytes -= Block.MAX_NO_PADDING_LENGTH;
                    _resSeq = BlockUtils.nextSeqence(_resSeq);

                    AutoLog.DEBUG.log("########Left %d bytes (readable=%d) and next seq=%d ...", bytes, buf.readableBytes(), _resSeq);
                    continue;
                }

                Block block = new Block(
                        _id, BlockUtils.sequence(_resSeq),
                        bytes, BufferUtils.toNioBuffer(buf)
                );

                _tunnel.write(block);
                _resSeq = BlockUtils.nextSeqence(_resSeq);

                break;
            }

            _lastRead = System.currentTimeMillis();

            ReferenceCountUtil.release(msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            AutoLog.ERROR.exception(cause).log("Connection %08x caught exception", _id);

            TunnelManager.getInstance().close(Connection.this);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            AutoLog.INFO.log("Connection %08x closed ...", _id);
            TunnelManager.getInstance().close(Connection.this);
            super.channelInactive(ctx);
        }
    }
}
