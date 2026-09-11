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

import java.io.IOException;

/**
 * Breaks no rule, and must stay that way.
 *
 * <p>In bytecode this is indistinguishable from {@link ExecTerminatingByProcessHandle}: both are an
 * {@code invokeinterface} on {@code java.lang.ProcessHandle.destroy}. The difference is whose
 * process the handle refers to, and killing a child is legitimate - the out-of-process transport at
 * M6.2 must be able to reap its helper. The rule tells them apart by whether the class also calls
 * {@code ProcessHandle.current()}, which this one does not.
 */
public final class ExecReapingAChildProcess {

    public void runHelper() throws IOException {
        Process helper = new ProcessBuilder("true").start();
        helper.toHandle().destroy();
    }
}
