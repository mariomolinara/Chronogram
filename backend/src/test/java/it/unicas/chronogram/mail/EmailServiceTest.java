package it.unicas.chronogram.mail;

import it.unicas.chronogram.common.exception.ApiExceptions.ServiceException;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link EmailService}. The {@link JavaMailSender} is mocked, so no
 * real SMTP connection is opened: the tests assert how the {@link MimeMessage} is
 * built (recipient, subject, sender, HTML link) and that transport failures are
 * wrapped in a {@link ServiceException}.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    /** A real MimeMessage backed by an empty session lets us inspect what the service set. */
    private MimeMessage newMimeMessage() {
        return new MimeMessage(jakarta.mail.Session.getInstance(new Properties()));
    }

    private String bodyOf(MimeMessage message) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString("UTF-8");
    }

    @Test
    void sendsMessageWithRecipientSubjectFromAndResetLink() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mime = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mime);

        EmailService service = new EmailService(mailSender, "noreply@chronogram.io");

        service.sendPasswordResetEmail("ada@example.com", "sel:ver", "http://app.local");

        // The very message we prepared must have been handed to the sender.
        verify(mailSender).send(mime);

        assertThat(mime.getRecipients(Message.RecipientType.TO)[0].toString())
                .isEqualTo("ada@example.com");
        assertThat(mime.getSubject()).isEqualTo("Richiesta di Reset Password - Chronogram");
        assertThat(mime.getFrom()[0].toString()).isEqualTo("noreply@chronogram.io");

        String body = bodyOf(mime);
        // Content-type is HTML and the reset link (baseUrl + path + token) is present.
        assertThat(mime.getContentType()).containsIgnoringCase("text/html");
        assertThat(body).contains("http://app.local/reset-password?token=sel:ver");
    }

    @Test
    void omitsFromHeaderWhenSenderNotConfigured() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mime = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mime);

        // Blank "from", as when spring.mail.username is not set.
        EmailService service = new EmailService(mailSender, "  ");

        service.sendPasswordResetEmail("ada@example.com", "sel:ver", "http://app.local");

        verify(mailSender).send(mime);
        assertThat(mime.getFrom()).isNull();
    }

    @Test
    void wrapsTransportFailureInServiceException() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        EmailService service = new EmailService(mailSender, "noreply@chronogram.io");

        assertThatThrownBy(() ->
                service.sendPasswordResetEmail("ada@example.com", "sel:ver", "http://app.local"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Could not send the password reset email")
                .hasRootCauseInstanceOf(MailSendException.class);
    }

    @Test
    void wrapsMimeCreationFailureAndDoesNotSend() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        // Simulate a failure while building the message (e.g. session/config problem).
        when(mailSender.createMimeMessage()).thenThrow(new IllegalStateException("no session"));

        EmailService service = new EmailService(mailSender, "noreply@chronogram.io");

        assertThatThrownBy(() ->
                service.sendPasswordResetEmail("ada@example.com", "sel:ver", "http://app.local"))
                .isInstanceOf(ServiceException.class);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
