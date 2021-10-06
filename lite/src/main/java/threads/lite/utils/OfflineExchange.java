package threads.lite.utils;

import androidx.annotation.NonNull;

import java.util.List;

import threads.lite.bitswap.Exchange;
import threads.lite.cid.Cid;
import threads.lite.core.Closeable;
import threads.lite.format.Block;
import threads.lite.format.BlockStore;

public class OfflineExchange implements Exchange {
    private final BlockStore blockstore;

    public OfflineExchange(@NonNull BlockStore blockstore) {
        this.blockstore = blockstore;
    }

    @Override
    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) {
        return blockstore.getBlock(cid);
    }

    @Override
    public void preload(@NonNull Closeable closeable, @NonNull List<Cid> preload) {
        // nothing to do here
    }


    @Override
    public void reset() {
        // nothing to do here
    }

}
