package it.unicas.chronogram.mail;

import it.unicas.chronogram.common.exception.ApiExceptions.ServiceException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Sends transactional emails via the configured SMTP server (Spring Mail).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * Sends the password-reset email containing a link with the given token.
     *
     * @param toEmail recipient address
     * @param token   full {@code selector:verifier} token
     * @param baseUrl canonical front-end base URL, without a trailing slash
     *                (normalised by the caller, see PasswordResetService)
     */
    public void sendPasswordResetEmail(String toEmail, String token, String baseUrl) {
        String resetUrl = baseUrl + "/reset-password?token=" + token;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            if (StringUtils.hasText(from)) {
                helper.setFrom(from);
            }
            helper.setTo(toEmail);
            helper.setSubject("Richiesta di Reset Password - Chronogram");
            helper.setText(buildHtmlBody(resetUrl), true);
            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("Failed to send password reset email to {}", toEmail, e);
            throw new ServiceException("Could not send the password reset email.", e);
        }
    }

    private String buildHtmlBody(String resetUrl) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <style>
                    body { font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 0; }
                    .container { width: 90%%; max-width: 600px; margin: 40px auto; background-color: #ffffff; padding: 30px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                    .logo { text-align: center; margin-bottom: 30px; }
                    .logo img { max-width: 160px; height: auto; }
                    h2 { color: #333333; }
                    p { color: #555555; font-size: 16px; line-height: 1.6; }
                    .button { display: inline-block; padding: 12px 24px; margin: 20px 0; background-color: #007bff; color: #ffffff; text-decoration: none; border-radius: 5px; font-weight: bold; }
                    .footer { font-size: 13px; color: #999999; margin-top: 30px; text-align: center; }
                  </style>
                </head>
                <body>
                <div class="container">
                  <div class="logo">
                    <img src="https://github.com/bonoboprog/Chronogram/blob/Backend-patch-2/docs/Logo.png?raw=true" alt="Chronogram Logo">
                  </div>
                  <h2>Password Reset Request</h2>
                  <p>Hello,</p>
                  <p>We received a request to reset the password for your Chronogram account. If you did not request this, please ignore this email.</p>
                  <p>To set a new password, click the button below:</p>
                  <p style="text-align: center;"><a href="%s" class="button">Reset Your Password</a></p>
                  <p>This link will expire in 30 minutes.</p>
                  <p>Thank you,<br>The Chronogram Team</p>
                  <div class="footer">
                    <p>This is an automated message. Please do not reply to this email.</p>
                  </div>
                </div>
                </body>
                </html>
                """.formatted(resetUrl);
    }
}
