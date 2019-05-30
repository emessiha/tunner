package com.lob.tunner.server;

import com.lob.tunner.BlockUtils;
import com.lob.tunner.BufferUtils;
import com.lob.tunner.common.Block;
import com.lob.tunner.logger.AutoLog;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.ReferenceCountUtil;

/**
 * ClientConnectionHandler handles a client connection by
 * 1. associate with a tunnel, if no tunnel, create one
 * 2. read incoming data from tunnel
 * 3. forward incoming data to tunnel (which will encode and wire to remote side)
 * 4. read data from tunnel
 * 5. write data to channel
 */
public class TunnelHandler extends ChannelInboundHandlerAdapter {
    /**
     * Tunnel associated
     */
    private final Tunnel _tunnel;

    /**
     * Constructor
     * @param channel
     */
    TunnelHandler(SocketChannel channel) {
        this._tunnel = new Tunnel(channel);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        TunnelManager.getInstance().accept(this._tunnel);
        super.channelRegistered(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        boolean canWrite = ctx.channel().isWritable();
        AutoLog.WARN.log(ctx.channel() + " 可写性：" + canWrite);

        _tunnel.channel().config().setAutoRead(canWrite);
        super.channelWritabilityChanged(ctx);
    }


    /**
     * Read something on channel, let's write to server ...
     * @param localCtx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext localCtx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf)msg;

        // AutoLog.INFO.log("Read %d bytes from client ...", buf.readableBytes());
        // _tunnel.read(BufferUtils.toNioBuffer(buf));
        _tunnel.read(buf);

        ReferenceCountUtil.release(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        AutoLog.ERROR.exception(cause).log("Caught exception");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        _tunnel.shutdown();

        super.channelInactive(ctx);
    }
}
