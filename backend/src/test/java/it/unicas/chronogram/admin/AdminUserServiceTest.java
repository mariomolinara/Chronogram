package it.unicas.chronogram.admin;

import it.unicas.chronogram.admin.dto.AdminUserPageResponse;
import it.unicas.chronogram.admin.dto.AdminUserResponse;
import it.unicas.chronogram.common.exception.ApiExceptions.ResourceNotFoundException;
import it.unicas.chronogram.common.exception.ApiExceptions.ServiceException;
import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.AccountStatus;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.domain.UserProfile;
import it.unicas.chronogram.mail.EmailService;
import it.unicas.chronogram.repository.ActivityRepository;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the back-office account actions. The emphasis is on the two
 * things that are easy to get wrong and expensive when they are: the guards that
 * stop an administrator from disabling the back office, and the rule that a mail
 * outage must never undo a decision that has already been applied.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceTest {

    private static final int ADMIN_ID = 1;
    private static final int TARGET_ID = 7;

    @Mock private UserAuthRepository userAuthRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private EmailService emailService;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userAuthRepository, userProfileRepository,
                activityRepository, emailService, new ChronogramProperties());
    }

    private UserAuth participant(AccountStatus status) {
        UserAuth user = new UserAuth();
        user.setUserId(TARGET_ID);
        user.setEmail("ada@example.com");
        user.setRole(Role.USER);
        user.setStatus(status);
        when(userAuthRepository.findById(TARGET_ID)).thenReturn(Optional.of(user));
        return user;
    }

    // ---- guards ----

    @Test
    void anAdministratorCannotActOnTheirOwnAccount() {
        assertThatThrownBy(() -> service.block(ADMIN_ID, ADMIN_ID, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("your own account");

        verify(userAuthRepository, never()).save(any());
    }

    @Test
    void anotherAdministratorCannotBeManagedFromTheList() {
        UserAuth otherAdmin = new UserAuth();
        otherAdmin.setUserId(TARGET_ID);
        otherAdmin.setRole(Role.ADMIN);
        otherAdmin.setStatus(AccountStatus.ACTIVE);
        when(userAuthRepository.findById(TARGET_ID)).thenReturn(Optional.of(otherAdmin));

        assertThatThrownBy(() -> service.block(ADMIN_ID, TARGET_ID, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Administrator accounts");
    }

    /**
     * The built-in account is the only guaranteed way back into the back office,
     * so it stays untouchable even if it were somehow not flagged ADMIN.
     */
    @Test
    void theBuiltInSystemAccountCannotBeDeleted() {
        UserAuth system = new UserAuth();
        system.setUserId(TARGET_ID);
        system.setRole(Role.USER);
        system.setSystemAccount(true);
        system.setStatus(AccountStatus.ACTIVE);
        when(userAuthRepository.findById(TARGET_ID)).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> service.delete(ADMIN_ID, TARGET_ID, "bye"))
                .isInstanceOf(ValidationException.class);

        verify(userAuthRepository, never()).delete(any());
    }

    @Test
    void actingOnAnUnknownUserIsANotFound() {
        when(userAuthRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(ADMIN_ID, TARGET_ID, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- approve ----

    @Test
    void approvingAPendingAccountActivatesItAndEmailsTheUser() {
        UserAuth user = participant(AccountStatus.PENDING);

        AdminUserResponse response = service.approve(ADMIN_ID, TARGET_ID, "Welcome aboard");

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        // The derived flag is what the JWT filter reads, so it has to follow.
        assertThat(user.isActive()).isTrue();
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        verify(userAuthRepository).save(user);
        verify(emailService).sendAccountApprovedEmail(eqEmail(), anyString(), eqMessage("Welcome aboard"));
    }

    @Test
    void anAlreadyActiveAccountCannotBeApprovedAgain() {
        participant(AccountStatus.ACTIVE);

        assertThatThrownBy(() -> service.approve(ADMIN_ID, TARGET_ID, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("pending");

        verify(userAuthRepository, never()).save(any());
    }

    // ---- block / unblock ----

    @Test
    void blockingAnActiveAccountRevokesItsAbilityToAuthenticate() {
        UserAuth user = participant(AccountStatus.ACTIVE);

        service.block(ADMIN_ID, TARGET_ID, "Study over");

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.BLOCKED);
        // is_active drives the JWT filter: tokens already issued stop working.
        assertThat(user.isActive()).isFalse();
        verify(emailService).sendAccountBlockedEmail("ada@example.com", "Study over");
    }

    /** Blocking a pending request is how an administrator turns it down. */
    @Test
    void aPendingRequestCanBeBlockedWithoutBeingDeleted() {
        UserAuth user = participant(AccountStatus.PENDING);

        service.block(ADMIN_ID, TARGET_ID, null);

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.BLOCKED);
    }

    @Test
    void blockingAnAlreadyBlockedAccountIsRejected() {
        participant(AccountStatus.BLOCKED);

        assertThatThrownBy(() -> service.block(ADMIN_ID, TARGET_ID, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already blocked");
    }

    @Test
    void unblockingClearsAnyStaleLockout() {
        UserAuth user = participant(AccountStatus.BLOCKED);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(30));

        service.unblock(ADMIN_ID, TARGET_ID, null);

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        // Otherwise the user would be told "try again later" right after being let back in.
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void onlyABlockedAccountCanBeUnblocked() {
        participant(AccountStatus.ACTIVE);

        assertThatThrownBy(() -> service.unblock(ADMIN_ID, TARGET_ID, null))
                .isInstanceOf(ValidationException.class);
    }

    // ---- delete ----

    @Test
    void deletingRemovesTheRowBeforeAnnouncingItAndNotifiesTheUser() {
        UserAuth user = participant(AccountStatus.ACTIVE);

        service.delete(ADMIN_ID, TARGET_ID, "Withdrawn from the study");

        var inOrder = org.mockito.Mockito.inOrder(userAuthRepository, emailService);
        inOrder.verify(userAuthRepository).delete(user);
        // Flushed first, so the message is only ever sent about a deletion that happened.
        inOrder.verify(userAuthRepository).flush();
        inOrder.verify(emailService).sendAccountDeletedEmail("ada@example.com", "Withdrawn from the study");
    }

    /**
     * The administrator has already been told the action succeeded; an SMTP
     * failure afterwards must not turn that into an error, nor roll the change back.
     */
    @Test
    void aMailFailureDoesNotUndoTheDecision() {
        UserAuth user = participant(AccountStatus.PENDING);
        doThrow(new ServiceException("smtp down"))
                .when(emailService).sendAccountApprovedEmail(anyString(), anyString(), any());

        AdminUserResponse response = service.approve(ADMIN_ID, TARGET_ID, null);

        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(userAuthRepository).save(user);
    }

    // ---- listing ----

    @Test
    void theListJoinsProfileAndActivityAndCarriesTheStatusCounts() {
        UserAuth user = new UserAuth();
        user.setUserId(TARGET_ID);
        user.setEmail("ada@example.com");
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.PENDING);
        user.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));

        Page<UserAuth> page = new PageImpl<>(List.of(user), PageRequest.of(0, 25), 1);
        when(userAuthRepository.search(any(), any(), any(), any())).thenReturn(page);

        UserProfile profile = new UserProfile();
        profile.setUserId(TARGET_ID);
        profile.setName("Ada");
        profile.setSurname("Lovelace");
        when(userProfileRepository.findByUserIdIn(List.of(TARGET_ID))).thenReturn(List.of(profile));

        when(activityRepository.summariseByUserIds(List.of(TARGET_ID)))
                .thenReturn(List.of(summary(TARGET_ID, 12, LocalDate.of(2026, 8, 4))));

        when(userAuthRepository.countByAccountStatusAndRoleNot(AccountStatus.PENDING, Role.ADMIN)).thenReturn(3L);
        when(userAuthRepository.countByAccountStatusAndRoleNot(AccountStatus.ACTIVE, Role.ADMIN)).thenReturn(20L);
        when(userAuthRepository.countByAccountStatusAndRoleNot(AccountStatus.BLOCKED, Role.ADMIN)).thenReturn(1L);

        AdminUserPageResponse response = service.list(AccountStatus.PENDING, "ada", 0, 25);

        assertThat(response.items()).hasSize(1);
        AdminUserResponse item = response.items().get(0);
        assertThat(item.email()).isEqualTo("ada@example.com");
        assertThat(item.name()).isEqualTo("Ada");
        assertThat(item.activityCount()).isEqualTo(12);
        assertThat(item.lastActivityDay()).isEqualTo(LocalDate.of(2026, 8, 4));
        assertThat(item.status()).isEqualTo(AccountStatus.PENDING);

        // Counts are global, not derived from the filtered page.
        assertThat(response.counts().pending()).isEqualTo(3);
        assertThat(response.counts().active()).isEqualTo(20);
        assertThat(response.counts().blocked()).isEqualTo(1);
        assertThat(response.totalItems()).isEqualTo(1);
    }

    @Test
    void aUserWithNoProfileOrActivityStillAppears() {
        UserAuth user = new UserAuth();
        user.setUserId(TARGET_ID);
        user.setEmail("ghost@example.com");
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.ACTIVE);
        when(userAuthRepository.search(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 25), 1));
        when(userProfileRepository.findByUserIdIn(any())).thenReturn(List.of());
        when(activityRepository.summariseByUserIds(any())).thenReturn(List.of());

        AdminUserResponse item = service.list(null, null, null, null).items().get(0);

        assertThat(item.name()).isNull();
        assertThat(item.activityCount()).isZero();
        assertThat(item.lastActivityDay()).isNull();
    }

    /** An oversized page must not let one request pull the whole table. */
    @Test
    void thePageSizeIsCapped() {
        when(userAuthRepository.search(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        AdminUserPageResponse response = service.list(null, null, 0, 5000);

        assertThat(response.size()).isEqualTo(100);
    }

    // ---- helpers ----

    private static String eqEmail() {
        return org.mockito.ArgumentMatchers.eq("ada@example.com");
    }

    private static String eqMessage(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }

    private static ActivityRepository.UserActivitySummary summary(Integer userId, long total, LocalDate lastDay) {
        return new ActivityRepository.UserActivitySummary() {
            @Override public Integer getUserId() {
                return userId;
            }

            @Override public long getTotal() {
                return total;
            }

            @Override public LocalDate getLastDay() {
                return lastDay;
            }
        };
    }
}
