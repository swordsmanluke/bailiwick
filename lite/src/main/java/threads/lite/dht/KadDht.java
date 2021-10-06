package threads.lite.dht;

import android.annotation.SuppressLint;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import net.luminis.quic.ConnectionIssue;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.stream.QuicOutputStream;
import net.luminis.quic.stream.QuicStream;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dht.pb.Dht;
import record.pb.RecordOuterClass;
import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Cid;
import threads.lite.cid.Multiaddr;
import threads.lite.cid.PeerId;
import threads.lite.cid.Protocol;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.core.DataLimitIssue;
import threads.lite.core.ProtocolIssue;
import threads.lite.core.RecordIssue;
import threads.lite.host.DnsResolver;
import threads.lite.host.LiteHost;
import threads.lite.ipns.Ipns;
import threads.lite.ipns.Validator;
import threads.lite.utils.DataHandler;


public class KadDht implements Routing {

    private static final String TAG = KadDht.class.getSimpleName();
    public final LiteHost host;
    public final PeerId self;

    public final int bucketSize;
    public final int alpha;
    public final RoutingTable routingTable;
    private final Validator validator;


    public KadDht(@NonNull LiteHost host, @NonNull Validator validator,
                  int alpha, int bucketSize) {
        this.host = host;
        this.validator = validator;
        this.self = host.self();
        this.bucketSize = bucketSize;
        this.routingTable = new RoutingTable(bucketSize, ID.convertPeerID(self));
        this.alpha = alpha;
    }


    void bootstrap() {
        // Fill routing table with currently connected peers that are DHT servers
        if (routingTable.isEmpty()) {
            synchronized (TAG.intern()) {
                try {
                    Set<String> addresses = new HashSet<>(IPFS.DHT_BOOTSTRAP_NODES);

                    for (String multiAddress : addresses) {
                        try {
                            Multiaddr multiaddr = new Multiaddr(multiAddress);
                            String name = multiaddr.getStringComponent(Protocol.Type.P2P);
                            Objects.requireNonNull(name);
                            PeerId peerId = PeerId.fromBase58(name);
                            Objects.requireNonNull(peerId);

                            List<Multiaddr> result = DnsResolver.resolveDnsAddress(multiaddr);

                            host.addToAddressBook(peerId, result, false);
                            peerFound(peerId, false);

                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        }
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }
        }
    }

    void peerFound(PeerId p, boolean isReplaceable) {
        try {
            routingTable.addPeer(p, isReplaceable);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    @NonNull
    private Set<PeerId> evalClosestPeers(@NonNull Dht.Message pms) {
        Set<PeerId> peers = new HashSet<>();
        List<Dht.Message.Peer> list = pms.getCloserPeersList();
        for (Dht.Message.Peer entry : list) {
            PeerId peerId = new PeerId(entry.getId().toByteArray());


            List<Multiaddr> multiAddresses = new ArrayList<>();
            List<ByteString> addresses = entry.getAddrsList();
            for (ByteString address : addresses) {
                Multiaddr multiaddr = preFilter(address);
                if (multiaddr != null) {
                    multiAddresses.add(multiaddr);
                }
            }
            host.addToAddressBook(peerId, multiAddresses, false);
            if (host.hasAddresses(peerId)) {
                peers.add(peerId);
            } else {
                LogUtils.info(TAG, "Ignore evalClosestPeers : " + multiAddresses.toString());
            }
        }
        return peers;
    }


    private void getClosestPeers(@NonNull Closeable closeable, @NonNull byte[] key,
                                 @NonNull Channel channel) {
        if (key.length == 0) {
            throw new RuntimeException("can't lookup empty key");
        }

        runLookupWithFollowup(closeable, key, (ctx1, p) -> {

            Dht.Message pms = findPeerSingle(ctx1, p, key);

            Set<PeerId> peers = evalClosestPeers(pms);

            for (PeerId peerId : peers) {
                channel.peer(peerId);
            }

            return peers;
        }, closeable::isClosed, true);


    }

    @Override
    public void putValue(@NonNull Closeable ctx, @NonNull byte[] key, @NonNull byte[] value) {

        bootstrap();

        // don't allow local users to put bad values.
        try {
            Ipns.Entry entry = validator.validate(key, value);
            Objects.requireNonNull(entry);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

        long start = System.currentTimeMillis();

        @SuppressLint("SimpleDateFormat") String format = new SimpleDateFormat(
                IPFS.TimeFormatIpfs).format(new Date());
        RecordOuterClass.Record rec = RecordOuterClass.Record.newBuilder().setKey(ByteString.copyFrom(key))
                .setValue(ByteString.copyFrom(value))
                .setTimeReceived(format).build();

        ConcurrentSkipListSet<PeerId> handled = new ConcurrentSkipListSet<>();
        ExecutorService service = Executors.newFixedThreadPool(4);
        try {
            getClosestPeers(ctx, key, peerId -> {

                if (!handled.contains(peerId)) {
                    handled.add(peerId);
                    service.execute(() -> putValueToPeer(ctx, peerId, rec));
                }
            });
        } finally {
            LogUtils.verbose(TAG, "Finish putValue at " + (System.currentTimeMillis() - start));
        }

    }

    private void putValueToPeer(@NonNull Closeable ctx, @NonNull PeerId peerId,
                                @NonNull RecordOuterClass.Record rec) {

        try {
            Dht.Message pms = Dht.Message.newBuilder()
                    .setType(Dht.Message.MessageType.PUT_VALUE)
                    .setKey(rec.getKey())
                    .setRecord(rec)
                    .setClusterLevelRaw(0).build();

            Dht.Message rimes = sendRequest(ctx, peerId, pms);

            if (!Arrays.equals(rimes.getRecord().getValue().toByteArray(),
                    pms.getRecord().getValue().toByteArray())) {
                throw new RuntimeException("value not put correctly put-message  " +
                        pms.toString() + " get-message " + rimes.toString());
            }
            LogUtils.verbose(TAG, "PutValue Success to " + peerId.toBase58());
        } catch (ClosedException | ConnectionIssue ignore) {
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    @Override
    public void findProviders(@NonNull Closeable closeable, @NonNull Providers providers,
                              @NonNull Cid cid) {
        if (!cid.isDefined()) {
            throw new RuntimeException("Cid invalid");
        }

        bootstrap();

        byte[] key = cid.getHash();

        long start = System.currentTimeMillis();

        try {

            runLookupWithFollowup(closeable, key, (ctx, p) -> {

                Dht.Message pms = findProvidersSingle(ctx, p, key);

                Set<PeerId> result = evalClosestPeers(pms);

                List<Dht.Message.Peer> list = pms.getProviderPeersList();
                for (Dht.Message.Peer entry : list) {

                    PeerId peerId = new PeerId(entry.getId().toByteArray());

                    List<Multiaddr> multiAddresses = new ArrayList<>();
                    List<ByteString> addresses = entry.getAddrsList();
                    for (ByteString address : addresses) {
                        Multiaddr multiaddr = preFilter(address);
                        if (multiaddr != null) {
                            multiAddresses.add(multiaddr);
                        }
                    }

                    LogUtils.info(TAG, "findProviders " + peerId.toBase58() + " "
                            + multiAddresses.toString() + " for " +
                            cid.String() + " " + cid.getVersion());

                    host.addToAddressBook(peerId, multiAddresses, false);

                    providers.peer(peerId);

                }

                return result;

            }, closeable::isClosed, false);
        } finally {
            LogUtils.debug(TAG, "Finish findProviders at " +
                    (System.currentTimeMillis() - start));
        }
    }


    public void removeFromRouting(PeerId p) {
        boolean result = routingTable.removePeer(p);
        if (result) {
            LogUtils.debug(TAG, "Remove from routing " + p.toBase58());
        }
    }


    private Dht.Message makeProvRecord(@NonNull byte[] key) {

        List<Multiaddr> addresses = host.listenAddresses();
        if (addresses.isEmpty()) {
            throw new RuntimeException("no known addresses for self, cannot put provider");
        }

        Dht.Message.Builder builder = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.ADD_PROVIDER)
                .setKey(ByteString.copyFrom(key))
                .setClusterLevelRaw(0);

        Dht.Message.Peer.Builder peerBuilder = Dht.Message.Peer.newBuilder()
                .setId(ByteString.copyFrom(self.getBytes()));
        for (Multiaddr ma : addresses) {
            peerBuilder.addAddrs(ByteString.copyFrom(ma.getBytes()));
        }
        builder.addProviderPeers(peerBuilder.build());

        return builder.build();
    }

    @Override
    public void provide(@NonNull Closeable closeable, @NonNull Cid cid) {

        if (!cid.isDefined()) {
            throw new RuntimeException("invalid cid: undefined");
        }

        bootstrap();

        byte[] key = cid.getHash();

        final Dht.Message mes = makeProvRecord(key);

        ConcurrentSkipListSet<PeerId> handled = new ConcurrentSkipListSet<>();
        ExecutorService service = Executors.newFixedThreadPool(4);
        getClosestPeers(closeable, key, peerId -> {
            if (!handled.contains(peerId)) {
                handled.add(peerId);
                service.execute(() -> sendMessage(closeable, peerId, mes));
            }
        });

    }

    private void sendMessage(@NonNull Closeable closeable, @NonNull PeerId peerId,
                             @NonNull Dht.Message message) {

        synchronized (peerId.toBase58().intern()) {
            QuicConnection conn = null;
            try {
                if (closeable.isClosed()) {
                    return;
                }
                conn = host.connect(peerId, IPFS.CONNECT_TIMEOUT, IPFS.MIN_STREAMS,
                        IPFS.DHT_STREAM_SIZE_LIMIT, false, false);

                if (closeable.isClosed()) {
                    return;
                }
                sendMessage(conn, message);
            } catch (ConnectionIssue ignore) {
                // ignore
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            } finally {
                if (conn != null) {
                    conn.close();
                }
            }
        }
    }

    private void sendMessage(@NonNull QuicConnection conn, @NonNull Dht.Message message) {
        long time = System.currentTimeMillis();
        boolean success = false;
        try {

            QuicStream quicStream = conn.createStream(true,
                    IPFS.CREATE_STREAM_TIMEOUT, TimeUnit.SECONDS);

            KadDhtSend kadDhtSend = new KadDhtSend(quicStream,
                    IPFS.DHT_SEND_READ_TIMEOUT, TimeUnit.SECONDS);
            QuicOutputStream outputStream = quicStream.getOutputStream();

            // TODO quicStream.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_HIGH, false));
            outputStream.write(DataHandler.writeToken(IPFS.STREAM_PROTOCOL, IPFS.DHT_PROTOCOL));
            outputStream.write(DataHandler.encode(message));
            outputStream.close();

            kadDhtSend.reading();
            success = true;
        } catch (ConnectionIssue ignore) {
            // ignore
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            LogUtils.debug(TAG, "Send " + success + " took " + (System.currentTimeMillis() - time));
        }
    }


    private Dht.Message sendRequest(@NonNull Closeable closeable, @NonNull PeerId peerId,
                                    @NonNull Dht.Message message)
            throws ClosedException, ProtocolIssue, ConnectionIssue, DataLimitIssue {

        synchronized (peerId.toBase58().intern()) {
            long time = System.currentTimeMillis();
            boolean success = false;

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            QuicConnection conn = host.connect(peerId, IPFS.CONNECT_TIMEOUT,
                    IPFS.MIN_STREAMS, IPFS.DHT_STREAM_SIZE_LIMIT,
                    false, false);

            if (closeable.isClosed()) {
                throw new ClosedException();
            }


            try {

                time = System.currentTimeMillis();

                QuicStream quicStream = conn.createStream(true,
                        IPFS.CREATE_STREAM_TIMEOUT, TimeUnit.SECONDS);

                KadDhtRequest dhtRequest = new KadDhtRequest(quicStream,
                        IPFS.DHT_REQUEST_READ_TIMEOUT, TimeUnit.SECONDS);
                QuicOutputStream outputStream = quicStream.getOutputStream();
                // TODO quicStream.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_NORMAL, false));

                outputStream.write(DataHandler.writeToken(IPFS.STREAM_PROTOCOL, IPFS.DHT_PROTOCOL));
                outputStream.write(DataHandler.encode(message));
                outputStream.close();

                Dht.Message msg = dhtRequest.reading();
                Objects.requireNonNull(msg);
                success = true;
                peerId.setLatency(System.currentTimeMillis() - time);

                return msg;
            } catch (ConnectionIssue connectionIssue) {
                throw connectionIssue;
            } catch (IOException ioException) {
                LogUtils.error(TAG, ioException);
                throw new ConnectionIssue();
            } finally {
                conn.close();
                LogUtils.debug(TAG, "Request " + success + " took " +
                        (System.currentTimeMillis() - time));
            }
        }
    }


    private Dht.Message getValueSingle(@NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] key)
            throws ProtocolIssue, ClosedException, ConnectionIssue, DataLimitIssue {
        Dht.Message pms = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.GET_VALUE)
                .setKey(ByteString.copyFrom(key))
                .setClusterLevelRaw(0).build();
        return sendRequest(ctx, p, pms);
    }

    private Dht.Message findPeerSingle(@NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] key)
            throws ClosedException, ProtocolIssue, ConnectionIssue, DataLimitIssue {
        Dht.Message pms = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.FIND_NODE)
                .setKey(ByteString.copyFrom(key))
                .setClusterLevelRaw(0).build();

        return sendRequest(ctx, p, pms);
    }

    private Dht.Message findProvidersSingle(@NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] key)
            throws ClosedException, ProtocolIssue, ConnectionIssue, DataLimitIssue {
        Dht.Message pms = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.GET_PROVIDERS)
                .setKey(ByteString.copyFrom(key))
                .setClusterLevelRaw(0).build();
        return sendRequest(ctx, p, pms);
    }


    @Nullable
    private Multiaddr preFilter(@NonNull ByteString address) {
        try {
            return new Multiaddr(address.toByteArray());
        } catch (Throwable ignore) {
            LogUtils.error(TAG, address.toStringUtf8());
        }
        return null;
    }

    @Override
    public void findPeer(@NonNull Closeable closeable, @NonNull Updater updater, @NonNull PeerId id) {

        bootstrap();

        byte[] key = id.getBytes();
        long start = System.currentTimeMillis();
        try {
            runLookupWithFollowup(closeable, key, (ctx, p) -> {
                Dht.Message pms = findPeerSingle(ctx, p, key);

                Set<PeerId> peers = evalClosestPeers(pms);
                for (PeerId peerId : peers) {
                    if (Objects.equals(peerId, id)) {
                        LogUtils.debug(TAG, "findPeer " + peerId.toBase58() + " " +
                                host.getAddresses(peerId).toString());
                        updater.peer(peerId);
                    }
                }

                if (ctx.isClosed()) {
                    return Collections.emptySet();
                }
                return peers;

            }, closeable::isClosed, false);
        } finally {
            LogUtils.debug(TAG, "Finish findPeer " + id.toBase58() +
                    " at " + (System.currentTimeMillis() - start));
        }
    }

    private Map<PeerId, PeerState> runQuery(@NonNull Closeable ctx, @NonNull byte[] target,
                                            @NonNull QueryFunc queryFn, @NonNull StopFunc stopFn) {
        // pick the K closest peers to the key in our Routing table.
        ID targetKadID = ID.convertKey(target);
        List<PeerId> seedPeers = routingTable.NearestPeers(targetKadID, bucketSize);
        if (seedPeers.size() == 0) {
            return Collections.emptyMap();
        }

        Query q = new Query(this, target, seedPeers, queryFn, stopFn);

        try {
            q.run(ctx);
        } catch (InterruptedException | ClosedException interruptedException) {
            return Collections.emptyMap();
        }

        return q.constructLookupResult(targetKadID);
    }

    // runLookupWithFollowup executes the lookup on the target using the given query function and stopping when either the
    // context is cancelled or the stop function returns true. Note: if the stop function is not sticky, i.e. it does not
    // return true every time after the first time it returns true, it is not guaranteed to cause a stop to occur just
    // because it momentarily returns true.
    //
    // After the lookup is complete the query function is run (unless stopped) against all of the top K peers from the
    // lookup that have not already been successfully queried.
    private void runLookupWithFollowup(@NonNull Closeable closeable, @NonNull byte[] target,
                                       @NonNull QueryFunc queryFn, @NonNull StopFunc stopFn,
                                       boolean runFollowUp) {


        Map<PeerId, PeerState> lookupRes = runQuery(closeable, target, queryFn, stopFn);


        // query all of the top K peers we've either Heard about or have outstanding queries we're Waiting on.
        // This ensures that all of the top K results have been queried which adds to resiliency against churn for query
        // functions that carry state (e.g. FindProviders and GetValue) as well as establish connections that are needed
        // by stateless query functions (e.g. GetClosestPeers and therefore Provide and PutValue)

        List<PeerId> queryPeers = new ArrayList<>();
        for (Map.Entry<PeerId, PeerState> entry : lookupRes.entrySet()) {
            PeerState state = entry.getValue();
            if (state == PeerState.PeerHeard || state == PeerState.PeerWaiting) {
                queryPeers.add(entry.getKey());
            }
        }

        if (queryPeers.size() == 0) {
            return;
        }

        if (stopFn.stop()) {
            return;
        }

        if (runFollowUp) {
            List<Future<Void>> futures = new ArrayList<>();
            ExecutorService executor = Executors.newFixedThreadPool(4);
            for (PeerId peerId : queryPeers) {
                Future<Void> future = executor.submit(() -> invokeQuery(closeable, queryFn, peerId));
                futures.add(future);
            }
            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (Throwable ignore) {
                    // ignore
                }
            }
        }
    }

    private Void invokeQuery(@NonNull Closeable closeable,
                             @NonNull QueryFunc queryFn,
                             @NonNull PeerId peerId) {
        if (closeable.isClosed()) {
            return null;
        }

        try {
            queryFn.query(closeable, peerId);
        } catch (Throwable ignore) {
            // ignore
        }
        return null;
    }


    private Pair<Ipns.Entry, Set<PeerId>> getValueOrPeers(
            @NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] key)
            throws ClosedException, ProtocolIssue, ConnectionIssue, DataLimitIssue {


        Dht.Message pms = getValueSingle(ctx, p, key);

        Set<PeerId> peers = evalClosestPeers(pms);

        if (pms.hasRecord()) {

            RecordOuterClass.Record rec = pms.getRecord();
            try {
                byte[] record = rec.getValue().toByteArray();
                if (record != null && record.length > 0) {
                    Ipns.Entry entry = validator.validate(rec.getKey().toByteArray(), record);
                    return Pair.create(entry, peers);
                }
            } catch (RecordIssue issue) {
                LogUtils.debug(TAG, issue.getMessage());
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }

        if (peers.size() > 0) {
            return Pair.create(null, peers);
        }
        return Pair.create(null, Collections.emptySet());
    }

    private void getValues(@NonNull Closeable ctx, @NonNull RecordValueFunc recordFunc,
                           @NonNull byte[] key, @NonNull StopFunc stopQuery) {


        runLookupWithFollowup(ctx, key, (ctx1, p) -> {

            Pair<Ipns.Entry, Set<PeerId>> result = getValueOrPeers(ctx1, p, key);
            Ipns.Entry entry = result.first;
            Set<PeerId> peers = result.second;

            if (entry != null) {
                recordFunc.record(entry);
            }

            return peers;
        }, stopQuery, true);

    }


    private void processValues(@NonNull Closeable ctx, @Nullable Ipns.Entry best,
                               @NonNull Ipns.Entry v, @NonNull RecordReportFunc reporter) {

        if (best != null) {
            if (Objects.equals(best, v)) {
                reporter.report(ctx, v, false);
            } else {
                int value = validator.compare(best, v);

                if (value == -1) {
                    reporter.report(ctx, v, false);
                }
            }
        } else {
            reporter.report(ctx, v, true);
        }
    }


    @Override
    public void searchValue(@NonNull Closeable closeable, @NonNull ResolveInfo resolveInfo,
                            @NonNull byte[] key) {

        bootstrap();

        AtomicReference<Ipns.Entry> best = new AtomicReference<>();
        long start = System.currentTimeMillis();
        try {
            getValues(closeable, entry -> processValues(closeable, best.get(),
                    entry, (ctx1, v, better) -> {
                        if (better) {
                            resolveInfo.resolved(v);
                            best.set(v);
                        }
                    }), key, closeable::isClosed);
        } finally {
            LogUtils.info(TAG, "Finish searchValue at " + (System.currentTimeMillis() - start));
        }
    }

    public interface StopFunc {
        boolean stop();
    }

    public interface QueryFunc {
        @NonNull
        Set<PeerId> query(@NonNull Closeable ctx, @NonNull PeerId peerId)
                throws ClosedException, ProtocolIssue, ConnectionIssue, DataLimitIssue;
    }


    public interface RecordValueFunc {
        void record(@NonNull Ipns.Entry entry);
    }

    public interface RecordReportFunc {
        void report(@NonNull Closeable ctx, @NonNull Ipns.Entry entry, boolean better);
    }


    interface Channel {
        void peer(@NonNull PeerId peerId);
    }

}
