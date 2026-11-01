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
package io.restest.arch;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.json.YamlText;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks that RESTest started through jbang starts the way it does from a release archive.
 *
 * <p>jbang is a tool that runs a Java program straight from Maven Central, downloading a Java first
 * when the machine has none. What it runs is said by an entry in a catalog,
 * {@code dist/jbang/jbang-catalog.json}: which class to start, the option Java needs for RESTest's
 * database driver, and the oldest Java RESTest runs on. The two launchers of a release archive,
 * {@code dist/bin/restest} and {@code dist/bin/restest.cmd}, say the same three things for anybody
 * who runs RESTest with a Java of their own, and so does the container image, which starts the first.
 * Written twice, they can drift apart: a renamed class, or an option added to the launchers alone,
 * would leave jbang starting RESTest some other way, or not at all, with every launcher still working.
 * This test reads both and fails when they disagree, so that whoever changes one changes the other.
 */
class JbangAliasTest {

    /** The line of {@code bin/restest} that starts Java: options, the class path, the class. */
    private static final Pattern UNIX_START = Pattern.compile(
            "(?m)^exec \"\\$java\" (?<options>.*?) ?-cp \"[^\"]+\" (?<main>[\\w.$]+) \"\\$@\"$");

    /** The same line of {@code bin\restest.cmd}. */
    private static final Pattern WINDOWS_START = Pattern.compile(
            "(?m)^\"%RESTEST_JAVA%\" (?<options>.*?) ?-cp \"[^\"]+\" (?<main>[\\w.$]+) %\\*$");

    /** Where {@code bin/restest} refuses a Java older than the one it names. */
    private static final Pattern UNIX_OLDEST = Pattern.compile("\\[ \"\\$major\" -lt (\\d+) ]");

    /** Where {@code bin\restest.cmd} does. */
    private static final Pattern WINDOWS_OLDEST = Pattern.compile("%RESTEST_JAVA_MAJOR% LSS (\\d+) ");

    @Test
    @DisplayName("the jbang alias starts the class both launchers start, and that class is RESTest's"
            + " entry point (ADR-0045)")
    void the_alias_starts_the_class_the_launchers_start() throws ReflectiveOperationException {
        String main = text(alias(), "main");

        assertThat(main)
                .describedAs("dist/bin/restest starts another class than the jbang alias names")
                .isEqualTo(start(UNIX_START, "dist/bin/restest").group("main"));
        assertThat(main)
                .describedAs("dist/bin/restest.cmd starts another class than the jbang alias names")
                .isEqualTo(start(WINDOWS_START, "dist/bin/restest.cmd").group("main"));
        Method entry = Class.forName(main).getMethod("main", String[].class);
        assertThat(Modifier.isPublic(entry.getModifiers()) && Modifier.isStatic(entry.getModifiers())
                && entry.getReturnType() == void.class)
                .describedAs("%s has no public static void main(String[])", main)
                .isTrue();
    }

    @Test
    @DisplayName("the jbang alias passes Java the options both launchers pass (ADR-0045)")
    void the_alias_passes_the_options_the_launchers_pass() {
        List<String> options = new ArrayList<>();
        for (JsonValue option : YamlText.asList(member(alias(), "runtime-options"),
                "'runtime-options'", "the jbang catalog")) {
            options.add(YamlText.asText(option, "a runtime option", "the jbang catalog"));
        }

        assertThat(options)
                .describedAs("dist/bin/restest passes Java other options than the jbang alias")
                .isEqualTo(words(start(UNIX_START, "dist/bin/restest").group("options")));
        assertThat(options)
                .describedAs("dist/bin/restest.cmd passes Java other options than the jbang alias")
                .isEqualTo(words(start(WINDOWS_START, "dist/bin/restest.cmd").group("options")));
    }

    @Test
    @DisplayName("the jbang alias asks for the oldest Java both launchers accept, or any later one"
            + " (ADR-0045)")
    void the_alias_asks_for_the_java_the_launchers_accept() {
        String oldest = oldest(UNIX_OLDEST, "dist/bin/restest");

        assertThat(oldest(WINDOWS_OLDEST, "dist/bin/restest.cmd"))
                .describedAs("the two launchers refuse different Javas")
                .isEqualTo(oldest);
        assertThat(text(alias(), "java"))
                .describedAs("the launchers start RESTest on Java %s or later, and the jbang alias "
                        + "should ask jbang for the same", oldest)
                .isEqualTo(oldest + "+");
    }

    @Test
    @DisplayName("the jbang catalog has one alias, restest, naming RESTest's command line on Maven "
            + "Central at the version dist/jbang/catalog.sh fills in (ADR-0045)")
    void the_catalog_names_restest_on_maven_central() {
        assertThat(aliases().members().keySet()).containsExactly("restest");
        assertThat(text(alias(), "script-ref")).isEqualTo("es.us.isa.restest:restest-cli:@VERSION@");
    }

    private static JsonValue.JsonObject aliases() {
        JsonValue.JsonObject catalog = YamlText.asObject(
                JsonText.read(read("dist/jbang/jbang-catalog.json")), "the catalog", "the jbang catalog");
        return YamlText.asObject(member(catalog, "aliases"), "'aliases'", "the jbang catalog");
    }

    private static JsonValue.JsonObject alias() {
        return YamlText.asObject(member(aliases(), "restest"), "'restest'", "the jbang catalog");
    }

    private static JsonValue member(JsonValue.JsonObject in, String name) {
        return in.member(name).orElseThrow(() ->
                new AssertionError("no '" + name + "' in " + in.members().keySet()));
    }

    private static String text(JsonValue.JsonObject in, String name) {
        return YamlText.asText(member(in, name), "'" + name + "'", "the jbang catalog");
    }

    /** The one line of a launcher that starts Java, or a failure saying the launcher has none or two. */
    private static MatchResult start(Pattern line, String launcher) {
        List<MatchResult> starts = line.matcher(read(launcher)).results().toList();
        assertThat(starts)
                .describedAs("%s should start Java on exactly one line, of the form this test reads: %s",
                        launcher, line.pattern())
                .hasSize(1);
        return starts.get(0);
    }

    private static String oldest(Pattern check, String launcher) {
        Matcher matcher = check.matcher(read(launcher));
        assertThat(matcher.find())
                .describedAs("%s has no check of the oldest Java the way this test reads it: %s",
                        launcher, check.pattern())
                .isTrue();
        return matcher.group(1);
    }

    private static List<String> words(String options) {
        return options.isBlank() ? List.of() : Arrays.asList(options.trim().split(" +"));
    }

    /** A file of the checkout, with Windows line endings read as Unix ones. */
    private static String read(String file) {
        return RepositoryRoot.read(RepositoryRoot.locate().resolve(file)).replace("\r\n", "\n");
    }
}
