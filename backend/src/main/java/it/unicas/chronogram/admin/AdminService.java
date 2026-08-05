package it.unicas.chronogram.admin;

import it.unicas.chronogram.admin.dto.AdminStatsResponse;
import it.unicas.chronogram.admin.dto.UpdateAdminCredentialsRequest;
import it.unicas.chronogram.common.exception.ApiExceptions.ResourceNotFoundException;
import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.Activity;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.domain.UserProfile;
import it.unicas.chronogram.repository.ActivityRepository;
import it.unicas.chronogram.repository.DailyCount;
import it.unicas.chronogram.repository.LoginEventRepository;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only reporting and data export for the back office, plus the one write the
 * administrator is allowed to perform on itself (email / password).
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final int RECENT_ACTIVITY_DAYS = 7;

    private final UserAuthRepository userAuthRepository;
    private final UserProfileRepository userProfileRepository;
    private final ActivityRepository activityRepository;
    private final LoginEventRepository loginEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChronogramProperties properties;

    public AdminService(UserAuthRepository userAuthRepository,
                        UserProfileRepository userProfileRepository,
                        ActivityRepository activityRepository,
                        LoginEventRepository loginEventRepository,
                        PasswordEncoder passwordEncoder,
                        ChronogramProperties properties) {
        this.userAuthRepository = userAuthRepository;
        this.userProfileRepository = userProfileRepository;
        this.activityRepository = activityRepository;
        this.loginEventRepository = loginEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    // ---- dashboard ----

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        ChronogramProperties.Stats config = properties.getStats();
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();

        // Windows are counted in whole calendar days ending today: a 10-day window
        // starts at midnight 9 days ago, so "today" is one of the ten.
        LocalDateTime activeSince = startOfToday.minusDays(config.getActiveWindowDays() - 1L);
        LocalDateTime regularSince = startOfToday.minusDays(config.getRegularWindowDays() - 1L);

        // The built-in administrator is an operator, not a study participant.
        long totalUsers = Math.max(0, userAuthRepository.count() - userAuthRepository.countByRole(Role.ADMIN));

        long activeUsers = loginEventRepository.countDistinctUsersSince(activeSince);
        long regularUsers = loginEventRepository.countUsersActiveOnAtLeastDistinctDays(
                regularSince, config.getRegularWindowDays());

        long totalActivities = activityRepository.count();

        LocalDate distributionFrom = today.minusDays(config.getDistributionDays() - 1L);
        List<AdminStatsResponse.DailyPoint> daily = fillGaps(
                activityRepository.countPerDaySince(distributionFrom), distributionFrom, today);

        // Queried separately rather than summed from `daily`: the distribution
        // window is configurable and could be shorter than seven days.
        LocalDate recentFrom = today.minusDays(RECENT_ACTIVITY_DAYS - 1L);
        long activitiesLastWeek = activityRepository.countPerDaySince(recentFrom).stream()
                .mapToLong(DailyCount::getTotal)
                .sum();

        return new AdminStatsResponse(totalUsers, activeUsers, regularUsers, totalActivities,
                activitiesLastWeek, config.getActiveWindowDays(), config.getRegularWindowDays(), daily);
    }

    /**
     * Turns the sparse per-day counts from SQL into a continuous series: a chart
     * with missing days would compress the gaps and misrepresent the trend.
     */
    private static List<AdminStatsResponse.DailyPoint> fillGaps(List<DailyCount> counts,
                                                                LocalDate from,
                                                                LocalDate to) {
        Map<LocalDate, Long> byDay = counts.stream()
                .collect(Collectors.toMap(DailyCount::getDay, DailyCount::getTotal, Long::sum, HashMap::new));

        List<AdminStatsResponse.DailyPoint> series = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            series.add(new AdminStatsResponse.DailyPoint(day, byDay.getOrDefault(day, 0L)));
        }
        return series;
    }

    // ---- CSV export ----

    /**
     * Activity records, pseudonymised: the numeric {@code user_id} is the only
     * link to a person, so the file can be analysed without carrying identities.
     * Join it with the users export on {@code user_id} when that is needed.
     */
    @Transactional(readOnly = true)
    public String exportActivitiesCsv() {
        CsvWriter csv = new CsvWriter(List.of(
                "activity_id", "user_id", "activity_date", "activity_type",
                "duration_mins", "pleasantness", "location", "cost_euro", "created_at"));

        for (Activity activity : activityRepository.findAllByOrderByActivityDateAscIdAsc()) {
            csv.writeRow(
                    activity.getId(),
                    activity.getUserId(),
                    activity.getActivityDate(),
                    activity.getActivityType() == null ? null : activity.getActivityType().getName(),
                    activity.getDurationMins(),
                    activity.getPleasantness(),
                    activity.getLocation(),
                    activity.getCostEuro(),
                    activity.getCreatedAt());
        }
        return csv.toCsv();
    }

    /**
     * Account and profile data, one row per participant. Contains personal data -
     * the administrator account itself is excluded, as it is not a participant.
     */
    @Transactional(readOnly = true)
    public String exportUsersCsv() {
        Map<Integer, UserProfile> profiles = userProfileRepository.findAll().stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity(), (a, b) -> a));

        CsvWriter csv = new CsvWriter(List.of(
                "user_id", "email", "name", "surname", "phone", "gender", "birthday", "address",
                "weekly_income", "weekly_income_other", "weekly_home_cost", "notes",
                "registered_at", "last_login", "account_status"));

        for (UserAuth user : userAuthRepository.findAllByOrderByUserIdAsc()) {
            if (user.getRole() == Role.ADMIN) {
                continue;
            }
            UserProfile profile = profiles.get(user.getUserId());
            csv.writeRow(
                    user.getUserId(),
                    user.getEmail(),
                    profile == null ? null : profile.getName(),
                    profile == null ? null : profile.getSurname(),
                    profile == null ? null : profile.getPhone(),
                    profile == null ? null : profile.getGender(),
                    profile == null ? null : profile.getBirthday(),
                    profile == null ? null : profile.getAddress(),
                    profile == null ? null : profile.getWeeklyIncome(),
                    profile == null ? null : profile.getWeeklyIncomeOther(),
                    profile == null ? null : profile.getWeeklyHomeCost(),
                    profile == null ? null : profile.getNotes(),
                    user.getCreatedAt(),
                    user.getLastLogin(),
                    user.getAccountStatus());
        }
        return csv.toCsv();
    }

    // ---- self-service credentials ----

    /**
     * Changes the administrator's own email and/or password. Role, system flag and
     * active state are intentionally not editable here: the built-in account must
     * stay an enabled administrator, and nothing in the API can remove it.
     *
     * @return true if the email changed, so the caller knows the current token's
     *         subject is stale and the session has to be re-established.
     */
    @Transactional
    public boolean updateOwnCredentials(Integer adminUserId, UpdateAdminCredentialsRequest request) {
        UserAuth admin = userAuthRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));

        if (!passwordEncoder.matches(request.currentPassword(), admin.getPasswordHash())) {
            throw new ValidationException("Current password is not correct.");
        }

        String newEmail = trimToNull(request.newEmail());
        String newPassword = trimToNull(request.newPassword());
        if (newEmail == null && newPassword == null) {
            throw new ValidationException("Provide a new email, a new password, or both.");
        }

        boolean emailChanged = false;
        if (newEmail != null && !newEmail.equalsIgnoreCase(admin.getEmail())) {
            if (userAuthRepository.existsByEmailIgnoreCase(newEmail)) {
                throw new ValidationException("That email is already in use.");
            }
            admin.setEmail(newEmail);
            emailChanged = true;
        }

        if (newPassword != null) {
            if (passwordEncoder.matches(newPassword, admin.getPasswordHash())) {
                throw new ValidationException("The new password must differ from the current one.");
            }
            admin.setPasswordHash(passwordEncoder.encode(newPassword));
            admin.setMustChangePassword(false);
        }

        admin.setUpdatedAt(LocalDateTime.now());
        userAuthRepository.save(admin);
        log.info("Administrator {} updated its credentials (emailChanged={}, passwordChanged={}).",
                admin.getEmail(), emailChanged, newPassword != null);
        return emailChanged;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
