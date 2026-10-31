package com.friendlyeats.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * AES-256-GCM com dado associado, usado pelas cifras de campo ({@link SecretCipher}, {@link AccessLogCipher}).
 * Formato: versão (1 byte) + IV (12) + cifra + tag. Chave por finalidade, derivada de {@link AppSecrets}.
 */
final class Aead {

    private static final byte VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    Aead(byte[] key) {
        this.key = new SecretKeySpec(key, "AES");
    }

    byte[] encrypt(byte[] plain, byte[] aad) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            c.updateAAD(aad);
            byte[] sealed = c.doFinal(plain);
            return ByteBuffer.allocate(1 + IV_BYTES + sealed.length).put(VERSION).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    byte[] decrypt(byte[] blob, byte[] aad) {
        try {
            ByteBuffer in = ByteBuffer.wrap(blob);
            if (in.get() != VERSION) {
                throw new IllegalStateException("Versão de cifra desconhecida");
            }
            byte[] iv = new byte[IV_BYTES];
            in.get(iv);
            byte[] sealed = new byte[in.remaining()];
            in.get(sealed);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            c.updateAAD(aad);
            return c.doFinal(sealed);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Dado cifrado não pôde ser decifrado", e);
        }
    }

    static byte[] longAad(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }
}
