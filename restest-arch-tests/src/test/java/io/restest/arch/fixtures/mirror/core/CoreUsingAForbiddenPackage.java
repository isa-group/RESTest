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
package io.restest.arch.fixtures.mirror.core;

import java.sql.Date;

/**
 * Violates {@code onlyOneModuleDependsOn} and {@code moduleHoldsNoDependencyOn}.
 *
 * <p>{@code java.sql} stands in for the real forbidden packages - {@code io.swagger} for the parser
 * rule, {@code java.net} and {@code okhttp3} for the no-network rule. Both rules take the forbidden
 * package as a parameter precisely so this fixture can use something already on the JDK's class
 * path: proving the rule fires needs no swagger-parser or OkHttp dependency in this module.
 */
public final class CoreUsingAForbiddenPackage {

    public Date today() {
        return new Date(0L);
    }
}
