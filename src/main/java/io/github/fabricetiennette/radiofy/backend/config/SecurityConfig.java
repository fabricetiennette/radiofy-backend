package io.github.fabricetiennette.radiofy.backend.config;

import io.github.fabricetiennette.radiofy.backend.auth.jwt.JwtAuthenticationFilter;
import io.github.fabricetiennette.radiofy.backend.auth.security.CustomAccessDeniedHandler;
import io.github.fabricetiennette.radiofy.backend.auth.security.CustomAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint authEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    DaoAuthenticationProvider authProvider(UserDetailsService uds, PasswordEncoder encoder) {
        var p = new DaoAuthenticationProvider(uds);
        p.setPasswordEncoder(encoder);
        return p;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        var cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("*")); // serrer plus tard (domain/app mobile)
        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization")); // utile pour envoyer le Bearer au client
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtFilter,
                                            DaoAuthenticationProvider authProvider) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v1/auth/**",
                                "/api/v1/auth/**",
                                "/swagger-ui/**", "/v3/api-docs/**",
                                "/openapi.yaml", "/api/openapi.yaml", "/api/v1/openapi.yaml",
                                "/stations/**",
                                "/api/actuator/health", "/api/actuator/info"
                        ).permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/v1/radiofy/**").authenticated()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authProvider)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
