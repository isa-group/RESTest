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
package io.restest.arch.fixtures.mirror.cli;

/**
 * Breaks no rule. The command-line module is the one place allowed to end the process, so this
 * class is what stops {@code onlyOneModuleMayTerminateTheProcess} from being a rule that simply
 * bans {@code System.exit} everywhere.
 */
public final class CliTerminatingTheProcess {

    public void exitWithFailure() {
        System.exit(1);
    }
}
