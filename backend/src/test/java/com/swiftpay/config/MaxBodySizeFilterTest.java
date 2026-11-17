package com.swiftpay.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MaxBodySizeFilterTest {

    private final MaxBodySizeFilter filter = new MaxBodySizeFilter(new ObjectMapper().findAndRegisterModules());

    @Test
    void oversizedBody_isRejectedWith413_andChainNeverCalled() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getContentLengthLong()).thenReturn(2_000_000L);
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(413);
        verify(chain, never()).doFilter(any(), any());
        assertThat(writer.toString()).contains("PAYLOAD_TOO_LARGE");
    }

    @Test
    void normalSizedBody_passesThrough() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getContentLengthLong()).thenReturn(100L);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(413);
    }
}
