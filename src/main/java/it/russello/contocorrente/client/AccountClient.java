package it.russello.contocorrente.client;

import it.russello.contocorrente.dto.AccountResponse;
import it.russello.contocorrente.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccountClient {
    private final RestClient restClient;

    @Value("${clients.account.base-url}")
    private String accountBaseUrl;

    public Optional<AccountResponse> findAccount(Long id, String token) {
        try {
            AccountResponse account = restClient.get()
                    .uri(accountBaseUrl + "/api/account/{id}", id)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(AccountResponse.class);
            if (account == null || account.personId() == null) {
                throw upstreamUnavailable();
            }
            return Optional.of(account);
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw upstreamUnavailable();
        }
    }

    private ApiException upstreamUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "UPSTREAM_UNAVAILABLE",
                "Servizio remoto non disponibile");
    }
}
