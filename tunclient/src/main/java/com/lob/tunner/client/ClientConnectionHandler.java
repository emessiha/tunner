package com.lob.tunner.client;

import com.lob.tunner.BlockUtils;
import com.lob.tunner.BufferUtils;
import com.lob.tunner.common.Block;
import com.lob.tunner.logger.AutoLog;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.ReferenceCountUtil;

import java.nio.ByteBuffer;

/**
 * ClientConnectionHandler handles a client connection by
 * 1. associate with a tunnel, if no tunnel, create one
 * 2. read incoming data from tunnel
 * 3. forward incoming data to tunnel (which will encode and wire to remote side)
 * 4. read data from tunnel
 * 5. respond data to channel
 */
public class ClientConnectionHandler extends ChannelInboundHandlerAdapter {
    private EventLoopGroup remoteLoopGroup = TunnelClient.REMOTEWORKER;

    /**
     * Channel to Client APP
     */
    private final Connection _clientConnection;

    /**
     * Constructor
     * @param channel
     */
    ClientConnectionHandler(SocketChannel channel) {
        this._clientConnection = new Connection(channel);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        TunnelManager.getInstance().accept(this._clientConnection, ctx.channel());

        super.channelRegistered(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        boolean canWrite = ctx.channel().isWritable();
        AutoLog.WARN.log(ctx.channel() + " 可写性：" + canWrite);

        _clientConnection.channel().config().setAutoRead(canWrite);
        super.channelWritabilityChanged(ctx);
    }


    /**
     * Read something on channel, let's respond to server ...
     * @param localCtx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext localCtx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf)msg;

        //
        // Create a block from the buffer and send it to tunnel
        int bytes = buf.readableBytes();

        for(;;) {
            // we need to split to smaller packets!!! Unlucky!!!!
            if(bytes > Block.MAX_LENGTH) {
                ByteBuffer data = ByteBuffer.allocate(Block.MAX_LENGTH);
                buf.readBytes(data);

                Block block = new Block(
                        _clientConnection.getID(),
                        BlockUtils.sequence(_clientConnection.nextRequest()),
                        Block.MAX_LENGTH, data
                );

                _clientConnection.request(block);

                bytes -= Block.MAX_LENGTH;

                continue;
            }

            Block block = new Block(
                    _clientConnection.getID(),
                    BlockUtils.sequence(_clientConnection.nextRequest()),
                    (short)bytes, BufferUtils.toNioBuffer(buf)
            );

            _clientConnection.request(block);

            break;
        }


        ReferenceCountUtil.release(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        AutoLog.ERROR.exception(cause).log("Connection %08x caught exception", _clientConnection.getID());

        TunnelManager.getInstance().close(_clientConnection);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        AutoLog.INFO.log("Connection %08x closed ...", _clientConnection.getID());
        TunnelManager.getInstance().close(_clientConnection);
        super.channelInactive(ctx);
    }
}
