package com.mgaray.ragserver.crypto;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptionDelegateTest {

    // openssl rand -base64 32 equivalent: a 256-bit AES key
    private static String newKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private final String key = newKey();
    private final EncryptionDelegate delegate = new EncryptionDelegate(key);

    @Test
    void roundTripsPlaintext() {
        String plaintext = "{\"promptExchanges\":[{\"prompt\":\"what are the setback rules?\"}]}";

        assertEquals(plaintext, delegate.decrypt(delegate.encrypt(plaintext)));
    }

    @Test
    void roundTripsEmptyString() {
        assertEquals("", delegate.decrypt(delegate.encrypt("")));
    }

    @Test
    void roundTripsMultiByteUtf8() {
        String plaintext = "café § 33.110 🌲";

        assertEquals(plaintext, delegate.decrypt(delegate.encrypt(plaintext)));
    }

    @Test
    void roundTripsLargePayload() {
        String plaintext = "session state chunk ".repeat(5000);

        assertEquals(plaintext, delegate.decrypt(delegate.encrypt(plaintext)));
    }

    @Test
    void producesDifferentCiphertextForTheSamePlaintext() {
        String plaintext = "same input, different random IV";

        String first = delegate.encrypt(plaintext);
        String second = delegate.encrypt(plaintext);

        assertNotEquals(first, second, "a random IV should make repeat encryptions differ");
        assertEquals(plaintext, delegate.decrypt(first));
        assertEquals(plaintext, delegate.decrypt(second));
    }

    @Test
    void ciphertextIsBase64AndCarriesTheTwelveByteIv() {
        byte[] decoded = Base64.getDecoder().decode(delegate.encrypt("x"));

        // 12-byte IV + ciphertext + 16-byte GCM auth tag
        assertTrue(decoded.length > 12 + 16, "expected IV and auth tag to be present, got " + decoded.length + " bytes");
    }

    @Test
    void anotherKeyCannotDecrypt() {
        String encrypted = delegate.encrypt("secret");
        EncryptionDelegate otherDelegate = new EncryptionDelegate(newKey());

        assertThrows(RuntimeException.class, () -> otherDelegate.decrypt(encrypted));
    }

    @Test
    void aDelegateWithTheSameKeyCanDecrypt() {
        String encrypted = delegate.encrypt("secret");

        assertEquals("secret", new EncryptionDelegate(key).decrypt(encrypted));
    }

    @Test
    void tamperedCiphertextIsRejectedByTheAuthTag() {
        byte[] decoded = Base64.getDecoder().decode(delegate.encrypt("secret"));
        decoded[decoded.length - 1] ^= 0x01; // flip one bit of the auth tag
        String tampered = Base64.getEncoder().encodeToString(decoded);

        assertThrows(RuntimeException.class, () -> delegate.decrypt(tampered));
    }

    @Test
    void tamperedIvIsRejected() {
        byte[] decoded = Base64.getDecoder().decode(delegate.encrypt("secret"));
        decoded[0] ^= 0x01; // flip one bit of the IV
        String tampered = Base64.getEncoder().encodeToString(decoded);

        assertThrows(RuntimeException.class, () -> delegate.decrypt(tampered));
    }

    @Test
    void decryptRejectsInputShorterThanTheIv() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[12]);

        assertThrows(RuntimeException.class, () -> delegate.decrypt(tooShort));
    }

    @Test
    void decryptRejectsNonBase64Input() {
        assertThrows(RuntimeException.class, () -> delegate.decrypt("not base64 !!!"));
    }

}
