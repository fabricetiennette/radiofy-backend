package io.github.fabricetiennette.radiofy.backend.auth.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtProperties props;

    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey key() { return this.key; }

    // ---------- Parse helpers ----------
    public String getSubject(String token) {
        return Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean isTokenValid(String token, String expectedSubject) {
        try {
            var payload = Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token)
                    .getPayload();

            return expectedSubject.equals(payload.getSubject())
                    && payload.getExpiration().toInstant().isAfter(Instant.now());
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- Issue tokens ----------
    public String generateAccess(String subject, Map<String, Object> claims) {
        return buildToken(subject, claims, Duration.ofMinutes(props.getAccessExpMin()));
    }

    private String buildToken(String subject, Map<String, Object> claims, Duration validity) {
        var now = Instant.now();
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key());

        if (claims != null && !claims.isEmpty()) {
            builder.claims(claims);
        }
        return builder.compact();
    }
}
