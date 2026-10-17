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
package io.restest.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restest.cli.ARun.codesIn;
import static io.restest.cli.ARun.operationsWith;
import static io.restest.cli.ARun.report;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restest.core.json.JsonValue;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A reply the document declares in a way RESTest could not read is not reported as one the
 * document leaves out, from the document to the report.
 *
 * <p>The stand-in answers 404 to everything. Its document declares a 404 for one operation by
 * pointing at a reply it never defines, which RESTest cannot read and says so before the run; and
 * it declares no 404 at all for the other. Only the second is a document that leaves the 404 out.
 */
class AReplyUnreadIsNotUndeclaredTest {

    private static final String DOCUMENT = """
            openapi: 3.0.3
            info:
              title: Night animals
              version: "1.0"
            paths:
              /owls:
                get:
                  operationId: listOwls
                  responses:
                    "200":
                      description: every owl
                    "404":
                      $ref: '#/components/responses/NoSuchThing'
              /bats:
                get:
                  operationId: listBats
                  responses:
                    "200":
                      description: every bat
            components:
              responses: {}
            """;

    private static WireMockServer api;

    @BeforeAll
    static void startTheApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        api.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(404)));
    }

    @AfterAll
    static void stopTheApi() {
        api.stop();
    }

    @Test
    @DisplayName("a 404 the document declares in a way RESTest could not read is left alone, and a "
            + "404 it does not declare at all is reported")
    void an_unread_reply_is_not_an_undeclared_one(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = ARun.run(DOCUMENT, api.baseUrl(), directory, screen);

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer).describedAs("the run said:%n%s", screen).isEqualTo(1);
        assertThat(screen.toString())
                .describedAs("what RESTest could not read is said before the run")
                .contains("the response '404' is a reference that does not resolve");
        assertThat(codesIn(report)).containsExactly(920);
        assertThat(operationsWith(920, report)).containsExactly("listBats");
    }
}
