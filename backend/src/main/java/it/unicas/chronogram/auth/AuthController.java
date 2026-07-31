package it.unicas.chronogram.auth;

import it.unicas.chronogram.auth.dto.*;
import it.unicas.chronogram.common.ApiResponse;
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

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.ok("Registration successful!");
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/request-reset")
    public ApiResponse<Void> requestReset(@Valid @RequestBody ForgotPasswordRequest request,
                                          @RequestHeader(value = "Origin", required = false) String origin) {
        passwordResetService.initiatePasswordReset(request.email(), origin);
        // Always return the same message so the existence of an account is not revealed.
        return ApiResponse.ok("If your email address exists in our system, you will receive a password reset link.");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.ok("Password has been successfully reset.");
    }
}
