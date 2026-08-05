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
 *
 * <p>Every message shares one HTML shell so the account-lifecycle mails look
 * like the password-reset one that already existed. Text coming from an
 * administrator is HTML-escaped before it is embedded: it is free-form input
 * that would otherwise be able to inject markup into the message.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final String LOGO_URL =
            "https://github.com/bonoboprog/Chronogram/blob/Backend-patch-2/docs/Logo.png?raw=true";

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
        String body = """
                <p>Hello,</p>
                <p>We received a request to reset the password for your Chronogram account. \
                If you did not request this, please ignore this email.</p>
                <p>To set a new password, click the button below:</p>
                <p style="text-align: center;"><a href="%s" class="button">Reset Your Password</a></p>
                <p>This link will expire in 30 minutes.</p>
                <p>Thank you,<br>The Chronogram Team</p>
                """.formatted(escape(resetUrl));
        send(toEmail, "Richiesta di Reset Password - Chronogram", "Password Reset Request", body,
                "Could not send the password reset email.");
    }

    /**
     * Acknowledges a registration that needs a decision, so the applicant is not
     * left wondering why the credentials they just chose do not work yet.
     */
    public void sendRegistrationPendingEmail(String toEmail) {
        String body = """
                <p>Hello,</p>
                <p>Thank you for registering with Chronogram. Your request has been received and is \
                waiting for an administrator to review it.</p>
                <p>You will receive another email as soon as your account is approved. Until then, \
                signing in is not yet possible.</p>
                <p>Thank you,<br>The Chronogram Team</p>
                """;
        send(toEmail, "Registration received - Chronogram", "Registration received", body);
    }

    /** Tells the applicant the account is now usable, with a link to sign in. */
    public void sendAccountApprovedEmail(String toEmail, String appBaseUrl, String adminMessage) {
        String body = """
                <p>Hello,</p>
                <p>Your Chronogram account has been approved. You can now sign in with the email and \
                password you chose when you registered.</p>
                <p style="text-align: center;"><a href="%s" class="button">Open Chronogram</a></p>
                %s
                <p>Thank you,<br>The Chronogram Team</p>
                """.formatted(escape(appBaseUrl), messageBlock("Message from the administrator", adminMessage));
        send(toEmail, "Your account has been approved - Chronogram", "Account approved", body);
    }

    /** Tells the user access has been suspended, and why if the admin said so. */
    public void sendAccountBlockedEmail(String toEmail, String adminMessage) {
        String body = """
                <p>Hello,</p>
                <p>Your Chronogram account has been blocked by an administrator and you can no longer \
                sign in. Your data has not been deleted.</p>
                %s
                <p>If you believe this is a mistake, please reply to the administrator who contacted you.</p>
                <p>The Chronogram Team</p>
                """.formatted(messageBlock("Message from the administrator", adminMessage));
        send(toEmail, "Your account has been blocked - Chronogram", "Account blocked", body);
    }

    /**
     * Final notice before the account disappears. Sent while the row still
     * exists, because afterwards there is no address left to write to.
     */
    public void sendAccountDeletedEmail(String toEmail, String adminMessage) {
        String body = """
                <p>Hello,</p>
                <p>Your Chronogram account and all the activities you recorded have been permanently \
                deleted by an administrator. This action cannot be undone.</p>
                %s
                <p>The Chronogram Team</p>
                """.formatted(messageBlock("Message from the administrator", adminMessage));
        send(toEmail, "Your account has been deleted - Chronogram", "Account deleted", body);
    }

    /**
     * Nudges the administrator that somebody is waiting. Without it a pending
     * request is only noticed by whoever happens to open the back office.
     */
    public void sendPendingRegistrationNotice(String adminEmail, String applicantEmail, String applicantName) {
        String who = StringUtils.hasText(applicantName)
                ? escape(applicantName) + " (" + escape(applicantEmail) + ")"
                : escape(applicantEmail);
        String body = """
                <p>Hello,</p>
                <p>A new Chronogram registration is waiting for approval:</p>
                <p><strong>%s</strong></p>
                <p>Open the admin section to approve or reject the request.</p>
                <p>The Chronogram Team</p>
                """.formatted(who);
        send(adminEmail, "A registration is waiting for approval - Chronogram",
                "Registration awaiting approval", body);
    }

    // ---- internals ----

    private void send(String toEmail, String subject, String heading, String bodyHtml) {
        send(toEmail, subject, heading, bodyHtml, "Could not send the email.");
    }

    private void send(String toEmail, String subject, String heading, String bodyHtml, String failureMessage) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            if (StringUtils.hasText(from)) {
                helper.setFrom(from);
            }
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(layout(heading, bodyHtml), true);
            mailSender.send(message);
            log.info("Email '{}' sent to {}", subject, toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("Failed to send email '{}' to {}", subject, toEmail, e);
            throw new ServiceException(failureMessage, e);
        }
    }

    /** Renders an optional administrator-written note, or nothing at all. */
    private static String messageBlock(String label, String adminMessage) {
        if (!StringUtils.hasText(adminMessage)) {
            return "";
        }
        return "<div class=\"note\"><p class=\"note-label\">" + escape(label) + "</p><p>"
                + escape(adminMessage).replace("\n", "<br>") + "</p></div>";
    }

    private static String layout(String heading, String bodyHtml) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <style>
                    body { font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 0; }
                    .container { width: 90%; max-width: 600px; margin: 40px auto; background-color: #ffffff; padding: 30px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                    .logo { text-align: center; margin-bottom: 30px; }
                    .logo img { max-width: 160px; height: auto; }
                    h2 { color: #333333; }
                    p { color: #555555; font-size: 16px; line-height: 1.6; }
                    .button { display: inline-block; padding: 12px 24px; margin: 20px 0; background-color: #007bff; color: #ffffff; text-decoration: none; border-radius: 5px; font-weight: bold; }
                    .note { margin: 24px 0; padding: 16px 20px; background-color: #f8f9fa; border-left: 4px solid #007bff; border-radius: 4px; }
                    .note-label { margin: 0 0 8px; font-size: 13px; font-weight: bold; color: #333333; text-transform: uppercase; letter-spacing: .04em; }
                    .note p { margin: 0; }
                    .footer { font-size: 13px; color: #999999; margin-top: 30px; text-align: center; }
                  </style>
                </head>
                <body>
                <div class="container">
                  <div class="logo">
                    <img src="%LOGO%" alt="Chronogram Logo">
                  </div>
                  <h2>%HEADING%</h2>
                  %BODY%
                  <div class="footer">
                    <p>This is an automated message. Please do not reply to this email.</p>
                  </div>
                </div>
                </body>
                </html>
                """
                .replace("%LOGO%", LOGO_URL)
                .replace("%HEADING%", escape(heading))
                .replace("%BODY%", bodyHtml);
    }

    /**
     * Minimal HTML escaping for values that reach the template from outside -
     * an administrator's free-text note, or a name taken from a profile.
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
