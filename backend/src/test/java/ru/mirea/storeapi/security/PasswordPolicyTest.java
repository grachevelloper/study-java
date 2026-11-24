package ru.mirea.storeapi.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.mirea.storeapi.error.BadRequestException;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsStrongPassword() {
        assertThatCode(() -> policy.validate("Correct-Horse-2026!",
            "operator@example.org")).doesNotThrowAnyException();
    }

    @Test
    void rejectsPasswordContainingLogin() {
        assertThatThrownBy(() -> policy.validate("Operator-Strong-2026!",
            "operator@example.org")).isInstanceOf(BadRequestException.class);
    }
}
