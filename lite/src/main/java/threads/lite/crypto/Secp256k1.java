package threads.lite.crypto;

import androidx.annotation.NonNull;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointUtil;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;

import crypto.pb.Crypto;


public class Secp256k1 {

    private static final X9ECParameters CURVE_PARAMS =
            CustomNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters CURVE;

    static {
        FixedPointUtil.precompute(CURVE_PARAMS.getG());

        ECCurve var5 = CURVE_PARAMS.getCurve();

        ECPoint var6 = CURVE_PARAMS.getG();

        BigInteger var7 = CURVE_PARAMS.getN();

        CURVE = new ECDomainParameters(var5, var6, var7, CURVE_PARAMS.getH());
    }


    public static PubKey unmarshalSecp256k1PublicKey(byte[] data) {
        return new Secp256k1PublicKey(new ECPublicKeyParameters(CURVE.getCurve().decodePoint(data), CURVE));
    }


    public static final class Secp256k1PublicKey extends PubKey {
        private final ECPublicKeyParameters pub;

        public Secp256k1PublicKey(ECPublicKeyParameters pub) {
            super(Crypto.KeyType.Secp256k1);
            this.pub = pub;
        }


        @NonNull
        public byte[] raw() {

            return this.pub.getQ().getEncoded(true);
        }

        public boolean verify(byte[] data, byte[] signature) {

            ECDSASigner signer = new ECDSASigner();
            signer.init(false, this.pub);
            ByteArrayInputStream var27 = new ByteArrayInputStream(signature);

            ASN1Primitive var33;
            try {
                ASN1InputStream var10 = new ASN1InputStream(var27);

                ASN1Primitive var37;
                try {
                    var37 = var10.readObject();
                } finally {
                    var10.close();
                    // CloseableKt.closeFinally(var10, var36);
                }

                var33 = var37;
            } catch (Throwable var25) {
                throw new RuntimeException(var25);
            } finally {
                try {
                    var27.close();
                } catch (Throwable ignore) {
                }
            }


            if (var33 == null) {
                throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.asn1.ASN1Sequence");
            } else {
                ASN1Encodable[] var29 = ((ASN1Sequence) var33).toArray();


                if (var29.length != 2) {
                    throw new RuntimeException("Invalid signature: expected 2 values for 'r' and 's' but got " + var29.length);
                } else {
                    ASN1Primitive var10000 = var29[0].toASN1Primitive();
                    if (var10000 == null) {
                        throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.asn1.ASN1Integer");
                    } else {
                        BigInteger r = ((ASN1Integer) var10000).getValue();
                        var10000 = var29[1].toASN1Primitive();
                        if (var10000 == null) {
                            throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.asn1.ASN1Integer");
                        } else {
                            BigInteger s = ((ASN1Integer) var10000).getValue();
                            return signer.verifySignature(Hash.sha256(data), r.abs(), s.abs());
                        }
                    }
                }
            }
        }

        public int hashCode() {
            return this.pub.hashCode();
        }
    }

}
