package it.unicas.chronogram.profile;

import it.unicas.chronogram.common.ApiResponse;
import it.unicas.chronogram.profile.dto.ChangePasswordRequest;
import it.unicas.chronogram.profile.dto.DeleteAccountRequest;
import it.unicas.chronogram.profile.dto.ProfileResponse;
import it.unicas.chronogram.profile.dto.UpdateProfileRequest;
import it.unicas.chronogram.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * JWT-protected self-service endpoints: the account owner's own profile,
 * password, and the deletion of the account itself. The subject is always the
 * authenticated {@link AuthPrincipal}, never an id taken from the path or the
 * body, so these routes cannot be pointed at somebody else's record.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ApiResponse<ProfileResponse> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok("Profile retrieved successfully", profileService.currentProfile(principal.userId()));
    }

    /**
     * POST rather than PUT to match the rest of this API. An {@code email}
     * property in the body is ignored: the address is the login identifier and
     * {@link UpdateProfileRequest} has no field to bind it to.
     */
    @PostMapping("/update")
    public ApiResponse<ProfileResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Valid @RequestBody UpdateProfileRequest request) {
        ProfileResponse updated = profileService.update(principal.userId(), request);
        return ApiResponse.ok("Profile updated successfully", updated);
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthPrincipal principal,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(principal.userId(), request);
        return ApiResponse.ok("Password changed successfully.");
    }

    /**
     * Erases the caller's own account and all the data belonging to it. POST with
     * an optional body rather than DELETE, to match the rest of this API and
     * because the body carries the reasons the user selected.
     *
     * <p>The body is optional at every level - absent, {@code {}}, or an empty
     * list are all accepted: leaving is the user's decision and must not fail over
     * a missing explanation.
     *
     * <p>Nothing is returned but the acknowledgement. The client is expected to
     * drop the token it holds; should it keep using it, every further call answers
     * 401, since the account behind the token no longer exists.
     */
    @PostMapping("/delete-account")
    public ApiResponse<Void> deleteAccount(@AuthenticationPrincipal AuthPrincipal principal,
                                           @Valid @RequestBody(required = false)
                                           DeleteAccountRequest request) {
        profileService.deleteAccount(principal.userId(),
                request == null ? List.of() : request.cleanedReasons());
        return ApiResponse.ok("Your account and all of your data have been permanently deleted.");
    }
}
