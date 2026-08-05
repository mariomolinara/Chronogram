package it.unicas.chronogram.support;

import it.unicas.chronogram.common.exception.ApiExceptions.FeatureUnavailableException;
import it.unicas.chronogram.common.exception.ApiExceptions.ResourceNotFoundException;
import it.unicas.chronogram.common.exception.ApiExceptions.ServiceException;
import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.domain.UserProfile;
import it.unicas.chronogram.mail.EmailService;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.repository.UserProfileRepository;
import it.unicas.chronogram.support.dto.SupportMessageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the support form: who the message is addressed to (and in which
 * order the candidates are tried), which identity is attached to it, and what
 * happens when there is nowhere to send it or SMTP refuses it.
 */
@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

    private static final int USER_ID = 42;

    @Mock private UserAuthRepository userAuthRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private EmailService emailService;

    private ChronogramProperties properties;
    private SupportService supportService;

    @BeforeEach
    void setUp() {
        properties = new ChronogramProperties();
        supportService = new SupportService(userAuthRepository, userProfileRepository,
                emailService, properties);
    }

    private void authorExists() {
        UserAuth account = new UserAuth();
        account.setUserId(USER_ID);
        account.setEmail("ada@unicas.it");
        when(userAuthRepository.findById(USER_ID)).thenReturn(Optional.of(account));

        UserProfile profile = new UserProfile();
        profile.setUserId(USER_ID);
        profile.setName("Ada");
        profile.setSurname("Lovelace");
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
    }

    private static SupportMessageRequest message() {
        return new SupportMessageRequest("  Cannot export  ", "  The CSV button does nothing.  ");
    }

    @Test
    void sendsToTheConfiguredSupportMailboxWithTheAuthorsIdentity() {
        properties.getSupport().setEmail("support@chronogram.example");
        authorExists();

        supportService.submit(USER_ID, message());

        // The author is taken from the account, never from the payload, and the
        // free text is trimmed before it reaches the message.
        verify(emailService).sendSupportMessage("support@chronogram.example", "ada@unicas.it",
                "Ada Lovelace", "Cannot export", "The CSV button does nothing.");
    }

    /**
     * Preferred over the configured ADMIN_EMAIL, which is only read when the
     * account is first provisioned and goes stale as soon as the administrator
     * changes their address from the back office.
     */
    @Test
    void withoutASupportMailboxItGoesToTheAdministratorAccountAsItStandsNow() {
        properties.getAdmin().setEmail("bootstrap@chronogram.example");
        authorExists();

        UserAuth admin = new UserAuth();
        admin.setEmail("current-admin@chronogram.example");
        when(userAuthRepository.findFirstBySystemAccountTrue()).thenReturn(Optional.of(admin));

        supportService.submit(USER_ID, message());

        verify(emailService).sendSupportMessage(eq("current-admin@chronogram.example"),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void withNoAdministratorRowItFallsBackToTheConfiguredAdminAddress() {
        properties.getAdmin().setEmail("bootstrap@chronogram.example");
        authorExists();
        when(userAuthRepository.findFirstBySystemAccountTrue()).thenReturn(Optional.empty());

        supportService.submit(USER_ID, message());

        verify(emailService).sendSupportMessage(eq("bootstrap@chronogram.example"),
                anyString(), anyString(), anyString(), anyString());
    }

    /**
     * Nothing is persisted, so with no recipient the message would simply vanish.
     * Reporting the feature as unconfigured (503) is the only honest answer.
     */
    @Test
    void withNoRecipientAtAllTheFeatureIsReportedAsUnavailable() {
        authorExists();
        when(userAuthRepository.findFirstBySystemAccountTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supportService.submit(USER_ID, message()))
                .isInstanceOf(FeatureUnavailableException.class);

        verify(emailService, never()).sendSupportMessage(any(), any(), any(), any(), any());
    }

    /** A user with no profile row still gets their message through, unnamed. */
    @Test
    void aMissingProfileDoesNotBlockTheMessage() {
        properties.getSupport().setEmail("support@chronogram.example");
        UserAuth account = new UserAuth();
        account.setUserId(USER_ID);
        account.setEmail("ada@unicas.it");
        when(userAuthRepository.findById(USER_ID)).thenReturn(Optional.of(account));
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        supportService.submit(USER_ID, message());

        verify(emailService).sendSupportMessage("support@chronogram.example", "ada@unicas.it",
                null, "Cannot export", "The CSV button does nothing.");
    }

    @Test
    void anUnknownAuthorIsReportedAsNotFound() {
        when(userAuthRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supportService.submit(USER_ID, message()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * The email IS the feature here, so unlike the account-lifecycle notices a
     * failure is allowed to surface instead of being logged and swallowed.
     */
    @Test
    void anSmtpFailurePropagatesInsteadOfBeingSwallowed() {
        properties.getSupport().setEmail("support@chronogram.example");
        authorExists();
        doThrow(new ServiceException("Could not send the support message."))
                .when(emailService).sendSupportMessage(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> supportService.submit(USER_ID, message()))
                .isInstanceOf(ServiceException.class);
    }
}
