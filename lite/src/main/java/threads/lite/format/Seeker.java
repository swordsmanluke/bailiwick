package threads.lite.format;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Stack;

import threads.lite.IPFS;
import threads.lite.cid.Cid;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.dag.FSNode;

public class Seeker {
    private static final String TAG = Seeker.class.getSimpleName();

    @Nullable
    public Cid next(@NonNull Closeable closeable, @NonNull Stack<Stage> stack) throws ClosedException {

        if (stack.isEmpty()) {
            return null;
        }

        NavigableNode visitedNode = stack.peek().getNode();
        int lastIndex = stack.peek().index();
        lastIndex++;
        int index = lastIndex;
        Node node = NavigableIPLDNode.extractIPLDNode(visitedNode);


        if (!(node instanceof ProtoNode)) {
            return null;
        }


        if (node.getLinks().size() > 0) {
            // Internal node, should be a `mdag.ProtoNode` containing a
            // `unixfs.FSNode` (see the `balanced` package for more details).
            FSNode fsNode = FSNode.extractFSNode(node);

            // If there aren't enough size hints don't seek
            // (see the `io.EOF` handling error comment below).
            if (fsNode.numChildren() != node.getLinks().size()) {
                return null;
            }


            // Internal nodes have no data, so just iterate through the
            // sizes of its children (advancing the child index of the
            // `dagWalker`) to find where we need to go down to next in
            // the search

            if (index < fsNode.numChildren()) {
                stack.peek().setIndex(index);
                long childSize = fsNode.getBlockSize(index);

                if (childSize > IPFS.CHUNK_SIZE) { // this is just guessing and might be wrong

                    NavigableNode fetched = visitedNode.fetchChild(closeable, index);
                    stack.push(new Stage(fetched, 0));

                    return next(closeable, stack);
                }
                return visitedNode.getChild(index);
            } else {
                stack.pop();
                return next(closeable, stack);
            }
        } else {
            stack.pop();
            return next(closeable, stack);
        }
    }


}

