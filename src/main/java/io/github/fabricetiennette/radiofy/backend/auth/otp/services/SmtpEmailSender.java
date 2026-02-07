package io.github.fabricetiennette.radiofy.backend.auth.otp.services;

import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import io.github.fabricetiennette.radiofy.backend.auth.otp.repositories.EmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
//@Profile("production")
@RequiredArgsConstructor
public class SmtpEmailSender {

    private final JavaMailSender mailSender;

    public void sendVerificationCodeEmail(String to, String code, int expiresInMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            helper.setTo(to);
            helper.setFrom("hello@radiofy.app");
            helper.setSubject("Your Radiofy verification code");

            String html = buildVerificationEmailHtml(code, expiresInMinutes);
            helper.setText(html, true); // true => HTML

            mailSender.send(message);
        } catch (MessagingException e) {
            // log + rethrow custom exception if needed
            throw new IllegalStateException("Failed to send verification email", e);
        }
    }

    private String buildVerificationEmailHtml(String code, int minutes) {
        return """
                <!DOCTYPE html>
                            <html lang="en">
                            <head>
                              <meta charset="UTF-8">
                              <meta name="viewport" content="width=device-width, initial-scale=1.0">
                              <style>
                                body, table, td, p { margin:0; padding:0; font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif; }
                                body { background-color:#f2f2f2; color:#111827; }
                                .wrapper { width:100%%; background-color:#f2f2f2; padding:24px 0; }
                                .container { max-width:480px; margin:0 auto; background-color:#ffffff; border-radius:12px; overflow:hidden;
                                             box-shadow:0 8px 24px rgba(15,23,42,0.12); }
                                .header { background-color:#191414; padding:20px; text-align:center; }
                                /*.header-title { color:#f9fafb; font-size:20px; font-weight:600; margin-bottom:12px; }*/
                                .logo { height:80px; }
                                .content { padding:24px; }
                                .paragraph { font-size:14px; line-height:1.6; margin-bottom:16px; color:#4b5563;}
                                .code-label { margin-top:8px; font-size:13px; text-transform:uppercase; letter-spacing:0.08em; color:#6b7280;text-align:center; }
                                .code-box {
                                margin-top:16px;
                                margin-left:auto;
                                margin-right:auto;
                                padding:16px;
                                border-radius:999px;
                                display:inline-block;
                                background:#191414;
                                color:#f9fafb;
                                font-size:26px;
                                font-weight:700;
                                letter-spacing:0.4em;
                                text-indent: 0.4em;
                                text-align:center;
                                }
                                .muted { margin-top:12px; font-size:12px; color:#9ca3af; text-align:center; }
                                .footer { padding:16px 24px 20px; border-top:1px solid #e5e7eb; }
                                .footer-text { font-size:11px; color:#9ca3af; line-height:1.5; }
                              </style>
                            </head>
                            <body>
                              <div class="wrapper">
                                <div class="container">
                                  <div class="header">
                                    <!--<div class="header-title">Verify your identity</div>-->
                                    <img src="https://radiofy-asset.s3.eu-west-3.amazonaws.com/RadiofyEmail.png" alt="Radiofy" class="logo">
                                  </div>
                                  <div class="content">
                                    <p class="paragraph">Hello,</p>
                                    <p class="paragraph">
                                      To finish signing in to <strong>Radiofy</strong>, please enter the verification code below
                                      in the app.
                                    </p>
                                    <p class="code-label">Verification code</p>
                                    <div style="text-align:center;">
                                    <div class="code-box">%s</div>
                                    </div>
                                    <p class="muted">This code will expire in %d minutes.</p>
                                    <p class="paragraph" style="margin-top:20px;">
                                      If you did not request this code, you can safely ignore this email.
                                    </p>
                                  </div>
                                  <div class="footer">
                                    <p class="footer-text">
                                      You are receiving this email because a verification was requested from the Radiofy app.
                                      Radiofy will never ask you for your password or payment information by email.
                                    </p>
                                  </div>
                                </div>
                              </div>
                            </body>
                            </html>
           """.formatted(code, minutes);
    }

    public void sendVerificationCode(String to, String code) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom("hello@radiofy.app");
        msg.setTo(to);
        msg.setSubject("Your Radiofy verification code");
        msg.setText("""
                Your Radiofy verification code is: %s
                
                It expires in 10 minutes.
                """.formatted(code));
        mailSender.send(msg);
    }

    public void sendPasswordResetCode(String to, String code) {
        int expiresInMinutes = 10;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            helper.setTo(to);
            helper.setFrom("hello@radiofy.app");
            helper.setSubject("Reset your Radiofy password");

            String html = buildPasswordResetEmailHtml(code, expiresInMinutes);
            helper.setText(html, true); // true => HTML

            mailSender.send(message);
        } catch (MessagingException e) {
            // log + rethrow custom exception if needed
            throw new IllegalStateException("Failed to send password reset email", e);
        }
    }

    private String buildPasswordResetEmailHtml(String code, int minutes) {
        return """
                <!DOCTYPE html>
                            <html lang="en">
                            <head>
                              <meta charset="UTF-8">
                              <meta name="viewport" content="width=device-width, initial-scale=1.0">
                              <style>
                                body, table, td, p { margin:0; padding:0; font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif; }
                                body { background-color:#f2f2f2; color:#111827; }
                                .wrapper { width:100%%; background-color:#f2f2f2; padding:24px 0; }
                                .container { max-width:480px; margin:0 auto; background-color:#ffffff; border-radius:12px; overflow:hidden;
                                             box-shadow:0 8px 24px rgba(15,23,42,0.12); }
                                .header { background-color:#191414; padding:20px; text-align:center; }
                                .logo { height:80px; }
                                .content { padding:24px; }
                                .paragraph { font-size:14px; line-height:1.6; margin-bottom:16px; color:#4b5563;}
                                .code-label { margin-top:8px; font-size:13px; text-transform:uppercase; letter-spacing:0.08em; color:#6b7280;text-align:center; }
                                .code-box {
                                margin-top:16px;
                                margin-left:auto;
                                margin-right:auto;
                                padding:16px;
                                border-radius:999px;
                                display:inline-block;
                                background:#191414;
                                color:#f9fafb;
                                font-size:26px;
                                font-weight:700;
                                letter-spacing:0.4em;
                                text-indent: 0.4em;
                                text-align:center;
                                }
                                .muted { margin-top:12px; font-size:12px; color:#9ca3af; text-align:center; }
                                .footer { padding:16px 24px 20px; border-top:1px solid #e5e7eb; }
                                .footer-text { font-size:11px; color:#9ca3af; line-height:1.5; }
                              </style>
                            </head>
                            <body>
                              <div class="wrapper">
                                <div class="container">
                                  <div class="header">
                                    <img src="https://radiofy-asset.s3.eu-west-3.amazonaws.com/RadiofyEmail.png" alt="Radiofy" class="logo">
                                  </div>
                                  <div class="content">
                                    <p class="paragraph">Hello,</p>
                                    <p class="paragraph">
                                      You have requested to reset your <strong>Radiofy</strong> password. Please enter the code below in the app to continue.
                                    </p>
                                    <p class="code-label">Password reset code</p>
                                    <div style="text-align:center;">
                                    <div class="code-box">%s</div>
                                    </div>
                                    <p class="muted">This code will expire in %d minutes.</p>
                                    <p class="paragraph" style="margin-top:20px;">
                                      If you did not request a password reset, you can safely ignore this email.
                                    </p>
                                  </div>
                                  <div class="footer">
                                    <p class="footer-text">
                                      You are receiving this email because a password reset was requested from the Radiofy app.
                                      Radiofy will never ask you for your password or payment information by email.
                                    </p>
                                  </div>
                                </div>
                              </div>
                            </body>
                            </html>
           """.formatted(code, minutes);
    }
}
