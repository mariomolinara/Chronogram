package it.unicas.chronogram.repository;

import it.unicas.chronogram.domain.AccountStatus;
import it.unicas.chronogram.domain.Activity;
import it.unicas.chronogram.domain.ActivityType;
import it.unicas.chronogram.domain.PasswordResetToken;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.domain.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Spring Data derived/@Query methods against a real MySQL started
 * by Testcontainers, with Flyway migrations applied and Hibernate
 * {@code ddl-auto=validate} - the same setup as production, unlike an H2
 * {@code @DataJpaTest} which cannot validate the MySQL-specific schema.
 *
 * <p>Skipped automatically (not failed) when no Docker daemon is reachable, via
 * the same {@code @EnabledIf("dockerAvailable")} guard as
 * {@code AuthFlowIntegrationTest}, so the build stays green without Docker.
 */
@SpringBootTest
@Testcontainers
@EnabledIf("dockerAvailable")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RepositoryQueriesIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("chronogram");

    static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("chronogram.security.jwt.secret",
                () -> "integration-test-secret-key-that-is-long-enough-32b");
    }

    // Mail is not exercised; avoid needing a real SMTP server for context startup.
    @MockBean private JavaMailSender mailSender;

    @Autowired private UserAuthRepository userAuthRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityTypeRepository activityTypeRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;

    private UserAuth persistUser(String email) {
        UserAuth user = new UserAuth();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setStatus(AccountStatus.ACTIVE);
        return userAuthRepository.saveAndFlush(user);
    }

    private Activity persistActivity(Integer userId, ActivityType type, LocalDate date, LocalDateTime createdAt) {
        Activity a = new Activity();
        a.setUserId(userId);
        a.setActivityType(type);
        a.setActivityDate(date);
        a.setDurationMins(30);
        a.setPleasantness(2);
        a.setLocation("Home");
        a.setCostEuro(new BigDecimal("1.00"));
        a.setCreatedAt(createdAt);
        return activityRepository.saveAndFlush(a);
    }

    // ---- UserAuthRepository ----

    @Test
    void findByEmailIgnoreCaseIsCaseInsensitive() {
        persistUser("Ada@Example.com");

        Optional<UserAuth> found = userAuthRepository.findByEmailIgnoreCase("ada@example.COM");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("Ada@Example.com");
    }

    @Test
    void existsByEmailIgnoreCaseMatchesRegardlessOfCase() {
        persistUser("Grace@Example.com");

        assertThat(userAuthRepository.existsByEmailIgnoreCase("grace@example.com")).isTrue();
        assertThat(userAuthRepository.existsByEmailIgnoreCase("unknown@example.com")).isFalse();
    }

    /**
     * The back-office search is the only query joining two entities that have no
     * JPA association, and it takes nullable enum/String parameters that behave
     * differently on a real MySQL than on an in-memory database - so it is
     * verified here rather than trusted.
     */
    @Test
    void searchFiltersByStatusAndFreeTextAndExcludesAdministrators() {
        UserAuth pending = persistUser("pending@example.com");
        pending.setStatus(AccountStatus.PENDING);
        userAuthRepository.saveAndFlush(pending);

        persistUser("active@example.com");

        UserAuth admin = persistUser("boss@example.com");
        admin.setRole(Role.ADMIN);
        userAuthRepository.saveAndFlush(admin);

        UserProfile profile = new UserProfile();
        profile.setUserId(pending.getUserId());
        profile.setName("Ada");
        profile.setSurname("Lovelace");
        userProfileRepository.saveAndFlush(profile);

        Pageable firstPage = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "userId"));

        // No filters at all: every participant, never an administrator.
        Page<UserAuth> all = userAuthRepository.search(Role.ADMIN, null, null, firstPage);
        assertThat(all.getContent()).extracting(UserAuth::getEmail)
                .contains("pending@example.com", "active@example.com")
                .doesNotContain("boss@example.com");

        // By state.
        Page<UserAuth> pendingOnly =
                userAuthRepository.search(Role.ADMIN, AccountStatus.PENDING, null, firstPage);
        assertThat(pendingOnly.getContent()).extracting(UserAuth::getEmail)
                .containsExactly("pending@example.com");

        // By a term that only the joined profile can satisfy.
        Page<UserAuth> byName = userAuthRepository.search(Role.ADMIN, null, "%lovelace%", firstPage);
        assertThat(byName.getContent()).extracting(UserAuth::getEmail)
                .containsExactly("pending@example.com");

        // By a term that only the email can satisfy.
        Page<UserAuth> byEmail = userAuthRepository.search(Role.ADMIN, null, "%active@%", firstPage);
        assertThat(byEmail.getContent()).extracting(UserAuth::getEmail)
                .containsExactly("active@example.com");
    }

    @Test
    void countByAccountStatusIgnoresAdministrators() {
        UserAuth blocked = persistUser("blocked@example.com");
        blocked.setStatus(AccountStatus.BLOCKED);
        userAuthRepository.saveAndFlush(blocked);

        UserAuth admin = persistUser("admin@example.com");
        admin.setRole(Role.ADMIN);
        admin.setStatus(AccountStatus.BLOCKED);
        userAuthRepository.saveAndFlush(admin);

        assertThat(userAuthRepository.countByAccountStatusAndRoleNot(AccountStatus.BLOCKED, Role.ADMIN))
                .isEqualTo(1);
    }

    /** The V4 migration must leave existing rows usable, not silently pending. */
    @Test
    void anAccountDefaultsToActiveSoTheMigrationCannotLockAnyoneOut() {
        UserAuth user = new UserAuth();
        user.setEmail("legacy@example.com");
        user.setPasswordHash("hash");
        UserAuth saved = userAuthRepository.saveAndFlush(user);

        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.isActive()).isTrue();
    }

    // ---- ActivityTypeRepository (seeded by Flyway V2) ----

    @Test
    void findAllByOrderByNameAscReturnsSeededTypesSortedByName() {
        List<ActivityType> types = activityTypeRepository.findAllByOrderByNameAsc();

        assertThat(types).isNotEmpty();
        List<String> names = types.stream().map(ActivityType::getName).toList();
        assertThat(names).isSorted();
    }

    // ---- ActivityRepository ----

    @Test
    void findByUserIdAndActivityDateOrdersByCreatedAtAscAndScopesToUserAndDate() {
        ActivityType type = activityTypeRepository.findAllByOrderByNameAsc().get(0);
        UserAuth owner = persistUser("owner@example.com");
        UserAuth other = persistUser("other@example.com");
        LocalDate today = LocalDate.of(2026, 7, 31);

        Activity later = persistActivity(owner.getUserId(), type, today, LocalDateTime.of(2026, 7, 31, 10, 0));
        Activity earlier = persistActivity(owner.getUserId(), type, today, LocalDateTime.of(2026, 7, 31, 8, 0));
        // Noise that must be excluded: different day and different user.
        persistActivity(owner.getUserId(), type, today.minusDays(1), LocalDateTime.of(2026, 7, 30, 9, 0));
        persistActivity(other.getUserId(), type, today, LocalDateTime.of(2026, 7, 31, 9, 0));

        List<Activity> result = activityRepository
                .findByUserIdAndActivityDateOrderByCreatedAtAsc(owner.getUserId(), today);

        assertThat(result).extracting(Activity::getId)
                .containsExactly(earlier.getId(), later.getId());
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        ActivityType type = activityTypeRepository.findAllByOrderByNameAsc().get(0);
        UserAuth owner = persistUser("act-owner@example.com");
        UserAuth intruder = persistUser("intruder@example.com");
        Activity activity = persistActivity(
                owner.getUserId(), type, LocalDate.of(2026, 7, 31), LocalDateTime.now());

        assertThat(activityRepository.findByIdAndUserId(activity.getId(), owner.getUserId())).isPresent();
        // Same id, wrong owner -> not returned.
        assertThat(activityRepository.findByIdAndUserId(activity.getId(), intruder.getUserId())).isEmpty();
    }

    /**
     * Backs the per-user figures in the back-office list: one grouped query for a
     * whole page instead of a count per row.
     */
    @Test
    void summariseByUserIdsCountsAndDatesPerUser() {
        ActivityType type = activityTypeRepository.findAllByOrderByNameAsc().get(0);
        UserAuth busy = persistUser("busy@example.com");
        UserAuth quiet = persistUser("quiet@example.com");
        UserAuth idle = persistUser("idle@example.com");

        persistActivity(busy.getUserId(), type, LocalDate.of(2026, 8, 1), LocalDateTime.of(2026, 8, 1, 9, 0));
        persistActivity(busy.getUserId(), type, LocalDate.of(2026, 8, 4), LocalDateTime.of(2026, 8, 4, 9, 0));
        persistActivity(quiet.getUserId(), type, LocalDate.of(2026, 7, 2), LocalDateTime.of(2026, 7, 2, 9, 0));

        List<ActivityRepository.UserActivitySummary> summaries = activityRepository.summariseByUserIds(
                List.of(busy.getUserId(), quiet.getUserId(), idle.getUserId()));

        assertThat(summaries).hasSize(2); // the idle user simply has no row
        ActivityRepository.UserActivitySummary busySummary = summaries.stream()
                .filter(s -> s.getUserId().equals(busy.getUserId())).findFirst().orElseThrow();
        assertThat(busySummary.getTotal()).isEqualTo(2);
        assertThat(busySummary.getLastDay()).isEqualTo(LocalDate.of(2026, 8, 4));
    }

    // ---- PasswordResetTokenRepository ----

    private PasswordResetToken persistToken(Integer userId, String selector, LocalDateTime expiry) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(userId);
        token.setSelector(selector);
        token.setVerifierHash("verifier-hash");
        token.setExpirationTime(expiry);
        token.setCreatedAt(LocalDateTime.now());
        return tokenRepository.saveAndFlush(token);
    }

    @Test
    void findBySelectorAndFindByUserIdLocateTheToken() {
        UserAuth user = persistUser("reset@example.com");
        persistToken(user.getUserId(), "sel-123", LocalDateTime.now().plusMinutes(30));

        assertThat(tokenRepository.findBySelector("sel-123")).isPresent();
        assertThat(tokenRepository.findByUserId(user.getUserId())).isPresent();
        assertThat(tokenRepository.findBySelector("does-not-exist")).isEmpty();
    }

    @Test
    void deleteBySelectorRemovesOnlyTheMatchingToken() {
        UserAuth u1 = persistUser("del1@example.com");
        UserAuth u2 = persistUser("del2@example.com");
        persistToken(u1.getUserId(), "keep", LocalDateTime.now().plusMinutes(30));
        persistToken(u2.getUserId(), "drop", LocalDateTime.now().plusMinutes(30));

        tokenRepository.deleteBySelector("drop");
        tokenRepository.flush();

        assertThat(tokenRepository.findBySelector("drop")).isEmpty();
        assertThat(tokenRepository.findBySelector("keep")).isPresent();
    }

    @Test
    void deleteExpiredRemovesOnlyPastTokens() {
        UserAuth expiredUser = persistUser("expired@example.com");
        UserAuth freshUser = persistUser("fresh@example.com");
        LocalDateTime now = LocalDateTime.now();
        persistToken(expiredUser.getUserId(), "expired-sel", now.minusMinutes(5));
        persistToken(freshUser.getUserId(), "fresh-sel", now.plusMinutes(30));

        int deleted = tokenRepository.deleteExpired(now);
        tokenRepository.flush();

        assertThat(deleted).isEqualTo(1);
        assertThat(tokenRepository.findBySelector("expired-sel")).isEmpty();
        assertThat(tokenRepository.findBySelector("fresh-sel")).isPresent();
    }
}
