package ru.mirea.storeapi.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import ru.mirea.storeapi.domain.UserAccount;

@Component
public class LoginAttemptService {
  private static final int MAX_FAILURES = 5;

  public void recordFailure(UserAccount user) {
    int failures = Math.min(user.getFailedLoginAttempts() + 1, MAX_FAILURES + 7);
    user.setFailedLoginAttempts(failures);
    if (failures >= MAX_FAILURES) {
      user.setLockedUntil(
          Instant.now().plus(5L * (failures - MAX_FAILURES + 1), ChronoUnit.MINUTES));
    }
  }

  public void recordSuccess(UserAccount user) {
    user.setFailedLoginAttempts(0);
    user.setLockedUntil(null);
  }

  public boolean isLocked(UserAccount user) {
    return user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
  }
}
