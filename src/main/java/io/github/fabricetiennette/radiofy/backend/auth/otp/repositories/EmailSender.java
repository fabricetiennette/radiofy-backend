package io.github.fabricetiennette.radiofy.backend.auth.otp.repositories;

public interface EmailSender {
    void sendVerificationCode(String to, String code, long expiresInMinutes);
    void sendPasswordResetCode(String to, String code, long expiresInMinutes);
}