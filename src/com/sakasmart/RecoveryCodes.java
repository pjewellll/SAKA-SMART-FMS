
package com.sakasmart;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Utilities shared by offline demonstration
 * and PostgreSQL password recovery workflows.
 */
public final class RecoveryCodes {

    public static final int MAX_ATTEMPTS = 5;
    public static final int LIFETIME_MINUTES = 20;

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    /**
     * Generates a 12-character recovery code.
     * Example format: XXXX-XXXX-XXXX
     */
    public static String create() {

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < 12; i++) {

            if (i != 0 && i % 4 == 0) {
                out.append('-');
            }

            out.append(
                ALPHABET[RANDOM.nextInt(ALPHABET.length)]
            );
        }

        return out.toString();
    }

    /**
     * Creates a SHA-256 hash of the recovery code.
     */
    public static String digest(String code) {

        try {
            MessageDigest sha =
                    MessageDigest.getInstance("SHA-256");

            byte[] bytes = sha.digest(
                normalize(code).getBytes(
                    StandardCharsets.US_ASCII
                )
            );

            return HexFormat.of().formatHex(bytes);

        } catch (Exception e) {
            throw new IllegalStateException(
                "SHA-256 not available", e
            );
        }
    }

    /**
     * Checks whether the entered recovery code matches.
     */
    public static boolean matches(
            String entered,
            String storedHash
    ) {

        if (entered == null ||
            storedHash == null ||
            storedHash.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
            digest(entered).getBytes(
                StandardCharsets.US_ASCII
            ),
            storedHash.getBytes(
                StandardCharsets.US_ASCII
            )
        );
    }

    /**
     * Removes spaces and dashes.
     */
    public static String normalize(String code) {

        return code == null
            ? ""
            : code.replace("-", "")
                  .replace(" ", "")
                  .trim()
                  .toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Checks whether a recovery code has expired.
     */
    public static boolean expired(String expiry) {

        try {
            return !Instant.now().isBefore(
                Instant.parse(expiry)
            );
        } catch (Exception e) {
            return true;
        }
    }

    public static String utcNow() {
        return Instant.now().toString();
    }

    private RecoveryCodes() {}
}
