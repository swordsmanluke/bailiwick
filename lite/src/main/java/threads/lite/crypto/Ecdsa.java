package threads.lite.crypto;

import androidx.annotation.NonNull;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;

import crypto.pb.Crypto;


public class Ecdsa {


    public static EcdsaPublicKey unmarshalEcdsaPublicKey(byte[] keyBytes) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("ECDSA");

            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
            if (publicKey == null) {
                throw new NullPointerException("null cannot be cast to non-null type java.security.interfaces.ECPublicKey");
            } else {
                return new EcdsaPublicKey((ECPublicKey) publicKey);
            }
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static final class EcdsaPublicKey extends PubKey {

        private final ECPublicKey publicKey;

        public EcdsaPublicKey(ECPublicKey pub) {
            super(Crypto.KeyType.ECDSA);
            this.publicKey = pub;
        }


        @NonNull
        public byte[] raw() {
            return this.publicKey.getEncoded();
        }

        public boolean verify(byte[] data, byte[] signature) {
            try {
                Signature sha256withECDSA = Signature.getInstance(
                        "SHA256withECDSA");
                sha256withECDSA.initVerify(this.publicKey);
                sha256withECDSA.update(data);
                return sha256withECDSA.verify(signature);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        public int hashCode() {
            return this.publicKey.hashCode();
        }


    }


}
