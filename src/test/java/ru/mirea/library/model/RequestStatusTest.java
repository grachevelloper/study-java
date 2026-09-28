package ru.mirea.library.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RequestStatusTest {
    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void allowsOnlyDeclaredTransitions(RequestStatus from, RequestStatus to) {
        assertTrue(from.canTransitionTo(to));
    }

    @ParameterizedTest
    @MethodSource("forbiddenTransitions")
    void rejectsUndeclaredTransitions(RequestStatus from, RequestStatus to) {
        assertFalse(from.canTransitionTo(to));
    }

    @Test
    void terminalStatusesHaveNoOutgoingTransitions() {
        assertAll(
            () -> assertTrue(RequestStatus.RETURNED.allowedTargets().isEmpty()),
            () -> assertTrue(RequestStatus.REJECTED.allowedTargets().isEmpty()),
            () -> assertTrue(RequestStatus.CANCELLED.allowedTargets().isEmpty())
        );
    }

    private static Stream<Arguments> allowedTransitions() {
        return Stream.of(
            Arguments.of(RequestStatus.CREATED, RequestStatus.APPROVED),
            Arguments.of(RequestStatus.CREATED, RequestStatus.REJECTED),
            Arguments.of(RequestStatus.CREATED, RequestStatus.CANCELLED),
            Arguments.of(RequestStatus.APPROVED, RequestStatus.ISSUED),
            Arguments.of(RequestStatus.APPROVED, RequestStatus.CANCELLED),
            Arguments.of(RequestStatus.ISSUED, RequestStatus.RETURNED)
        );
    }

    private static Stream<Arguments> forbiddenTransitions() {
        return Stream.of(RequestStatus.values())
            .flatMap(from -> Stream.of(RequestStatus.values())
                .filter(to -> !from.allowedTargets().contains(to))
                .map(to -> Arguments.of(from, to)));
    }
}
