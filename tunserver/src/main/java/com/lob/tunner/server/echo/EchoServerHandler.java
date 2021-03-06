package com.lob.tunner.server.echo;

import com.lob.tunner.logger.AutoLog;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.CharsetUtil;

@ChannelHandler.Sharable
public class EchoServerHandler extends ChannelInboundHandlerAdapter {
    private final SocketChannel _channel;

    EchoServerHandler(SocketChannel channel) {
        _channel = channel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf in = (ByteBuf) msg;
        AutoLog.INFO.log("Server received: " + in.toString(CharsetUtil.UTF_8));
        _channel.writeAndFlush(in);
        // ctx.writeAndFlush(in);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        super.channelReadComplete(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        AutoLog.INFO.log("Client closed ...");
        super.channelInactive(ctx);
    }
}