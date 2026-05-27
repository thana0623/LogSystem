package com.logsys.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RequestIdInterceptor implements HandlerInterceptor {

    private static final String HEADER = "X-Request-Id";
    private static final int MAX_LENGTH = 128;
    private static final Pattern VALID_FORMAT = Pattern.compile("^[a-zA-Z0-9\\-]{1,128}$");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank() || !VALID_FORMAT.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }
        response.setHeader(HEADER, requestId);
        return true;
    }
}
