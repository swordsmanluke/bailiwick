package threads.lite.dag;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import threads.lite.IPFS;
import threads.lite.cid.Builder;
import threads.lite.format.Node;
import threads.lite.format.ProtoNode;
import threads.lite.format.RawNode;
import threads.lite.utils.Splitter;


public class DagBuilderHelper {
    private final DagService dagService;
    private final Builder builder;
    private final Splitter splitter;
    private final boolean rawLeaves;


    public DagBuilderHelper(@NonNull DagService dagService,
                            @NonNull Builder builder,
                            @NonNull Splitter splitter,
                            boolean rawLeaves) {
        this.dagService = dagService;
        this.builder = builder;
        this.splitter = splitter;
        this.rawLeaves = rawLeaves;
    }


    public FSNodeOverDag createFSNodeOverDag(@NonNull unixfs.pb.Unixfs.Data.DataType fsNodeType) {
        return new FSNodeOverDag(new ProtoNode(), FSNode.createFSNode(fsNodeType), builder);
    }

    @Nullable
    public Pair<Node, Integer> createLeafDataNode(@NonNull unixfs.pb.Unixfs.Data.DataType dataType) {

        byte[] fileData = nextBytes();
        if (fileData != null) {
            int dataSize = fileData.length;

            Node node = createLeafNode(fileData, dataType);

            return Pair.create(node, dataSize);
        }
        return null;
    }

    private Node createLeafNode(byte[] data, @NonNull unixfs.pb.Unixfs.Data.DataType fsNodeType) {

        if (data.length > IPFS.BLOCK_SIZE_LIMIT) {
            throw new RuntimeException();
        }

        if (rawLeaves) {
            // Encapsulate the data in a raw node.
            return RawNode.NewRawNodeWPrefix(data, builder);
        }


        FSNodeOverDag fsNodeOverDag = createFSNodeOverDag(fsNodeType);
        fsNodeOverDag.setFileData(data);

        return fsNodeOverDag.commit();


    }

    private byte[] nextBytes() {
        return splitter.nextBytes();
    }

    public void fillNodeLayer(@NonNull FSNodeOverDag node) {

        while ((node.numChildren() < IPFS.LINKS_PER_BLOCK) && !Done()) {
            Pair<Node, Integer> result = createLeafDataNode(unixfs.pb.Unixfs.Data.DataType.Raw);
            if (result != null) {
                node.addChild(result.first, result.second, this);
            }
        }
        node.commit();
    }

    public void add(@NonNull Node node) {
        dagService.add(node);
    }

    public boolean Done() {
        return splitter.done();
    }

    public static class FSNodeOverDag {
        private final ProtoNode dag;
        private final FSNode file;

        private FSNodeOverDag(@NonNull ProtoNode protoNode, @NonNull FSNode fsNode, @NonNull Builder builder) {
            dag = protoNode;
            file = fsNode;
            dag.setCidBuilder(builder);
        }


        int numChildren() {
            return file.numChildren();
        }

        public void addChild(@NonNull Node child, long fileSize, @NonNull DagBuilderHelper dagBuilderHelper) {

            dag.addNodeLink("", child);
            file.addBlockSize(fileSize);

            dagBuilderHelper.add(child);
        }

        public Node commit() {
            byte[] fileData = file.getBytes();
            dag.setData(fileData);
            return dag;
        }

        public void setFileData(byte[] data) {
            file.setData(data);
        }

        public long fileSize() {
            return file.getFileSize();
        }
    }
}
