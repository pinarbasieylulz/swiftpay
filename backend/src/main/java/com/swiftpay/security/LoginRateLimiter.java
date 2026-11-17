package com.swiftpay.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window brute-force guard for the login endpoint. Two independent limits are enforced:
 * (1) per IP+username (stops repeated guessing against one account) and
 * (2) per IP alone, at a higher threshold (stops credential-stuffing a single IP across many
 * different usernames, which the first limit alone would not catch).
 * In-memory and per-instance: sufficient for a single-node deployment. A multi-instance
 * deployment would need a shared store (e.g. Redis) instead — see README assumptions.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS_PER_ACCOUNT = 5;
    private static final int MAX_ATTEMPTS_PER_IP = 20;
    private static final long WINDOW_MS = 60_000;

    private final ConcurrentHashMap<String, Window> perAccountWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> perIpWindows = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip, String username) {
        return isBlocked(perAccountWindows, key(ip, username), MAX_ATTEMPTS_PER_ACCOUNT)
                || isBlocked(perIpWindows, ip, MAX_ATTEMPTS_PER_IP);
    }

    private boolean isBlocked(ConcurrentHashMap<String, Window> windows, String k, int maxAttempts) {
        Window w = windows.get(k);
        if (w == null) {
            return false;
        }
        return w.count >= maxAttempts && Instant.now().toEpochMilli() - w.windowStart < WINDOW_MS;
    }

    public void recordFailure(String ip, String username) {
        record(perAccountWindows, key(ip, username));
        record(perIpWindows, ip);
    }

    private void record(ConcurrentHashMap<String, Window> windows, String k) {
        long now = Instant.now().toEpochMilli();
        windows.compute(k, (key, w) -> {
            if (w == null || now - w.windowStart >= WINDOW_MS) {
                return new Window(now, 1);
            }
            w.count++;
            return w;
        });
    }

    public void recordSuccess(String ip, String username) {
        perAccountWindows.remove(key(ip, username));
        // Intentionally not clearing the per-IP counter on success: a successful login for one
        // username must not reset the credential-stuffing budget an attacker built up against
        // other usernames from the same IP.
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
