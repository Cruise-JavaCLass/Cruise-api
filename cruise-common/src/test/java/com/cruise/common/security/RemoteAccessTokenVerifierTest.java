package com.cruise.common.security;

import com.cruise.common.enums.Role;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RemoteAccessTokenVerifierTest {
    private HttpServer server;
    private RemoteAccessTokenVerifier verifier;
    private final AtomicReference<String> body = new AtomicReference<>("{\"success\":true}");
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/access-context", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).contains("\"token\":\"access-token\"");
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        verifier = new RemoteAccessTokenVerifier("http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void readsTheLiveIdentityAndDoesNotCachePositiveResults() {
        body.set("{\"success\":true,\"data\":{\"userId\":9,\"username\":\"activity.mgr\",\"role\":\"ACTIVITY_MANAGER\"}}");
        assertThat(verifier.verify("access-token")).contains(new VerifiedCaller(9L, "activity.mgr", Role.ACTIVITY_MANAGER));
        body.set("{\"success\":true}");
        assertThat(verifier.verify("access-token")).isEmpty();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void networkFailureAndMalformedOrUnknownRoleResponsesDenyAccess() {
        for (String response : new String[]{"not-json", "{}", "{\"success\":true,\"data\":{\"userId\":1,\"username\":\"x\",\"role\":\"ADMIN\"}}"}) {
            body.set(response);
            assertThatThrownBy(() -> verifier.verify("access-token")).isInstanceOf(AccessTokenVerifier.Unavailable.class);
        }
        status.set(500);
        assertThatThrownBy(() -> verifier.verify("access-token")).isInstanceOf(AccessTokenVerifier.Unavailable.class);
        server.stop(0);
        assertThatThrownBy(() -> verifier.verify("access-token")).isInstanceOf(AccessTokenVerifier.Unavailable.class);
    }
}
