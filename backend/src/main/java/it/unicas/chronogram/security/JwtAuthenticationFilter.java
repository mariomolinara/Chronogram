package it.unicas.chronogram.security;

import io.jsonwebtoken.JwtException;
import it.unicas.chronogram.repository.UserAuthRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates the {@code Authorization: Bearer <jwt>} header on each request and,
 * when valid, populates the {@link SecurityContextHolder} with an
 * {@link AuthPrincipal}. Requests without a token simply proceed unauthenticated
 * and are rejected later by the authorization rules if the endpoint is protected.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserAuthRepository userAuthRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserAuthRepository userAuthRepository) {
        this.jwtService = jwtService;
        this.userAuthRepository = userAuthRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                String email = jwtService.extractEmail(token);
                userAuthRepository.findByEmailIgnoreCase(email)
                        .filter(u -> u.isActive())
                        .ifPresent(user -> authenticate(request, user.getUserId(), user.getEmail()));
            } catch (JwtException ex) {
                log.debug("Rejected invalid JWT: {}", ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, Integer userId, String email) {
        AuthPrincipal principal = new AuthPrincipal(userId, email);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
