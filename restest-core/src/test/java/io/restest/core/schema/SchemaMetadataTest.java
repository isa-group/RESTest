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
package io.restest.core.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.restest.core.json.JsonValue;
import io.restest.core.schema.SchemaMetadata.Access;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SchemaMetadataTest {

    @Test
    @DisplayName("nothing stated means nullable false, no enumeration, no default, read and write")
    void the_empty_metadata_states_nothing() {
        SchemaMetadata none = SchemaMetadata.none();

        assertThat(none.description()).isEmpty();
        assertThat(none.nullable()).isFalse();
        assertThat(none.enumeration()).isEmpty();
        assertThat(none.defaultValue()).isEmpty();
        assertThat(none.deprecated()).isFalse();
        assertThat(none.access()).isEqualTo(Access.READ_WRITE);
        assertThat(none.isEnumerated()).isFalse();
    }

    @Test
    @DisplayName("read-only and write-only are one choice, so they cannot both be true")
    void access_is_a_single_choice() {
        SchemaMetadata readOnly = SchemaMetadata.none().withAccess(Access.READ_ONLY);

        assertThat(readOnly.access()).isEqualTo(Access.READ_ONLY);
        assertThat(readOnly.withAccess(Access.WRITE_ONLY).access()).isEqualTo(Access.WRITE_ONLY);
    }

    @Test
    @DisplayName("each fact can be added without disturbing the others")
    void facts_are_added_one_at_a_time() {
        SchemaMetadata metadata = SchemaMetadata.none()
                .withDescription("the pet's status")
                .withNullable(true)
                .withEnumeration(List.of(JsonValue.of("sold")))
                .withDefault(JsonValue.of("sold"));

        assertThat(metadata.description()).contains("the pet's status");
        assertThat(metadata.nullable()).isTrue();
        assertThat(metadata.enumeration()).containsExactly(JsonValue.of("sold"));
        assertThat(metadata.defaultValue()).contains(JsonValue.of("sold"));
    }

    @Test
    @DisplayName("changing the list an enumeration was built from does not change the metadata")
    void an_enumeration_is_copied() {
        List<JsonValue> values = new ArrayList<>(List.of(JsonValue.of("sold")));

        SchemaMetadata metadata = SchemaMetadata.none().withEnumeration(values);
        values.add(JsonValue.of("pending"));

        assertThat(metadata.enumeration()).hasSize(1);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> metadata.enumeration().add(JsonValue.of("pending")));
    }
}
