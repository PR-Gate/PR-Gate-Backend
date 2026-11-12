package com.prgate.users.infraestructure.github;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import com.prgate.users.application.github.GitHubLinkException;
import static com.prgate.users.application.github.GitHubLinkException.Reason.TOKEN_STORAGE_UNAVAILABLE;

/** AES-256-GCM, with each ciphertext bound to its academic user and GitHub identity. */
public final class GitHubTokenCipher {
    private final SecretKeySpec key;
    public GitHubTokenCipher(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) { key = null; return; }
        try {
            byte[] bytes = Base64.getDecoder().decode(encodedKey);
            if (bytes.length != 32) throw new IllegalArgumentException();
            key = new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("GITHUB_TOKEN_ENCRYPTION_KEY must be a Base64-encoded 32-byte key");
        }
    }
    public String encrypt(String token, String binding) {
        if (key == null) throw new GitHubLinkException(TOKEN_STORAGE_UNAVAILABLE);
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." +
                Base64.getEncoder().encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) { throw new GitHubLinkException(TOKEN_STORAGE_UNAVAILABLE); }
    }
    public String decrypt(String encrypted, String binding) {
        if (key == null) throw new GitHubLinkException(TOKEN_STORAGE_UNAVAILABLE);
        try {
            String[] parts = encrypted.split("\\.");
            if (parts.length != 2) throw new IllegalArgumentException();
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            if (iv.length != 12) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception failure) { throw new GitHubLinkException(TOKEN_STORAGE_UNAVAILABLE); }
    }
}
