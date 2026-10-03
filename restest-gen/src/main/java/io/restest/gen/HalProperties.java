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

import io.restest.core.json.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Takes out of a request body the two properties a server keeps for itself in a common way of
 * writing JSON.
 *
 * <p>That way of writing JSON is called HAL. A reply written in it carries {@code _links} - the
 * addresses of things related to the one returned - and {@code _embedded} - other things carried
 * inside it. Both are written by the server, about its own addresses, and HAL reserves the two
 * names for exactly that. An API built on HAL that finds them in a request reads them the way HAL
 * writes them, whatever the API's description says they look like: one of the APIs this tool is
 * measured on describes {@code _links} as a list, reads it as an object keyed by the kind of link,
 * and answers a request carrying one as described with an error.
 *
 * <p>So a body is sent without them. The {@link RandomTestCaseGenerator} asks for this once, when a
 * body is complete and about to become part of a request, which is the one point every body passes
 * through - invented, sent back from a reply, taken from a list of values or from the description's
 * own samples. A request made by changing one thing in an accepted one starts from a body that
 * already went through it. A property called {@code links}, without the underscore, is an ordinary
 * property and stays.
 */
final class HalProperties {

    /** The two names HAL keeps for what a server writes into its replies. */
    static final Set<String> RESERVED = Set.of("_links", "_embedded");

    private HalProperties() {
    }

    /**
     * The value with every member called {@code _links} or {@code _embedded} taken out of every
     * object in it, however deep: inside other objects, and inside the elements of lists.
     *
     * @param value a body, or any part of one
     * @return the same value when it holds neither, or a copy without them
     */
    static JsonValue withoutTheServersOwn(JsonValue value) {
        return switch (value) {
            case JsonValue.JsonObject thing -> {
                Map<String, JsonValue> kept = new LinkedHashMap<>();
                boolean changed = false;
                for (Map.Entry<String, JsonValue> member : thing.members().entrySet()) {
                    if (RESERVED.contains(member.getKey())) {
                        changed = true;
                        continue;
                    }
                    JsonValue inside = withoutTheServersOwn(member.getValue());
                    changed |= inside != member.getValue();
                    kept.put(member.getKey(), inside);
                }
                yield changed ? new JsonValue.JsonObject(kept) : thing;
            }
            case JsonValue.JsonArray list -> {
                List<JsonValue> kept = new ArrayList<>();
                boolean changed = false;
                for (JsonValue element : list.elements()) {
                    JsonValue inside = withoutTheServersOwn(element);
                    changed |= inside != element;
                    kept.add(inside);
                }
                yield changed ? new JsonValue.JsonArray(kept) : list;
            }
            default -> value;
        };
    }
}
