package it.unicas.chronogram.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns a JSON 403 body when an authenticated caller lacks the required role
 * (e.g. an ordinary user hitting {@code /api/admin/**}). Without this the
 * container renders its default error page, which the axios layer cannot parse.
 * The message is deliberately generic - it must not confirm what exists behind
 * the endpoint.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.fail("Forbidden: insufficient privileges."));
    }
}
