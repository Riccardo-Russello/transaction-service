package it.russello.contocorrente.security;

import it.russello.contocorrente.client.AccountClient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("accountSecurity")
@RequiredArgsConstructor
public class AccountSecurity {
    private final AccountClient accountClient;

    public Long currentPersonId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("Autenticazione JWT richiesta");
        }
        Object claim = jwt.getClaims().get("personId");
        if (claim == null) {
            throw new AuthenticationCredentialsNotFoundException("Persona assente nel JWT");
        }
        try {
            Long personId = Long.valueOf(claim.toString());
            if (personId <= 0) {
                throw new NumberFormatException();
            }
            return personId;
        } catch (NumberFormatException e) {
            throw new AuthenticationCredentialsNotFoundException("personId non valido nel JWT");
        }
    }

    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_admin".equals(a.getAuthority()));
    }

    public boolean isOwner(Long accountId, Authentication authentication) {
        return accountId != null
                && accountId > 0
                && authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt
                && ownsAccount(accountId);
    }

    private boolean ownsAccount(Long id) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !jwt.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Autenticazione JWT richiesta");
        }

        return accountClient.findAccount(id, jwt.getToken().getTokenValue())
                .map(account -> currentPersonId().equals(account.personId()))
                .orElse(false);
    }

}
