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
package com.zhangyx.vmail.dnsserver.impl;

import com.google.common.collect.ImmutableList;

import com.zhangyx.vmail.configure.ConfigurationProvider;
import com.zhangyx.vmail.dnsserver.DNSService;
import com.zhangyx.vmail.dnsserver.TemporaryResolutionException;
import com.zhangyx.vmail.leftcycle.Configurable;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xbill.DNS.*;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * 提供dns查询服务
 */
@Service("dnsService")
public class DNSServiceImpl implements DNSService, Configurable {

    @Autowired
    private ConfigurationProvider configurationProvider;

    /**
     * A resolver instance used to retrieve DNS records. This is a reference to
     * a third party library object.
     */
    protected Resolver resolver;

    /**
     * A TTL cache of results received from the DNS server. This is a reference
     * to a third party library object.
     */
    protected Cache cache;

    /**
     * Maximum number of RR to cache.
     */
    private int maxCacheSize = 50000;

    /**
     * Whether the DNS response is required to be authoritative
     */
    private int dnsCredibility;

    /**
     * The DNS servers to be used by this service
     */
    private final List<String> dnsServers = new ArrayList<String>();

    /**
     * The search paths to be used
     */
    private Name[] searchPaths = null;

    /**
     * The MX Comparator used in the MX sort.
     */
    private final Comparator<MXRecord> mxComparator = new MXRecordComparator();

    /**
     * If true register this service as the default resolver/cache for DNSJava
     * static calls
     */
    private boolean setAsDNSJavaDefault;

    private String localHostName;

    private String localCanonicalHostName;

    private String localAddress;

    private Logger logger = LoggerFactory.getLogger(DNSServiceImpl.class);

    @Override
    public void configure(HierarchicalConfiguration configuration) throws ConfigurationException {

        boolean autodiscover = configuration.getBoolean("autodiscover", true);

        List<Name> sPaths = new ArrayList<Name>();
        if (autodiscover) {
            logger.info("Autodiscovery is enabled - trying to discover your system's DNS Servers");
            String[] serversArray = ResolverConfig.getCurrentConfig().servers();
            if (serversArray != null) {
                for (String aServersArray : serversArray) {
                    dnsServers.add(aServersArray);
                    logger.info("Adding autodiscovered server " + aServersArray);
                }
            }
            Name[] systemSearchPath = ResolverConfig.getCurrentConfig().searchPath();
            if (systemSearchPath != null && systemSearchPath.length > 0) {
                sPaths.addAll(Arrays.asList(systemSearchPath));
            }
            if (logger.isInfoEnabled()) {
                for (Name searchPath : sPaths) {
                    logger.info("Adding autodiscovered search path " + searchPath.toString());
                }
            }
        }

        // singleIPPerMX = configuration.getBoolean( "singleIPperMX", false );

        setAsDNSJavaDefault = configuration.getBoolean("setAsDNSJavaDefault", true);

        // Get the DNS servers that this service will use for lookups
        Collections.addAll(dnsServers, configuration.getStringArray("servers.server"));

        // Get the DNS servers that this service will use for lookups
        for (String aSearchPathsConfiguration : configuration.getStringArray("searchpaths.searchpath")) {
            try {
                sPaths.add(Name.fromString(aSearchPathsConfiguration));
            } catch (TextParseException e) {
                throw new ConfigurationException("Unable to parse searchpath host: " + aSearchPathsConfiguration, e);
            }
        }

        searchPaths = sPaths.toArray(new Name[sPaths.size()]);

        if (dnsServers.isEmpty()) {
            logger.info("No DNS servers have been specified or found by autodiscovery - adding 127.0.0.1");
            dnsServers.add("127.0.0.1");
        }

        boolean authoritative = configuration.getBoolean("authoritative", false);
        // TODO: Check to see if the credibility field is being used correctly.
        // From the
        // docs I don't think so
        dnsCredibility = authoritative ? Credibility.AUTH_ANSWER : Credibility.NONAUTH_ANSWER;

        maxCacheSize = configuration.getInt("maxcachesize", maxCacheSize);
    }

    @PostConstruct
    public void init() throws Exception {
        logger.debug("DNSService init...");

        HierarchicalConfiguration config = configurationProvider.getConfiguration("dnsservice");
        configure(config);

        // If no DNS servers were configured, default to local host
        if (dnsServers.isEmpty()) {
            try {
                dnsServers.add(InetAddress.getLocalHost().getHostName());
            } catch (UnknownHostException ue) {
                dnsServers.add("127.0.0.1");
            }
        }

        // Create the extended resolver...
        final String[] serversArray = dnsServers.toArray(new String[dnsServers.size()]);

        if (logger.isInfoEnabled()) {
            for (String aServersArray : serversArray) {
                logger.info("DNS Server is: " + aServersArray);
            }
        }

        try {
            resolver = new ExtendedResolver(serversArray);
        } catch (UnknownHostException uhe) {
            logger.error("DNS service could not be initialized.  The DNS servers specified are not recognized hosts.", uhe);
            throw uhe;
        }

        cache = new Cache(DClass.IN);
        cache.setMaxEntries(maxCacheSize);

        if (setAsDNSJavaDefault) {
            Lookup.setDefaultResolver(resolver);
            Lookup.setDefaultCache(cache, DClass.IN);
            Lookup.setDefaultSearchPath(searchPaths);
            logger.info("Registered cache, resolver and search paths as DNSJava defaults");
        }

        // Cache the local hostname and local address. This is needed because
        // the following issues:
        // JAMES-787
        // JAMES-302
        InetAddress addr = getLocalHost();
        localCanonicalHostName = addr.getCanonicalHostName();
        localHostName = addr.getHostName();
        localAddress = addr.getHostAddress();

        logger.debug("DNSService ...init end");
    }

    /**
     * Return the list of DNS servers in use by this service
     *
     * @return an array of DNS server names
     */
    public Name[] getSearchPaths() {
        return searchPaths;
    }

    /**
     * Return a prioritized unmodifiable list of MX records obtained from the
     * server.
     *
     * @param hostname domain name to look up
     * @return a list of MX records corresponding to this mail domain
     * @throws TemporaryResolutionException get thrown on temporary problems
     */
    private List<String> findMXRecordsRaw(String hostname) throws TemporaryResolutionException {
        Record answers[] = lookup(hostname, Type.MX, "MX");
        List<String> servers = new ArrayList<String>();
        if (answers == null) {
            return servers;
        }

        MXRecord[] mxAnswers = new MXRecord[answers.length];

        for (int i = 0; i < answers.length; i++) {
            mxAnswers[i] = (MXRecord) answers[i];
        }
        // just sort for now.. This will ensure that mx records with same prio
        // are in sequence
        Arrays.sort(mxAnswers, mxComparator);

        // now add the mx records to the right list and take care of shuffle
        // mx records with the same priority
        int currentPrio = -1;
        List<String> samePrio = new ArrayList<String>();
        for (int i = 0; i < mxAnswers.length; i++) {
            boolean same = false;
            boolean lastItem = i + 1 == mxAnswers.length;
            MXRecord mx = mxAnswers[i];
            if (i == 0) {
                currentPrio = mx.getPriority();
            } else {
                same = currentPrio == mx.getPriority();
            }

            String mxRecord = mx.getTarget().toString();
            if (same) {
                samePrio.add(mxRecord);
            } else {
                // shuffle entries with same prio
                // JAMES-913
                Collections.shuffle(samePrio);
                servers.addAll(samePrio);

                samePrio.clear();
                samePrio.add(mxRecord);

            }

            if (lastItem) {
                // shuffle entries with same prio
                // JAMES-913
                Collections.shuffle(samePrio);
                servers.addAll(samePrio);
            }
            logger.debug("Found MX record " + mxRecord);
        }
        return servers;
    }

    @Override
    public Collection<String> findMXRecords(String hostname) throws TemporaryResolutionException {
        List<String> servers = new ArrayList<String>();
        try {
            servers = findMXRecordsRaw(hostname);
            return Collections.unmodifiableCollection(servers);
        } finally {
            // If we found no results, we'll add the original domain name if
            // it's a valid DNS entry
            if (servers.size() == 0) {
                StringBuffer logBuffer = new StringBuffer(128).append("Couldn't resolve MX records for domain ").append(hostname).append(".");
                logger.info(logBuffer.toString());
                try {
                    getByName(hostname);
                    servers.add(hostname);
                } catch (UnknownHostException uhe) {
                    // The original domain name is not a valid host,
                    // so we can't add it to the server list. In this
                    // case we return an empty list of servers
                    logBuffer = new StringBuffer(128).append("Couldn't resolve IP address for host ").append(hostname).append(".");
                    logger.error(logBuffer.toString());
                }
            }
        }
    }

    /**
     * Looks up DNS records of the specified type for the specified name.
     * <p/>
     * This method is a public wrapper for the private implementation method
     *
     * @param namestr  the name of the host to be looked up
     * @param type     the type of record desired
     * @param typeDesc the description of the record type, for debugging purpose
     */
    protected Record[] lookup(String namestr, int type, String typeDesc) throws TemporaryResolutionException {
        // Name name = null;
        try {
            // name = Name.fromString(namestr, Name.root);
            Lookup l = new Lookup(namestr, type);

            l.setCache(cache);
            l.setResolver(resolver);
            l.setCredibility(dnsCredibility);
            l.setSearchPath(searchPaths);
            Record[] r = l.run();

            try {
                if (l.getResult() == Lookup.TRY_AGAIN) {
                    throw new TemporaryResolutionException("DNSService is temporary not reachable");
                } else {
                    return r;
                }
            } catch (IllegalStateException ise) {
                // This is okay, because it mimics the original behaviour
                // TODO find out if it's a bug in DNSJava
                logger.debug("Error determining result ", ise);
                throw new TemporaryResolutionException("DNSService is temporary not reachable");
            }

            // return rawDNSLookup(name, false, type, typeDesc);
        } catch (TextParseException tpe) {
            // TODO: Figure out how to handle this correctly.
            logger.error("Couldn't parse name " + namestr, tpe);
            return null;
        }
    }

    protected Record[] lookupNoException(String namestr, int type, String typeDesc) {
        try {
            return lookup(namestr, type, typeDesc);
        } catch (TemporaryResolutionException e) {
            return null;
        }
    }

    /*
     * RFC 2821 section 5 requires that we sort the MX records by their
     * preference. Reminder for maintainers: the return value on a Comparator
     * can be counter-intuitive for those who aren't used to the old C strcmp
     * function:
     * 
     * < 0 ==> a < b = 0 ==> a = b > 0 ==> a > b
     */
    private static class MXRecordComparator implements Comparator<MXRecord> {
        public int compare(MXRecord a, MXRecord b) {
            int pa = a.getPriority();
            int pb = b.getPriority();
            return pa - pb;
        }
    }

    /*
     * java.net.InetAddress.get[All]ByName(String) allows an IP literal to be
     * passed, and will recognize it even with a trailing '.'. However,
     * org.xbill.DNS.Address does not recognize an IP literal with a trailing
     * '.' character. The problem is that when we lookup an MX record for some
     * domains, we may find an IP address, which will have had the trailing '.'
     * appended by the time we get it back from dnsjava. An MX record is not
     * allowed to have an IP address as the right-hand-side, but there are still
     * plenty of such records on the Internet. Since java.net.InetAddress can
     * handle them, for the time being we've decided to support them.
     * 
     * These methods are NOT intended for use outside of James, and are NOT
     * declared by the org.apache.james.services.DNSServer. This is currently a
     * stopgap measure to be revisited for the next release.
     */

    private static String allowIPLiteral(String host) {
        if ((host.charAt(host.length() - 1) == '.')) {
            String possible_ip_literal = host.substring(0, host.length() - 1);
            if (Address.isDottedQuad(possible_ip_literal)) {
                host = possible_ip_literal;
            }
        }
        return host;
    }

    @Override
    public InetAddress getByName(String host) throws UnknownHostException {
        String name = allowIPLiteral(host);

        try {
            // Check if its local
            if (name.equalsIgnoreCase(localHostName) || name.equalsIgnoreCase(localCanonicalHostName) || name.equals(localAddress)) {
                return getLocalHost();
            }

            return Address.getByAddress(name);
        } catch (UnknownHostException e) {
            Record[] records = lookupNoException(name, Type.A, "A");

            if (records != null && records.length >= 1) {
                ARecord a = (ARecord) records[0];
                return InetAddress.getByAddress(name, a.getAddress().getAddress());
            } else
                throw e;
        } finally {

        }
    }

    @Override
    public Collection<InetAddress> getAllByName(String host) throws UnknownHostException {
        String name = allowIPLiteral(host);
        try {
            // Check if its local
            if (name.equalsIgnoreCase(localHostName) || name.equalsIgnoreCase(localCanonicalHostName) || name.equals(localAddress)) {
                return ImmutableList.of(getLocalHost());
            }

            InetAddress addr = Address.getByAddress(name);
            return ImmutableList.of(addr);
        } catch (UnknownHostException e) {
            Record[] records = lookupNoException(name, Type.A, "A");

            if (records != null && records.length >= 1) {
                InetAddress[] addrs = new InetAddress[records.length];
                for (int i = 0; i < records.length; i++) {
                    ARecord a = (ARecord) records[i];
                    addrs[i] = InetAddress.getByAddress(name, a.getAddress().getAddress());
                }
                return ImmutableList.copyOf(addrs);
            } else
                throw e;
        } finally {

        }
    }

    @Override
    public Collection<String> findTXTRecords(String hostname) {
        List<String> txtR = new ArrayList<String>();
        Record[] records = lookupNoException(hostname, Type.TXT, "TXT");

        try {
            if (records != null) {
                for (Record record : records) {
                    TXTRecord txt = (TXTRecord) record;
                    txtR.add(txt.rdataToString());
                }

            }
            return txtR;
        } finally {

        }
    }

    @Override
    public String getHostName(InetAddress addr) {
        String result;
        Name name = ReverseMap.fromAddress(addr);
        Record[] records = lookupNoException(name.toString(), Type.PTR, "PTR");

        try {
            if (records == null) {
                result = addr.getHostAddress();
            } else {
                PTRRecord ptr = (PTRRecord) records[0];
                result = ptr.getTarget().toString();
            }
            return result;
        } finally {
        }
    }

    @Override
    public InetAddress getLocalHost() throws UnknownHostException {
        return InetAddress.getLocalHost();
    }
}
