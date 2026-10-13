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
import java.util.List;
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
                version: 2
                strategies:
                  - name: nominal
                    share: 70
                    kind: nominal
                    sources:
                      - source: enum
                      - source: dictionaries
                      - weighted: {example: 60, random: 40}
                  - name: pushing
                    share: 30
                    kind: fuzzing
                    sources:
                      - source: random
                operations:
                  methods: [GET, post]
                  only: [getPetById, "GET /pets/{petId}"]
                """, "ours.yaml");

        assertThat(plan.strategies()).hasSize(2);
        assertThat(plan.strategies().get(0).name()).isEqualTo("nominal");
        assertThat(plan.strategies().get(0).share()).isEqualTo(70);
        assertThat(plan.strategies().get(0).sources()).containsExactly(
                new Campaign.Entry.Single(new Campaign.Source.Builtin(Campaign.Builtin.ENUM)),
                new Campaign.Entry.Single(new Campaign.Source.EveryListGiven()),
                new Campaign.Entry.Group(List.of(
                        new Campaign.Share(
                                new Campaign.Source.Builtin(Campaign.Builtin.EXAMPLE), 60),
                        new Campaign.Share(
                                new Campaign.Source.Builtin(Campaign.Builtin.RANDOM), 40))));
        assertThat(plan.strategies().get(1).pushesAtTheApi())
                .describedAs("a strategy of kind fuzzing pushes at the API")
                .isTrue();
        assertThat(plan.strategies().get(1).sources())
                .describedAs("RESTest's own list of values to push with comes with the kind, "
                        + "before what the strategy names")
                .containsExactly(
                        new Campaign.Entry.Single(new Campaign.Source.OneList("fuzzing")),
                        new Campaign.Entry.Single(
                                new Campaign.Source.Builtin(Campaign.Builtin.RANDOM)));
        assertThat(plan.operations().methods())
                .describedAs("a document writes 'get' and a person writes 'GET'")
                .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
        assertThat(plan.operations().only())
                .containsExactly("getPetById", "GET /pets/{petId}");
    }

    @Test
    @DisplayName("a plan that narrows nothing touches every operation there is")
    void a_plan_with_no_filter_keeps_everything() {
        assertThat(read("- source: random").operations().narrowsAnything()).isFalse();
    }

    @Test
    @DisplayName("each kind of strategy is read as what it does")
    void each_kind_is_read() {
        Campaign plan = CampaignDocument.read("""
                version: 2
                strategies:
                  - name: ordinary
                    share: 40
                    kind: nominal
                    sources: [{source: random}]
                  - name: pushing
                    share: 10
                    kind: fuzzing
                    sources: [{source: random}]
                  - name: changes
                    share: 30
                    kind: mutation
                    sources: [{source: enum}, {source: random}]
                  - name: series
                    share: 20
                    kind: sequences
                    sources: [{source: random}]
                """, "ours.yaml");

        assertThat(plan.strategies()).extracting(Campaign.PlannedStrategy::pushesAtTheApi)
                .containsExactly(false, true, false, false);
        assertThat(plan.strategies()).extracting(Campaign.PlannedStrategy::mutatesAccepted)
                .containsExactly(false, false, true, false);
        assertThat(plan.strategies()).extracting(Campaign.PlannedStrategy::sendsSequences)
                .containsExactly(false, false, false, true);
        assertThat(plan.strategies().get(2).sources())
                .describedAs("what it builds from when there is nothing accepted to change")
                .hasSize(2);
    }

    @Test
    @DisplayName("a group is a map from each source to its weight, your dictionaries by name under "
            + "'dictionary', and is asked in one order whatever order the file writes")
    void a_group_is_a_map_in_a_fixed_order() {
        Campaign written = read("""
                - weighted:
                    dictionary: {zoo: 10, pet-clinic: 15}
                    enum: 5
                    default: 5
                    dictionaries: 25
                    example: 15
                    random: 25""");
        Campaign reordered = read("""
                - weighted:
                    random: 25
                    example: 15
                    dictionaries: 25
                    default: 5
                    enum: 5
                    dictionary: {pet-clinic: 15, zoo: 10}""");

        assertThat(written.strategies().get(0).sources()).containsExactly(
                new Campaign.Entry.Group(List.of(
                        new Campaign.Share(
                                new Campaign.Source.Builtin(Campaign.Builtin.EXAMPLE), 15),
                        new Campaign.Share(
                                new Campaign.Source.Builtin(Campaign.Builtin.RANDOM), 25),
                        new Campaign.Share(new Campaign.Source.EveryListGiven(), 25),
                        new Campaign.Share(
                                new Campaign.Source.Builtin(Campaign.Builtin.DEFAULT), 5),
                        new Campaign.Share(
                                new Campaign.Source.Builtin(Campaign.Builtin.ENUM), 5),
                        new Campaign.Share(new Campaign.Source.OneList("pet-clinic"), 15),
                        new Campaign.Share(new Campaign.Source.OneList("zoo"), 10))));
        assertThat(reordered)
                .describedAs("a tool that sorts the keys of a map does not change what is drawn")
                .isEqualTo(written);
    }

    @Test
    @DisplayName("every source of RESTest's own has its word in a plan, and every word names one")
    void every_source_of_restests_own_has_its_word() {
        assertThat(CampaignDocument.sourceWords())
                .describedAs("a source added to RESTest and forgotten here would be read as "
                        + "'source: <word>' and refused inside a group")
                .containsExactlyInAnyOrderElementsOf(java.util.stream.Stream.concat(
                        java.util.Arrays.stream(Campaign.Builtin.values())
                                .map(Campaign.Builtin::inAPlan),
                        java.util.stream.Stream.of("dictionaries")).toList());
    }

    @Test
    @DisplayName("a list of values called 'example' is a list, not the document's own samples")
    void a_list_may_be_called_after_a_source() {
        Campaign plan = read("""
                - dictionary: example
                - source: example""");

        assertThat(plan.strategies().get(0).sources())
                .describedAs("saying which kind a name is, rather than keeping a list of reserved "
                        + "words, is what stops a file name shadowing one of RESTest's own sources")
                .containsExactly(
                        new Campaign.Entry.Single(new Campaign.Source.OneList("example")),
                        new Campaign.Entry.Single(
                                new Campaign.Source.Builtin(Campaign.Builtin.EXAMPLE)));
    }

    @Nested
    @DisplayName("what a plan is refused for")
    class Refusals {

        @Test
        @DisplayName("a version this build does not read, said as a version with a way forward")
        void another_version() {
            for (int version : List.of(1, 9)) {
                assertThatThrownBy(() -> CampaignDocument.read("""
                        version: %d
                        strategies:
                          - name: nominal
                            share: 100
                            sources:
                              - source: random
                        """.formatted(version), "ours.yaml"))
                        .isInstanceOf(JsonException.class)
                        .hasMessageContaining("written in version " + version)
                        .hasMessageContaining("reads version 2")
                        .hasMessageContaining("'restest run --print-campaign' prints a plan it "
                                + "reads");
            }
        }

        @Test
        @DisplayName("a strategy that does not say its kind, told the four there are")
        void a_strategy_with_no_kind() {
            assertThatThrownBy(() -> CampaignDocument.read("""
                    version: 2
                    strategies:
                      - name: nominal
                        share: 100
                        sources:
                          - source: random
                    """, "ours.yaml"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("the strategy called 'nominal' says no 'kind'")
                    .hasMessageContaining("nominal, fuzzing, mutation, sequences");
        }

        @Test
        @DisplayName("a kind that is not one of the four")
        void a_kind_nobody_has() {
            assertThatThrownBy(() -> plan("mutate", "- source: random"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("is of kind 'mutate'")
                    .hasMessageContaining("nominal, fuzzing, mutation, sequences");
        }

        @Test
        @DisplayName("a strategy with no sources, whatever its kind")
        void a_strategy_with_no_sources() {
            for (String kind : List.of("nominal", "fuzzing", "mutation", "sequences")) {
                assertThatThrownBy(() -> CampaignDocument.read("""
                        version: 2
                        strategies:
                          - name: alone
                            share: 100
                            kind: %s
                            sources: []
                        """.formatted(kind), "ours.yaml"))
                        .describedAs(kind)
                        .isInstanceOf(JsonException.class)
                        .hasMessageContaining("has no sources")
                        .hasMessageContaining("needs at least one");
            }
            assertThatThrownBy(() -> CampaignDocument.read("""
                    version: 2
                    strategies:
                      - name: mutation
                        share: 100
                        kind: mutation
                    """, "ours.yaml"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("no 'sources'");
        }

        @Test
        @DisplayName("a word that is not one of the sources RESTest has, with the ones it has")
        void a_source_nobody_has() {
            assertThatThrownBy(() -> read("- source: observations"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("'observations' is not one of RESTest's own sources")
                    .hasMessageContaining("example, random, observed, dictionaries, default, enum")
                    .describedAs("somebody meaning a list of their own is told which word to use")
                    .hasMessageContaining("'dictionary'");
        }

        @Test
        @DisplayName("RESTest's own list to push with, in every way it could be named as a source, "
                + "pointed at the kind that uses it")
        void fuzzing_is_not_a_source() {
            for (String written : List.of("- source: fuzzing", "- dictionary: fuzzing",
                    "- weighted: {fuzzing: 50, random: 50}",
                    "- weighted: {dictionary: {fuzzing: 50}, random: 50}")) {
                assertThatThrownBy(() -> read(written))
                        .describedAs(written)
                        .isInstanceOf(JsonException.class)
                        .hasMessageContaining("'fuzzing' is RESTest's own list of values to push "
                                + "with, which a strategy of kind fuzzing asks before its own "
                                + "sources");
            }
        }

        @Test
        @DisplayName("'dictionary' and 'dictionaries', one letter apart, each suggested for the "
                + "other")
        void dictionary_and_dictionaries() {
            assertThatThrownBy(() -> read("- source: dictionary"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("for every dictionary handed over, write 'dictionaries'");
            assertThatThrownBy(() -> read("- weighted: {dictionary: 40, random: 60}"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("as dictionary: {pet-clinic: 40}")
                    .hasMessageContaining("write dictionaries: 40");
        }

        @Test
        @DisplayName("a source that says nothing at all about where its values come from")
        void a_source_naming_nothing() {
            assertThatThrownBy(() -> read("- {}"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("says where its values come from")
                    .describedAs("all three ways of naming one, since the reader cannot guess "
                            + "which was meant")
                    .hasMessageContaining("'source'")
                    .hasMessageContaining("'dictionary'")
                    .hasMessageContaining("'weighted'");
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
        @DisplayName("a weight on a source that is not inside a group, said with how to write one")
        void a_weight_outside_a_group() {
            assertThatThrownBy(() -> read("""
                    - source: random
                      weight: 50"""))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("a weight only goes inside a 'weighted' group")
                    .hasMessageContaining("weighted: {example: 50, random: 50}");
            assertThatThrownBy(() -> read("- dictionary: {pet-clinic: 40}"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("weights only go inside a 'weighted' group")
                    .hasMessageContaining("dictionary: <its name>");
        }

        @Test
        @DisplayName("a group written as a list, the way version 1 wrote one")
        void a_group_written_as_a_list() {
            assertThatThrownBy(() -> read("""
                    - weighted:
                        - source: example
                          weight: 50
                        - source: random
                          weight: 50"""))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("is a map from each source to its weight")
                    .hasMessageContaining("weighted: {example: 50, random: 50}");
        }

        @Test
        @DisplayName("a group naming something that is neither a source nor 'dictionary'")
        void a_group_with_an_unknown_key() {
            assertThatThrownBy(() -> read("- weighted: {exmaple: 50, random: 50}"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("names 'exmaple': 'exmaple' is not one of RESTest's own "
                            + "sources")
                    .hasMessageContaining("your dictionaries go under 'dictionary'");
        }

        @Test
        @DisplayName("'dictionary' in a group naming no dictionary at all")
        void a_group_with_an_empty_dictionary() {
            assertThatThrownBy(() -> read("- weighted: {dictionary: {}, random: 50, example: 50}"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("and this one names none");
        }

        @Test
        @DisplayName("a weight that is not a whole number from 1 to 100")
        void weights_out_of_range() {
            assertThatThrownBy(() -> read("- weighted: {example: 0, random: 100}"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("'example' in a 'weighted' group of the strategy called "
                            + "'nominal' is 0")
                    .hasMessageContaining("a whole number from 1 to 100")
                    .hasMessageContaining("left out instead");
            assertThatThrownBy(() -> read("- weighted: {example: 101, random: 1}"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("is 101")
                    .hasMessageContaining("a whole number from 1 to 100");
            assertThatThrownBy(() -> read("- weighted: {example: 50.5, random: 49.5}"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("whole number");
        }

        @Test
        @DisplayName("weights inside a group that do not add up to a hundred")
        void weights_that_do_not_add_up() {
            assertThatThrownBy(() -> read("- weighted: {example: 50, random: 40}"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("add up to 100")
                    .hasMessageContaining("these add up to 90");
        }

        @Test
        @DisplayName("a group with one source in it, told to write that very source on its own, "
                + "and a group with none")
        void a_group_of_one() {
            assertThatThrownBy(() -> read("- weighted: {example: 100}"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("has one source in it")
                    .hasMessageContaining("as source: example");
            assertThatThrownBy(() -> read("- weighted: {dictionary: {pet-clinic: 100}}"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("as dictionary: pet-clinic");
            assertThatThrownBy(() -> read("- weighted: {}"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("names no source");
        }

        @Test
        @DisplayName("shares that do not add up to a hundred, and what they do add up to")
        void shares_that_do_not_add_up() {
            assertThatThrownBy(() -> CampaignDocument.read("""
                    version: 2
                    strategies:
                      - name: nominal
                        share: 70
                        kind: nominal
                        sources:
                          - source: random
                      - name: pushing
                        share: 20
                        kind: fuzzing
                        sources:
                          - source: random
                    """, "ours.yaml"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("these add up to 90");
        }

        @Test
        @DisplayName("a misspelt member, which would otherwise load and quietly do nothing")
        void a_misspelling() {
            assertThatThrownBy(() -> CampaignDocument.read("""
                    version: 2
                    strategies:
                      - name: nominal
                        share: 100
                        kind: nominal
                        source:
                          - source: random
                    """, "ours.yaml"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("does not recognise (source)")
                    .hasMessageContaining("usually a misspelling");
        }

        @Test
        @DisplayName("a method HTTP does not have")
        void a_method_nobody_has() {
            assertThatThrownBy(() -> CampaignDocument.read("""
                    version: 2
                    strategies:
                      - name: nominal
                        share: 100
                        kind: nominal
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
            assertThatThrownBy(() -> read("- weighted: {random: 50, random: 50}"))
                    .isInstanceOf(JsonException.class)
                    .hasMessageContaining("could not be read");
        }

        private static Campaign plan(String kind, String sources) {
            String indented = sources.lines().map(line -> "      " + line)
                    .collect(java.util.stream.Collectors.joining("\n"));
            return CampaignDocument.read("""
                    version: 2
                    strategies:
                      - name: nominal
                        share: 100
                        kind: %s
                        sources:
                    """.formatted(kind) + indented + "\n", "ours.yaml");
        }
    }

    /**
     * One strategy's worth of plan, of the ordinary kind, with these lines as its sources.
     *
     * <p>Indented here rather than by the caller, because a text block inside a text block loses
     * exactly the leading spaces that decide what YAML means.
     */
    private static Campaign read(String sources) {
        return Refusals.plan("nominal", sources);
    }
}
