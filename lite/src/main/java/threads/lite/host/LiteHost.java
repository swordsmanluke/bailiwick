package threads.lite.host;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;

import net.luminis.quic.ConnectionIssue;
import net.luminis.quic.QuicClientConnectionImpl;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.TransportParameters;
import net.luminis.quic.Version;
import net.luminis.quic.server.ApplicationProtocolConnection;
import net.luminis.quic.server.ApplicationProtocolConnectionFactory;
import net.luminis.quic.server.Server;
import net.luminis.quic.stream.QuicStream;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import bitswap.pb.MessageOuterClass;
import identify.pb.IdentifyOuterClass;
import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.bitswap.BitSwap;
import threads.lite.bitswap.BitSwapMessage;
import threads.lite.cid.Cid;
import threads.lite.cid.Multiaddr;
import threads.lite.cid.PeerId;
import threads.lite.cid.Protocol;
import threads.lite.core.Closeable;
import threads.lite.crypto.PrivKey;
import threads.lite.crypto.PubKey;
import threads.lite.dht.KadDht;
import threads.lite.dht.Routing;
import threads.lite.format.BlockStore;
import threads.lite.ipns.Ipns;
import threads.lite.push.Push;
import threads.lite.relay.RelayConnection;


public class LiteHost {

    @NonNull
    private static final ExecutorService executors = Executors.newFixedThreadPool(2);
    @NonNull
    private static final String TAG = LiteHost.class.getSimpleName();
    @NonNull
    private static final Duration DefaultRecordEOL = Duration.ofHours(24);
    /* NOT YET REQUIRED
    @NonNull

    @NonNull
    private static final TrustManager tm = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String s) {
            try {
                if (IPFS.EVALUATE_PEER) {
                    for (X509Certificate cert : chain) {
                        PubKey pubKey = LiteHostCertificate.extractPublicKey(cert);
                        Objects.requireNonNull(pubKey);
                        PeerId peerId = PeerId.fromPubKey(pubKey);
                        Objects.requireNonNull(peerId);
                    }
                }
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String s) {

            try {
                if (IPFS.EVALUATE_PEER) {
                    for (X509Certificate cert : chain) {
                        PubKey pubKey = LiteHostCertificate.extractPublicKey(cert);
                        Objects.requireNonNull(pubKey);
                        PeerId peerId = PeerId.fromPubKey(pubKey);
                        Objects.requireNonNull(peerId);
                        remotes.put(peerId, pubKey);
                    }
                }
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };*/
    private static int failure = 0;
    private static int success = 0;
    @NonNull
    public final List<ConnectionHandler> handlers = new ArrayList<>();
    @NonNull
    public final AtomicBoolean inet6 = new AtomicBoolean(false);
    @NonNull
    private final ConcurrentHashMap<PeerId, QuicConnection> relays = new ConcurrentHashMap<>();
    @NonNull
    private final ConcurrentHashMap<PeerId, QuicConnection> connections = new ConcurrentHashMap<>();

    @NonNull
    private final ConcurrentSkipListSet<InetAddress> addresses = new ConcurrentSkipListSet<>(
            Comparator.comparing(InetAddress::getHostAddress)
    );
    @NonNull
    private final ConcurrentHashMap<PeerId, Set<Multiaddr>> addressBook = new ConcurrentHashMap<>();
    @NonNull
    private final Routing routing;
    @NonNull
    private final PrivKey privKey;
    @NonNull
    private final BitSwap bitSwap;
    private final int port;
    @NonNull
    private final LiteHostCertificate selfSignedCertificate;
    @NonNull
    private final Set<PeerId> swarm = ConcurrentHashMap.newKeySet();
    @Nullable
    private Push push;
    @Nullable
    private Server server;

    public LiteHost(@NonNull LiteHostCertificate selfSignedCertificate,
                    @NonNull PrivKey privKey,
                    @NonNull BlockStore blockstore,
                    int alpha) {
        this.selfSignedCertificate = selfSignedCertificate;
        this.privKey = privKey;


        this.routing = new KadDht(this,
                new Ipns(), alpha, IPFS.DHT_BUCKET_SIZE);

        this.bitSwap = new BitSwap(blockstore, this);
        int port = IPFS.DEFAULT_PORT;
        if (!isLocalPortFree(port)) {
            port = nextFreePort();
        }
        this.port = port;
        try {
            List<Version> supportedVersions = new ArrayList<>();
            supportedVersions.add(Version.IETF_draft_29);
            supportedVersions.add(Version.QUIC_version_1);

            boolean requireRetry = false; // TODO what does it mean
            server = new Server(port, IPFS.APRN,
                    new FileInputStream(selfSignedCertificate.certificate()),
                    new FileInputStream(selfSignedCertificate.privateKey()),
                    supportedVersions, requireRetry,
                    new ApplicationProtocolConnectionFactory() {
                        @Override
                        public ApplicationProtocolConnection createConnection(
                                String protocol, QuicConnection quicConnection) {
                            return new ServerHandler(LiteHost.this, quicConnection);

                        }
                    });
            server.start();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    private static int nextFreePort() {
        int port = ThreadLocalRandom.current().nextInt(4001, 65535);
        while (true) {
            if (isLocalPortFree(port)) {
                return port;
            } else {
                port = ThreadLocalRandom.current().nextInt(4001, 65535);
            }
        }
    }

    private static boolean isLocalPortFree(int port) {
        try {
            new ServerSocket(port).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    public ConcurrentHashMap<PeerId, QuicConnection> relays() {
        return relays;
    }

    @NonNull
    public Routing getRouting() {
        return routing;
    }

    @NonNull
    public BitSwap getBitSwap() {
        return bitSwap;
    }

    public PeerId self() {
        return PeerId.fromPubKey(privKey.publicKey());
    }

    public void addConnectionHandler(@NonNull ConnectionHandler connectionHandler) {
        handlers.add(connectionHandler);
    }

    @Nullable
    public BitSwapMessage message(@NonNull QuicStream quicStream, @NonNull MessageOuterClass.Message msg) {
        BitSwapMessage message = BitSwapMessage.newMessageFromProto(msg);
        return bitSwap.receiveMessage(quicStream, message);
    }

    public void findProviders(@NonNull Closeable closeable, @NonNull Routing.Providers providers,
                              @NonNull Cid cid) {
        routing.findProviders(closeable, providers, cid);
    }

    public boolean hasAddresses(@NonNull PeerId peerId) {
        Set<Multiaddr> res = addressBook.get(peerId);
        if (res == null) {
            return false;
        }
        return !res.isEmpty();
    }


    @NonNull
    public Set<Multiaddr> getAddresses(@NonNull PeerId peerId) {
        try {
            Collection<Multiaddr> multiaddrs = addressBook.get(peerId);
            if (multiaddrs != null) {
                return new HashSet<>(multiaddrs);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return Collections.emptySet();
    }

    @NonNull
    private List<Multiaddr> prepareAddresses(@NonNull PeerId peerId) {
        List<Multiaddr> all = new ArrayList<>();
        for (Multiaddr ma : getAddresses(peerId)) {
            try {
                if (ma.has(Protocol.Type.DNS)) {
                    all.add(DnsResolver.resolveDns(ma));
                } else if (ma.has(Protocol.Type.DNS6)) {
                    all.add(DnsResolver.resolveDns6(ma));
                } else if (ma.has(Protocol.Type.DNS4)) {
                    all.add(DnsResolver.resolveDns4(ma));
                } else if (ma.has(Protocol.Type.DNSADDR)) {
                    all.addAll(DnsResolver.resolveDnsAddress(ma));
                } else {
                    all.add(ma);
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, ma.toString() + " prepareAddresses " + throwable);
            }
        }
        List<Multiaddr> result = new ArrayList<>();
        for (Multiaddr ma : all) {
            if (isSupported(ma, true)) {
                result.add(ma);
            }
        }
        return result;
    }

    public boolean findPeer(@NonNull Closeable closeable, @NonNull PeerId peerId) {
        AtomicBoolean done = new AtomicBoolean(false);
        routing.findPeer(() -> closeable.isClosed() || done.get(), peerId1 -> done.set(true), peerId);
        return done.get();
    }


    public void publishName(@NonNull Closeable closable, @NonNull PrivKey privKey,
                            @NonNull String name, @NonNull PeerId id, int sequence) {


        Date eol = Date.from(new Date().toInstant().plus(DefaultRecordEOL));

        Duration duration = Duration.ofHours(IPFS.IPNS_DURATION);
        ipns.pb.Ipns.IpnsEntry
                record = Ipns.create(privKey, name.getBytes(), sequence, eol, duration);

        PubKey pk = privKey.publicKey();

        record = Ipns.embedPublicKey(pk, record);

        byte[] bytes = record.toByteArray();

        byte[] ipns = IPFS.IPNS_PATH.getBytes();
        byte[] ipnsKey = Bytes.concat(ipns, id.getBytes());
        routing.putValue(closable, ipnsKey, bytes);
    }

    public void swarmReduce(@NonNull PeerId peerId) {
        swarm.remove(peerId);
    }

    private boolean isSupported(@NonNull Multiaddr address, boolean acceptLocal) {

        if (address.has(Protocol.Type.DNSADDR)) {
            return true;
        }
        if (address.has(Protocol.Type.DNS)) {
            return true;
        }
        if (address.has(Protocol.Type.DNS4)) {
            return true;
        }
        if (address.has(Protocol.Type.DNS6)) {
            return true;
        }


        try {
            InetAddress inetAddress = InetAddress.getByName(address.getHost());
            if (inetAddress.isAnyLocalAddress() || inetAddress.isLinkLocalAddress()
                    || (!acceptLocal && inetAddress.isLoopbackAddress())
                    || (!acceptLocal && inetAddress.isSiteLocalAddress())) {
                return false;
            }
        } catch (Throwable throwable) {
            LogUtils.debug(TAG, "" + throwable);
            return false;
        }

        return address.has(Protocol.Type.QUIC);
    }

    public void addToAddressBook(@NonNull PeerId peerId,
                                 @NonNull Collection<Multiaddr> addresses,
                                 boolean acceptSiteLocal) {

        try {
            synchronized (peerId.toBase58().intern()) {
                Set<Multiaddr> info = addressBook.computeIfAbsent(peerId, k -> new HashSet<>());
                for (Multiaddr ma : addresses) {
                    if (isSupported(ma, acceptSiteLocal)) {
                        info.add(ma);
                    }
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void handleConnection(@NonNull QuicConnection connection, @NonNull PeerId peerId) {

        if (handlers.size() > 0) {
            executors.execute(() -> {
                for (ConnectionHandler handle : handlers) {
                    try {
                        handle.handleConnection(connection, peerId);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
            });
        }
    }

    public void swarmEnhance(@NonNull PeerId peerId) {
        swarm.add(peerId);
    }

    @NonNull
    public Set<PeerId> getPeers() {
        return new HashSet<>(swarm);
    }

    public boolean isConnected(@NonNull PeerId peerId) {

        QuicConnection conn = connections.get(peerId);
        if (conn != null && conn.isConnected()) {
            return true;
        } else {
            connections.remove(peerId);
            return false;
        }

    }

    private void removeConnection(@NonNull PeerId peerId) {
        LogUtils.debug(TAG, "Remove connection " + peerId.toBase58());

        QuicConnection conn = connections.remove(peerId);
        if (conn != null) {
            conn.close();
        }

    }

    @NonNull
    public List<Multiaddr> listenAddresses() {
        try {

            List<Multiaddr> list = new ArrayList<>();

            for (String address : IPFS.CIRCUIT_NODES) {
                list.add(new Multiaddr(address));
            }

            if (addresses.isEmpty()) {
                evaluateDefaultHost();
            }

            for (InetAddress inetAddress : addresses) {
                String pre = "/ip4/";
                if (inetAddress instanceof Inet6Address) {
                    pre = "/ip6/";
                }

                Multiaddr multiaddr = new Multiaddr(pre +
                        inetAddress.getHostAddress() + "/udp/" + port + "/quic");

                list.add(multiaddr);
            }

            return list;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return Collections.emptyList();

    }

    private void evaluateDefaultHost() throws UnknownHostException {
        if (addresses.isEmpty()) {
            addresses.add(InetAddress.getByName("127.0.0.1"));
            addresses.add(InetAddress.getByName("::1"));
        }
    }

    public Set<PeerId> bootstrap() {
        Set<PeerId> peers = new HashSet<>();

        try {
            Set<String> addresses = DnsResolver.resolveDnsAddress(IPFS.LIB2P_DNS);
            addresses.addAll(IPFS.DHT_BOOTSTRAP_NODES);

            for (String address : addresses) {
                try {
                    Multiaddr multiaddr = new Multiaddr(address);
                    String name = multiaddr.getStringComponent(Protocol.Type.P2P);
                    Objects.requireNonNull(name);
                    PeerId peerId = PeerId.fromBase58(name);
                    Objects.requireNonNull(peerId);

                    addToAddressBook(peerId,
                            Collections.singletonList(multiaddr), false);
                    peers.add(peerId);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        return peers;
    }


    @Nullable
    public QuicConnection find(@NonNull PeerId peerId, int timeout, int initialMaxStreams,
                               int initialMaxStreamData, boolean keepConnection,
                               @NonNull Closeable closeable) {
        try {
            return connect(peerId, timeout, initialMaxStreams,
                    initialMaxStreamData, keepConnection, false);
        } catch (Throwable ignore) {
            if (findPeer(closeable, peerId)) {
                try {
                    return connect(peerId, timeout, initialMaxStreams,
                            initialMaxStreamData, keepConnection, false);
                } catch (Throwable throwable) {
                    // ignore exception again
                }
            }
        }
        return null;
    }


    @NonNull
    public QuicConnection relay(@NonNull PeerId peerId)
            throws ConnectionIssue {

        synchronized (peerId.toBase58().intern()) {


            RelayConnection conn = RelayConnection.createRandomRelayConnection(
                    peerId, LiteHost.this);

            handleConnection(conn, peerId);


            return conn;
        }
    }

    @NonNull
    public QuicConnection connect(@NonNull PeerId peerId, int timeout,
                                  int initialMaxStreams, int initialMaxStreamData,
                                  boolean keepConnection, boolean keepAlive) throws ConnectionIssue {


        if (keepConnection) {
            QuicConnection conn = connections.get(peerId);
            if (conn != null && conn.isConnected()) {
                LogUtils.verbose(TAG, "Reuse connection " + peerId.toBase58());
                return conn;
            } else {
                removeConnection(peerId);
            }
        }

        boolean ipv6 = inet6.get();
        List<Multiaddr> multiaddrs = prepareAddresses(peerId);
        multiaddrs.sort((o1, o2) -> o2.getHost().compareTo(o1.getHost()));
        int addresses = multiaddrs.size();
        if (addresses == 0) {
            LogUtils.debug(TAG, "Run false" + " Success " + success + " " +
                    "Failure " + failure + " " + "/p2p/" + peerId.toBase58() + " " +
                    "No address");
            throw new ConnectionIssue();
        }


        for (Multiaddr address : multiaddrs) {


            // sort out IPv6 Addresses in case of non ipv6 network
            if (!ipv6 && address.has(Protocol.Type.IP6)) {
                continue;
            }

            boolean relayConnection = address.has(Protocol.Type.P2PCIRCUIT);


            long start = System.currentTimeMillis();
            boolean run = false;
            try {

                if (relayConnection) {
                    PeerId relayId = PeerId.fromBase58(
                            address.getStringComponent(Protocol.Type.IPFS));
                    String host = address.getHost();
                    int port = address.getPort();
                    boolean ip4 = address.isIP4();
                    String pre = "/ip6/";
                    if (ip4) {
                        pre = "/ip4/";
                    }

                    Multiaddr multiaddr = new Multiaddr(pre.concat(host).concat("/udp/").
                            concat(String.valueOf(port)).concat("/quic"));

                    addToAddressBook(relayId,
                            Collections.singletonList(multiaddr), false);

                    // keepAlive is set to false, only for own relays we are
                    // keeping the connection
                    QuicConnection conn = connect(relayId, IPFS.CONNECT_TIMEOUT,
                            IPFS.MAX_STREAMS, IPFS.MESSAGE_SIZE_MAX, true,
                            false);

                    RelayConnection relayConn = RelayConnection.createRelayConnection(
                            conn, peerId, this);

                    handleConnection(relayConn, peerId);

                    if (keepConnection) {
                        connections.put(peerId, relayConn);
                    }
                    run = true;
                    return relayConn;
                }

                QuicClientConnectionImpl conn = QuicClientConnectionImpl.newBuilder()
                        .version(Version.IETF_draft_29)
                        .noServerCertificateCheck()
                        .clientCertificate(selfSignedCertificate.cert())
                        .clientCertificateKey(selfSignedCertificate.key())
                        .host(address.getHost())
                        .port(address.getPort())
                        .build();

                Objects.requireNonNull(conn);

                conn.connect(timeout, IPFS.APRN,
                        new TransportParameters(IPFS.GRACE_PERIOD, initialMaxStreamData,
                                initialMaxStreams, IPFS.MIN_STREAMS), null);

                if (keepAlive) {
                    conn.keepAlive(IPFS.KEEP_ALIVE_TIMEOUT, IPFS.PING_INTERVAL);
                }

                if (initialMaxStreams > 0) {
                    conn.setPeerInitiatedStreamCallback(
                            (quicStream) -> new StreamHandler(quicStream, this));
                }

                handleConnection(conn, peerId);

                if (keepConnection) {
                    connections.put(peerId, conn);
                }

                run = true;
                return conn;
            } catch (TimeoutException ignore) {
                // nothing to do here
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            } finally {
                if (run) {
                    success++;
                } else {
                    failure++;
                }

                if (relayConnection) {
                    LogUtils.error(TAG, "Run " + run + " Relay Connection Success " + success + " " +
                            "Failure " + failure +
                            " Peer " + peerId.toBase58() + " " +
                            address + " " + (System.currentTimeMillis() - start));
                } else {
                    LogUtils.debug(TAG, "Run " + run + " Success " + success + " " +
                            "Failure " + failure +
                            " Peer " + peerId.toBase58() + " " +
                            address + " " + (System.currentTimeMillis() - start));
                }
            }
        }

        throw new ConnectionIssue();

    }

    public void push(@NonNull QuicConnection connection, @NonNull byte[] content) {
        try {
            Objects.requireNonNull(connection);
            Objects.requireNonNull(content);

            if (push != null) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> push.push(connection, new String(content)));
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    public void setPush(@Nullable Push push) {
        this.push = push;
    }

    @NonNull
    public Multiaddr transform(@NonNull InetSocketAddress inetSocketAddress) {

        InetAddress inetAddress = inetSocketAddress.getAddress();
        boolean ipv6 = false;
        if (inetAddress instanceof Inet6Address) {
            ipv6 = true;
        }
        int port = inetSocketAddress.getPort();
        String multiaddress = "";
        if (ipv6) {
            multiaddress = multiaddress.concat("/ip6/");
        } else {
            multiaddress = multiaddress.concat("/ip4/");
        }
        multiaddress = multiaddress + inetAddress.getHostAddress() + "/udp/" + port + "/quic";
        return new Multiaddr(multiaddress);

    }

    public IdentifyOuterClass.Identify createIdentity(@Nullable InetSocketAddress inetSocketAddress) {

        IdentifyOuterClass.Identify.Builder builder = IdentifyOuterClass.Identify.newBuilder()
                .setAgentVersion(IPFS.AGENT)
                .setPublicKey(ByteString.copyFrom(privKey.publicKey().bytes()))
                .setProtocolVersion(IPFS.PROTOCOL_VERSION);

        List<Multiaddr> addresses = listenAddresses();
        for (Multiaddr addr : addresses) {
            builder.addListenAddrs(ByteString.copyFrom(addr.getBytes()));
        }

        List<String> protocols = getProtocols();
        for (String protocol : protocols) {
            builder.addProtocols(protocol);
        }

        if (inetSocketAddress != null) {
            Multiaddr observed = transform(inetSocketAddress);
            builder.setObservedAddr(ByteString.copyFrom(observed.getBytes()));
        }

        return builder.build();
    }

    private List<String> getProtocols() {
        return Arrays.asList(IPFS.STREAM_PROTOCOL, IPFS.PUSH_PROTOCOL, IPFS.BITSWAP_PROTOCOL,
                IPFS.IDENTITY_PROTOCOL, IPFS.DHT_PROTOCOL, IPFS.RELAY_PROTOCOL);
    }

    public void shutdown() {
        try {
            if (server != null) {
                server.shutdown();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            server = null;
        }
    }


    public boolean swarmContains(@NonNull PeerId peerId) {
        return swarm.contains(peerId);
    }


    public void updateNetwork(@NonNull String networkInterface) {
        updateListenAddresses(networkInterface);
    }

    public void updateListenAddresses(@NonNull String networkInterfaceName) {

        try {
            boolean ipv6 = false;
            List<InetAddress> collect = new ArrayList<>();
            List<NetworkInterface> interfaces = Collections.list(
                    NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface networkInterface : interfaces) {
                if (Objects.equals(networkInterface.getName(), networkInterfaceName)) {

                    List<InetAddress> addresses =
                            Collections.list(networkInterface.getInetAddresses());
                    for (InetAddress inetAddress : addresses) {

                        if (!(inetAddress.isAnyLocalAddress() ||
                                inetAddress.isLinkLocalAddress() ||
                                inetAddress.isLoopbackAddress())) {
                            if (inetAddress instanceof Inet6Address) {
                                ipv6 = true;
                            }

                            collect.add(inetAddress);
                        }
                    }
                }
            }
            synchronized (TAG.intern()) {
                inet6.set(ipv6);
                addresses.clear();
                addresses.addAll(collect);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public int getPort() {
        return port;
    }

}
