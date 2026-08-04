package it.unicas.chronogram.admin;

import it.unicas.chronogram.admin.dto.AdminStatsResponse;
import it.unicas.chronogram.admin.dto.UpdateAdminCredentialsRequest;
import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.Activity;
import it.unicas.chronogram.domain.ActivityType;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.domain.UserProfile;
import it.unicas.chronogram.repository.ActivityRepository;
import it.unicas.chronogram.repository.DailyCount;
import it.unicas.chronogram.repository.LoginEventRepository;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the back-office service: the dashboard arithmetic (which
 * windows are queried, who is excluded from the counts, how gaps in the series
 * are filled), CSV shape and escaping, and the guards around the one write the
 * administrator may perform on itself.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserAuthRepository userAuthRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private LoginEventRepository loginEventRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private ChronogramProperties properties;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        properties = new ChronogramProperties();
        adminService = new AdminService(userAuthRepository, userProfileRepository, activityRepository,
                loginEventRepository, passwordEncoder, properties);
    }

    private static DailyCount dailyCount(LocalDate day, long total) {
        return new DailyCount() {
            @Override public LocalDate getDay() {
                return day;
            }

            @Override public long getTotal() {
                return total;
            }
        };
    }

    // ---- stats ----

    @Test
    void statsExcludeAdminAccountsFromTheUserCount() {
        when(userAuthRepository.count()).thenReturn(12L);
        when(userAuthRepository.countByRole(Role.ADMIN)).thenReturn(1L);
        when(activityRepository.count()).thenReturn(340L);
        when(activityRepository.countPerDaySince(any())).thenReturn(List.of());

        AdminStatsResponse stats = adminService.stats();

        assertThat(stats.totalUsers()).isEqualTo(11);
        assertThat(stats.totalActivities()).isEqualTo(340);
    }

    @Test
    void statsQueryTheConfiguredActivityAndRegularityWindows() {
        properties.getStats().setActiveWindowDays(10);
        properties.getStats().setRegularWindowDays(7);
        when(activityRepository.countPerDaySince(any())).thenReturn(List.of());
        when(loginEventRepository.countDistinctUsersSince(any())).thenReturn(4L);
        when(loginEventRepository.countUsersActiveOnAtLeastDistinctDays(any(), anyInt())).thenReturn(2L);

        AdminStatsResponse stats = adminService.stats();

        // Windows are inclusive of today: 10 days back means midnight 9 days ago.
        LocalDateTime expectedActiveSince = LocalDate.now().atStartOfDay().minusDays(9);
        LocalDateTime expectedRegularSince = LocalDate.now().atStartOfDay().minusDays(6);
        verify(loginEventRepository).countDistinctUsersSince(expectedActiveSince);
        verify(loginEventRepository).countUsersActiveOnAtLeastDistinctDays(expectedRegularSince, 7);

        assertThat(stats.activeUsers()).isEqualTo(4);
        assertThat(stats.regularUsers()).isEqualTo(2);
        assertThat(stats.activeWindowDays()).isEqualTo(10);
        assertThat(stats.regularWindowDays()).isEqualTo(7);
    }

    @Test
    void dailySeriesIsContinuousAndFillsMissingDaysWithZero() {
        properties.getStats().setDistributionDays(5);
        LocalDate today = LocalDate.now();
        // Only two of the five days have data; the rest must still appear.
        when(activityRepository.countPerDaySince(any()))
                .thenReturn(List.of(dailyCount(today.minusDays(3), 7), dailyCount(today, 2)));

        List<AdminStatsResponse.DailyPoint> series = adminService.stats().dailyActivities();

        assertThat(series).hasSize(5);
        assertThat(series.get(0).day()).isEqualTo(today.minusDays(4));
        assertThat(series.get(series.size() - 1).day()).isEqualTo(today);
        assertThat(series.stream().map(AdminStatsResponse.DailyPoint::count))
                .containsExactly(0L, 7L, 0L, 0L, 2L);
    }

    // ---- CSV ----

    @Test
    void activityCsvIsPseudonymisedAndQuotesEmbeddedSeparators() {
        ActivityType type = new ActivityType();
        type.setName("Leisure");

        Activity activity = new Activity();
        activity.setId(9);
        activity.setUserId(42);
        activity.setActivityDate(LocalDate.of(2026, 8, 1));
        activity.setActivityType(type);
        activity.setDurationMins(45);
        activity.setPleasantness(3);
        activity.setLocation("Rome, Italy");
        activity.setCostEuro(new BigDecimal("12.50"));
        when(activityRepository.findAllByOrderByActivityDateAscIdAsc()).thenReturn(List.of(activity));

        String csv = adminService.exportActivitiesCsv();
        String[] lines = csv.split("\r\n");

        assertThat(lines[0]).startsWith("activity_id,user_id,activity_date,activity_type");
        // No email anywhere: the numeric id is the only link back to a person.
        assertThat(csv).doesNotContain("@");
        // The comma inside the location must not become a column break.
        assertThat(lines[1]).contains("\"Rome, Italy\"");
        assertThat(lines[1]).startsWith("9,42,2026-08-01,Leisure,45,3,");
    }

    @Test
    void userCsvSkipsAdministratorsAndToleratesMissingProfiles() {
        UserAuth participant = new UserAuth();
        participant.setUserId(42);
        participant.setEmail("ada@example.com");
        participant.setRole(Role.USER);

        UserAuth admin = new UserAuth();
        admin.setUserId(1);
        admin.setEmail("admin@example.com");
        admin.setRole(Role.ADMIN);

        UserProfile profile = new UserProfile();
        profile.setUserId(42);
        profile.setName("Ada");
        profile.setSurname("Lovelace");

        when(userAuthRepository.findAllByOrderByUserIdAsc()).thenReturn(List.of(admin, participant));
        when(userProfileRepository.findAll()).thenReturn(List.of(profile));

        String csv = adminService.exportUsersCsv();

        assertThat(csv).contains("ada@example.com").contains("Ada,Lovelace");
        assertThat(csv).doesNotContain("admin@example.com");
    }

    // ---- credentials ----

    private UserAuth adminAccount() {
        UserAuth admin = new UserAuth();
        admin.setUserId(1);
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("stored-hash");
        admin.setRole(Role.ADMIN);
        admin.setSystemAccount(true);
        admin.setMustChangePassword(true);
        return admin;
    }

    @Test
    void credentialsUpdateRequiresTheCurrentPassword() {
        UserAuth admin = adminAccount();
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> adminService.updateOwnCredentials(1,
                new UpdateAdminCredentialsRequest("wrong", null, "brand-new-password")))
                .isInstanceOf(ValidationException.class);

        verify(userAuthRepository, never()).save(any());
    }

    @Test
    void changingPasswordClearsTheForcedChangeFlag() {
        UserAuth admin = adminAccount();
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("initial-password", "stored-hash")).thenReturn(true);
        when(passwordEncoder.matches("brand-new-password", "stored-hash")).thenReturn(false);
        when(passwordEncoder.encode("brand-new-password")).thenReturn("new-hash");

        boolean emailChanged = adminService.updateOwnCredentials(1,
                new UpdateAdminCredentialsRequest("initial-password", null, "brand-new-password"));

        assertThat(emailChanged).isFalse();
        assertThat(admin.getPasswordHash()).isEqualTo("new-hash");
        assertThat(admin.isMustChangePassword()).isFalse();
        // The account must remain the system administrator.
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isSystemAccount()).isTrue();
        verify(userAuthRepository).save(admin);
    }

    @Test
    void changingEmailReportsThatTheSessionMustBeReestablished() {
        UserAuth admin = adminAccount();
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(eq("initial-password"), anyString())).thenReturn(true);
        when(userAuthRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);

        boolean emailChanged = adminService.updateOwnCredentials(1,
                new UpdateAdminCredentialsRequest("initial-password", "new@example.com", null));

        assertThat(emailChanged).isTrue();
        assertThat(admin.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void emailAlreadyInUseIsRejected() {
        UserAuth admin = adminAccount();
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(eq("initial-password"), anyString())).thenReturn(true);
        when(userAuthRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> adminService.updateOwnCredentials(1,
                new UpdateAdminCredentialsRequest("initial-password", "taken@example.com", null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void anEmptyChangeIsRejected() {
        UserAuth admin = adminAccount();
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(eq("initial-password"), anyString())).thenReturn(true);

        assertThatThrownBy(() -> adminService.updateOwnCredentials(1,
                new UpdateAdminCredentialsRequest("initial-password", "  ", null)))
                .isInstanceOf(ValidationException.class);
    }
}
