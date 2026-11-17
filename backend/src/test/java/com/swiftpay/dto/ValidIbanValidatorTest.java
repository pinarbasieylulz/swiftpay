package com.swiftpay.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidIbanValidatorTest {

    private final ValidIban.Validator validator = new ValidIban.Validator();

    @Test
    void validTrIbanWithCorrectChecksum_isValid() {
        assertThat(validator.isValid("TR180006200000000011110001", null)).isTrue();
    }

    @Test
    void nullOrBlank_isInvalid() {
        assertThat(validator.isValid(null, null)).isFalse();
        assertThat(validator.isValid("   ", null)).isFalse();
    }

    @Test
    void wrongLength_isInvalid() {
        assertThat(validator.isValid("TR18", null)).isFalse();
    }

    @Test
    void nonTrCountryCode_isInvalid() {
        assertThat(validator.isValid("DE89370400440532013000", null)).isFalse();
    }

    @Test
    void lowercase_isInvalid() {
        assertThat(validator.isValid("tr180006200000000011110001", null)).isFalse();
    }

    @Test
    void embeddedSpaces_isInvalid() {
        assertThat(validator.isValid("TR18 0006 2000 0000 0011 1100 01", null)).isFalse();
    }

    @Test
    void invalidChecksum_isInvalid() {
        assertThat(validator.isValid("TR110006200000000011110001", null)).isFalse();
    }
}
