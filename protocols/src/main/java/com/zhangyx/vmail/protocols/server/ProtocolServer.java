/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package com.zhangyx.vmail.protocols.server;

import java.net.InetSocketAddress;
import java.util.List;

/**
 *  处理协议的server抽象类
 *
 */
public interface ProtocolServer {

    /**
     * 绑定端口，启动服务
     * 
     * @throws Exception 
     * 
     */
    void start() throws Exception;
    
    /**
     * 关闭端口，停止服务
     */
    void stop();
    
    /**
     * 判断服务是否被绑定
     * 
     * @return bound
     */
    boolean isBound();

    /**
     * 返回socket的读写超时时间，单位为s
     * @return the timeout
     */
    int  getTimeout();
    
    /**
     * 返回socket的backlog
     * 
     * @return backlog
     */
    int getBacklog();
    
    /**
     * 返回服务监听的ip列表
     * 
     * @return ips
     */
    List<InetSocketAddress> getListenAddresses();
}
