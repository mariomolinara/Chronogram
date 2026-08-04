package it.unicas.chronogram.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.admin.dto.AdminStatsResponse;
import it.unicas.chronogram.common.GlobalExceptionHandler;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.security.AuthPrincipal;
import it.unicas.chronogram.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the back-office endpoints: JSON envelope of the dashboard, the
 * download headers of the CSV exports, and that the administrator's id comes
 * from the authenticated principal rather than the request body. Role gating
 * itself lives in the security chain and is covered by SecurityConfigCorsTest.
 */
@WebMvcTest(controllers = AdminController.class)
@Import(GlobalExceptionHandler.class)
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AdminService adminService;
    // Required by the auto-registered JwtAuthenticationFilter (a @Component).
    @MockBean private JwtService jwtService;
    @MockBean private UserAuthRepository userAuthRepository;

    private Authentication admin() {
        AuthPrincipal principal = new AuthPrincipal(1, "admin@example.com", Role.ADMIN);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void statsAreReturnedInsideTheStandardEnvelope() throws Exception {
        when(adminService.stats()).thenReturn(new AdminStatsResponse(
                11, 4, 2, 340, 25, 10, 7,
                List.of(new AdminStatsResponse.DailyPoint(LocalDate.of(2026, 8, 1), 5))));

        mockMvc.perform(get("/api/admin/stats").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(11))
                .andExpect(jsonPath("$.data.activeUsers").value(4))
                .andExpect(jsonPath("$.data.regularUsers").value(2))
                .andExpect(jsonPath("$.data.dailyActivities[0].day").value("2026-08-01"))
                .andExpect(jsonPath("$.data.dailyActivities[0].count").value(5));
    }

    @Test
    void activitiesExportIsServedAsADownloadableCsv() throws Exception {
        when(adminService.exportActivitiesCsv()).thenReturn("activity_id,user_id\r\n9,42\r\n");

        mockMvc.perform(get("/api/admin/export/activities.csv").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"chronogram-activities.csv\""))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                // UTF-8 BOM first, so Excel on Windows does not mangle accents.
                .andExpect(result -> {
                    byte[] body = result.getResponse().getContentAsByteArray();
                    if (body.length < 3 || (body[0] & 0xFF) != 0xEF
                            || (body[1] & 0xFF) != 0xBB || (body[2] & 0xFF) != 0xBF) {
                        throw new AssertionError("CSV response is missing the UTF-8 BOM");
                    }
                });
    }

    @Test
    void usersExportIsServedAsADownloadableCsv() throws Exception {
        when(adminService.exportUsersCsv()).thenReturn("user_id,email\r\n42,ada@example.com\r\n");

        mockMvc.perform(get("/api/admin/export/users.csv").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"chronogram-users.csv\""));
    }

    @Test
    void credentialsUpdateUsesTheAuthenticatedAdministratorId() throws Exception {
        when(adminService.updateOwnCredentials(eq(1), any())).thenReturn(false);
        Map<String, Object> body = Map.of(
                "currentPassword", "initial-password",
                "newPassword", "brand-new-password");

        mockMvc.perform(post("/api/admin/account/credentials").with(authentication(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void credentialsUpdateRejectsAShortNewPassword() throws Exception {
        Map<String, Object> body = Map.of(
                "currentPassword", "initial-password",
                "newPassword", "short");

        mockMvc.perform(post("/api/admin/account/credentials").with(authentication(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
