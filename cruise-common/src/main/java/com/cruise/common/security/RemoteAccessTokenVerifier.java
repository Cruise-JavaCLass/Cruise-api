package com.cruise.common.security;

import com.cruise.common.enums.Role;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Bounded, uncached introspection: logout, deletion and role changes take effect per request. */
public final class RemoteAccessTokenVerifier implements AccessTokenVerifier {
    private final RestClient client;
    private final URI endpoint;

    public RemoteAccessTokenVerifier(String authBaseUrl) {
        this.endpoint = URI.create(authBaseUrl.replaceAll("/+$", "") + "/api/auth/access-context");
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Optional<VerifiedCaller> verify(String token) {
        try {
            Map<?, ?> response = client.post().uri(endpoint).body(Map.of("token", token))
                    .retrieve().body(Map.class);
            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                throw new IllegalStateException("Invalid identity service response");
            }
            Object data = response.get("data");
            if (data == null) return Optional.empty();
            if (!(data instanceof Map<?, ?> caller)) throw new IllegalStateException("Invalid identity data");
            return Optional.of(new VerifiedCaller(((Number) caller.get("userId")).longValue(),
                    (String) caller.get("username"), Role.valueOf((String) caller.get("role"))));
        } catch (RestClientException | IllegalArgumentException | IllegalStateException | ClassCastException
                 | NullPointerException ex) {
            throw new AccessTokenVerifier.Unavailable(ex);
        }
    }
}
