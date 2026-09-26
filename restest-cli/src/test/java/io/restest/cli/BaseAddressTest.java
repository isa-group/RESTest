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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.restest.core.model.ApiModel;
import io.restest.core.model.Server;
import io.restest.core.model.ServerVariable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BaseAddressTest {

    private static final ApiModel NO_SERVERS = ApiModel.of("Pets", "1.0", List.of());

    @Test
    @DisplayName("what a person typed wins over whatever the document claims")
    void the_person_wins() {
        ApiModel model = declaring(Server.at("https://production.example/v2"));

        assertThat(BaseAddress.resolve("http://localhost:8080/api/v3", model))
                .isEqualTo("http://localhost:8080/api/v3");
    }

    @Test
    @DisplayName("with nothing typed, the first address the document declares is used")
    void the_document_answers_when_nobody_else_does() {
        ApiModel model = declaring(Server.at("https://api.example/v2"),
                Server.at("http://api.example/v2"));

        assertThat(BaseAddress.resolve(null, model)).isEqualTo("https://api.example/v2");
    }

    @Test
    @DisplayName("an address the document leaves half-written is skipped in favour of a whole one")
    void a_template_nobody_filled_in_is_skipped() {
        Server template = new Server("https://{region}.example.com/v2",
                Map.of("zone", new ServerVariable("eu", List.of(), Optional.empty())),
                Optional.empty());
        ApiModel model = declaring(template, Server.at("https://fallback.example/v2"));

        assertThat(BaseAddress.resolve(null, model)).isEqualTo("https://fallback.example/v2");
    }

    @Test
    @DisplayName("a document's variables are filled in from its own defaults")
    void a_template_with_defaults_is_usable() {
        Server template = new Server("https://{region}.example.com/v2",
                Map.of("region", new ServerVariable("eu", List.of("eu", "us"), Optional.empty())),
                Optional.empty());

        assertThat(BaseAddress.resolve(null, declaring(template)))
                .isEqualTo("https://eu.example.com/v2");
    }

    @Test
    @DisplayName("a document that declares nowhere to send requests asks for --url by name")
    void nothing_declared_asks_for_the_option() {
        assertThatThrownBy(() -> BaseAddress.resolve(null, NO_SERVERS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the document names none")
                .hasMessageContaining("--url");
    }

    @Test
    @DisplayName("the lone slash a document with no servers block is given is not an address")
    void the_parsers_placeholder_is_refused() {
        assertThatThrownBy(() -> BaseAddress.resolve(null, declaring(Server.at("/"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the document names '/'")
                .hasMessageContaining("--url");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "localhost:8080",
        "ftp://api.example",
        "/api/v3",
        "http:///api",
        "http://api.example/v2?key=abc",
        "http://api.example/v2#section",
        "not an address at all",
    })
    @DisplayName("an address requests could not be sent to is refused before the run opens anything")
    void an_unusable_address_is_refused(String typed) {
        assertThatThrownBy(() -> BaseAddress.resolve(typed, NO_SERVERS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not somewhere requests can be sent");
    }

    @Test
    @DisplayName("a blank --url is the same as not saying anything")
    void blank_is_the_same_as_silence() {
        assertThat(BaseAddress.resolve("   ", declaring(Server.at("https://api.example/v2"))))
                .isEqualTo("https://api.example/v2");
    }

    @Test
    @DisplayName("a host with an underscore in its name, as Docker Compose services often have, is "
            + "somewhere requests can be sent")
    void an_underscore_in_the_host_is_accepted() {
        assertThat(BaseAddress.resolve("http://pet_store:8080", NO_SERVERS))
                .isEqualTo("http://pet_store:8080");
    }

    @Test
    @DisplayName("and it is recognised as the machine the document declares, directory and all")
    void an_underscore_host_still_names_the_declared_machine() {
        ApiModel model = declaring(Server.at("https://production.example/v2"),
                Server.at("http://pet_store:8080/api/v3"));

        assertThat(BaseAddress.resolve("http://pet_store:8080", model))
                .isEqualTo("http://pet_store:8080/api/v3");
    }

    @Test
    @DisplayName("surrounding spaces in a pasted address are not a mistake")
    void spaces_are_forgiven() {
        assertThat(BaseAddress.resolve("  http://localhost:8080  ", NO_SERVERS))
                .isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("moving the API to another machine does not move it out of its own directory")
    void the_directory_the_document_declares_is_kept() {
        ApiModel model = declaring(Server.at("http://localhost:9966/petclinic/api"));

        assertThat(BaseAddress.resolve("http://localhost:15012", model))
                .isEqualTo("http://localhost:15012/petclinic/api");
    }

    @Test
    @DisplayName("a trailing slash on the address given does not become two")
    void a_trailing_slash_does_not_double_up() {
        ApiModel model = declaring(Server.at("http://localhost:9966/petclinic/api"));

        assertThat(BaseAddress.resolve("http://localhost:15012/", model))
                .isEqualTo("http://localhost:15012/petclinic/api");
    }

    @Test
    @DisplayName("an address given with a directory of its own replaces the document's")
    void a_directory_typed_wins() {
        ApiModel model = declaring(Server.at("http://localhost:9966/petclinic/api"));

        assertThat(BaseAddress.resolve("http://localhost:8080/somewhere/else", model))
                .isEqualTo("http://localhost:8080/somewhere/else");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://api.example", "http://api.example/"})
    @DisplayName("a document that names no directory leaves the address exactly as typed")
    void nothing_is_added_when_the_document_declares_nothing(String declared) {
        assertThat(BaseAddress.resolve("http://localhost:8080", declaring(Server.at(declared))))
                .isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("a document that declares nothing at all leaves the address exactly as typed")
    void nothing_is_added_when_the_document_declares_no_address() {
        assertThat(BaseAddress.resolve("http://localhost:8080", NO_SERVERS))
                .isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("a document whose address is only a directory still says which directory")
    void an_address_that_is_only_a_path_still_counts() {
        ApiModel model = declaring(Server.at("/api/v3"));

        assertThat(BaseAddress.resolve("http://localhost:8080", model))
                .isEqualTo("http://localhost:8080/api/v3");
    }

    @Test
    @DisplayName("a blank the document fills in itself still says which directory")
    void a_blank_with_a_default_still_says_which_directory() {
        Server template = new Server("https://{region}.example.com/v2",
                Map.of("region", new ServerVariable("eu", List.of(), Optional.empty())),
                Optional.empty());

        assertThat(BaseAddress.resolve("http://localhost:8080", declaring(template)))
                .isEqualTo("http://localhost:8080/v2");
    }

    @Test
    @DisplayName("a blank nobody filled in is not sent to the API as a directory")
    void a_blank_left_unfilled_says_nothing() {
        Server template = new Server("http://{host}/{context}/api",
                Map.of("zone", new ServerVariable("eu", List.of(), Optional.empty())),
                Optional.empty());

        assertThat(BaseAddress.resolve("http://localhost:8080", declaring(template)))
                .describedAs("asking a server for /{context}/api gets the same nothing as asking "
                        + "it for the wrong path, which is the mistake this rule exists to prevent")
                .isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("an API that moves machine does not move into a directory it was never in")
    void a_later_address_does_not_lend_its_directory() {
        // What a Spring document generates, next to the address the same API is published at.
        ApiModel model = declaring(Server.at("http://localhost:8080"),
                Server.at("https://api.example.com/v1"));

        assertThat(BaseAddress.resolve(null, model)).isEqualTo("http://localhost:8080");
        assertThat(BaseAddress.resolve("http://staging:9000", model))
                .describedAs("the first address names no directory, so there is none - taking the "
                        + "second one's would send every request somewhere the first says does "
                        + "not exist")
                .isEqualTo("http://staging:9000");
    }

    @Test
    @DisplayName("a blank in the machine's name does not hide the directory beside it")
    void a_blank_in_the_machine_leaves_the_directory_readable() {
        Server templatedHost = new Server("https://{customer}.api.example.com/v2",
                Map.of(), Optional.empty());

        // Nothing can be sent to that address, so --url is not optional here: the document is all
        // there is to go on, and what it says about the directory is perfectly clear.
        assertThatThrownBy(() -> BaseAddress.resolve(null, declaring(templatedHost)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(BaseAddress.resolve("https://acme.internal:8443", declaring(templatedHost)))
                .isEqualTo("https://acme.internal:8443/v2");
    }

    @Test
    @DisplayName("an older document that never said http or https still says which directory")
    void an_address_with_no_protocol_still_says_which_directory() {
        // What reading a Swagger 2.0 document with a host and a base path but no schemes produces.
        ApiModel model = declaring(Server.at("//api.example.com/v2"));

        assertThat(BaseAddress.resolve("http://localhost:8080", model))
                .isEqualTo("http://localhost:8080/v2");
    }

    @Test
    @DisplayName("an address carrying a query string is not read for its directory either")
    void an_address_with_a_query_is_not_read() {
        ApiModel model = declaring(Server.at("https://api.example/v1?key=abc"));

        assertThat(BaseAddress.resolve("http://localhost:8080", model))
                .describedAs("this class refuses a query string when a person types one, and "
                        + "reading half of one the document wrote would be no more consistent")
                .isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("resolving without a document to read is a mistake, not an address")
    void a_missing_model_is_refused() {
        assertThatThrownBy(() -> BaseAddress.resolve("http://localhost:8080", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("model");
    }

    @Test
    @DisplayName("the directory comes from the same address the document would have been asked for")
    void the_two_readings_of_the_document_agree() {
        Server unfilled = new Server("https://{region}.example.com/beta",
                Map.of("zone", new ServerVariable("eu", List.of(), Optional.empty())),
                Optional.empty());
        ApiModel model = declaring(unfilled, Server.at("https://api.example/v2"));

        assertThat(BaseAddress.resolve(null, model)).isEqualTo("https://api.example/v2");
        assertThat(BaseAddress.resolve("http://localhost:8080", model))
                .describedAs("an API that moves to another machine must not also appear to move "
                        + "to another directory")
                .isEqualTo("http://localhost:8080/v2");
    }

    @Test
    @DisplayName("a document that describes the very machine it was pointed at has already answered")
    void the_document_answers_for_the_machine_it_describes() {
        // The same two addresses as the test above, the other way round, which is how a document
        // written by hand usually lists them: what it is, then where to try it.
        ApiModel model = declaring(Server.at("https://api.example.com/v2"),
                Server.at("http://localhost:8080"));

        assertThat(BaseAddress.resolve("http://localhost:8080", model))
                .describedAs("the document says this machine serves the API at its root, and it "
                        + "was asked about this machine")
                .isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("a port left unwritten is still the port its protocol implies")
    void a_port_left_unwritten_still_names_the_same_machine() {
        // The first address would answer if the second were not recognised as the same machine.
        ApiModel model = declaring(Server.at("https://elsewhere.example/v9"),
                Server.at("https://api.example.com:443"));

        assertThat(BaseAddress.resolve("https://api.example.com", model))
                .describedAs("the document describes this very machine, port written out, and "
                        + "says it serves the API at its root")
                .isEqualTo("https://api.example.com");

        ApiModel underADirectory = declaring(Server.at("https://elsewhere.example/v9"),
                Server.at("https://api.example.com:443/v2"));
        assertThat(BaseAddress.resolve("https://api.example.com", underADirectory))
                .isEqualTo("https://api.example.com/v2");
    }

    @Test
    @DisplayName("a declared address that could not be used decides nothing by being listed first")
    void an_unreadable_address_does_not_answer_for_its_machine() {
        ApiModel model = declaring(Server.at("http://localhost:8080/api?debug=1"),
                Server.at("http://localhost:8080/petclinic/api"));

        assertThat(BaseAddress.resolve(null, model))
                .isEqualTo("http://localhost:8080/petclinic/api");
        assertThat(BaseAddress.resolve("http://localhost:8080", model))
                .describedAs("the same document must not say two different things depending on "
                        + "whether somebody typed the address it already names")
                .isEqualTo("http://localhost:8080/petclinic/api");
    }

    @Test
    @DisplayName("among addresses none of which could be used, the first that names a directory wins")
    void the_first_readable_directory_wins_when_nothing_is_usable() {
        ApiModel model = declaring(Server.at("/"), Server.at("//api.example.com/second"));

        assertThat(BaseAddress.resolve("http://localhost:8080", model))
                .isEqualTo("http://localhost:8080/second");
    }

    @Test
    @DisplayName("a protocol shouted in capitals is the same protocol")
    void a_protocol_in_capitals_is_read_the_same_way() {
        ApiModel model = declaring(Server.at("HTTPS://{customer}.api.example.com/v2"));

        assertThat(BaseAddress.resolve("http://localhost:8080", model))
                .isEqualTo("http://localhost:8080/v2");
    }

    @Test
    @DisplayName("an address served over something other than the web says nothing about directories")
    void a_protocol_we_do_not_speak_is_not_read() {
        assertThat(BaseAddress.resolve("http://localhost:8080",
                declaring(Server.at("wss://api.example/socket"))))
                .describedAs("nothing would ever be sent there, so it says nothing about where "
                        + "what we do send should go")
                .isEqualTo("http://localhost:8080");
        assertThat(BaseAddress.resolve("http://localhost:8080",
                declaring(Server.at("file:/srv/api"))))
                .isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("a protocol written with a slash missing is still a protocol, not a directory")
    void a_slash_missing_from_the_protocol_does_not_make_a_directory() {
        ApiModel model = declaring(Server.at("http:/api.example.com/v2"));

        assertThat(BaseAddress.resolve("http://localhost:8080", model))
                .describedAs("reading that as a directory would send every request to a path made "
                        + "out of a machine's name")
                .isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("an older document's address gives its directory, and not its machine's name")
    void a_protocol_relative_address_gives_only_its_directory() {
        assertThat(BaseAddress.resolve("http://localhost:8080",
                declaring(Server.at("//api.example.com/v2"))))
                .isEqualTo("http://localhost:8080/v2");
        assertThat(BaseAddress.resolve("http://localhost:8080",
                declaring(Server.at("//api.example.com/100%/v2"))))
                .describedAs("one character no parser accepts must not turn the machine's name "
                        + "into the directory")
                .isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("a directory that would not be a legal path is not one")
    void something_that_is_not_a_path_is_not_a_directory() {
        assertThat(BaseAddress.resolve("http://localhost:8080",
                declaring(Server.at("http://api.example.com/v 2"))))
                .isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("a directory written with escapes is kept exactly as the document wrote it")
    void escapes_in_a_directory_are_not_spelled_out() {
        ApiModel model = declaring(Server.at("https://api.example/a%2Fb/v2"));

        assertThat(BaseAddress.resolve("http://localhost:8080", model))
                .describedAs("spelling %2F out as a slash would name a different resource, and "
                        + "spelling %3F out would bolt a query string onto an address that had none")
                .isEqualTo("http://localhost:8080/a%2Fb/v2");
    }

    @Test
    @DisplayName("an address the document writes without a protocol is not read as a directory")
    void an_address_with_no_protocol_and_no_leading_slash_is_skipped() {
        ApiModel model = declaring(Server.at("api.example/v2"));

        assertThat(BaseAddress.resolve("http://localhost:8080", model))
                .describedAs("reading it as a directory would splice the machine's name into the "
                        + "path, and nothing says that is what the document meant")
                .isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("how many slashes were typed does not decide whether the directory is kept")
    void slashes_alone_are_not_a_directory() {
        ApiModel model = declaring(Server.at("http://localhost:9966/petclinic/api"));

        assertThat(BaseAddress.resolve("http://localhost:15012//", model))
                .isEqualTo("http://localhost:15012/petclinic/api");
    }

    @Test
    @DisplayName("a trailing slash on the document's directory is not carried over")
    void the_documents_trailing_slash_is_dropped() {
        ApiModel model = declaring(Server.at("http://localhost:9966/petclinic/api/"));

        assertThat(BaseAddress.resolve("http://localhost:15012", model))
                .isEqualTo("http://localhost:15012/petclinic/api");
    }

    private static ApiModel declaring(Server... servers) {
        return new ApiModel("Pets", "1.0", List.of(servers), List.of(), Map.of(), List.of(),
                Optional.empty());
    }
}
