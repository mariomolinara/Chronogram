package it.unicas.chronogram.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.repository.UserAuthRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentCaptor;
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
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        // Gives the support endpoint a recipient; without one it would (correctly)
        // report the feature as unconfigured instead of sending anything.
        registry.add("chronogram.support.email", () -> "support@chronogram.test");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAuthRepository userAuthRepository;

    // No real SMTP server: the account-lifecycle mails are simply swallowed,
    // and the support test stubs createMimeMessage() to inspect what was built.
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

    /**
     * The self-service surface end to end against the real schema: reading and
     * editing one's own profile, and replacing the password with BCrypt and the
     * whole security chain in play. The final re-login is the part that matters -
     * it proves the new hash was actually persisted and the old one no longer works.
     */
    @Test
    void profileCanBeReadAndEditedAndThePasswordReplaced() throws Exception {
        Map<String, Object> register = Map.of(
                "name", "Alan", "surname", "Turing",
                "email", "alan@unicas.it", "password", "OldPass1!",
                "birthday", "23-06-1912", "gender", "M", "address", "Wilmslow");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("ACTIVE"));

        String token = signIn("alan@unicas.it", "OldPass1!");

        // 1. The profile reads back what registration stored, with an ISO birthday
        //    even though the registration form posts dd-MM-yyyy.
        mockMvc.perform(get("/api/profile/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Alan"))
                .andExpect(jsonPath("$.data.email").value("alan@unicas.it"))
                .andExpect(jsonPath("$.data.address").value("Wilmslow"))
                .andExpect(jsonPath("$.data.birthday").value("1912-06-23"));

        // 2. An edit that also tries to change the login address: the email field
        //    is ignored, everything else is applied.
        Map<String, Object> edit = Map.of(
                "name", "Alan M.", "surname", "Turing", "address", "Manchester",
                "phone", "+44 161", "birthday", "1912-06-23", "gender", "M",
                "email", "attacker@evil.example");
        mockMvc.perform(post("/api/profile/update")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Alan M."))
                .andExpect(jsonPath("$.data.address").value("Manchester"))
                .andExpect(jsonPath("$.data.phone").value("+44 161"))
                .andExpect(jsonPath("$.data.email").value("alan@unicas.it"));

        assertThat(userAuthRepository.existsByEmailIgnoreCase("attacker@evil.example")).isFalse();

        // 3. A mandatory field left empty is a 400 in the standard envelope.
        Map<String, Object> invalid = Map.of("name", "Alan", "surname", "Turing", "address", "  ");
        mockMvc.perform(post("/api/profile/update")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 4. Wrong current password -> 400, never 401: a 401 would make the client
        //    tear down a session that is perfectly valid.
        mockMvc.perform(post("/api/profile/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", "WrongPass1!", "newPassword", "NewPass1!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));

        // 5. A new password that does not meet the policy -> 400.
        mockMvc.perform(post("/api/profile/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", "OldPass1!", "newPassword", "weakpassword"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 6. The real change.
        mockMvc.perform(post("/api/profile/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", "OldPass1!", "newPassword", "NewPass1!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 7. The old password is gone, the new one works, and the token issued
        //    before the change is deliberately still accepted (stateless JWT: see
        //    ProfileService.changePassword).
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "alan@unicas.it", "password", "OldPass1!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(signIn("alan@unicas.it", "NewPass1!")).isNotBlank();

        mockMvc.perform(get("/api/profile/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * The support form with SMTP mocked: the message is accepted, addressed to the
     * configured mailbox and attributed to the signed-in user, and the validation
     * rules are enforced before anything is sent.
     */
    @Test
    void supportMessagesAreValidatedAndDelivered() throws Exception {
        // MimeMessageHelper needs a real message to populate; the mocked sender
        // would otherwise hand back null.
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        Map<String, Object> register = Map.of(
                "name", "Barbara", "surname", "Liskov",
                "email", "barbara@unicas.it", "password", "Subst1tution!",
                "birthday", "07-11-1939", "gender", "F", "address", "Los Angeles");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk());

        String token = signIn("barbara@unicas.it", "Subst1tution!");

        mockMvc.perform(post("/api/support/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("subject", "Export fails",
                                        "message", "The CSV button does nothing."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getSubject()).contains("Export fails");
        assertThat(sent.getValue().getAllRecipients()[0].toString())
                .isEqualTo("support@chronogram.test");
        // Replying to the ticket reaches the user who opened it.
        assertThat(sent.getValue().getReplyTo()[0].toString()).isEqualTo("barbara@unicas.it");

        // An empty subject never reaches the mail layer.
        mockMvc.perform(post("/api/support/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("subject", "  ", "message", "Body."))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // And neither does an oversized one.
        mockMvc.perform(post("/api/support/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("subject", "x".repeat(151), "message", "Body."))))
                .andExpect(status().isBadRequest());

        // The endpoint is not public: no token, no relay through our SMTP account.
        mockMvc.perform(post("/api/support/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("subject", "Anonymous", "message", "Body."))))
                .andExpect(status().isUnauthorized());

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    /** Signs in and returns the issued token, failing the test if none was. */
    private String signIn(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
        assertThat(token).isNotBlank();
        return token;
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
