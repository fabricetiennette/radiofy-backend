package io.github.fabricetiennette.radiofy.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OtpHasher {

    private final PasswordEncoder encoder; // BCrypt bean déjà présent

    public String hash(String code) {
        return encoder.encode(code);
    }

    public boolean matches(String rawCode, String codeHash) {
        return encoder.matches(rawCode, codeHash);
    }
}
