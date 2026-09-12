/*
 * Copyright 2026 ISA Research Group, Universidad de Sevilla.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.restest.exec;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restest.core.exec.EngineSettings;
import io.restest.core.exec.EngineStatistics;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.Payload;
import io.restest.core.model.HttpMethod;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sends real requests to a stub server standing in for an API, and checks that what the engine
 * reports is what actually happened on the wire.
 */
class OkHttpEngineTest {

    private static WireMockServer api;

    private OkHttpEngine engine;

    @BeforeAll
    static void startTheStubApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
    }

    @AfterAll
    static void stopTheStubApi() {
        api.stop();
    }

    @BeforeEach
    void freshEngine() {
        api.resetAll();
        engine = new OkHttpEngine();
    }

    @AfterEach
    void closeEngine() {
        engine.close();
    }

    @Test
    @DisplayName("a reply is recorded with its status, its headers and its body")
    void a_reply_is_recorded_in_full() {
        api.stubFor(get(urlEqualTo("/widgets")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withHeader("X-Request-Id", "abc-123")
                .withBody("{\"widgets\":[]}")));

        Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/widgets"),
                Requests.get(url("/widgets")));

        assertThat(interaction.isAnswered()).isTrue();
        HttpResponseRecord response = interaction.response().orElseThrow();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.statusLine().reasonPhrase()).contains("OK");
        assertThat(response.statusLine().protocolVersion()).contains("http/1.1");
        assertThat(response.headerValues("X-Request-Id")).containsExactly("abc-123");
        Payload body = response.body().orElseThrow();
        assertThat(new String(body.content(), StandardCharsets.UTF_8))
                .isEqualTo("{\"widgets\":[]}");
        assertThat(body.mediaType()).isEqualTo("application/json");
        assertThat(body.truncated()).isFalse();
        assertThat(interaction.elapsed()).isGreaterThanOrEqualTo(java.time.Duration.ZERO);
    }

    @Test
    @DisplayName("the recorded request is the one that went out, headers the client added included")
    void the_recorded_request_is_the_one_that_went_out() {
        api.stubFor(get(urlEqualTo("/widgets")).willReturn(aResponse().withStatus(200)));
        HttpRequestRecord asked = new HttpRequestRecord(HttpMethod.GET, url("/widgets"),
                List.of(Header.of("Accept", "application/json")), java.util.Optional.empty());

        Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/widgets"), asked);

        HttpRequestRecord sent = interaction.request();
        assertThat(sent.headerValues("Accept")).containsExactly("application/json");
        assertThat(sent.headerValues("User-Agent")).containsExactly("RESTest/2.0");
        assertThat(sent.headerValues("Host")).isNotEmpty();
        assertThat(sent.url()).isEqualTo(url("/widgets"));
    }

    @Test
    @DisplayName("a caller that sets its own user agent keeps it")
    void a_user_agent_the_caller_set_is_left_alone() {
        api.stubFor(get(urlEqualTo("/widgets")).willReturn(aResponse().withStatus(200)));
        HttpRequestRecord asked = new HttpRequestRecord(HttpMethod.GET, url("/widgets"),
                List.of(Header.of("User-Agent", "somebody-else/1.0")), java.util.Optional.empty());

        Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/widgets"), asked);

        assertThat(interaction.request().headerValues("User-Agent"))
                .containsExactly("somebody-else/1.0");
    }

    @Test
    @DisplayName("a body is sent as given, and recorded as sent")
    void a_request_body_is_sent_and_recorded() {
        api.stubFor(post(urlEqualTo("/widgets")).willReturn(aResponse().withStatus(201)));

        Interaction interaction = engine.send(Requests.testCase(HttpMethod.POST, "/widgets"),
                Requests.json(HttpMethod.POST, url("/widgets"), "{\"name\":\"a widget\"}"));

        assertThat(interaction.response().orElseThrow().statusCode()).isEqualTo(201);
        api.verify(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                        urlEqualTo("/widgets"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock
                        .equalToJson("{\"name\":\"a widget\"}")));
        HttpRequestRecord sent = interaction.request();
        assertThat(sent.headerValues("Content-Type"))
                .containsExactly("application/json");
        assertThat(sent.headerValues("Content-Length")).containsExactly("19");
        assertThat(new String(sent.body().orElseThrow().content(), StandardCharsets.UTF_8))
                .isEqualTo("{\"name\":\"a widget\"}");
    }

    @Test
    @DisplayName("a write operation with nothing to say is still sent")
    void a_post_without_a_body_is_still_sent() {
        api.stubFor(post(urlEqualTo("/widgets/1/publish")).willReturn(aResponse().withStatus(202)));

        Interaction interaction = engine.send(Requests.testCase(HttpMethod.POST, "/publish"),
                HttpRequestRecord.of(HttpMethod.POST, url("/widgets/1/publish")));

        assertThat(interaction.response().orElseThrow().statusCode()).isEqualTo(202);
    }

    @Test
    @DisplayName("a reply with no content has no body, rather than an empty one")
    void a_204_has_no_body() {
        api.stubFor(delete(urlEqualTo("/widgets/1")).willReturn(aResponse().withStatus(204)));

        Interaction interaction = engine.send(Requests.testCase(HttpMethod.DELETE, "/widgets/1"),
                HttpRequestRecord.of(HttpMethod.DELETE, url("/widgets/1")));

        HttpResponseRecord response = interaction.response().orElseThrow();
        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
    }

    @Test
    @DisplayName("a redirection is reported as a redirection, not quietly followed")
    void a_redirect_is_reported_rather_than_followed() {
        api.stubFor(get(urlEqualTo("/old")).willReturn(aResponse()
                .withStatus(302)
                .withHeader("Location", "/new")));
        api.stubFor(get(urlEqualTo("/new")).willReturn(aResponse().withStatus(200)));

        Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/old"),
                Requests.get(url("/old")));

        assertThat(interaction.response().orElseThrow().statusCode()).isEqualTo(302);
        api.verify(0, com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlEqualTo("/new")));
    }

    @Test
    @DisplayName("a caller who wants redirections followed can have them")
    void redirects_can_be_switched_on() {
        api.stubFor(get(urlEqualTo("/old")).willReturn(aResponse()
                .withStatus(302)
                .withHeader("Location", "/new")));
        api.stubFor(get(urlEqualTo("/new")).willReturn(aResponse().withStatus(200)));

        try (OkHttpEngine following =
                new OkHttpEngine(EngineSettings.defaults().withFollowRedirects(true))) {
            Interaction interaction = following.send(Requests.testCase(HttpMethod.GET, "/old"),
                    Requests.get(url("/old")));

            assertThat(interaction.response().orElseThrow().statusCode()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("a reply too big to keep is kept in part, and still says how big it was")
    void a_large_reply_is_kept_only_in_part() {
        String large = "x".repeat(4096);
        api.stubFor(get(urlEqualTo("/large")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/plain")
                .withBody(large)));

        try (OkHttpEngine small = new OkHttpEngine(
                EngineSettings.defaults().withMaxRetainedResponseBytes(64))) {
            Interaction interaction = small.send(Requests.testCase(HttpMethod.GET, "/large"),
                    Requests.get(url("/large")));

            Payload body = interaction.response().orElseThrow().body().orElseThrow();
            assertThat(body.size()).isEqualTo(64);
            assertThat(body.truncated()).isTrue();
            assertThat(body.deliveredLength()).isEqualTo(4096);
        }
    }

    @Test
    @DisplayName("a compressed reply is recorded as what it says, not as the compressed bytes")
    void a_compressed_reply_is_recorded_readable() {
        String json = "{\"widgets\":[\"one\",\"two\",\"three\"]}";
        api.stubFor(get(urlEqualTo("/compressed")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(json)));

        Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/compressed"),
                Requests.get(url("/compressed")));

        assertThat(interaction.request().headerValues("Accept-Encoding"))
                .describedAs("the client offers compression, as any client would")
                .containsExactly("gzip");
        assertThat(new String(interaction.response().orElseThrow().body().orElseThrow().content(),
                StandardCharsets.UTF_8))
                .describedAs("what is recorded is the reply the API meant, not its packaging")
                .isEqualTo(json);
    }

    @Test
    @DisplayName("the engine counts what it sent and how long it was doing nothing")
    void statistics_describe_the_run() {
        api.stubFor(get(urlEqualTo("/widgets")).willReturn(aResponse().withStatus(200)));
        assertThat(engine.statistics().requestsSent()).isZero();

        engine.send(Requests.testCase(HttpMethod.GET, "/widgets"), Requests.get(url("/widgets")));
        engine.send(Requests.testCase(HttpMethod.GET, "/widgets"), Requests.get(url("/widgets")));

        EngineStatistics statistics = engine.statistics();
        assertThat(statistics.requestsSent()).isEqualTo(2);
        assertThat(statistics.wallClock()).isPositive();
        assertThat(statistics.peakConcurrency()).isEqualTo(1);
        assertThat(statistics.idleFraction()).isBetween(0.0, 1.0);
        assertThat(statistics.meanResponseTime()).isPositive();
    }

    @Test
    @DisplayName("two engines in one program do not see each other's traffic")
    void two_engines_keep_separate_books() {
        api.stubFor(get(urlEqualTo("/widgets")).willReturn(aResponse().withStatus(200)));

        try (OkHttpEngine other = new OkHttpEngine()) {
            engine.send(Requests.testCase(HttpMethod.GET, "/widgets"),
                    Requests.get(url("/widgets")));
            engine.send(Requests.testCase(HttpMethod.GET, "/widgets"),
                    Requests.get(url("/widgets")));
            other.send(Requests.testCase(HttpMethod.GET, "/widgets"),
                    Requests.get(url("/widgets")));

            assertThat(engine.statistics().requestsSent()).isEqualTo(2);
            assertThat(other.statistics().requestsSent()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a closed engine says so rather than failing obscurely, and closing twice is fine")
    void a_closed_engine_refuses_new_work() {
        engine.close();
        engine.close();

        assertThatIllegalStateException()
                .isThrownBy(() -> engine.sendAsync(Requests.testCase(HttpMethod.GET, "/widgets"),
                        Requests.get(url("/widgets"))))
                .withMessageContaining("closed");
    }

    @Test
    @DisplayName("an address that is not an address is reported, and nothing is sent")
    void an_unusable_address_is_reported_rather_than_thrown() {
        Interaction interaction = engine.send(Requests.testCase(HttpMethod.GET, "/widgets"),
                Requests.get("not even a URL"));

        assertThat(interaction.isAnswered()).isFalse();
        assertThat(interaction.outcome().toString()).contains("could not be assembled");
    }

    private static String url(String path) {
        return api.baseUrl() + path;
    }
}
