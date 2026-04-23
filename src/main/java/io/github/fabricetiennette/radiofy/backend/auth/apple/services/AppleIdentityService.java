package io.github.fabricetiennette.radiofy.backend.auth.apple.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.fabricetiennette.radiofy.backend.auth.apple.dtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Collection;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class AppleIdentityService {

    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";

    @Value("${apple.client-id}")
    private String appleClientId;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AppleIdentity verify(String idToken) {
        try {
            // A JWT must contain exactly 3 Base64URL-encoded parts: header, payload, and signature.
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid Apple identity token format");
            }

            // Decode only the JWT header for now so we can read the signing metadata.
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            Map<String, Object> header = objectMapper.readValue(headerJson, new TypeReference<>() {});

            // Apple signs identity tokens with rotating public keys.
            // We need the key id (kid) and algorithm (alg) to select the correct public key later.
            String kid = (String) header.get("kid");
            String alg = (String) header.get("alg");

            if (kid == null || kid.isBlank()) {
                throw new IllegalArgumentException("Missing Apple token key id");
            }
            if (alg == null || alg.isBlank()) {
                throw new IllegalArgumentException("Missing Apple token algorithm");
            }
            // Apple identity tokens are expected to be signed with RS256.
            if (!"RS256".equals(alg)) {
                throw new IllegalArgumentException("Unsupported Apple token algorithm: " + alg);
            }

            // Fetch Apple's JWKS document and select the RSA public key matching the token kid.
            RSAPublicKey publicKey = fetchApplePublicKey(kid);

            // Verify the token signature with Apple's public key and parse the claims.
            Jws<Claims> signedClaims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(idToken);

            Claims claims = signedClaims.getPayload();

            // Validate the issuer, audience, expiration, and subject.
            validateIssuer(claims.getIssuer());
            validateAudience(claims.get("aud"));
            validateExpiration(claims.getExpiration());

            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("Apple identity token is missing subject");
            }

            String email = claims.get("email", String.class);
            return new AppleIdentity(subject, email);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid Apple identity token", e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Apple identity token", e);
        }
    }

    private void validateIssuer(String issuer) {
        if (!"https://appleid.apple.com".equals(issuer)) {
            throw new IllegalArgumentException("Invalid Apple token issuer: " + issuer);
        }
    }

    private void validateAudience(Object audienceClaim) {
        if (audienceClaim == null) {
            throw new IllegalArgumentException("Apple identity token is missing audience");
        }

        if (audienceClaim instanceof String audience) {
            if (!appleClientId.equals(audience)) {
                throw new IllegalArgumentException("Invalid Apple token audience: " + audience);
            }
            return;
        }

        if (audienceClaim instanceof Collection<?> audiences) {
            boolean matches = audiences.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .anyMatch(appleClientId::equals);

            if (!matches) {
                throw new IllegalArgumentException("Invalid Apple token audience");
            }
            return;
        }

        throw new IllegalArgumentException("Unsupported Apple token audience format");
    }

    private void validateExpiration(java.util.Date expiration) {
        if (expiration == null) {
            throw new IllegalArgumentException("Apple identity token is missing expiration");
        }

        if (expiration.toInstant().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Apple identity token is expired");
        }
    }

    private RSAPublicKey fetchApplePublicKey(String kid) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(APPLE_JWKS_URL))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalArgumentException("Failed to fetch Apple JWKS. HTTP status: " + response.statusCode());
        }

        Map<String, Object> jwks = objectMapper.readValue(response.body(), new TypeReference<>() {});
        Object keysObject = jwks.get("keys");
        if (!(keysObject instanceof List<?> keys)) {
            throw new IllegalArgumentException("Invalid Apple JWKS payload");
        }

        for (Object keyObject : keys) {
            if (!(keyObject instanceof Map<?, ?> rawKey)) {
                continue;
            }

            Object keyId = rawKey.get("kid");
            if (!(keyId instanceof String currentKid) || !kid.equals(currentKid)) {
                continue;
            }

            Object keyType = rawKey.get("kty");
            Object modulus = rawKey.get("n");
            Object exponent = rawKey.get("e");

            if (!"RSA".equals(keyType)) {
                throw new IllegalArgumentException("Unsupported Apple JWK key type: " + keyType);
            }
            if (!(modulus instanceof String n) || !(exponent instanceof String e)) {
                throw new IllegalArgumentException("Apple JWK is missing RSA modulus or exponent");
            }

            PublicKey publicKey = buildRsaPublicKey(n, e);
            if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
                throw new IllegalArgumentException("Resolved Apple public key is not RSA");
            }

            return rsaPublicKey;
        }

        throw new IllegalArgumentException("No Apple public key found for kid: " + kid);
    }

    private PublicKey buildRsaPublicKey(String n, String e) throws Exception {
        byte[] modulusBytes = Base64.getUrlDecoder().decode(n);
        byte[] exponentBytes = Base64.getUrlDecoder().decode(e);

        BigInteger modulus = new BigInteger(1, modulusBytes);
        BigInteger exponent = new BigInteger(1, exponentBytes);

        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }
}

