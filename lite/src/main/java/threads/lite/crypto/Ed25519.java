package threads.lite.crypto;

import androidx.annotation.NonNull;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import crypto.pb.Crypto;


public class Ed25519 {


    public static PubKey unmarshalEd25519PublicKey(byte[] keyBytes) {

        return new Ed25519PublicKey(new Ed25519PublicKeyParameters(keyBytes, 0));
    }


    public static final class Ed25519PublicKey extends PubKey {
        private final Ed25519PublicKeyParameters pub;

        public Ed25519PublicKey(Ed25519PublicKeyParameters pub) {
            super(Crypto.KeyType.Ed25519);
            this.pub = pub;
        }


        @NonNull
        public byte[] raw() {
            return this.pub.getEncoded();
        }

        public boolean verify(byte[] data, byte[] signature) {

            Ed25519Signer ed25519Signer = new Ed25519Signer();
            ed25519Signer.init(false, this.pub);
            ed25519Signer.update(data, 0, data.length);
            return ed25519Signer.verifySignature(signature);
        }

        public int hashCode() {
            return this.pub.hashCode();
        }
    }

}
