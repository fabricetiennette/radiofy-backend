package io.github.fabricetiennette.radiofy.backend.auth.security;

import org.springframework.security.core.AuthenticationException;

public class JwtAuthenticationException extends AuthenticationException {
    public enum Reason { MISSING, MALFORMED, EXPIRED, UNSUPPORTED, BAD_SIGNATURE, INVALID }

    private final Reason reason;

    public JwtAuthenticationException(String msg, Reason reason) {
        super(msg);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
