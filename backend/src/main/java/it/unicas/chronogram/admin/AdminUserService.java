package it.unicas.chronogram.admin;

import it.unicas.chronogram.admin.dto.AdminUserPageResponse;
import it.unicas.chronogram.admin.dto.AdminUserResponse;
import it.unicas.chronogram.common.exception.ApiExceptions.ResourceNotFoundException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Account administration: the participant list and the decisions an
 * administrator can take on it - approve a pending registration, block or
 * unblock an account, delete one for good.
 *
 * <p>Kept apart from {@link AdminService}, which stays read-only reporting and
 * export. Everything here writes, and every write is guarded by the same rule:
 * an administrator may act on participants, never on an administrator and never
 * on the built-in system account, or the installation could be left with no way
 * back into the back office.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 25;

    private final UserAuthRepository userAuthRepository;
    private final UserProfileRepository userProfileRepository;
    private final ActivityRepository activityRepository;
    private final EmailService emailService;
    private final ChronogramProperties properties;

    public AdminUserService(UserAuthRepository userAuthRepository,
                            UserProfileRepository userProfileRepository,
                            ActivityRepository activityRepository,
                            EmailService emailService,
                            ChronogramProperties properties) {
        this.userAuthRepository = userAuthRepository;
        this.userProfileRepository = userProfileRepository;
        this.activityRepository = activityRepository;
        this.emailService = emailService;
        this.properties = properties;
    }

    // ---- listing ----

    /**
     * One page of participants, newest registration first - the order that puts
     * whoever is waiting for a decision closest to the top.
     *
     * @param status restricts to one lifecycle state, or null for all
     * @param query  free-text term matched against email, name and surname
     */
    @Transactional(readOnly = true)
    public AdminUserPageResponse list(AccountStatus status, String query, Integer page, Integer size) {
        int pageIndex = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        String term = StringUtils.hasText(query)
                ? "%" + query.trim().toLowerCase(Locale.ROOT) + "%"
                : null;

        Page<UserAuth> result = userAuthRepository.search(Role.ADMIN, status, term,
                PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "userId")));

        List<Integer> ids = result.getContent().stream().map(UserAuth::getUserId).toList();

        // Two lookups for the whole page instead of two per row.
        Map<Integer, UserProfile> profiles = ids.isEmpty() ? Map.of()
                : userProfileRepository.findByUserIdIn(ids).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity(), (a, b) -> a));
        Map<Integer, ActivityRepository.UserActivitySummary> activity = ids.isEmpty() ? Map.of()
                : activityRepository.summariseByUserIds(ids).stream()
                .collect(Collectors.toMap(ActivityRepository.UserActivitySummary::getUserId,
                        Function.identity(), (a, b) -> a));

        List<AdminUserResponse> items = result.getContent().stream()
                .map(user -> toResponse(user, profiles.get(user.getUserId()), activity.get(user.getUserId())))
                .toList();

        return new AdminUserPageResponse(items, pageIndex, pageSize,
                result.getTotalElements(), result.getTotalPages(), counts());
    }

    private AdminUserPageResponse.StatusCounts counts() {
        return new AdminUserPageResponse.StatusCounts(
                userAuthRepository.countByAccountStatusAndRoleNot(AccountStatus.PENDING, Role.ADMIN),
                userAuthRepository.countByAccountStatusAndRoleNot(AccountStatus.ACTIVE, Role.ADMIN),
                userAuthRepository.countByAccountStatusAndRoleNot(AccountStatus.BLOCKED, Role.ADMIN));
    }

    private static AdminUserResponse toResponse(UserAuth user,
                                                UserProfile profile,
                                                ActivityRepository.UserActivitySummary summary) {
        return new AdminUserResponse(
                user.getUserId(),
                user.getEmail(),
                profile == null ? null : profile.getName(),
                profile == null ? null : profile.getSurname(),
                user.getAccountStatus(),
                user.getCreatedAt(),
                user.getLastLogin(),
                summary == null ? 0L : summary.getTotal(),
                summary == null ? null : summary.getLastDay());
    }

    // ---- decisions ----

    /** Lets a pending registration in. Only PENDING accounts can be approved. */
    @Transactional
    public AdminUserResponse approve(Integer adminUserId, Integer targetUserId, String message) {
        UserAuth user = manageable(adminUserId, targetUserId);
        if (user.getAccountStatus() != AccountStatus.PENDING) {
            throw new ValidationException("Only a pending account can be approved; this one is "
                    + user.getAccountStatus().name().toLowerCase(Locale.ROOT) + ".");
        }

        applyStatus(user, AccountStatus.ACTIVE);
        log.info("Administrator {} approved account {} ({})", adminUserId, targetUserId, user.getEmail());

        String appUrl = stripTrailingSlash(properties.getReset().getAppBaseUrl());
        notify(user.getEmail(), "approval",
                () -> emailService.sendAccountApprovedEmail(user.getEmail(), appUrl, message));
        return reload(user);
    }

    /**
     * Suspends access. Allowed from ACTIVE and from PENDING, the latter being how
     * a registration request is turned down without destroying the record.
     */
    @Transactional
    public AdminUserResponse block(Integer adminUserId, Integer targetUserId, String message) {
        UserAuth user = manageable(adminUserId, targetUserId);
        if (user.getAccountStatus() == AccountStatus.BLOCKED) {
            throw new ValidationException("This account is already blocked.");
        }

        applyStatus(user, AccountStatus.BLOCKED);
        log.info("Administrator {} blocked account {} ({})", adminUserId, targetUserId, user.getEmail());

        notify(user.getEmail(), "block",
                () -> emailService.sendAccountBlockedEmail(user.getEmail(), message));
        return reload(user);
    }

    /** Restores a blocked account to normal use. */
    @Transactional
    public AdminUserResponse unblock(Integer adminUserId, Integer targetUserId, String message) {
        UserAuth user = manageable(adminUserId, targetUserId);
        if (user.getAccountStatus() != AccountStatus.BLOCKED) {
            throw new ValidationException("Only a blocked account can be unblocked.");
        }

        applyStatus(user, AccountStatus.ACTIVE);
        log.info("Administrator {} unblocked account {} ({})", adminUserId, targetUserId, user.getEmail());

        String appUrl = stripTrailingSlash(properties.getReset().getAppBaseUrl());
        notify(user.getEmail(), "unblock",
                () -> emailService.sendAccountApprovedEmail(user.getEmail(), appUrl, message));
        return reload(user);
    }

    /**
     * Removes the account and, through the foreign keys declared with
     * {@code ON DELETE CASCADE}, its profile, activities, login history and any
     * pending reset token.
     *
     * <p>The row is flushed before the email goes out so the message is only ever
     * sent about a deletion that really happened; a mail failure at that point is
     * logged, never rolled back, since the data is already gone.
     */
    @Transactional
    public void delete(Integer adminUserId, Integer targetUserId, String message) {
        UserAuth user = manageable(adminUserId, targetUserId);
        String email = user.getEmail();

        userAuthRepository.delete(user);
        userAuthRepository.flush();
        log.info("Administrator {} deleted account {} ({})", adminUserId, targetUserId, email);

        notify(email, "deletion", () -> emailService.sendAccountDeletedEmail(email, message));
    }

    // ---- guards and helpers ----

    /**
     * Loads the target and refuses everything an administrator must not touch:
     * their own account, another administrator, and the built-in system account.
     * Without this a single click could remove the last way into the back office.
     */
    private UserAuth manageable(Integer adminUserId, Integer targetUserId) {
        if (targetUserId == null) {
            throw new ValidationException("A user id is required.");
        }
        if (targetUserId.equals(adminUserId)) {
            throw new ValidationException("You cannot perform this action on your own account.");
        }

        UserAuth user = userAuthRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (user.isSystemAccount() || user.getRole() == Role.ADMIN) {
            throw new ValidationException("Administrator accounts cannot be managed from this list.");
        }
        return user;
    }

    private void applyStatus(UserAuth user, AccountStatus status) {
        user.setStatus(status);
        // A blocked account must not carry a stale lockout that would keep
        // producing "try again later" once it is unblocked.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(LocalDateTime.now());
        userAuthRepository.save(user);
    }

    /**
     * Notifications are a courtesy, not part of the decision: an SMTP outage must
     * not undo an approval the administrator has already been told succeeded.
     */
    private void notify(String email, String what, Runnable send) {
        try {
            send.run();
        } catch (RuntimeException e) {
            log.error("The {} notification to {} could not be sent; the action itself was applied",
                    what, email, e);
        }
    }

    private AdminUserResponse reload(UserAuth user) {
        UserProfile profile = userProfileRepository.findById(user.getUserId()).orElse(null);
        List<ActivityRepository.UserActivitySummary> summaries =
                activityRepository.summariseByUserIds(List.of(user.getUserId()));
        return toResponse(user, profile, summaries.isEmpty() ? null : summaries.get(0));
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
