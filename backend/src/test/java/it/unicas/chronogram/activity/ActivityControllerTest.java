package it.unicas.chronogram.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.activity.dto.ActivityResponse;
import it.unicas.chronogram.common.GlobalExceptionHandler;
import it.unicas.chronogram.common.exception.ApiExceptions.ResourceNotFoundException;
import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.security.AuthPrincipal;
import it.unicas.chronogram.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the activity controller. The authenticated {@link AuthPrincipal}
 * is injected via spring-security-test's {@code authentication()} post-processor,
 * exercising the {@code @AuthenticationPrincipal} binding without a real JWT.
 */
@WebMvcTest(controllers = ActivityController.class)
@Import(GlobalExceptionHandler.class)
class ActivityControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ActivityService activityService;
    // Required by the auto-registered JwtAuthenticationFilter (a @Component).
    @MockBean private JwtService jwtService;
    @MockBean private UserAuthRepository userAuthRepository;

    private Authentication principal() {
        AuthPrincipal p = new AuthPrincipal(55, "ada@example.com");
        return new UsernamePasswordAuthenticationToken(p, null, List.of());
    }

    private ActivityResponse sampleResponse() {
        return new ActivityResponse(
                100, LocalDate.of(2026, 7, 31), 60, 2, "Office", "12.50",
                55, 3, null, null, "Work", "Working", true, false);
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void createUsesPrincipalUserId() throws Exception {
        when(activityService.create(eq(55), any())).thenReturn(sampleResponse());
        Map<String, Object> body = Map.of("activityTypeId", 3, "durationMins", 60, "pleasantness", 2);

        mockMvc.perform(post("/api/activities/create").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activityId").value(100))
                .andExpect(jsonPath("$.data.activityTypeName").value("Work"));

        verify(activityService).create(eq(55), any());
    }

    @Test
    void createRejectsMissingActivityTypeWith400() throws Exception {
        Map<String, Object> body = Map.of("durationMins", 60); // no activityTypeId

        mockMvc.perform(post("/api/activities/create").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createRejectsOutOfRangePleasantnessWith400() throws Exception {
        Map<String, Object> body = Map.of("activityTypeId", 3, "pleasantness", 9);

        mockMvc.perform(post("/api/activities/create").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMapsNotFoundTo404() throws Exception {
        when(activityService.update(eq(55), any()))
                .thenThrow(new ResourceNotFoundException("Activity not found or access denied."));
        Map<String, Object> body = Map.of("activityId", 999, "durationMins", 30);

        mockMvc.perform(post("/api/activities/update").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deleteReturnsOk() throws Exception {
        Map<String, Object> body = Map.of("activityId", 100);

        mockMvc.perform(post("/api/activities/delete").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(activityService).delete(55, 100);
    }

    @Test
    void listReturnsActivitiesForPrincipal() throws Exception {
        when(activityService.listByDate(eq(55), any())).thenReturn(List.of(sampleResponse()));
        Map<String, Object> body = Map.of("activityDate", "2026-07-31");

        mockMvc.perform(post("/api/activities/list").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].activityId").value(100));
    }

    @Test
    void listMapsInvalidDateTo400() throws Exception {
        when(activityService.listByDate(eq(55), eq("31-07-2026")))
                .thenThrow(new ValidationException("Invalid date format. Expected format: YYYY-MM-DD."));
        Map<String, Object> body = Map.of("activityDate", "31-07-2026");

        mockMvc.perform(post("/api/activities/list").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointReturns401WithoutAuthentication() throws Exception {
        Map<String, Object> body = Map.of("activityTypeId", 3);

        mockMvc.perform(post("/api/activities/create").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }
}
