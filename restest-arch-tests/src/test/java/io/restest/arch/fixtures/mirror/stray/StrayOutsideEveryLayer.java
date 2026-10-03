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
package io.restest.arch.fixtures.mirror.stray;

/**
 * Violates {@code dependenciesPointInwards} by belonging to no layer at all.
 *
 * <p>This is the failure mode the layered rule has on its own: with
 * {@code consideringOnlyDependenciesInLayers}, a class whose package matches none of the nine has
 * every dependency ignored, so it could reach anywhere it liked and the build would stay green. The
 * realistic version is not a package called "stray" but a plausible near-miss - a domain model that
 * lands in {@code io.restest.model}, or an oracle package written in the singular.
 */
public final class StrayOutsideEveryLayer {

    public String describe() {
        return "in no layer";
    }
}
