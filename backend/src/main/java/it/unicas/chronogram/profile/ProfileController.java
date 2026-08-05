package it.unicas.chronogram.profile;

import it.unicas.chronogram.common.ApiResponse;
import it.unicas.chronogram.profile.dto.ChangePasswordRequest;
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

/**
 * JWT-protected self-service endpoints: the account owner's own profile and
 * password. The subject is always the authenticated {@link AuthPrincipal}, never
 * an id taken from the path or the body, so these routes cannot be pointed at
 * somebody else's record.
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
}
