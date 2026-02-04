package io.github.fabricetiennette.radiofy.backend.AuthTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringBootTest
class AuthControllerRegisterTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("radiofy_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        // DB
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Flyway (if needed explicitly)
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;

    @MockitoBean
    io.github.fabricetiennette.radiofy.backend.auth.otp.services.SmtpEmailSender smtpEmailSender;

    @Test
    void register_should_create_user_and_send_otp() throws Exception {
        var payload = """
                {"email":"test_%s@radiofy.io","password":"Test1234"}
                """.formatted(System.currentTimeMillis());

        mvc.perform(post("/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                // Adjust if your controller returns 200 instead of 201
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
        // If you return AuthResponse tokens, add:
        // .andExpect(jsonPath("$.accessToken").exists())
        // .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void register_should_return_409_when_email_already_exists() throws Exception {
        String email = "dupe_%s@radiofy.io".formatted(System.currentTimeMillis());
        String payload = """
                {"email":"%s","password":"Test1234"}
                """.formatted(email);

        // First time OK
        mvc.perform(post("/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is2xxSuccessful());

        // Second time should conflict
        mvc.perform(post("/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }
}
