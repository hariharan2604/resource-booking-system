package com.booking.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the two security-layer error responses (401 for missing/invalid auth,
 * 403 for authenticated-but-forbidden) so they share the same JSON shape as
 * GlobalExceptionHandler's ErrorResponse.
 */
public class RestAuthEntryPoints {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Component
    public static class Unauthorized implements AuthenticationEntryPoint {
        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                              org.springframework.security.core.AuthenticationException authException) throws IOException {
            write(response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "A valid JWT is required to access this resource", request.getRequestURI());
        }
    }

    @Component
    public static class Forbidden implements AccessDeniedHandler {
        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                            org.springframework.security.access.AccessDeniedException accessDeniedException) throws IOException {
            write(response, HttpStatus.FORBIDDEN, "Forbidden",
                    "You do not have permission to perform this action", request.getRequestURI());
        }
    }

    private static void write(HttpServletResponse response, HttpStatus status, String error,
                               String message, String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", path);

        response.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
