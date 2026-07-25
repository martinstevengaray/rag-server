package com.mgaray.ragserver.common;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public class EncryptionUtils {

    private static final int KEY_SIZE_BITS = 256;
    private static final int IV_SIZE_BYTES = 12;
    private static final int AUTH_TAG_SIZE_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static void main(String[] args) throws GeneralSecurityException {
        SecretKey secretKey = generateKey();

        String originalText = "This is a secret message.";

        String encryptedText = encrypt(originalText, secretKey);
        String decryptedText = decrypt(encryptedText, secretKey);

        System.out.println("Original:  " + originalText);
        System.out.println("Encrypted: " + encryptedText);
        System.out.println("Decrypted: " + decryptedText);

        // Only for demonstration. Do not normally print secret keys.
        System.out.println(
                "Key:       " +
                        Base64.getEncoder().encodeToString(secretKey.getEncoded())
        );
    }

    public static SecretKey generateKey() throws GeneralSecurityException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(KEY_SIZE_BITS);
        return keyGenerator.generateKey();
    }

    public static String encrypt(String plaintext, SecretKey key)
            throws GeneralSecurityException {

        byte[] iv = new byte[IV_SIZE_BYTES];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        GCMParameterSpec parameterSpec =
                new GCMParameterSpec(AUTH_TAG_SIZE_BITS, iv);

        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        byte[] ciphertext = cipher.doFinal(
                plaintext.getBytes(StandardCharsets.UTF_8)
        );

        // Store the IV together with the ciphertext.
        ByteBuffer result = ByteBuffer.allocate(iv.length + ciphertext.length);
        result.put(iv);
        result.put(ciphertext);

        return Base64.getEncoder().encodeToString(result.array());
    }

    public static String decrypt(String encryptedData, SecretKey key)
            throws GeneralSecurityException {

        byte[] decoded = Base64.getDecoder().decode(encryptedData);

        if (decoded.length <= IV_SIZE_BYTES) {
            throw new IllegalArgumentException("Invalid encrypted data");
        }

        ByteBuffer buffer = ByteBuffer.wrap(decoded);

        byte[] iv = new byte[IV_SIZE_BYTES];
        buffer.get(iv);

        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        GCMParameterSpec parameterSpec =
                new GCMParameterSpec(AUTH_TAG_SIZE_BITS, iv);

        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

        byte[] plaintext = cipher.doFinal(ciphertext);

        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
