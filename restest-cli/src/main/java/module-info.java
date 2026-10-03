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
/**
 * The command line: {@code restest run <specification> --url <base> --budget <duration>}.
 *
 * <p>This is the only module that ends the program, and the only one that sees every other module
 * at once. The run loop lives here, so that the part of the tool that invents requests never gains
 * a dependency on the part that sends them or on the part that stores what came back.
 *
 * <p>There is no {@code opens} for the command-line framework. The tool runs on the class path, not
 * the module path, because two versions of the specification parser's module cannot sit on a module
 * path together.
 */
module io.restest.cli {
    requires info.picocli;
    requires io.restest.core;
    requires io.restest.spec;
    requires io.restest.idl;
    requires io.restest.gen;
    requires io.restest.exec;
    requires io.restest.store;
    requires io.restest.oracles;
    requires io.restest.report;
}
