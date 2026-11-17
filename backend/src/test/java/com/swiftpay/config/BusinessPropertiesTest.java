package com.swiftpay.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessPropertiesTest {

    private BusinessProperties validProperties() {
        BusinessProperties props = new BusinessProperties();
        props.getBusiness().setZoneId("Europe/Istanbul");
        props.getLimits().setPerTransfer(new BigDecimal("50000.00"));
        props.getLimits().setDailyTotal(new BigDecimal("100000.00"));
        props.getLimits().setMonthlyTotal(new BigDecimal("500000.00"));
        return props;
    }

    @Test
    void validate_withSaneDefaults_doesNotThrow() {
        BusinessProperties props = validProperties();
        props.validate();
        assertThat(props.getBusiness().getZoneId()).isEqualTo("Europe/Istanbul");
    }

    @Test
    void validate_invalidZoneId_throws() {
        BusinessProperties props = validProperties();
        props.getBusiness().setZoneId("Not/AZone");
        assertThatThrownBy(props::validate);
    }

    @Test
    void validate_negativeLimit_throws() {
        BusinessProperties props = validProperties();
        props.getLimits().setPerTransfer(new BigDecimal("-1"));
        assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_dailyBelowPerTransfer_throws() {
        BusinessProperties props = validProperties();
        props.getLimits().setDailyTotal(new BigDecimal("10.00"));
        assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_monthlyBelowDaily_throws() {
        BusinessProperties props = validProperties();
        props.getLimits().setMonthlyTotal(new BigDecimal("1.00"));
        assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class);
    }
}
