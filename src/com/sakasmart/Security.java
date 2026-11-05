package com.sakasmart;

import java.security.*;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Password hashes only. Never persist plaintext passwords. */
public final class Security {
    private static final int ITERATIONS = 180_000;
    public static String hash(char[] password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" +
            Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }
    public static boolean verify(char[] password, String stored) {
        try {
            String[] parts = stored.split(":",3);
            int iterations = Integer.parseInt(parts[0]);
            byte[] actual = derive(password, Base64.getDecoder().decode(parts[1]), iterations);
            return MessageDigest.isEqual(actual, Base64.getDecoder().decode(parts[2]));
        } catch(Exception e) { return false; }
    }
    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception e) { throw new IllegalStateException("Password hashing unavailable",e); }
        finally { spec.clearPassword(); }
    }
    private Security() {}
}
