package threads.lite.bitswap;

import androidx.annotation.NonNull;

import net.luminis.quic.QuicConnection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Cid;
import threads.lite.cid.PeerId;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.format.Block;
import threads.lite.format.BlockStore;
import threads.lite.host.LiteHost;


public class BitSwapManager {

    private static final String TAG = BitSwapManager.class.getSimpleName();

    private final LiteHost host;
    private final BlockStore blockStore;
    private final ScheduledThreadPoolExecutor providers = new ScheduledThreadPoolExecutor(6);
    private final ExecutorService connector = Executors.newFixedThreadPool(6);
    private final ConcurrentHashMap<QuicConnection, Boolean> peers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Cid, ConcurrentLinkedDeque<QuicConnection>> matches = new ConcurrentHashMap<>();
    private final Blocker blocker = new Blocker();
    private final BitSwap bitSwap;

    public BitSwapManager(@NonNull BitSwap bitSwap, @NonNull BlockStore blockStore, @NonNull LiteHost host) {
        this.bitSwap = bitSwap;
        this.blockStore = blockStore;
        this.host = host;
    }

    private void addPeer(@NonNull QuicConnection conn, boolean newCreated) {
        if (!peers.containsKey(conn)) {
            peers.put(conn, newCreated);
        }
    }

    public void haveReceived(@NonNull QuicConnection conn, @NonNull List<Cid> cids) {

        for (Cid cid : cids) {
            ConcurrentLinkedDeque<QuicConnection> res = matches.get(cid);
            if (res != null) {
                res.add(conn);
            }
        }
    }

    public void reset() {

        LogUtils.debug(TAG, "Reset");
        try {
            for (Map.Entry<QuicConnection, Boolean> entry : peers.entrySet()) {
                if (entry.getValue()) {
                    QuicConnection conn = entry.getKey();
                    conn.close();
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        try {
            peers.clear();
            matches.clear();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void find(@NonNull Closeable closeable, @NonNull PeerId peerId) {
        boolean wasConn = host.isConnected(peerId);
        try {
            if (closeable.isClosed()) {
                return;
            }

            QuicConnection conn;
            try {
                conn = host.connect(peerId, IPFS.CONNECT_TIMEOUT,
                        IPFS.MAX_STREAMS, IPFS.MESSAGE_SIZE_MAX,
                        true, false);
            } catch (Throwable ignore) {
                conn = host.relay(peerId);
            }
            Objects.requireNonNull(conn);

            LogUtils.debug(TAG, "New relay connection " + peerId.toBase58());

            if (closeable.isClosed()) {
                return;
            }

            addPeer(conn, !wasConn);
        } catch (Throwable ignore) {
            // ignore
        }
    }


    public void relay(@NonNull Closeable closeable, @NonNull PeerId peerId) {
        boolean wasConn = host.isConnected(peerId);

        try {
            if (closeable.isClosed()) {
                return;
            }
            QuicConnection conn = host.relay(peerId);

            LogUtils.debug(TAG, "New relay connection " + peerId.toBase58());

            if (closeable.isClosed()) {
                return;
            }

            addPeer(conn, !wasConn);

        } catch (Throwable ignore) {
            // ignore
        }
    }

    public void connect(@NonNull Closeable closeable, @NonNull PeerId peerId) {
        boolean wasConn = host.isConnected(peerId);

        try {
            if (closeable.isClosed()) {
                return;
            }

            QuicConnection conn = host.connect(peerId, IPFS.CONNECT_TIMEOUT,
                    IPFS.MAX_STREAMS, IPFS.MESSAGE_SIZE_MAX,
                    true, false);

            LogUtils.debug(TAG, "New connection " + peerId.toBase58());

            if (closeable.isClosed()) {
                return;
            }

            addPeer(conn, !wasConn);

        } catch (Throwable ignore) {
            // ignore
        }
    }


    public void runHaveMessage(@NonNull Closeable closeable, QuicConnection conn,
                               @NonNull List<Cid> cids) {
        new Thread(() -> {
            long start = System.currentTimeMillis();
            boolean success = false;
            try {
                if (closeable.isClosed()) {
                    return;
                }
                bitSwap.sendHaveMessage(conn, cids);
                success = true;
            } catch (Throwable throwable) {
                LogUtils.error(TAG, "runHaveMessage " + throwable.getClass().getName());
            } finally {
                LogUtils.debug(TAG, "runHaveMessage " + success + " " +
                        " took " + (System.currentTimeMillis() - start));
            }
        }).start();
    }


    public Block runWantHaves(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException {

        matches.put(cid, new ConcurrentLinkedDeque<>());


        loadProviders(closeable, cid, IPFS.BITSWAP_LOAD_PROVIDERS_DELAY, TimeUnit.SECONDS);

        Set<QuicConnection> haves = new HashSet<>();

        Set<PeerId> swarm = host.getPeers();

        for (PeerId peerId : swarm) {
            if (!IPFS.BITSWAP_SUPPORT_LOAD_PROVIDERS) {
                connector.execute(() -> find(closeable, peerId));
            } else {
                connector.execute(() -> connect(closeable, peerId));
            }
        }

        while (matches.containsKey(cid)) {

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            for (QuicConnection peer : peers.keySet()) {
                if (!haves.contains(peer)) {
                    haves.add(peer);
                    runHaveMessage(closeable, peer, Collections.singletonList(cid));
                }
            }

            ConcurrentLinkedDeque<QuicConnection> set = matches.get(cid);
            if (set != null) {
                QuicConnection conn = set.poll();
                if (conn != null) {

                    long start = System.currentTimeMillis();
                    try {
                        if (matches.containsKey(cid)) {
                            bitSwap.sendWantsMessage(conn, Collections.singletonList(cid));

                            blocker.subscribe(cid, closeable);
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    } finally {
                        LogUtils.debug(TAG, "Match CID " + cid.String() +
                                " took " + (System.currentTimeMillis() - start));
                    }
                }

            }

            if (closeable.isClosed()) {
                throw new ClosedException();
            }
        }
        return blockStore.getBlock(cid);
    }


    public void blockReceived(@NonNull Block block) {

        try {
            Cid cid = block.getCid();
            blockStore.putBlock(block);
            matches.remove(cid);
            blocker.release(cid);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    public void loadBlocks(@NonNull Closeable closeable, @NonNull List<Cid> cids) {

        LogUtils.verbose(TAG, "LoadBlocks " + cids.size());

        List<QuicConnection> handled = new ArrayList<>();

        for (QuicConnection conn : peers.keySet()) {
            if (!handled.contains(conn)) {
                handled.add(conn);
                runHaveMessage(closeable, conn, cids);
            }
        }
    }

    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException {
        try {
            synchronized (cid.String().intern()) {
                Block block = blockStore.getBlock(cid);
                if (block == null) {
                    AtomicBoolean done = new AtomicBoolean(false);
                    LogUtils.info(TAG, "Block Get " + cid.String());

                    if (root) {
                        loadProviders(() -> closeable.isClosed() || done.get(), cid, 1,
                                TimeUnit.MILLISECONDS);
                    }
                    try {
                        return runWantHaves(() -> closeable.isClosed() || done.get(), cid);
                    } finally {
                        done.set(true);
                    }
                }
                return block;
            }
        } finally {
            blocker.release(cid);
            LogUtils.info(TAG, "Block Release  " + cid.String());
        }
    }

    private void loadProviders(@NonNull Closeable closeable, @NonNull Cid cid,
                               long delay, @NonNull TimeUnit delayUnit) {

        if (IPFS.BITSWAP_SUPPORT_LOAD_PROVIDERS) {

            // TODO activate later again
            ConcurrentHashMap<PeerId, Long> shittiest = new ConcurrentHashMap<>();

            providers.schedule(() -> {

                long start = System.currentTimeMillis();
                try {

                    LogUtils.debug(TAG, "Load Provider Start " + cid.String());

                    if (closeable.isClosed()) {
                        return;
                    }

                    host.findProviders(closeable,
                            peerId -> {
                                if (host.hasAddresses(peerId)) {
                                    // TODO activate later again
                                    shittiest.remove(peerId);
                                    connector.execute(() -> connect(closeable, peerId));
                                } else {
                                    Long value = shittiest.get(peerId);
                                    if (value == null) {
                                        shittiest.put(peerId, 1L);
                                    } else {
                                        long newValue = value + 1;
                                        shittiest.put(peerId, newValue);


                                        if (newValue == IPFS.RELAY_THRESHOLD_CONNECT_PEER) {
                                            relay(closeable, peerId);
                                        }
                                    }
                                }
                            }, cid);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable.getMessage());
                } finally {
                    LogUtils.info(TAG, "Load Provider Finish " + cid.String() +
                            " onStart [" + (System.currentTimeMillis() - start) + "]...");
                }
            }, delay, delayUnit);
        }
    }

}
