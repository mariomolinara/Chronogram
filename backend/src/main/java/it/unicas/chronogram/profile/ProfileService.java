package it.unicas.chronogram.profile;

import it.unicas.chronogram.common.exception.ApiExceptions.ResourceNotFoundException;
import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.domain.UserProfile;
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

/**
 * Self-service account management: reading and editing one's own profile, and
 * changing one's own password.
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

    private final UserAuthRepository userAuthRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(UserAuthRepository userAuthRepository,
                          UserProfileRepository userProfileRepository,
                          PasswordEncoder passwordEncoder) {
        this.userAuthRepository = userAuthRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
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

    // ---- helpers ----

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
