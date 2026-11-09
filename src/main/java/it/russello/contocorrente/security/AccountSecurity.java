package it.russello.contocorrente.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import it.russello.contocorrente.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component("accountSecurity")
@RequiredArgsConstructor
public class AccountSecurity {
    private final RestClient restClient;

    @Value("${clients.account.base-url}")
    private String accountBaseUrl;

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

        try {
            Map<?, ?> account = restClient.get()
                    .uri(accountBaseUrl + "/api/account/{id}", id)
                    .headers(headers -> headers.setBearerAuth(jwt.getToken().getTokenValue()))
                    .retrieve()
                    .body(Map.class);

            if (account != null && account.get("personId") instanceof Number ownerId) {
                return currentPersonId().equals(ownerId.longValue());
            }
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.NotFound ex) {
            return false;
        } catch (RestClientException ex) {
            throw upstreamUnavailable();
        }
        throw upstreamUnavailable();
    }

    private ApiException upstreamUnavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "UPSTREAM_UNAVAILABLE",
                "Servizio remoto non disponibile");
    }

}
