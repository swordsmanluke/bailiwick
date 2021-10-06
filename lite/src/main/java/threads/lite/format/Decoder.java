package threads.lite.format;

import androidx.annotation.NonNull;

import threads.lite.cid.Cid;

public class Decoder {
    @NonNull
    public static Node Decode(@NonNull Block block) {

        if (block instanceof Node) {
            return (Node) block;
        }

        long type = block.getCid().getType();

        if (type == Cid.DagProtobuf) {
            return DecodeProtobufBlock(block);
        } else if (type == Cid.Raw) {
            return DecodeRawBlock(block);
        } else if (type == Cid.DagCBOR) {
            throw new RuntimeException("Not supported decoder");
        } else {
            throw new RuntimeException("Not supported decoder");
        }
    }


    public static Node DecodeRawBlock(@NonNull Block block) {
        if (block.getCid().getType() != Cid.Raw) {
            throw new RuntimeException("raw nodes cannot be decoded from non-raw blocks");
        }
        return new RawNode(block);
    }

    public static Node DecodeProtobufBlock(@NonNull Block b) {
        Cid c = b.getCid();
        if (c.getType() != Cid.DagProtobuf) {
            throw new RuntimeException("this function can only decode protobuf nodes");
        }

        ProtoNode protoNode = decodeProtobuf(b.getRawData());
        protoNode.cached = c;
        return protoNode;
    }

    public static ProtoNode decodeProtobuf(byte[] encoded) {
        ProtoNode protoNode = new ProtoNode();
        protoNode.unmarshal(encoded);
        return protoNode;
    }
}
