package com.swiftpay.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window brute-force guard for the login endpoint, keyed by client IP + username.
 * In-memory and per-instance: sufficient for a single-node deployment. A multi-instance
 * deployment would need a shared store (e.g. Redis) instead — see README assumptions.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 60_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip, String username) {
        Window w = windows.get(key(ip, username));
        if (w == null) {
            return false;
        }
        return w.count >= MAX_ATTEMPTS && Instant.now().toEpochMilli() - w.windowStart < WINDOW_MS;
    }

    public void recordFailure(String ip, String username) {
        long now = Instant.now().toEpochMilli();
        windows.compute(key(ip, username), (k, w) -> {
            if (w == null || now - w.windowStart >= WINDOW_MS) {
                return new Window(now, 1);
            }
            w.count++;
            return w;
        });
    }

    public void recordSuccess(String ip, String username) {
        windows.remove(key(ip, username));
    }

    private String key(String ip, String username) {
        return ip + "|" + username;
    }

    private static final class Window {
        final long windowStart;
        int count;

        Window(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
