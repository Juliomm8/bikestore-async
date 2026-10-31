package com.bikestore.asyncapp.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProcessProfileValidatorTest {
    @Test
    void allowsEachSingleProcessMode() {
        assertThatCode(() -> ProcessProfileValidator.validate("api")).doesNotThrowAnyException();
        assertThatCode(() -> ProcessProfileValidator.validate("payment", "evaluation"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProcessProfileValidator.validate("email")).doesNotThrowAnyException();
        assertThatCode(() -> ProcessProfileValidator.validate("integrated")).doesNotThrowAnyException();
        assertThatCode(() -> ProcessProfileValidator.validate()).doesNotThrowAnyException();
    }

    @Test
    void rejectsMoreThanOneProcessMode() {
        assertThatThrownBy(() -> ProcessProfileValidator.validate("api", "payment"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Activa un solo modo");
    }

    @Test
    void evaluationProfileRequiresPaymentMode() {
        assertThatThrownBy(() -> ProcessProfileValidator.validate("evaluation", "email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("solo se puede combinar con payment");
    }
}
