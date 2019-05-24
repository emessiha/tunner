package com.lob.tunner.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

/**
 * Read data on channel and
 */
public class InboundDataHandler extends ChannelInboundHandlerAdapter {
    private final ByteBuf _data = PooledByteBufAllocator.DEFAULT.buffer();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(msg instanceof ByteBuf){
            ByteBuf buf=(ByteBuf)msg;
            _data.writeBytes(buf);
        }

        ReferenceCountUtil.release(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if(_data.writerIndex()!=0){
            ByteBuf data = PooledByteBufAllocator.DEFAULT.buffer();
            data.writeBytes(_data);
            _data.clear();

            ctx.fireChannelRead(data);
        }

        super.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ReferenceCountUtil.release(_data);
        super.channelInactive(ctx);
    }
}

