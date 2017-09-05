package com.zhangyx.vmail.protocols.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class AbstractAsyncServer implements ProtocolServer {
    private static final Logger logger = LoggerFactory.getLogger(AbstractAsyncServer.class);

    private volatile boolean started;
    public static final int DEFAULT_IO_WORKER_COUNT = Runtime.getRuntime().availableProcessors() * 2;
    private volatile int backlog = 250;

    private volatile int timeout = 120;

    private ServerBootstrap bootstrap;

    private volatile int ioWorker = DEFAULT_IO_WORKER_COUNT;

    private List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    //设置监听地址
    public synchronized void setListenAddresses(InetSocketAddress... addresses) {
        if (started) throw new IllegalStateException("Can only be set when the server is not running");
        this.addresses = Collections.unmodifiableList(Arrays.asList(addresses));
    }

    // 设置IO-worker线程数量，默认是核心数*2，DEFAULT_IO_WORKER_COUNT
    public void setIoWorker(int ioWorker) {
        if (started) throw new IllegalStateException("Can only be set when the server is not running");
        this.ioWorker = ioWorker;
    }

    // 在bound之前设置bootstrap的参数
    protected void configureBootstrap(ServerBootstrap bootstrap) {
        bootstrap.option(ChannelOption.SO_BACKLOG, backlog);
        bootstrap.option(ChannelOption.SO_REUSEADDR, true);
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
    }

    public synchronized void start() throws Exception {
        if (started) {
            throw new IllegalStateException("Server running already");
        }

        if (addresses.isEmpty()) {
            throw new RuntimeException("Please specify at least on socketaddress to which the server should get bound!");
        }

        bootstrap = new ServerBootstrap();
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup(ioWorker);

        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class);
        ChannelInitializer channelInitializer = createChannelInitializer();
        configureBootstrap(bootstrap);
        bootstrap.childHandler(channelInitializer);

        for (InetSocketAddress address : addresses) {
            Channel channel = bootstrap.bind(address).sync().channel();
            channels.add(channel);
        }
        logger.info("服务器已经启动！！！");
        started = true;
    }

    public synchronized void stop() {
        if (!started) return;
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        channels.close().awaitUninterruptibly();
        bossGroup = null;
        workerGroup = null;
        started = false;
    }

    // 创建初始化的channel
    protected abstract ChannelInitializer createChannelInitializer();

    public void setTimeout(int timeout) {
        if (started) throw new IllegalStateException("Can only be set when the server is not running");
        this.timeout = timeout;
    }

    public void setBacklog(int backlog) {
        if (started) throw new IllegalStateException("Can only be set when the server is not running");
        this.backlog = backlog;
    }

    public int getBacklog() {
        return backlog;
    }

    public int getTimeout() {
        return timeout;
    }

    public boolean isBound() {
        return started;
    }
}
