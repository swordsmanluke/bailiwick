package threads.lite.crypto;

import androidx.annotation.NonNull;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

import crypto.pb.Crypto;
import threads.lite.LogUtils;


public class Rsa {
    private static final String TAG = Rsa.class.getSimpleName();


    public static PubKey unmarshalRsaPublicKey(byte[] keyBytes) {
        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
            return new RsaPublicKey(publicKey);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }


    public static final class RsaPrivateKey extends PrivKey {
        private final RsaPublicKey rsaPublicKey;
        private final byte[] pkcs1PrivateKeyBytes;
        private final PrivateKey sk;
        private final PublicKey pk;

        public RsaPrivateKey(PrivateKey sk, PublicKey pk) throws IOException {
            super(Crypto.KeyType.RSA);
            this.sk = sk;
            this.pk = pk;
            this.rsaPublicKey = new RsaPublicKey(this.pk);
            String var10000 = this.sk.getFormat();
            boolean isKeyOfFormat = var10000 != null && var10000.equals("PKCS#8");
            if (!isKeyOfFormat) {
                throw new RuntimeException("Private key must be of \"PKCS#8\" format");
            } else {
                PrivateKeyInfo bcPrivateKeyInfo = PrivateKeyInfo.getInstance(this.sk.getEncoded());
                ASN1Primitive var10001 = bcPrivateKeyInfo.parsePrivateKey().toASN1Primitive();

                this.pkcs1PrivateKeyBytes = var10001.getEncoded();
            }
        }


        @NonNull
        public byte[] raw() {
            return this.pkcs1PrivateKeyBytes;
        }


        public byte[] sign(byte[] data) {
            try {
                Signature sha256withRSA = Signature.getInstance("SHA256withRSA");
                sha256withRSA.initSign(this.sk);
                sha256withRSA.update(data);
                return sha256withRSA.sign();
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
                throw new RuntimeException(throwable);
            }
        }


        public PubKey publicKey() {
            return this.rsaPublicKey;
        }

        public int hashCode() {
            return this.pk.hashCode();
        }
    }


    public static final class RsaPublicKey extends PubKey {
        private final PublicKey publicKey;

        public RsaPublicKey(PublicKey publicKey) {
            super(Crypto.KeyType.RSA);
            this.publicKey = publicKey;
        }


        @NonNull
        public byte[] raw() {
            return this.publicKey.getEncoded();
        }

        public boolean verify(byte[] data, byte[] signature) {
            try {
                Signature sha256withRSA = Signature.getInstance("SHA256withRSA");
                sha256withRSA.initVerify(this.publicKey);
                sha256withRSA.update(data);
                return sha256withRSA.verify(signature);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        public int hashCode() {
            return this.publicKey.hashCode();
        }
    }
}
