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
package io.restest.core.auth;

import static io.restest.core.auth.CredentialPlanTest.CORE;
import static io.restest.core.auth.CredentialPlanTest.KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** How a key, the place it goes and the ways it is written are named and spelled. */
class WordsTest {

    @Test
    @DisplayName("a key handed over is named by where it came from, and never shown")
    void a_key_is_named_by_where_it_came_from() {
        assertThat(AuthGiven.typed(KEY, 1).named()).isEqualTo("the key given with --auth");
        assertThat(AuthGiven.typed(KEY, 2).named()).isEqualTo("the key given with the second --auth");
        assertThat(AuthGiven.typed(KEY, 5).named()).isEqualTo("the key given with the fifth --auth");
        assertThat(List.of(6, 11, 12, 13, 21, 22, 23, 101, 111))
                .extracting(position -> AuthGiven.typed(KEY, position).named())
                .containsExactly("the key given with the 6th --auth",
                        "the key given with the 11th --auth", "the key given with the 12th --auth",
                        "the key given with the 13th --auth", "the key given with the 21st --auth",
                        "the key given with the 22nd --auth", "the key given with the 23rd --auth",
                        "the key given with the 101st --auth", "the key given with the 111th --auth");
        assertThat(AuthGiven.fromTheEnvironment(KEY).named()).isEqualTo("the key in RESTEST_AUTH");
        assertThat(AuthGiven.fromTheEnvironment(KEY).source())
                .isEqualTo(AuthGiven.Source.ENVIRONMENT);
        assertThat(AuthGiven.typed(KEY, 3).toString()).doesNotContain(CORE);
    }

    @Test
    @DisplayName("a secret printed shows what stands in its place, and two of the same are equal")
    void a_secret_is_never_printed() {
        Secret secret = new Secret(KEY, "REDACTED-AUTH");

        assertThat(secret.toString()).isEqualTo("REDACTED-AUTH");
        assertThat(new Credential(new Place(Place.Where.HEADER, "api_key"), secret).toString())
                .doesNotContain(CORE);
        assertThat(secret).isEqualTo(new Secret(KEY, "REDACTED-AUTH"))
                .hasSameHashCodeAs(new Secret(KEY, "REDACTED-AUTH"))
                .isNotEqualTo(new Secret(KEY, "REDACTED-AUTH.other"))
                .isNotEqualTo(KEY);
        assertThatIllegalArgumentException().isThrownBy(() -> new Secret("", "REDACTED-AUTH"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Secret(KEY, ""));
    }

    @Test
    @DisplayName("a header is the same header whatever its capitals, and nothing else is")
    void places_are_the_same_as_a_request_sees_them() {
        Place header = new Place(Place.Where.HEADER, "X-API-Key");

        assertThat(header.sameAs(new Place(Place.Where.HEADER, "x-api-key"))).isTrue();
        assertThat(header.sameAs(new Place(Place.Where.QUERY, "X-API-Key"))).isFalse();
        assertThat(new Place(Place.Where.QUERY, "Key").sameAs(new Place(Place.Where.QUERY, "key")))
                .isFalse();
        assertThat(header.described()).isEqualTo("the header X-API-Key");
        assertThat(new Place(Place.Where.QUERY, "k").described()).isEqualTo("the query parameter k");
        assertThat(new Place(Place.Where.FORM_FIELD, "k").described()).isEqualTo("the form field k");
        assertThat(new Place(Place.Where.COOKIE, "k").described()).isEqualTo("the cookie k");
        assertThatIllegalArgumentException().isThrownBy(() -> new Place(Place.Where.HEADER, ""));
    }

    @Test
    @DisplayName("a key is written the ways addresses, forms, JSON and web pages write it")
    void the_ways_of_writing_a_key() {
        assertThat(Encoding.percent("a b/+~é")).isEqualTo("a%20b%2F%2B~%C3%A9");
        assertThat(Encoding.percentInLowerCase("a b/+")).isEqualTo("a%20b%2f%2b");
        assertThat(Encoding.formStyle("a b*~.")).isEqualTo("a+b*%7E.");
        assertThat(Encoding.json("a\"b\\c/d\u0001", false)).isEqualTo("a\\\"b\\\\c/d\\u0001");
        assertThat(Encoding.json("c/d", true)).isEqualTo("c\\/d");
        assertThat(Encoding.jsonEscapingAllPunctuation("a=b", true)).isEqualTo("a\\u003Db");
        assertThat(Encoding.jsonEscapingAllPunctuation("a=b", false)).isEqualTo("a\\u003db");
        assertThat(Encoding.html("<a href='x'>&\"</a>", false))
                .isEqualTo("&lt;a href=&#39;x&#39;&gt;&amp;&quot;&lt;/a&gt;");
        assertThat(Encoding.html("'", true)).isEqualTo("&#x27;");
        assertThat(Encoding.decoded("a+b%20c%2Bd%zz%")).isEqualTo("a b c+d%zz%");
        assertThat(Encoding.decoded("caf%C3%A9é")).isEqualTo("caféé");
    }
}
