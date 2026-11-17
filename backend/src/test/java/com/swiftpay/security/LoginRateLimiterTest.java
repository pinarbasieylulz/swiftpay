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

    @Test
    void perIpLimit_blocksCredentialStuffingAcrossManyUsernames() {
        // Each username alone stays under the per-account threshold (5), but the same IP
        // trying 20+ distinct usernames must still trip the per-IP limit.
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 20; i++) {
            limiter.recordFailure("9.9.9.9", "user" + i);
        }
        assertThat(limiter.isBlocked("9.9.9.9", "brand-new-user")).isTrue();
    }

    @Test
    void perIpLimit_doesNotAffectOtherIps() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 20; i++) {
            limiter.recordFailure("9.9.9.9", "user" + i);
        }
        assertThat(limiter.isBlocked("10.10.10.10", "alice")).isFalse();
    }

    @Test
    void recordSuccess_doesNotResetPerIpBudget() {
        // A successful login for one username must not reset the credential-stuffing
        // budget an attacker already built up against other usernames from the same IP.
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 20; i++) {
            limiter.recordFailure("9.9.9.9", "user" + i);
        }
        limiter.recordSuccess("9.9.9.9", "legit-user");
        assertThat(limiter.isBlocked("9.9.9.9", "another-new-user")).isTrue();
    }
}
