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
 * The specification parser. The only module allowed to reference {@code io.swagger}.
 *
 * <p>Exports {@code io.restest.spec} so a caller can construct {@code SwaggerSpecificationParser}
 * directly. The {@code provides} declaration for {@code SpecificationParser} does not yet do
 * anything on its own - nothing in this project runs {@code ServiceLoader.load} against the
 * interface, and no consuming module declares {@code uses} - it is here so that when one does, this
 * module is already ready to be found rather than needing a second change alongside the first
 * consumer, at no cost while nothing looks for it.
 *
 * <p>Two of the required modules carry names nobody chose: {@code swagger.parser.core} and
 * {@code swagger.parser.v3} come out of jars that never say what they are called, so the name is
 * taken from the jar's filename instead. The build warns about that on every compile, and the
 * warning is expected rather than a defect to fix - it is a warning about somebody else's jars.
 *
 * <p>Asking for those two is also a deliberate choice over a third jar in the same library, which
 * offers a single convenient entry point covering both. That jar shares a package with two much
 * older jars the library still carries for reading documents written in the previous format, and a
 * package that arrives from several jars at once is ambiguous: some compilers quietly pick one and
 * carry on, others refuse the import outright and fill an editor with errors. Naming the two
 * readers keeps this module clear of that package, which is why the parser class here offers a
 * document to each reader in turn instead of calling the one entry point.
 */
module io.restest.spec {
    requires io.restest.core;
    requires swagger.parser.core;
    requires swagger.parser.v3;
    requires io.swagger.v3.core;
    requires io.swagger.v3.oas.models;
    requires org.yaml.snakeyaml;

    exports io.restest.spec;

    provides io.restest.core.spec.SpecificationParser with io.restest.spec.SwaggerSpecificationParser;
}
