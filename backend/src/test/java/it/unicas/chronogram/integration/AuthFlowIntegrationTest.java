package it.unicas.chronogram.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.repository.UserAuthRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end register + login + protected-CRUD flow against a real MySQL started
 * by Testcontainers (Flyway migrations run against it). Skipped automatically
 * when no Docker daemon is reachable, so the build stays green in environments
 * without Docker (e.g. plain CI without a socket).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EnabledIf("dockerAvailable")
class AuthFlowIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("chronogram");

    static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // A 32+ byte HMAC secret so JwtService can start.
        registry.add("chronogram.security.jwt.secret",
                () -> "integration-test-secret-key-that-is-long-enough-32b");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAuthRepository userAuthRepository;

    // Mail is not exercised here; avoid needing a real SMTP server.
    @MockBean private JavaMailSender mailSender;

    @Test
    void registerThenLoginThenCreateAndListActivity() throws Exception {
        // 1. Register from the trusted domain -> auto-approved (ACTIVE)
        Map<String, Object> register = Map.of(
                "name", "Ada", "surname", "Lovelace",
                "email", "ada@unicas.it", "password", "password123",
                "birthday", "10-12-1815", "gender", "F", "address", "London");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ACTIVE"));

        assertThat(userAuthRepository.existsByEmailIgnoreCase("ada@unicas.it")).isTrue();

        // 2. Duplicate registration -> 409
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isConflict());

        // 3. Login -> token
        Map<String, Object> login = Map.of("email", "ada@unicas.it", "password", "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginJson.get("token").asText();
        assertThat(token).isNotBlank();

        // 4. Protected activity endpoint without token -> 401
        mockMvc.perform(post("/api/activities/list")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        // 5. List activity types (seeded by Flyway V2) to obtain a valid type id
        MvcResult typesResult = mockMvc.perform(post("/api/activities/types")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        JsonNode types = objectMapper.readTree(typesResult.getResponse().getContentAsString()).get("data");
        assertThat(types.isArray()).isTrue();
        assertThat(types.size()).isGreaterThan(0);
        int activityTypeId = types.get(0).get("activityTypeId").asInt();

        // 6. Create an activity with the JWT
        Map<String, Object> create = Map.of(
                "activityTypeId", activityTypeId, "durationMins", 45,
                "pleasantness", 2, "location", "Home", "costEuro", "9.99");
        MvcResult createResult = mockMvc.perform(post("/api/activities/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durationMins").value(45))
                .andExpect(jsonPath("$.data.costEuro").value("9.99"))
                .andReturn();
        int activityId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("activityId").asInt();

        // 7. List today's activities -> contains the created one
        mockMvc.perform(post("/api/activities/list")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].activityId").value(activityId));

        // 8. Delete it
        mockMvc.perform(post("/api/activities/delete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("activityId", activityId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void registrationFromUntrustedDomainStaysPendingAndCannotLogIn() throws Exception {
        Map<String, Object> register = Map.of(
                "name", "Grace", "surname", "Hopper",
                "email", "grace@example.com", "password", "password123",
                "birthday", "09-12-1906", "gender", "F", "address", "New York");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("PENDING"));

        // Correct password, but the account has not been approved yet: the login
        // is refused with success=false and no token is issued.
        Map<String, Object> login = Map.of("email", "grace@example.com", "password", "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.token").isEmpty());
    }
}
