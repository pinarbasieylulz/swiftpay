package com.swiftpay.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.dto.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Rejects obviously oversized request bodies (by Content-Length) before they reach
 * controller/validation code, returning the standard error model instead of letting
 * the servlet container reset the connection.
 */
@Component
public class MaxBodySizeFilter extends HttpFilter {

    private static final long MAX_BODY_BYTES = 1_000_000; // 1 MB — generous for this API's small JSON payloads
    private final ObjectMapper objectMapper;

    public MaxBodySizeFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            String traceId = UUID.randomUUID().toString();
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    new ApiErrorResponse("PAYLOAD_TOO_LARGE", "Request body exceeds the maximum allowed size", traceId));
            return;
        }
        chain.doFilter(request, response);
    }
}
