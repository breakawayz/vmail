package com.zhangyx.vmail.protocols.server;


import com.google.common.collect.Lists;
import com.zhangyx.vmail.leftcycle.Configurable;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

public abstract class AbstractConfigurableAsyncServer extends AbstractAsyncServer implements Configurable {
    private static final Logger logger = LoggerFactory.getLogger(AbstractConfigurableAsyncServer.class);

    private static final int DEFAULT_BACKLOG = 200;
    private static final int DEAULT_TIMEOUT = 5 * 60;
    private static final String TIMEOUT_NAME = "connectiontimeout";
    private static final String BACKLOG_NAME = "connectionBacklog";
    private static final String HELLO_NAME = "helloName";

    public static final int DEFAULT_MAX_EXECUTOR_COUNT = 16;
    private static final String defaultX509algorithm = "SunX509";

    // The X.509 certificate algorithm
    private String x509Algorithm = defaultX509algorithm;

    // default value is true
    private boolean enabled = true;

    public final void configure(HierarchicalConfiguration config) throws ConfigurationException {
        enabled = config.getBoolean("[@enabled]", enabled);
        if (!enabled) {
            logger.info(getServiceType() + "disabled by configuration");
        }
        //获取监听地址和端口
        String[] listen = config.getString("bind", "0.0.0.0:" + getDefaultPort()).split(",");
        List<InetSocketAddress> bindAddresses = Lists.newArrayListWithCapacity(listen.length);
        for (String tmpListen : listen) {
            String bind[] = tmpListen.split(":");

            InetSocketAddress address;
            String ip = bind[0].trim();
            int port = Integer.parseInt(bind[1].trim());
            if (!ip.equals("0.0.0.0")) {
                try {
                    ip = InetAddress.getByName(ip).getHostName();
                } catch (UnknownHostException e) {
                    throw new ConfigurationException("Malformed bind parameter in configuration of service " + getServiceType(), e);
                }
            }
            address = new InetSocketAddress(ip, port);

            logger.info(getServiceType() + "bound to " + ip + ":" + port);
            bindAddresses.add(address);
        }

        setListenAddresses(bindAddresses.toArray(new InetSocketAddress[bindAddresses.size()]));

        // 从配置文件中获取ioWorker count
        int ioWorkerCount = config.getInt("ioWorkerCount", DEFAULT_IO_WORKER_COUNT);
        setIoWorker(ioWorkerCount);


    }

    // 生成服务类型
    public abstract String getServiceType();

    //获取默认端口
    protected abstract int getDefaultPort();

}
