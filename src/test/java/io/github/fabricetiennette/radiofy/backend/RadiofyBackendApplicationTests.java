package io.github.fabricetiennette.radiofy.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Without this the default "local" profile applies, and it demands SMTP_HOST and
// friends from the environment — which a test run has no reason to provide.
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class RadiofyBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
