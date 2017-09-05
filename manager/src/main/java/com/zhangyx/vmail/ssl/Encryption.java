package com.zhangyx.vmail.ssl;

import lombok.Data;

import javax.net.ssl.SSLContext;

/**
 * 加密协议对象处理
 */
@Data
public final class Encryption {

    private final SSLContext context;
    private final boolean starttls;
    private final String[] enabledCipherSuites;

    private Encryption(SSLContext context, boolean starttls, String[] enabledCipherSuites) {
        this.context = context;
        this.starttls = starttls;
        this.enabledCipherSuites = enabledCipherSuites;
    }

    public static Encryption createSsl(SSLContext context, boolean starttls, String[] enabledCipherSuites){
        return new Encryption(context, starttls, enabledCipherSuites);
    }
}
