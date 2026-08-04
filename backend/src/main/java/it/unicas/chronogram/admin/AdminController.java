package it.unicas.chronogram.admin;

import it.unicas.chronogram.admin.dto.AdminStatsResponse;
import it.unicas.chronogram.admin.dto.UpdateAdminCredentialsRequest;
import it.unicas.chronogram.common.ApiResponse;
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

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
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
