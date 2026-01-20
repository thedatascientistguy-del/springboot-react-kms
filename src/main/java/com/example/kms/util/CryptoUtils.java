package com.example.kms.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class CryptoUtils {
    private static final String HMAC_ALG = "HmacSHA256";
    private static final int HASH_LEN = 32;

    private CryptoUtils() {}

    // RFC5869: HKDF extract+expand
    public static byte[] hkdfExtractAndExpand(byte[] salt, byte[] ikm, byte[] info, int outputLen) throws Exception {
        if (salt == null) salt = new byte[HASH_LEN]; // zeros
        byte[] prk = hmac(salt, ikm); // extract
        return hkdfExpand(prk, info, outputLen);
    }

    private static byte[] hkdfExpand(byte[] prk, byte[] info, int outputLen) throws Exception {
        int n = (int) Math.ceil((double) outputLen / HASH_LEN);
        if (n > 255) throw new IllegalArgumentException("Cannot expand to more than 255 blocks");
        byte[] okm = new byte[0];
        byte[] previous = new byte[0];

        for (int i = 1; i <= n; i++) {
            ByteBuffer bb = ByteBuffer.allocate(previous.length + (info == null ? 0 : info.length) + 1);
            bb.put(previous);
            if (info != null) bb.put(info);
            bb.put((byte) i);
            previous = hmac(prk, bb.array());
            okm = concat(okm, previous);
        }
        return Arrays.copyOf(okm, outputLen);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_ALG);
        mac.init(new SecretKeySpec(key, HMAC_ALG));
        return mac.doFinal(data == null ? new byte[0] : data);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
