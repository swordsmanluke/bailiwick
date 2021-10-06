package threads.lite.dag;

import android.util.Pair;

import androidx.annotation.NonNull;

import threads.lite.format.Node;


public class Trickle {

    public static final int depthRepeat = 4;

    public static Node Layout(@NonNull DagBuilderHelper db) {
        DagBuilderHelper.FSNodeOverDag newRoot =
                db.createFSNodeOverDag(unixfs.pb.Unixfs.Data.DataType.File);
        Pair<Node, Long> result = fillTrickleRec(db, newRoot, -1);

        Node root = result.first;
        db.add(root);
        return root;
    }


    public static Pair<Node, Long> fillTrickleRec(@NonNull DagBuilderHelper db,
                                                  @NonNull DagBuilderHelper.FSNodeOverDag node,
                                                  int maxDepth) {
        // Always do this, even in the base case
        db.fillNodeLayer(node);


        for (int depth = 1; maxDepth == -1 || depth < maxDepth; depth++) {
            if (db.Done()) {
                break;
            }

            for (int repeatIndex = 0; repeatIndex < depthRepeat && !db.Done(); repeatIndex++) {

                Pair<Node, Long> result = fillTrickleRec(db, db.createFSNodeOverDag(
                        unixfs.pb.Unixfs.Data.DataType.File), depth);

                node.addChild(result.first, result.second, db);
            }
        }
        Node filledNode = node.commit();

        return Pair.create(filledNode, node.fileSize());
    }

}
