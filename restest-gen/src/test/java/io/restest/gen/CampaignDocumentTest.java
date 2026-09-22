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
package io.restest.gen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.restest.core.json.JsonException;
import io.restest.core.model.HttpMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What a plan written by hand is read as, and what it is refused for.
 *
 * <p>The plan is the file a person edits to say where a run's values should come from. Almost
 * everything here is about refusing one: a plan that quietly lost a strategy would send a quarter
 * of its requests somewhere nobody asked for, and nothing in the run's output would say so - so a
 * file this version cannot read is named and refused rather than half-understood.
 */
class CampaignDocumentTest {

    @Test
    @DisplayName("a plan says which sources fill in a run's values, and in what arrangement")
    void a_whole_plan_is_read() {
        Campaign plan = CampaignDocument.read("""
                version: 1
                strategies:
                  - name: nominal
                    share: 70
                    sources:
                      - source: enum
                      - dictionaries: given
                      - weighted:
                          - source: example
                            weight: 60
                          - source: random
                            weight: 40
                  - name: pushing
                    share: 30
                    sources:
                      - dictionary: fuzzing
                operations:
                  methods: [GET, post]
                  only: [getPetById, "GET /pets/{petId}"]
                """, "ours.yaml");

        assertThat(plan.strategies()).hasSize(2);
        assertThat(plan.strategies().get(0).name()).isEqualTo("nominal");
        assertThat(plan.strategies().get(0).share()).isEqualTo(70);
        assertThat(plan.strategies().get(0).sources()).hasSize(3);
        assertThat(plan.strategies().get(0).sources().get(1))
                .describedAs("every list this run was handed, named without knowing their names")
                .isEqualTo(new Campaign.Entry.Single(new Campaign.Source.EveryListGiven()));
        assertThat(plan.strategies().get(0).sources().get(2))
                .isInstanceOf(Campaign.Entry.Group.class);
        assertThat(plan.strategies().get(1).pushesAtTheApi())
                .describedAs("drawing on the list of values to push with is what makes a strategy "
                        + "one that pushes; no word in the file says so")
                .isTrue();
        assertThat(plan.operations().methods())
                .describedAs("a document writes 'get' and a person writes 'GET'")
                .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
        assertThat(plan.operations().only())
                .containsExactly("getPetById", "GET /pets/{petId}");
    }

    @Test
    @DisplayName("a plan that narrows nothing touches every operation there is")
    void a_plan_with_no_filter_keeps_everything() {
        Campaign plan = CampaignDocument.read("""
                version: 1
                strategies:
                  - name: nominal
                    share: 100
                    sources:
                      - source: random
                """, "ours.yaml");

        assertThat(plan.operations().narrowsAnything()).isFalse();
    }

    @Nested
    @DisplayName("what a plan is refused for")
    class Refusals {

        @Test
        @DisplayName("a word that is not one of the sources RESTest has, with the ones it has")
        void a_source_nobody_has() {
            assertThatThrownBy(() -> read("- source: observations"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("'observations' is not one of the sources")
                    .hasMessageContaining("enum, example, default, random")
                    .describedAs("somebody meaning a list of their own is told which word to use")
                    .hasMessageContaining("'dictionary'");
        }

        @Test
        @DisplayName("a source that says nothing at all about where its values come from")
        void a_source_naming_nothing() {
            assertThatThrownBy(() -> read("- {}"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("a source says where its values come from")
                    .describedAs("all three ways of naming one, since the reader cannot guess "
                            + "which was meant")
                    .hasMessageContaining("'source'")
                    .hasMessageContaining("'dictionary'")
                    .hasMessageContaining("'dictionaries: given'");
        }

        @Test
        @DisplayName("a source named two ways at once")
        void a_source_named_twice() {
            assertThatThrownBy(() -> read("""
                    - source: random
                      dictionary: ours"""))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("named 2 ways at once");
        }

        @Test
        @DisplayName("'dictionaries' saying anything other than the one thing it can say")
        void every_list_but_not_that_word() {
            assertThatThrownBy(() -> read("- dictionaries: mine"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("the only thing it can say is 'given'");
        }

        @Test
        @DisplayName("a weight on a source that is not inside a group, where nothing is divided")
        void a_weight_outside_a_group() {
            assertThatThrownBy(() -> read("""
                    - source: random
                      weight: 50"""))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("it is not inside a 'weighted' group");
        }

        @Test
        @DisplayName("a source inside a group that does not say how much of the choice it gets")
        void a_group_member_with_no_weight() {
            assertThatThrownBy(() -> read("""
                    - weighted:
                        - source: example
                          weight: 50
                        - source: random"""))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("has no 'weight'");
        }

        @Test
        @DisplayName("weights inside a group that do not add up to a hundred")
        void weights_that_do_not_add_up() {
            assertThatThrownBy(() -> read("""
                    - weighted:
                        - source: example
                          weight: 50
                        - source: random
                          weight: 40"""))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("add up to 100")
                    .hasMessageContaining("these add up to 90");
        }

        @Test
        @DisplayName("a group with one source in it, where there is nothing to choose between")
        void a_group_of_one() {
            assertThatThrownBy(() -> read("""
                    - weighted:
                        - source: random
                          weight: 100"""))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("at least two in it");
        }

        @Test
        @DisplayName("shares that do not add up to a hundred, and what they do add up to")
        void shares_that_do_not_add_up() {
            assertThatThrownBy(() -> CampaignDocument.read("""
                    version: 1
                    strategies:
                      - name: nominal
                        share: 70
                        sources:
                          - source: random
                      - name: pushing
                        share: 20
                        sources:
                          - dictionary: fuzzing
                    """, "ours.yaml"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("these add up to 90");
        }

        @Test
        @DisplayName("a misspelt member, which would otherwise load and quietly do nothing")
        void a_misspelling() {
            assertThatThrownBy(() -> CampaignDocument.read("""
                    version: 1
                    strategies:
                      - name: nominal
                        share: 100
                        source:
                          - source: random
                    """, "ours.yaml"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("does not recognise (source)")
                    .hasMessageContaining("usually a misspelling");
        }

        @Test
        @DisplayName("a version this build does not read, said as a version rather than a typo")
        void a_later_version() {
            assertThatThrownBy(() -> CampaignDocument.read("""
                    version: 9
                    strategies:
                      - name: nominal
                        share: 100
                        sources:
                          - source: random
                    """, "ours.yaml"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("written in version 9")
                    .hasMessageContaining("reads version 1");
        }

        @Test
        @DisplayName("a method HTTP does not have")
        void a_method_nobody_has() {
            assertThatThrownBy(() -> CampaignDocument.read("""
                    version: 1
                    strategies:
                      - name: nominal
                        share: 100
                        sources:
                          - source: random
                    operations:
                      methods: [FETCH]
                    """, "ours.yaml"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("'FETCH' is not an HTTP method");
        }

        @Test
        @DisplayName("something that is not a plan at all")
        void not_even_yaml() {
            assertThatThrownBy(() -> CampaignDocument.read("strategies: [", "ours.yaml"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("could not be read");
        }

        @Test
        @DisplayName("a heading written twice, which YAML allows and which would lose the first")
        void a_heading_written_twice() {
            assertThatThrownBy(() -> CampaignDocument.read("""
                    version: 1
                    strategies:
                      - name: nominal
                        share: 100
                        sources:
                          - source: random
                    strategies:
                      - name: other
                        share: 100
                        sources:
                          - source: example
                    """, "ours.yaml"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("could not be read");
        }

        /**
         * One strategy's worth of plan, with these lines as its sources.
         *
         * <p>Indented here rather than by the caller, because a text block inside a text block
         * loses exactly the leading spaces that decide what YAML means.
         */
        private static Campaign read(String sources) {
            String indented = sources.lines().map(line -> "      " + line)
                    .collect(java.util.stream.Collectors.joining("\n"));
            return CampaignDocument.read("""
                    version: 1
                    strategies:
                      - name: nominal
                        share: 100
                        sources:
                    """ + indented + "\n", "ours.yaml");
        }
    }

    @Test
    @DisplayName("a list of values called 'example' is a list, not the document's own samples")
    void a_list_may_be_called_after_a_source() {
        Campaign plan = CampaignDocument.read("""
                version: 1
                strategies:
                  - name: nominal
                    share: 100
                    sources:
                      - dictionary: example
                      - source: example
                """, "ours.yaml");

        assertThat(plan.strategies().get(0).sources())
                .describedAs("saying which kind a name is, rather than keeping a list of reserved "
                        + "words, is what stops a file name shadowing one of RESTest's own sources")
                .containsExactly(
                        new Campaign.Entry.Single(new Campaign.Source.OneList("example")),
                        new Campaign.Entry.Single(
                                new Campaign.Source.Builtin(Campaign.Builtin.EXAMPLE)));
    }
}
