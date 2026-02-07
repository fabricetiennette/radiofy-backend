package io.github.fabricetiennette.radiofy.backend.auth.jwt;


import io.github.fabricetiennette.radiofy.backend.auth.security.JwtAuthenticationException;
import io.github.fabricetiennette.radiofy.backend.auth.security.JwtAuthenticationException.Reason;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UserDetailsService uds;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            // Pas de jeton -> laisser Spring Security gérer (EntryPoint 401 si nécessaire)
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            String subject = jwt.getSubject(token); // peut jeter des exceptions jjwt

            // Déjà authentifié ? alors on ne réauthentifie pas
            if (subject != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails user = uds.loadUserByUsername(subject);

                if (jwt.isTokenValid(token, user.getUsername())) {
                    var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    throw new JwtAuthenticationException("Invalid token", JwtAuthenticationException.Reason.INVALID);
                }
            }

            chain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            throw new JwtAuthenticationException("Token expired", Reason.EXPIRED);
        } catch (MalformedJwtException e) {
            throw new JwtAuthenticationException("Malformed token", Reason.MALFORMED);
        } catch (UnsupportedJwtException e) {
            throw new JwtAuthenticationException("Unsupported token", Reason.UNSUPPORTED);
        } catch (SignatureException e) {
            throw new JwtAuthenticationException("Invalid token signature", Reason.BAD_SIGNATURE);
        } catch (JwtException e) {
            throw new JwtAuthenticationException("Invalid token", Reason.INVALID);
        }
    }
}

