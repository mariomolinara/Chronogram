package it.unicas.chronogram.security;

import io.jsonwebtoken.JwtException;
import it.unicas.chronogram.domain.AccountStatus;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.repository.UserAuthRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the filter populates the SecurityContext only for a valid Bearer
 * token belonging to an active user, and always continues the chain.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UserAuthRepository userAuthRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(jwtService, userAuthRepository);
    }

    private UserAuth activeUser() {
        UserAuth user = new UserAuth();
        user.setUserId(7);
        user.setEmail("ada@example.com");
        user.setStatus(AccountStatus.ACTIVE);
        return user;
    }

    @Test
    void authenticatesValidTokenForActiveUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.extractEmail("good-token")).thenReturn("ada@example.com");
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(activeUser()));

        filter().doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(AuthPrincipal.class);
        assertThat(((AuthPrincipal) auth.getPrincipal()).userId()).isEqualTo(7);
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateWhenNoHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void ignoresInvalidTokenButStillContinuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.extractEmail("bad-token")).thenThrow(new JwtException("bad"));

        filter().doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateInactiveUser() throws Exception {
        UserAuth inactive = activeUser();
        inactive.setStatus(AccountStatus.BLOCKED);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.extractEmail("good-token")).thenReturn("ada@example.com");
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(inactive));

        filter().doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
