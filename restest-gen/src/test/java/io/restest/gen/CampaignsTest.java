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

import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The plan a run ends up following, and what it is told about it before a request is sent.
 *
 * <p>Either a file somebody pointed the tool at or the one RESTest carries. Nothing here ends a
 * run: a plan that cannot be read costs whatever its author meant by it, and one asking for
 * something that will not happen is said out loud - because a run that quietly does less than its
 * plan says looks exactly like one that did as it was told.
 */
class CampaignsTest {

    private static final ApiModel PETS = ApiModel.of("Pets", "1.0", List.of(
            Operation.of(HttpMethod.GET, "/pets"), Operation.of(HttpMethod.POST, "/pets")));

    @Test
    @DisplayName("the plan RESTest carries is a real file, and it is a plan this version reads")
    void the_shipped_plan_is_read_from_the_file_that_ships() throws IOException {
        Campaign carried = Campaigns.shipped();

        assertThat(carried.strategies()).extracting(Campaign.PlannedStrategy::name)
                .containsExactly("nominal", "fuzzing");
        assertThat(carried.strategies()).extracting(Campaign.PlannedStrategy::share)
                .describedAs("the shares of a plan add up to a hundred, this one included")
                .containsExactly(75, 25);
        assertThat(carried.operations().narrowsAnything())
                .describedAs("the plan RESTest carries touches every operation there is")
                .isFalse();
        assertThat(carried.strategies().get(1).pushesAtTheApi()).isTrue();
        assertThat(Campaigns.shippedText())
                .describedAs("printed for somebody to copy, so it keeps the comments that say why")
                .contains("# What a run does when nobody has said otherwise.")
                .contains("--print-campaign");
    }

    @Test
    @DisplayName("the plan RESTest carries chooses among its sources rather than ranking them, so "
            + "that no parameter is pinned to one value for a whole run")
    void the_shipped_plan_chooses_rather_than_ranks() throws IOException {
        List<Campaign.Entry> nominal = Campaigns.shipped().strategies().get(0).sources();

        assertThat(nominal).element(0)
                .describedAs("a closed list is the whole set of values the API takes, so it is "
                        + "the one thing nothing else may answer over")
                .isEqualTo(new Campaign.Entry.Single(
                        new Campaign.Source.Builtin(Campaign.Builtin.ENUM)));
        assertThat(nominal).hasSize(2);
        assertThat(nominal.get(1))
                .describedAs("everything else is chosen among: asked in turn, a parameter whose "
                        + "document offers one sample would receive it on every request, and a "
                        + "run that deletes the row it names never recovers. Measured on a "
                        + "containerised pet-clinic over five seeds: 16.8 operations answered 2XX "
                        + "when ranked, 19.6 when chosen among")
                .isInstanceOf(Campaign.Entry.Group.class);
        assertThat(((Campaign.Entry.Group) nominal.get(1)).among())
                .extracting(Campaign.Share::source)
                .containsExactly(
                        new Campaign.Source.Builtin(Campaign.Builtin.EXAMPLE),
                        new Campaign.Source.Builtin(Campaign.Builtin.RANDOM),
                        new Campaign.Source.EveryListGiven(),
                        new Campaign.Source.Builtin(Campaign.Builtin.DEFAULT));
    }

    @Test
    @DisplayName("a run given no plan of its own follows the one RESTest carries, and is told "
            + "nothing, because there is nothing to tell")
    void no_plan_named_means_the_carried_one() {
        Campaigns.Found found = Campaigns.gather(Optional.empty(), PETS, Set.of("fuzzing"));

        assertThat(found.problems())
                .describedAs("the list RESTest carries is in play on every run, so the plan that "
                        + "names it has nothing wrong with it")
                .isEmpty();
        assertThat(found.campaign().strategies()).hasSize(2);
    }

    @Test
    @DisplayName("a plan somebody named and this version cannot read ends the run, rather than "
            + "quietly running a different one")
    void a_plan_that_cannot_be_read(@TempDir Path directory) throws IOException {
        Path broken = directory.resolve("plan.yaml");
        Files.writeString(broken, "version: 1\nstrategies: [\n");

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() ->
                        Campaigns.gather(Optional.of(broken), PETS, Set.of("fuzzing")))
                .describedAs("falling back to the plan RESTest carries, which narrows nothing, "
                        + "would answer a request to touch the operations that only read by "
                        + "touching all of them and writing to the API")
                .isInstanceOf(io.restest.core.json.JsonException.class)
                .hasMessageContaining("could not be read");
    }

    @Test
    @DisplayName("and so does a plan named that is not there, said as the missing file it is")
    void a_plan_that_is_not_there(@TempDir Path directory) {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> Campaigns.gather(
                        Optional.of(directory.resolve("nope.yaml")), PETS, Set.of("fuzzing")))
                .isInstanceOf(io.restest.core.json.JsonException.class)
                .describedAs("the path alone, which is all the platform says, is not a sentence")
                .hasMessageContaining("there is no such file");
    }

    @Test
    @DisplayName("a plan naming operations this API does not have is named back, because it was "
            + "probably written against an older document")
    void a_plan_naming_operations_nobody_has(@TempDir Path directory) throws IOException {
        Path plan = directory.resolve("plan.yaml");
        Files.writeString(plan, """
                version: 1
                strategies:
                  - name: nominal
                    share: 100
                    sources:
                      - source: random
                operations:
                  only: [getOwner, deleteOwner]
                """);

        assertThat(Campaigns.gather(Optional.of(plan), PETS, Set.of()).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("2 operation(s) this API does not have (getOwner, deleteOwner)");
    }

    @Test
    @DisplayName("a plan drawing on a list nobody handed over is named back too")
    void a_plan_naming_a_list_nobody_handed_over(@TempDir Path directory) throws IOException {
        Path plan = directory.resolve("plan.yaml");
        Files.writeString(plan, """
                version: 1
                strategies:
                  - name: nominal
                    share: 100
                    sources:
                      - dictionary: good-values
                      - source: random
                """);

        assertThat(Campaigns.gather(Optional.of(plan), PETS, Set.of()).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("draws on a list of values called 'good-values'")
                .contains("nothing will come from it");
    }

    @Test
    @DisplayName("a source written below invention is said to be unreachable, since invention "
            + "answers wherever anything could")
    void a_source_nothing_will_reach(@TempDir Path directory) throws IOException {
        Path plan = directory.resolve("plan.yaml");
        Files.writeString(plan, """
                version: 1
                strategies:
                  - name: nominal
                    share: 100
                    sources:
                      - source: random
                      - source: example
                      - source: default
                """);

        assertThat(Campaigns.gather(Optional.of(plan), PETS, Set.of()).problems())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("asks for 'random' before 2 other source(s)")
                .describedAs("said rather than refused: a shape nothing satisfies leaves "
                        + "invention with no answer either, and a plan may rely on that")
                .contains("hardly ever be reached");
    }

    @Test
    @DisplayName("asking for a different amount of pushing changes that and nothing else")
    void the_share_of_pushing_can_be_set() throws IOException {
        Campaign carried = Campaigns.shipped();

        Campaign tenth = carried.withTheShareOfPushingSetTo(10);
        assertThat(tenth.strategies()).extracting(Campaign.PlannedStrategy::share)
                .containsExactly(90, 10);
        assertThat(tenth.strategies().get(0).sources())
                .describedAs("the shares move; what each strategy draws on does not")
                .isEqualTo(carried.strategies().get(0).sources());

        assertThat(carried.withTheShareOfPushingSetTo(0).strategies())
                .describedAs("a strategy given none of the time would never run, so asking for "
                        + "no pushing takes it out rather than listing a run nobody will get")
                .extracting(Campaign.PlannedStrategy::name)
                .containsExactly("nominal");
    }
}
