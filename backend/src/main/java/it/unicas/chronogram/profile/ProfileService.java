package it.unicas.chronogram.profile;

import it.unicas.chronogram.common.exception.ApiExceptions.ResourceNotFoundException;
import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.domain.UserProfile;
import it.unicas.chronogram.mail.EmailService;
import it.unicas.chronogram.profile.dto.ChangePasswordRequest;
import it.unicas.chronogram.profile.dto.ProfileResponse;
import it.unicas.chronogram.profile.dto.UpdateProfileRequest;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.repository.UserProfileRepository;
import it.unicas.chronogram.security.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Self-service account management: reading and editing one's own profile,
 * changing one's own password, and deleting one's own account.
 *
 * <p>Every method takes the user id resolved from the JWT by the caller and uses
 * it as the sole key, so there is no request parameter through which one account
 * could reach another's data.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    /** The format the profile endpoints speak, in and out. */
    private static final DateTimeFormatter ISO_BIRTHDAY = DateTimeFormatter.ISO_LOCAL_DATE;
    /**
     * Also accepted on input: the registration form and the Android app post
     * birthdays this way, and a profile screen that reuses that widget would
     * otherwise fail on a date the same user was allowed to enter at sign-up.
     */
    private static final DateTimeFormatter LEGACY_BIRTHDAY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /** How much of a single reason reaches the log, in characters. */
    private static final int MAX_LOGGED_REASON_LENGTH = 120;

    private final UserAuthRepository userAuthRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public ProfileService(UserAuthRepository userAuthRepository,
                          UserProfileRepository userProfileRepository,
                          PasswordEncoder passwordEncoder,
                          EmailService emailService) {
        this.userAuthRepository = userAuthRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public ProfileResponse currentProfile(Integer userId) {
        return toResponse(requireProfile(userId), requireAccount(userId));
    }

    /**
     * Applies an edit to the caller's own profile and returns the stored result,
     * so the client renders what the server actually kept rather than what it
     * hoped to send.
     */
    @Transactional
    public ProfileResponse update(Integer userId, UpdateProfileRequest request) {
        UserAuth account = requireAccount(userId);
        UserProfile profile = requireProfile(userId);

        profile.setName(request.name().trim());
        profile.setSurname(request.surname().trim());
        profile.setAddress(request.address().trim());
        profile.setPhone(trimToNull(request.phone()));
        profile.setGender(trimToNull(request.gender()));
        profile.setBirthday(parseBirthday(request.birthday()));
        profile.setUpdatedAt(LocalDateTime.now());
        userProfileRepository.save(profile);

        log.info("Profile updated for user_id={}", userId);
        return toResponse(profile, account);
    }

    /**
     * Replaces the caller's password after proving they know the current one.
     *
     * <p>Tokens already issued to this account are deliberately left valid,
     * including the one used to make this very call. The JWT setup is stateless -
     * the filter re-reads the account on every request but only to check that it
     * is still enabled, and nothing in the token or in the schema records which
     * password generation issued it - so "sign the other devices out" cannot be
     * expressed without a new claim plus a column to compare it against. Doing it
     * halfway (invalidating the caller's own session) would only log the user out
     * of the screen they just used, which is the one session known to be in the
     * legitimate owner's hands. An account believed to be compromised is handled
     * by the administrator blocking it, which does revoke every token at once.
     */
    @Transactional
    public void changePassword(Integer userId, ChangePasswordRequest request) {
        UserAuth account = requireAccount(userId);

        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            // 400, not 401: the caller's session is perfectly valid, it is the
            // typed-in password that is wrong. A 401 here would make the client's
            // interceptor tear the session down over a typo.
            log.warn("Password change refused for user_id={}: current password does not match", userId);
            throw new ValidationException("Current password is incorrect.");
        }

        PasswordPolicy.validate(request.newPassword());

        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new ValidationException("The new password must differ from the current one.");
        }

        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // The account no longer uses a password it was handed, so any forced-change
        // flag is satisfied - same rule the reset flow applies.
        account.setMustChangePassword(false);
        account.setUpdatedAt(LocalDateTime.now());
        userAuthRepository.save(account);

        log.info("Password changed for user_id={}", userId);
    }

    /**
     * Deletes the caller's own account and everything it owns: the profile, every
     * activity logged, the login history and any pending reset token.
     *
     * <p>The removal itself is a single {@code DELETE} on {@code user_auth}. Every
     * table that points at it does so with {@code ON DELETE CASCADE} (migrations
     * V1 and V3), which is the same mechanism the administrator-side deletion in
     * {@code AdminUserService.delete} relies on - one way of erasing an account,
     * not two that could drift apart. No schema change was needed, and no row can
     * be left orphaned: the database refuses to keep a child of a deleted parent.
     *
     * <p>Nothing survives except a log line with the reasons the user selected,
     * which is the point of asking for them - product feedback, not a record of
     * the person. They are sanitised first: they are free-form client input and
     * must not be able to forge extra log lines.
     *
     * <p>No password is asked for. The client already requires an explicit
     * confirmation, and a token in hand is the same proof of identity that would
     * be needed to change the password before deleting anyway.
     *
     * <p>The token used to make this call keeps validating, but it now names an
     * account that no longer exists, so {@code JwtAuthenticationFilter} finds no
     * row, leaves the request unauthenticated and every further call answers 401.
     *
     * @param userId  the caller, taken from the JWT
     * @param reasons optional, already-cleaned reasons for leaving
     * @throws ValidationException       (400) if the account is one the installation
     *                                  cannot afford to lose - see {@link #ensureDeletable}
     * @throws ResourceNotFoundException (404) if the account is already gone
     */
    @Transactional
    public void deleteAccount(Integer userId, List<String> reasons) {
        UserAuth account = requireAccount(userId);
        ensureDeletable(account);

        // Read before the row goes: afterwards there is no address left to write to.
        String email = account.getEmail();

        log.info("Account deletion requested by user_id={}; reasons: {}", userId, formatReasons(reasons));

        userAuthRepository.delete(account);
        // Flushed here so the confirmation below is only ever sent about a deletion
        // the database has actually accepted.
        userAuthRepository.flush();

        log.info("Account user_id={} deleted at its owner's request", userId);

        // A courtesy attached to a change already made: an SMTP outage must not
        // resurrect an account the user has been told is gone. Same rule as the
        // account-lifecycle notifications in AdminUserService.
        try {
            emailService.sendAccountSelfDeletedEmail(email);
        } catch (RuntimeException e) {
            log.error("The deletion confirmation to {} could not be sent; the account was deleted anyway",
                    email, e);
        }
    }

    // ---- helpers ----

    /**
     * Refuses the two deletions that would cost the installation its back office.
     *
     * <p>The built-in system account is the one {@code AdminAccountInitializer}
     * provisions from configuration: deleting it would take its history with it
     * and leave the next boot re-creating an admin with the password still sitting
     * in {@code .env}. The last remaining administrator is refused for the same
     * reason {@code AdminUserService} will not let one administrator delete
     * another - somebody has to be able to get back in. An administrator who is
     * not the last one may leave freely.
     *
     * <p>400 rather than 403: nothing is wrong with the caller's session, the
     * account simply is not one that can be deleted, and the message says which.
     */
    private void ensureDeletable(UserAuth account) {
        if (account.isSystemAccount()) {
            log.warn("Self-deletion refused for user_id={}: built-in administrator account",
                    account.getUserId());
            throw new ValidationException("The built-in administrator account cannot be deleted.");
        }
        if (account.getRole() == Role.ADMIN && userAuthRepository.countByRole(Role.ADMIN) <= 1) {
            log.warn("Self-deletion refused for user_id={}: last administrator", account.getUserId());
            throw new ValidationException("This is the only administrator account and cannot be deleted. "
                    + "Appoint another administrator first.");
        }
    }

    /**
     * Renders the selected reasons for the log: control characters (CR/LF above
     * all, which would let a crafted reason fake a log entry) collapsed to spaces
     * and each reason capped.
     */
    private static String formatReasons(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "none given";
        }
        StringBuilder rendered = new StringBuilder();
        for (String reason : reasons) {
            String safe = reason.replaceAll("\\p{Cntrl}+", " ").trim();
            if (safe.length() > MAX_LOGGED_REASON_LENGTH) {
                safe = safe.substring(0, MAX_LOGGED_REASON_LENGTH) + "...";
            }
            if (!rendered.isEmpty()) {
                rendered.append(" | ");
            }
            rendered.append(safe);
        }
        return rendered.toString();
    }

    private UserAuth requireAccount(Integer userId) {
        return userAuthRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));
    }

    private UserProfile requireProfile(Integer userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found."));
    }

    private static ProfileResponse toResponse(UserProfile profile, UserAuth account) {
        return new ProfileResponse(
                profile.getName(),
                profile.getSurname(),
                profile.getAddress(),
                profile.getPhone(),
                account.getEmail(),
                profile.getBirthday(),
                profile.getGender());
    }

    /**
     * Unlike registration - which quietly stores {@code null} for an unparseable
     * date - an edit rejects one: the user is looking at the form and can fix it,
     * and silently dropping the value they just typed would be worse than an error.
     */
    private static LocalDate parseBirthday(String value) {
        String birthday = trimToNull(value);
        if (birthday == null) {
            return null;
        }
        try {
            return LocalDate.parse(birthday, ISO_BIRTHDAY);
        } catch (DateTimeParseException ignored) {
            // fall through to the legacy format below
        }
        try {
            return LocalDate.parse(birthday, LEGACY_BIRTHDAY);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Invalid birthday format. Expected format: YYYY-MM-DD.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
