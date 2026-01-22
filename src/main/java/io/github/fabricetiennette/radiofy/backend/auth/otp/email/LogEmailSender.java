package io.github.fabricetiennette.radiofy.backend.auth.otp.email;

import io.github.fabricetiennette.radiofy.backend.auth.otp.repositories.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "test"})
@Slf4j
public class LogEmailSender implements EmailSender {
    @Override
    public void sendVerificationCode(String to, String code, long expiresInMinutes) {
        log.info("[EMAIL][VERIFY] to={} code={} ttl={}min", to, code, expiresInMinutes);
    }
    @Override
    public void sendPasswordResetCode(String to, String code, long expiresInMinutes) {
        log.info("[EMAIL][RESET] to={} code={} ttl={}min", to, code, expiresInMinutes);
    }
}
