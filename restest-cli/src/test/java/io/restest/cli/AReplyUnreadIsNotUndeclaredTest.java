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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
 * document leaves out, from the document to the report, and an operation RESTest read whole is held
 * to its document even when RESTest had to give it another name, or leave out of it a parameter no
 * request could carry - which is then never broken on purpose and reported as accepted.
 *
 * <p>The stand-in answers 404 to everything except {@code GET /favourites}, which only the third
 * document has, and which answers 200. Its first document declares a 404 for one operation by
 * pointing at a reply it never defines, which RESTest cannot read and says so before the run; and
 * it declares no 404 at all for the other. Only the second is a document that leaves the 404 out.
 *
 * <p>The second document gives one name to three operations, so RESTest names the second and the
 * third by their method and path. The second also points at a reply that is never defined: that
 * is said of it, under its new name, and it is left alone. The first and the third declare no 404,
 * and both are reported.
 *
 * <p>The third document gives two operations an identifier their paths have no place for, which
 * RESTest leaves out and says so. One of them is the stand-in's only operation that answers 200,
 * whatever it is sent: nothing is broken on purpose in a request to it, since the identifier is the
 * only thing such a request could have changed. The other answers 404, which it does not declare,
 * and is reported: having nothing left out that a request could carry, it is held to its document.
 *
 * <p>The fourth document builds a parameter of one operation, and the body of a reply of another,
 * from a shape it never defines. RESTest says so before the run, and both operations are still held
 * to the replies they declare, since every one of those was read: the 404 neither declares is
 * reported.
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

    /** Three operations under one name, the second with a reply RESTest cannot read. */
    private static final String ONE_NAME_FOR_THREE = """
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
              /bats:
                get:
                  operationId: listOwls
                  responses:
                    "200":
                      description: every bat
                    "404":
                      $ref: '#/components/responses/NoSuchThing'
              /moths:
                get:
                  operationId: listOwls
                  responses:
                    "200":
                      description: every moth
            components:
              responses: {}
            """;

    /** Two operations declaring an identifier that neither of their paths has a place for. */
    private static final String AN_IDENTIFIER_WITH_NO_PLACE = """
            openapi: 3.0.3
            info:
              title: Night animals
              version: "1.0"
            paths:
              /favourites:
                get:
                  operationId: listFavourites
                  parameters:
                    - name: id
                      in: path
                      required: true
                      schema:
                        type: integer
                  responses:
                    "200":
                      description: every favourite
              /profile:
                get:
                  operationId: getProfile
                  parameters:
                    - name: id
                      in: path
                      required: true
                      schema:
                        type: integer
                  responses:
                    "200":
                      description: the profile
            """;

    /** A shape the document never defines, in a request of one operation and a reply of another. */
    private static final String A_SHAPE_NEVER_DEFINED = """
            openapi: 3.0.3
            info:
              title: Night animals
              version: "1.0"
            paths:
              /moths:
                get:
                  operationId: listMoths
                  parameters:
                    - name: kind
                      in: query
                      schema:
                        $ref: '#/components/schemas/NoSuchShape'
                  responses:
                    "200":
                      description: every moth
              /larks:
                get:
                  operationId: listLarks
                  responses:
                    "200":
                      description: every lark
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/NoSuchShape'
            components:
              schemas: {}
            """;

    private static WireMockServer api;

    @BeforeAll
    static void startTheApi() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        api.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(404)));
        // Added last, so that it is the one that answers there.
        api.stubFor(get(urlPathEqualTo("/favourites")).willReturn(aResponse().withStatus(200)));
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
        assertThat(answer)
                .describedAs("an undeclared status code is a warning, which ends no run with 1 "
                        + "unless asked; the run said:%n%s", screen)
                .isZero();
        assertThat(screen.toString())
                .describedAs("what RESTest could not read is said before the run")
                .contains("the response '404' is a reference that does not resolve");
        assertThat(codesIn(report)).containsExactly(920);
        assertThat(operationsWith(920, report)).containsExactly("listBats");
    }

    @Test
    @DisplayName("an operation renamed because the document repeats its name is held to its "
            + "document, unless RESTest could not read all of it, which is said of it and not of "
            + "the operation that kept the name")
    void a_renamed_operation_is_held_to_its_document(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = ARun.run(ONE_NAME_FOR_THREE, api.baseUrl(), directory, screen);

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer)
                .describedAs("an undeclared status code is a warning, which ends no run with 1 "
                        + "unless asked; the run said:%n%s", screen)
                .isZero();
        assertThat(screen.toString())
                .describedAs("what RESTest could not read, and the new names, are said before the "
                        + "run")
                .contains(
                        "paths./bats.get.responses.404: the response '404' is a reference that "
                                + "does not resolve",
                        "paths./bats.get: another operation is already called 'listOwls'; "
                                + "renamed 'GET /bats' to stay unique",
                        "renamed 'GET /moths' to stay unique");
        assertThat(codesIn(report)).containsExactly(920);
        assertThat(operationsWith(920, report)).containsExactlyInAnyOrder("listOwls", "GET /moths");
    }

    @Test
    @DisplayName("a shape RESTest cannot read, in a request or inside a reply it read, hides no "
            + "reply the document declares, so a 404 it does not declare is reported")
    void a_shape_it_cannot_read_hides_no_reply(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = ARun.run(A_SHAPE_NEVER_DEFINED, api.baseUrl(), directory, screen);

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer)
                .describedAs("an undeclared status code is a warning, which ends no run with 1 "
                        + "unless asked; the run said:%n%s", screen)
                .isZero();
        assertThat(screen.toString())
                .describedAs("what RESTest could not read is said before the run")
                .contains("paths./moths.get: this operation's data includes a shape RESTest does "
                                + "not fully understand yet",
                        "paths./larks.get: this operation's data includes a shape RESTest does "
                                + "not fully understand yet");
        assertThat(codesIn(report)).containsExactly(920);
        assertThat(operationsWith(920, report))
                .containsExactlyInAnyOrder("listMoths", "listLarks");
    }

    @Test
    @DisplayName("an identifier a path has no place for is left out, and said, so that no request "
            + "sent as it was accepted is reported as broken data accepted, and the operation is "
            + "still held to its document")
    void what_no_request_carries_is_left_out(@TempDir Path directory) throws IOException {
        StringWriter screen = new StringWriter();

        int answer = ARun.run(AN_IDENTIFIER_WITH_NO_PLACE, api.baseUrl(), directory, screen);

        JsonValue.JsonObject report = report(directory.resolve("out"));
        assertThat(answer)
                .describedAs("an undeclared status code is a warning, which ends no run with 1 "
                        + "unless asked; the run said:%n%s", screen)
                .isZero();
        assertThat(screen.toString())
                .describedAs("what RESTest left out is said before the run")
                .contains("paths./favourites.get: the path parameter 'id' has no '{id}' in the "
                        + "path '/favourites', so no request can carry it; it is left out");
        assertThat(codesIn(report)).containsExactly(920);
        assertThat(operationsWith(920, report)).containsExactly("getProfile");
        JsonValue.JsonArray issues =
                (JsonValue.JsonArray) report.member("documentIssues").orElseThrow();
        assertThat(issues.elements())
                .extracting(issue -> ((JsonValue.JsonObject) issue).member("effect").orElseThrow())
                .containsExactly(JsonValue.of("leftOut"), JsonValue.of("leftOut"));
    }
}
