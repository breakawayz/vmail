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
package com.zhangyx.vmail.dnsserver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

/**
 * DNS解析抽象接口，
 * mail使用，通用dns解析方式
 *
 */
public interface DNSService {

    /**
     * <p>
     *     返回domain的优先级不可修改的邮件处理列表
     * </p>
     * 
     * <p>
     *     首先查找MX主机，然后查找CNAME地址的MX主机，如果没有找到服务器返回hostname的IP
     * </p>
     * 
     * @param hostname
     *            需要查找的domain
     * 
     * @return
     *      与此邮件域名相对应的处理服务器的不可修改列表
     * @throws TemporaryResolutionException
     *             抛出临时问题
     */
    Collection<String> findMXRecords(String hostname) throws TemporaryResolutionException;

    /**
     * 获取DNS TXT记录列表
     * 
     * @param hostname
     *            需要检查的domain
     * @return txt记录的列表
     */
    Collection<String> findTXTRecords(String hostname);

    /**
     * 将给定的主机名解析为基于dns服务器的inetAddress数组，
     * 不应该考虑本地主机列表中定义的主机名
     * 
     * @return InetAddress数组
     */
    Collection<InetAddress> getAllByName(String host) throws UnknownHostException;

    /**
     * 将给定的主机名解析为基于dns服务器的inetAddress地址，
     * 不应该考虑本地主机列表中定义的主机名
     * 
     * @return 解析到的InetAddress或者null
     */
    InetAddress getByName(String host) throws UnknownHostException;

    /**
     * 解析机器的本地主机名并返回.
     * 依赖于本地主机表中定义的主机名
     * 
     * @return 本地主机的InetAddress.
     */
    InetAddress getLocalHost() throws UnknownHostException;

    /**
     * 解析InetAddress为dns主机名. It
     * 不应该考虑本地主机列表中定义的主机名
     * 
     * @return 已解析的主机名或者null
     */
    String getHostName(InetAddress addr);

}
