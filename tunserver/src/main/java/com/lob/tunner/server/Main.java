package com.lob.tunner.server;

import com.lob.tunner.common.Config;
import com.lob.tunner.logger.AutoLog;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * Create a listening port to accept client connections, read-in data and multiplexing the data on
 * one of the tunnels
 */
public class Main {
    private static int port=8080;

    public final static EventLoopGroup TUNWORKERS = new NioEventLoopGroup(1);

    private final static TunnelManager _tunnelManager = TunnelManager.getInstance();
    private final static EventLoopGroup BOSS = new NioEventLoopGroup(1);

    public static void main(String[] args){
        Config.initialize(args);

        try {
            _tunnelManager.start();

            /**
             * Handle client APP connection.
             * When client APP connecting, we create a Connection object to wrap the socket channel, which will
             * 1. read data from client, forward the data to one of the tunnel (create if not existing)
             * 2. read data from assigned tunnel, forward the data to client
             */
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(BOSS, TUNWORKERS)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(new TunnelHandler(channel));
                        }
                    });

            ChannelFuture future = bootstrap.bind(Main.port ).sync();

            AutoLog.INFO.log("Starting at port " + Main.port);

            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            BOSS.shutdownGracefully();
            TUNWORKERS.shutdownGracefully();
        }
    }
}
