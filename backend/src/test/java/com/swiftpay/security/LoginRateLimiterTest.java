package com.swiftpay.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    @Test
    void notBlocked_beforeAnyFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        assertThat(limiter.isBlocked("1.2.3.4", "alice")).isFalse();
    }

    @Test
    void blocked_afterFiveFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("1.2.3.4", "alice");
        }
        assertThat(limiter.isBlocked("1.2.3.4", "alice")).isTrue();
    }

    @Test
    void notBlocked_withFewerThanFiveFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("1.2.3.4", "alice");
        }
        assertThat(limiter.isBlocked("1.2.3.4", "alice")).isFalse();
    }

    @Test
    void differentKeys_areTrackedIndependently() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("1.2.3.4", "alice");
        }
        assertThat(limiter.isBlocked("1.2.3.4", "bob")).isFalse();
        assertThat(limiter.isBlocked("5.6.7.8", "alice")).isFalse();
    }

    @Test
    void recordSuccess_clearsFailureCount() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("1.2.3.4", "alice");
        }
        limiter.recordSuccess("1.2.3.4", "alice");
        assertThat(limiter.isBlocked("1.2.3.4", "alice")).isFalse();
    }
}
