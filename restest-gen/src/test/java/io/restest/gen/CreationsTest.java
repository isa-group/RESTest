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

import static io.restest.gen.TheCorpus.operation;
import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreationsTest {

    private static final ApiModel PET_CLINIC = TheCorpus.priority("pet-clinic");

    @Test
    @DisplayName("an owner lives one gap past where it is created, and what is under it is found")
    void an_owner_and_what_is_under_it() {
        Creations.Creation owners = Creations.among(PET_CLINIC.operations())
                .of(operation(PET_CLINIC, HttpMethod.POST, "/owners")).orElseThrow();

        assertThat(owners.own()).hasSize(1);
        Creations.Address own = owners.own().get(0);
        assertThat(own.path()).isEqualTo("/owners/{ownerId}");
        assertThat(own.gap()).isEqualTo("ownerId");
        assertThat(own.byPrefix()).isTrue();
        assertThat(own.operations()).containsOnlyKeys(HttpMethod.GET, HttpMethod.PUT,
                HttpMethod.DELETE);
        assertThat(owners.under()).extracting(under -> under.operation().method() + " "
                        + under.operation().path())
                .describedAs("in the order the document declares them")
                .containsExactly(
                        "POST /owners/{ownerId}/pets",
                        "GET /owners/{ownerId}/pets/{petId}",
                        "PUT /owners/{ownerId}/pets/{petId}",
                        "POST /owners/{ownerId}/pets/{petId}/visits");
        assertThat(owners.under()).allSatisfy(under ->
                assertThat(under.gap()).isEqualTo("ownerId"));
    }

    @Test
    @DisplayName("a pet made under an owner is also found where the API deletes pets, by its kind")
    void a_pet_is_found_by_its_kind_too() {
        Creations.Creation pets = Creations.among(PET_CLINIC.operations())
                .of(operation(PET_CLINIC, HttpMethod.POST, "/owners/{ownerId}/pets"))
                .orElseThrow();

        assertThat(pets.own()).extracting(Creations.Address::path)
                .containsExactly("/owners/{ownerId}/pets/{petId}", "/pets/{petId}");
        assertThat(pets.own().get(0).shared())
                .describedAs("a pet asked for under its owner is asked for under the owner it was "
                        + "created under")
                .containsExactly(Map.entry("ownerId", "ownerId"));
        assertThat(pets.own().get(1).shared())
                .describedAs("an address found by kind shares nothing with the creation's own")
                .isEmpty();
        assertThat(pets.first(HttpMethod.DELETE).orElseThrow().operation().path())
                .describedAs("pet-clinic deletes a pet only at /pets/{petId}")
                .isEqualTo("/pets/{petId}");
        assertThat(pets.addressWith(HttpMethod.GET, HttpMethod.DELETE).orElseThrow().path())
                .describedAs("where a pet can be read and deleted at the same address")
                .isEqualTo("/pets/{petId}");
        assertThat(pets.first(HttpMethod.GET).orElseThrow().operation().path())
                .describedAs("the address one gap past the creation is asked first")
                .isEqualTo("/owners/{ownerId}/pets/{petId}");
        assertThat(pets.under()).extracting(under -> under.operation().path())
                .containsExactly("/owners/{ownerId}/pets/{petId}/visits");
        assertThat(pets.under().get(0).gap()).isEqualTo("petId");
        assertThat(pets.under().get(0).shared()).containsExactly(Map.entry("ownerId", "ownerId"));
    }

    @Test
    @DisplayName("a kafka topic lives by its name, under the cluster it was created in")
    void a_topic_and_what_is_under_it() {
        ApiModel kafka = TheCorpus.priority("kafka-rest-proxy");
        Creations.Creation topics = Creations.among(kafka.operations())
                .of(operation(kafka, HttpMethod.POST, "/v3/clusters/{cluster_id}/topics"))
                .orElseThrow();

        Creations.Address own = topics.own().get(0);
        assertThat(own.path()).isEqualTo("/v3/clusters/{cluster_id}/topics/{topic_name}");
        assertThat(own.gap()).isEqualTo("topic_name");
        assertThat(own.shared()).containsExactly(Map.entry("cluster_id", "cluster_id"));
        assertThat(own.operations()).containsOnlyKeys(HttpMethod.GET, HttpMethod.PATCH,
                HttpMethod.DELETE);
        assertThat(topics.under()).hasSize(14);
        assertThat(topics.under()).allSatisfy(under -> {
            assertThat(under.gap()).isEqualTo("topic_name");
            assertThat(under.shared()).containsEntry("cluster_id", "cluster_id");
        });
    }

    @Test
    @DisplayName("gaps match gaps whatever they are called: {id} under a hospital is a hospital's")
    void gaps_match_by_position() {
        ApiModel gestao = TheCorpus.priority("gestao-hospital");
        Creations.Creation hospitals = Creations.among(gestao.operations())
                .of(operation(gestao, HttpMethod.POST, "/v1/hospitais/"))
                .orElseThrow();

        assertThat(hospitals.own().get(0).path()).isEqualTo("/v1/hospitais/{hospital_id}");
        assertThat(hospitals.under()).extracting(under -> under.operation().path())
                .contains("/v1/hospitais/{id}/leitos", "/v1/hospitais/{hospital_id}/estoque",
                        "/v1/hospitais/{id}/transferencia/{productId}");
        assertThat(hospitals.under()).filteredOn(under ->
                        under.operation().path().equals("/v1/hospitais/{id}/leitos"))
                .extracting(Creations.Under::gap)
                .containsExactly("id");
    }

    @Test
    @DisplayName("a notebook is read, changed and deleted at its own address")
    void a_notebook() {
        ApiModel notebooks = TheCorpus.priority("notebook-manager");
        Creations.Creation made = Creations.among(notebooks.operations())
                .of(operation(notebooks, HttpMethod.POST, "/api/notebooks"))
                .orElseThrow();

        assertThat(made.own()).singleElement().satisfies(own -> {
            assertThat(own.path()).isEqualTo("/api/notebooks/{notebookId}");
            assertThat(own.operations()).containsOnlyKeys(HttpMethod.GET, HttpMethod.PATCH,
                    HttpMethod.DELETE);
        });
        assertThat(made.under()).isEmpty();
    }

    @Test
    @DisplayName("an operation the run may not touch is never where a thing lives")
    void only_what_the_run_may_touch() {
        List<Operation> getsAndPosts = PET_CLINIC.operations().stream()
                .filter(operation -> operation.method() == HttpMethod.GET
                        || operation.method() == HttpMethod.POST)
                .toList();

        Creations.Creation owners = Creations.among(getsAndPosts)
                .of(operation(PET_CLINIC, HttpMethod.POST, "/owners")).orElseThrow();

        assertThat(owners.own().get(0).operations()).containsOnlyKeys(HttpMethod.GET);
        assertThat(owners.first(HttpMethod.DELETE)).isEmpty();
        assertThat(owners.under()).extracting(under -> under.operation().method())
                .doesNotContain(HttpMethod.PUT, HttpMethod.DELETE);
    }

    @Test
    @DisplayName("a POST whose address ends in a gap does something to a thing that exists, so it "
            + "makes nothing with an address of its own")
    void a_post_to_a_thing_makes_nothing() {
        ApiModel petstore = TheCorpus.community("Petstore");
        Creations among = Creations.among(petstore.operations());

        Creations.Creation update = among.of(operation(petstore, HttpMethod.POST, "/pet/{petId}"))
                .orElseThrow();

        assertThat(update.own())
                .describedAs("the pet it answers with is the one its address named, which the run "
                        + "did not make")
                .isEmpty();
        assertThat(update.under()).isEmpty();
        assertThat(update.list()).isEmpty();
        assertThat(among.of(operation(petstore, HttpMethod.POST, "/pet")).orElseThrow().own())
                .extracting(Creations.Address::path)
                .containsExactly("/pet/{petId}");
    }

    @Test
    @DisplayName("an address elsewhere is about the same kind of thing when the word before its gap "
            + "says so, whatever the gap is called")
    void the_word_before_the_gap_names_the_kind() {
        ApiModel github = TheCorpus.community("GitHub");

        Creations.Creation invitations = Creations.among(github.operations())
                .of(operation(github, HttpMethod.POST, "/orgs/{org}/invitations")).orElseThrow();

        assertThat(invitations.own()).extracting(Creations.Address::path)
                .describedAs("{invitation_id} names an invitation, but repository_invitations "
                        + "holds another kind of thing")
                .contains("/orgs/{org}/invitations/{invitation_id}")
                .doesNotContain("/user/repository_invitations/{invitation_id}");
    }

    @Test
    @DisplayName("an operation that is not a creation makes nothing")
    void a_read_makes_nothing() {
        assertThat(Creations.among(PET_CLINIC.operations())
                .of(operation(PET_CLINIC, HttpMethod.GET, "/owners"))).isEmpty();
    }
}
