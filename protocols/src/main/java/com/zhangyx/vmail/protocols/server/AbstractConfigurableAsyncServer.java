package com.zhangyx.vmail.protocols.server;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.zhangyx.vmail.filesystem.FileSystem;
import com.zhangyx.vmail.leftcycle.Configurable;
import com.zhangyx.vmail.protocols.handler.ChannelHandlerFactory;
import com.zhangyx.vmail.ssl.Encryption;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.List;

/**
 * configurable server抽象类，通用
 */
public abstract class AbstractConfigurableAsyncServer extends AbstractAsyncServer implements Configurable {
    private static final Logger logger = LoggerFactory.getLogger(AbstractConfigurableAsyncServer.class);
    // 对象注入
    @Autowired
    private FileSystem fileSystem;
    // 配置文件定义的变量名
    private static final String TIMEOUT_NAME = "connectiontimeout";
    private static final String BACKLOG_NAME = "connectionBacklog";
    private static final String HELLO_NAME = "helloName";
    private static final String CONNECTION_LIMIT_NAME = "connectionLimit";

    //默认值
    private static final int DEFAULT_BACKLOG = 200;
    private static final int DEFAULT_TIMEOUT = 5 * 60;
    public static final int DEFAULT_MAX_EXECUTOR_COUNT = 16;
    private static final String defaultX509algorithm = "SunX509";

    // The X.509 certificate algorithm
    private String x509Algorithm = defaultX509algorithm;

    // default value is true
    private boolean enabled = true;

    private String helloName;
    protected int connectionLimit;
    protected int connPerIP;

    // 加密，赋予初始值
    private boolean useStartTLS = false;
    private boolean useSSL = false;
    private String keystore = null;
    private String secret = "";
    //加密套件
    private String[] enabledCipherSuites;
    //加密对象存放
    private Encryption encryption;

    private ChannelHandlerFactory frameHandlerFactory;

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
        // 设置超时时间
        setTimeout(config.getInt(TIMEOUT_NAME, DEFAULT_TIMEOUT));
        configureHelloName(config);

        // 设置连接限制
        String connectLimitStr = config.getString(CONNECTION_LIMIT_NAME, null);
        if (!Strings.isNullOrEmpty(connectLimitStr)) {
            try {
                connectionLimit = Integer.parseInt(connectLimitStr);
            } catch (NumberFormatException e) {
                logger.error("connect limit value is not properly formatted.", e);
            }

            if (connectionLimit < 0) {
                logger.error("connect limit value is less than zero.");
                throw new ConfigurationException("connect limit cannot be less than zero");
            }
        }

        // 设置每个ip限制的连接数量
        String connectionLimitPerIPStr = config.getString("connectionLimitPerIP", null);
        if (!Strings.isNullOrEmpty(connectionLimitPerIPStr)) {
            try {
                connPerIP = Integer.parseInt(connectionLimitPerIPStr);
            } catch (NumberFormatException e) {
                logger.error("connection limit per ip value is not properly formattd.", e);
            }

            if (connPerIP < 0) {
                logger.error("connection limit per ip value cannot be less than zero");
                throw new ConfigurationException("connection limit per ip value cannot be less than zero.");
            }
        }

        useStartTLS = config.getBoolean("tls.[@startTLS]", useStartTLS);
        useSSL = config.getBoolean("tls.[@socketTLS]", useSSL);

        if (useSSL && useStartTLS) {
            throw new ConfigurationException("ssl and startTls cannot exist together");
        }

        if (useStartTLS || useSSL) {
            keystore = config.getString("tls.keystore", keystore);
            if (keystore == null) {
                throw new ConfigurationException("keystore needs to get configured");
            }
            secret = config.getString("tls.secret", secret);
            x509Algorithm = config.getString("tls.algorithm", defaultX509algorithm);
            // TODO: ssl这里还要好好想想
            // 加密套件
            enabledCipherSuites = config.getStringArray("tls.supportedCipherSuites.cipherSuite");
        }
        doConfigure(config);
    }

    // 配置提示
    protected void configureHelloName(Configuration config) throws ConfigurationException {
        String hostName;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostName = "localhost";
        }

        boolean autodetect = config.getBoolean(HELLO_NAME + ".[@autodetect]", true);
        if (autodetect) {
            helloName = hostName;
        } else {
            helloName = config.getString(HELLO_NAME);
            if (Strings.isNullOrEmpty(helloName)) {
                throw new ConfigurationException("Please configure the hello or use autodetect");
            }
        }
    }

    // 服务器初始化，并启动
    public final void init() throws Exception {
        if (isEnabled()) {
            buildSSLContext();
            preInit(); // 子类处理实现
            frameHandlerFactory = createFrameHandlerFactory();
            start();

            logger.info("start {} done", getServiceType());
        }
    }

    public final void destroy() {
        logger.info("start dispose {}", getServiceType());
        if (isEnabled()) {
            // TODO: 实现销毁逻辑
            stop();   // 父类通用方法
            postDestroy(); //子类实现
        }
        logger.info("end dispose {} done", getServiceType());
    }

    // 构建ssl context
    public void buildSSLContext() throws Exception {
        if (useSSL || useStartTLS) {
            FileInputStream fis = null;
            try {
                KeyStore ks = KeyStore.getInstance("JKS");
                fis = new FileInputStream(fileSystem.getFile(keystore));
                ks.load(fis, secret.toCharArray());

                // Set up key manager factory to use our key store
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(x509Algorithm);
                kmf.init(ks, secret.toCharArray());

                // Initialize the SSLContext to work with our key managers.
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(kmf.getKeyManagers(), null, null);
                encryption = Encryption.createSsl(context, useStartTLS, enabledCipherSuites);
            } finally {
                if (fis != null) {
                    fis.close();
                }
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // 生成服务类型
    public abstract String getServiceType();

    //获取默认端口
    protected abstract int getDefaultPort();

    protected void doConfigure(HierarchicalConfiguration config) throws ConfigurationException {
        // 子类重写
    }

    /**
     * 在服务器初始化时，执行该方法
     *
     * @throws Exception
     */
    protected void preInit() throws Exception {
        // 子类重写
    }

    /**
     * 销毁资源
     */
    protected void postDestroy() {
        // 子类重写
    }

    // 解码器，子类实现
    protected abstract ChannelHandlerFactory createFrameHandlerFactory();
}
