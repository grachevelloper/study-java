package ru.mirea.storeapi.security;

import java.security.Principal;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.mirea.storeapi.error.AuthenticationFailedException;
import ru.mirea.storeapi.domain.Role;
import ru.mirea.storeapi.domain.UserAccount;

public record RequestUser(
    UUID id,
    String email,
    String displayName,
    boolean passwordChangeRequired,
    Set<Role> roles)
    implements Principal {

  public static RequestUser from(UserAccount user) {
    return new RequestUser(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.isPasswordChangeRequired(),
        Set.copyOf(user.getRoles()));
  }

  public Collection<? extends GrantedAuthority> getAuthorities() {
    return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
  }

  public static RequestUser currentOrNull() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getPrincipal() instanceof RequestUser user
        ? user : null;
  }

  public static RequestUser require() {
    RequestUser user = currentOrNull();
    if (user == null) throw new AuthenticationFailedException();
    return user;
  }

  @Override
  public String getName() {
    return email;
  }
}
