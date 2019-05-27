package com.lob.tunner.server.echo;

import com.lob.tunner.logger.AutoLog;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class EchoServer {
    private final static EventLoopGroup _LOOP = new NioEventLoopGroup();
    private final static ServerBootstrap _bootstrap = new ServerBootstrap();

    public static ChannelFuture start(int port) throws Exception {
        _bootstrap.group(_LOOP)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        AutoLog.INFO.log("Receiving one new connection from %s ...", socketChannel.remoteAddress().toString());
                        socketChannel.pipeline().addLast(new EchoServerHandler(socketChannel));
                    }
                });


        return _bootstrap.bind(port).sync();
    }

    public static void shutdown() throws Exception {
        _LOOP.shutdownGracefully().sync();
    }

    public static void main(String []args) throws Exception {
        ChannelFuture future = start(8888);
        System.out.println("EchoServer started at 8888 ...");
        future.channel().closeFuture().sync();
    }
}
