package it.unicas.chronogram.admin;

import it.unicas.chronogram.admin.dto.AdminStatsResponse;
import it.unicas.chronogram.admin.dto.AdminUserActionRequest;
import it.unicas.chronogram.admin.dto.AdminUserPageResponse;
import it.unicas.chronogram.admin.dto.AdminUserResponse;
import it.unicas.chronogram.admin.dto.DeleteUserRequest;
import it.unicas.chronogram.admin.dto.UpdateAdminCredentialsRequest;
import it.unicas.chronogram.common.ApiResponse;
import it.unicas.chronogram.domain.AccountStatus;
import it.unicas.chronogram.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Back-office endpoints. The whole {@code /api/admin/**} tree requires the ADMIN
 * role (see {@code SecurityConfig}); nothing here trusts a client-supplied id.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AdminUserService adminUserService;

    public AdminController(AdminService adminService, AdminUserService adminUserService) {
        this.adminService = adminService;
        this.adminUserService = adminUserService;
    }

    @GetMapping("/stats")
    public ApiResponse<AdminStatsResponse> stats() {
        return ApiResponse.ok("Stats retrieved successfully", adminService.stats());
    }

    @GetMapping(value = "/export/activities.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportActivities() {
        return csv("chronogram-activities.csv", adminService.exportActivitiesCsv());
    }

    @GetMapping(value = "/export/users.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportUsers() {
        return csv("chronogram-users.csv", adminService.exportUsersCsv());
    }

    // ---- participants ----

    /**
     * The participant list. All filters are optional, so the bare URL returns the
     * first page of everyone.
     */
    @GetMapping("/users")
    public ApiResponse<AdminUserPageResponse> users(@RequestParam(required = false) AccountStatus status,
                                                    @RequestParam(required = false) String query,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.ok("Users retrieved successfully",
                adminUserService.list(status, query, page, size));
    }

    /**
     * The acting administrator always comes from the authenticated principal,
     * never from the request, so nobody can act "as" another operator.
     */
    @PostMapping("/users/{userId}/approve")
    public ApiResponse<AdminUserResponse> approve(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable Integer userId,
                                                  @Valid @RequestBody(required = false)
                                                  AdminUserActionRequest request) {
        AdminUserResponse user = adminUserService.approve(principal.userId(), userId, messageOf(request));
        return ApiResponse.ok("Account approved. The user has been notified by email.", user);
    }

    @PostMapping("/users/{userId}/block")
    public ApiResponse<AdminUserResponse> block(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable Integer userId,
                                                @Valid @RequestBody(required = false)
                                                AdminUserActionRequest request) {
        AdminUserResponse user = adminUserService.block(principal.userId(), userId, messageOf(request));
        return ApiResponse.ok("Account blocked. The user has been notified by email.", user);
    }

    @PostMapping("/users/{userId}/unblock")
    public ApiResponse<AdminUserResponse> unblock(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable Integer userId,
                                                  @Valid @RequestBody(required = false)
                                                  AdminUserActionRequest request) {
        AdminUserResponse user = adminUserService.unblock(principal.userId(), userId, messageOf(request));
        return ApiResponse.ok("Account unblocked. The user has been notified by email.", user);
    }

    /**
     * Irreversible, and the only action whose message is mandatory: the user is
     * owed an explanation before every activity they logged disappears. Modelled
     * as POST rather than DELETE because the body carries that message and the
     * rest of this API is POST-based.
     */
    @PostMapping("/users/{userId}/delete")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable Integer userId,
                                    @Valid @RequestBody DeleteUserRequest request) {
        adminUserService.delete(principal.userId(), userId, request.message());
        return ApiResponse.ok("Account deleted. The user has been notified by email.");
    }

    private static String messageOf(AdminUserActionRequest request) {
        return request == null ? null : request.message();
    }

    @PostMapping("/account/credentials")
    public ApiResponse<Void> updateCredentials(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Valid @RequestBody UpdateAdminCredentialsRequest request) {
        boolean emailChanged = adminService.updateOwnCredentials(principal.userId(), request);
        return ApiResponse.ok(emailChanged
                ? "Credentials updated. Please sign in again with your new email."
                : "Credentials updated successfully.");
    }

    /**
     * UTF-8 with a BOM: without it Excel on Windows reads the file as ANSI and
     * mangles accented names, which is the first thing anyone notices in an export.
     */
    private static ResponseEntity<byte[]> csv(String filename, String body) {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, payload, 0, bom.length);
        System.arraycopy(content, 0, payload, bom.length, content.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(payload);
    }
}
