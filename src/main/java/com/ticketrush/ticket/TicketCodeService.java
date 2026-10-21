package com.ticketrush.ticket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Component
public class TicketCodeService {

    public record Parsed(Long showId, UUID ticketId) {}

    private final byte[] secret;

    public TicketCodeService(@Value("${app.ticket.secret}") String secret) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("app.ticket.secret must be at least 32 bytes");
        this.secret = bytes;
    }

    public String encode(Long showId, UUID ticketId) {
        return "TR1." + showId + "." + ticketId + "." + b64(hmac(showId, ticketId));
    }

    public Optional<Parsed> verify(String code) {
        if (code == null || code.length() > 200) return Optional.empty();
        String[] p = code.trim().split("\\.");
        if (p.length != 4 || !p[0].equals("TR1")) return Optional.empty();
        try {
            Long showId = Long.valueOf(p[1]);
            UUID ticketId = UUID.fromString(p[2]);
            byte[] given = Base64.getUrlDecoder().decode(p[3]);
            return MessageDigest.isEqual(hmac(showId, ticketId), given)   // constant-time compare
                    ? Optional.of(new Parsed(showId, ticketId)) : Optional.empty();
        } catch (IllegalArgumentException e) {                            // also covers NumberFormatException
            return Optional.empty();
        }
    }

    private byte[] hmac(Long showId, UUID ticketId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");                      // Mac is not thread-safe: new one per call
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal((showId + ":" + ticketId).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    private static String b64(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
}
