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
package io.restest.arch.fixtures.mirror.exec;

import java.util.function.IntConsumer;

/**
 * Violates {@code onlyOneModuleMayTerminateTheProcess} without ever calling {@code System.exit}.
 *
 * <p>{@code System::exit} compiles to a method reference, not a method call, and ArchUnit models the
 * two separately. A rule written with {@code callMethod} alone reports the direct form and passes
 * this one, which is the realistic shape of the mistake: a fatal-error handler or a shutdown hook
 * passed as an {@code IntConsumer}. The JVM goes down either way.
 */
public final class ExecTerminatingByMethodReference {

    public IntConsumer fatalErrorHandler() {
        return System::exit;
    }
}
