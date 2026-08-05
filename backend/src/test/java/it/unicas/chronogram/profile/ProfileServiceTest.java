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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for self-service profile and password management: what is stored
 * for a given edit, which inputs are refused, and the guarantee that the email
 * behind the account is never touched from here.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final int USER_ID = 42;
    private static final String CURRENT_HASH = "$2a$10$storedhash";

    @Mock private UserAuthRepository userAuthRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(userAuthRepository, userProfileRepository, passwordEncoder);
    }

    private UserAuth account() {
        UserAuth account = new UserAuth();
        account.setUserId(USER_ID);
        account.setEmail("ada@unicas.it");
        account.setPasswordHash(CURRENT_HASH);
        account.setMustChangePassword(true);
        return account;
    }

    private UserProfile profile() {
        UserProfile profile = new UserProfile();
        profile.setUserId(USER_ID);
        profile.setName("Ada");
        profile.setSurname("Lovelace");
        profile.setAddress("London");
        profile.setPhone("+39 000");
        profile.setGender("F");
        profile.setBirthday(LocalDate.of(1815, 12, 10));
        return profile;
    }

    private static UpdateProfileRequest edit(String birthday) {
        return new UpdateProfileRequest("Grace", "Hopper", "New York", "+1 555", birthday, "F");
    }

    // ---- read ----

    @Test
    void currentProfileCombinesProfileFieldsWithTheAccountEmail() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile()));
        when(userAuthRepository.findById(USER_ID)).thenReturn(Optional.of(account()));

        ProfileResponse response = profileService.currentProfile(USER_ID);

        assertThat(response.name()).isEqualTo("Ada");
        assertThat(response.surname()).isEqualTo("Lovelace");
        assertThat(response.address()).isEqualTo("London");
        assertThat(response.phone()).isEqualTo("+39 000");
        assertThat(response.gender()).isEqualTo("F");
        assertThat(response.birthday()).isEqualTo(LocalDate.of(1815, 12, 10));
        // The email is the login identifier and lives on the account, not the profile.
        assertThat(response.email()).isEqualTo("ada@unicas.it");
    }

    @Test
    void missingProfileIsReportedAsNotFound() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.currentProfile(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- update ----

    @Test
    void updateStoresTheEditAndReturnsWhatWasStored() {
        UserProfile stored = profile();
        when(userAuthRepository.findById(USER_ID)).thenReturn(Optional.of(account()));
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

        ProfileResponse response = profileService.update(USER_ID, edit("1906-12-09"));

        assertThat(response.name()).isEqualTo("Grace");
        assertThat(response.surname()).isEqualTo("Hopper");
        assertThat(response.address()).isEqualTo("New York");
        assertThat(response.phone()).isEqualTo("+1 555");
        assertThat(response.birthday()).isEqualTo(LocalDate.of(1906, 12, 9));
        // Unchanged and unchangeable through this endpoint.
        assertThat(response.email()).isEqualTo("ada@unicas.it");

        verify(userProfileRepository).save(stored);
        assertThat(stored.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateAlsoAcceptsTheDayFirstFormatTheRegistrationFormPosts() {
        when(userAuthRepository.findById(USER_ID)).thenReturn(Optional.of(account()));
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile()));

        ProfileResponse response = profileService.update(USER_ID, edit("09-12-1906"));

        assertThat(response.birthday()).isEqualTo(LocalDate.of(1906, 12, 9));
    }

    @Test
    void updateRejectsAnUnparseableBirthdayInsteadOfSilentlyDroppingIt() {
        when(userAuthRepository.findById(USER_ID)).thenReturn(Optional.of(account()));
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile()));

        assertThatThrownBy(() -> profileService.update(USER_ID, edit("not-a-date")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("YYYY-MM-DD");

        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void blankOptionalFieldsClearTheStoredValues() {
        UserProfile stored = profile();
        when(userAuthRepository.findById(USER_ID)).thenReturn(Optional.of(account()));
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

        ProfileResponse response = profileService.update(USER_ID,
                new UpdateProfileRequest("  Grace  ", "Hopper", " New York ", "   ", "", "  "));

        assertThat(response.phone()).isNull();
        assertThat(response.gender()).isNull();
        assertThat(response.birthday()).isNull();
        // Mandatory fields are trimmed rather than stored with the padding.
        assertThat(response.name()).isEqualTo("Grace");
        assertThat(response.address()).isEqualTo("New York");
    }

    // ---- password ----

    @Test
    void changePasswordStoresTheNewHashAndClearsTheForcedChangeFlag() {
        UserAuth stored = account();
        when(userAuthRepository.findById(USER_ID)).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("OldPass1!", CURRENT_HASH)).thenReturn(true);
        when(passwordEncoder.matches("NewPass1!", CURRENT_HASH)).thenReturn(false);
        when(passwordEncoder.encode("NewPass1!")).thenReturn("$2a$10$newhash");

        profileService.changePassword(USER_ID, new ChangePasswordRequest("OldPass1!", "NewPass1!"));

        ArgumentCaptor<UserAuth> saved = ArgumentCaptor.forClass(UserAuth.class);
        verify(userAuthRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$10$newhash");
        assertThat(saved.getValue().isMustChangePassword()).isFalse();
        assertThat(saved.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void changePasswordRefusesAWrongCurrentPasswordWithoutTouchingTheAccount() {
        when(userAuthRepository.findById(USER_ID)).thenReturn(Optional.of(account()));
        when(passwordEncoder.matches("wrong", CURRENT_HASH)).thenReturn(false);

        assertThatThrownBy(() -> profileService.changePassword(USER_ID,
                new ChangePasswordRequest("wrong", "NewPass1!")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Current password is incorrect.");

        verify(userAuthRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void changePasswordEnforcesTheStrengthPolicyOnTheNewPassword() {
        when(userAuthRepository.findById(USER_ID)).thenReturn(Optional.of(account()));
        when(passwordEncoder.matches("OldPass1!", CURRENT_HASH)).thenReturn(true);

        assertThatThrownBy(() -> profileService.changePassword(USER_ID,
                new ChangePasswordRequest("OldPass1!", "weakpassword")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("uppercase");

        verify(userAuthRepository, never()).save(any());
    }

    @Test
    void changePasswordRefusesReusingTheCurrentPassword() {
        when(userAuthRepository.findById(USER_ID)).thenReturn(Optional.of(account()));
        when(passwordEncoder.matches("SamePass1!", CURRENT_HASH)).thenReturn(true);

        assertThatThrownBy(() -> profileService.changePassword(USER_ID,
                new ChangePasswordRequest("SamePass1!", "SamePass1!")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must differ");

        verify(userAuthRepository, never()).save(any());
    }
}
