package it.unicas.chronogram.auth;

import it.unicas.chronogram.auth.dto.*;
import it.unicas.chronogram.common.ApiResponse;
import it.unicas.chronogram.domain.AccountStatus;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Public authentication endpoints (no JWT required). Paths and JSON shapes are
 * preserved from the legacy Struts API for front-end compatibility.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    /**
     * The message differs by outcome: an auto-approved account can sign in at
     * once, while a pending one must not be sent to the login form only to be
     * turned away there.
     */
    @PostMapping("/register")
    public ApiResponse<String> register(@Valid @RequestBody RegisterRequest request) {
        AccountStatus status = authService.register(request);
        String message = status == AccountStatus.PENDING
                ? "Registration received. An administrator has to approve your account before you can sign in; "
                + "we will email you as soon as that happens."
                : "Registration successful!";
        return ApiResponse.ok(message, status.name());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/request-reset")
    public ApiResponse<Void> requestReset(@Valid @RequestBody ForgotPasswordRequest request) {
        // The reset link is built server-side from the configured canonical URL,
        // never from the caller's Origin header (see PasswordResetService).
        passwordResetService.initiatePasswordReset(request.email());
        // Always return the same message so the existence of an account is not revealed.
        return ApiResponse.ok("If your email address exists in our system, you will receive a password reset link.");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.ok("Password has been successfully reset.");
    }
}
