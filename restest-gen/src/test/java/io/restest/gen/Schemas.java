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

import io.restest.core.gen.ValueRequest;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.CanonicalSchema;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/** Small helpers shared by the generation tests. */
final class Schemas {

    private Schemas() {
    }

    /** A question about one parameter, which is all any source of values is ever given. */
    static ValueRequest asking(CanonicalSchema schema) {
        return new ValueRequest(OperationId.of("GET /widgets"), "widgetId",
                ParameterLocation.QUERY, schema);
    }

    /** A source of randomness that makes the same choices every time the tests run. */
    static RandomGenerator fixedRandom() {
        return RandomGeneratorFactory.getDefault().create(20260912L);
    }
}
