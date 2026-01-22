package io.github.fabricetiennette.radiofy.backend.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         org.springframework.security.core.AuthenticationException ex) throws IOException {

        String message = "Invalid or missing authentication token";
        if (ex != null && ex.getMessage() != null && !ex.getMessage().isBlank()) {
            message = ex.getMessage();
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        var body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", 401,
                "error", "Unauthorized",
                "message", message,
                "path", request.getRequestURI()
        );
        mapper.writeValue(response.getOutputStream(), body);
    }
}
