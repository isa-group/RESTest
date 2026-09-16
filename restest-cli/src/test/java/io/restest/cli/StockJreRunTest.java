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
package io.restest.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.restest.core.execution.TestCase;
import io.restest.core.model.ApiModel;
import io.restest.gen.RandomTestCaseGenerator;
import io.restest.spec.SwaggerSpecificationParser;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The whole command, run on a plain Java runtime rather than on a developer's full installation.
 *
 * <p>Every other test runs RESTest on the machine that built it, which has all of Java on it. Most
 * of the world does not. The runtime in the official Java container images, and anything assembled
 * to be small, carries only the parts of Java that are compulsory and leaves the optional ones out -
 * and a tool that reaches for an optional part does not merely lose a feature there, it refuses to
 * start. That happened: the first thing every run did was ask for a random number generator by a
 * name that, on this release of Java, exists only where an optional piece is installed, so on an
 * ordinary Java runtime the tool died before sending a single request.
 *
 * <p>Java 21 rather than a newer one, and that is the whole point of the choice. Java 23 folded
 * those generators into the compulsory part, so the same mistake is invisible on 23, 24 and 25 -
 * which is what every machine this project is developed on runs. 21 is the oldest release RESTest
 * supports, so it is the one where a gate like this earns its keep.
 *
 * <p>This runs the command inside exactly such a runtime and checks it gets through a whole run.
 * Nothing is mocked and nothing is simulated: the same class files that were just compiled are
 * copied into the container and run by the Java that is in there.
 *
 * <p>The address it is pointed at answers nothing, on purpose. What is being tested is the tool,
 * not an API - so the interesting part is everything before the first reply would have arrived:
 * reading the document, inventing requests, sending them, writing the files, and saying afterwards
 * that nothing answered. An API that did answer would add nothing here and would take longer.
 *
 * <p>It needs a container runtime, so it does not run in an ordinary build.
 */
@Tag("smoke")
class StockJreRunTest {

    /** Asked for by name, so that a machine without containers fails rather than quietly passing. */
    private static final String ASKED_FOR = "restest.smoke.required";

    /**
     * The runtime this is really about: the official Java 21 image, pinned by content.
     *
     * <p>Pinned rather than followed, for the usual reason - what it contains is then the same next
     * month - and by content rather than by tag so that it keeps working on both kinds of processor
     * this project is built on.
     *
     * <p>Whoever raises this to a newer image should know what they are giving up: on 23 and later
     * the piece that was missing is part of every runtime, so the same image would still pass with
     * the mistake put back in. The test below refuses to run on such an image rather than pass
     * quietly, but the pin is what keeps it meaningful in the first place.
     */
    private static final DockerImageName STOCK_JRE = DockerImageName.parse(
            "eclipse-temurin@sha256:"
                    + "6cbdfc89c9657478bc5abea638030310f6c0267404e98a5808097bb1925932f1");

    /** Where the compiled code is put inside the container. */
    private static final String INSIDE = "/restest";

    /** The document, and how long the run gets. */
    private static final String SPECIFICATION = INSIDE + "/pet-shelter.yaml";
    private static final String BUDGET = "5s";

    /**
     * An address nothing is listening on: the run must survive being pointed at one, and it gets
     * there quickly, since a refused connection costs nothing to wait for.
     */
    private static final String NOWHERE = "http://127.0.0.1:1";

    /** The generator RESTest used to ask for by name, and the release it stopped being optional in. */
    private static final String ONCE_ASKED_FOR = "L64X128MixRandom";
    private static final String EXPECTED_RELEASE = "21.";

    /** The number both runtimes are given, and how many requests are compared. */
    private static final long SEED = 20260916L;
    private static final int COMPARED = 12;

    private static GenericContainer<?> jre;

    @BeforeAll
    static void startTheRuntime() throws URISyntaxException {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            if (Boolean.getBoolean(ASKED_FOR)) {
                fail("this gate was asked for, but there is no container runtime on this machine, "
                        + "so the plain Java runtime it tests cannot be started");
            }
            Assumptions.abort("no container runtime on this machine");
        }
        jre = new GenericContainer<>(STOCK_JRE)
                // The image would otherwise start a Java shell and exit. Nothing is meant to happen
                // until a command is sent in; the container only has to stay up until then.
                .withCommand("sleep", "infinity")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(specificationOnThisMachine()), SPECIFICATION);
        int entry = 0;
        for (String onThisMachine : System.getProperty("java.class.path")
                .split(File.pathSeparator)) {
            // Numbered, because two modules both compile into a directory called `classes` and the
            // second copy would otherwise land on top of the first.
            jre.withCopyFileToContainer(MountableFile.forHostPath(onThisMachine),
                    INSIDE + "/cp/" + entry++ + "/" + Path.of(onThisMachine).getFileName());
        }
        jre.start();
    }

    @AfterAll
    static void stopTheRuntime() {
        if (jre != null) {
            jre.stop();
        }
    }

    @Test
    @DisplayName("the runtime this is tested on really is one the old code could not have started on")
    void the_runtime_is_one_the_old_code_could_not_have_started_on() throws Exception {
        Container.ExecResult probe = probeInsideTheContainer();

        assertThat(probe.getExitCode())
                .describedAs("the probe itself has to run, or the two assertions below are about "
                        + "an empty string. It said:%n%s%n%s", probe.getStdout(), probe.getStderr())
                .isZero();
        assertThat(lineOf(probe, "java="))
                .describedAs("the pinned image is meant to be Java 21, the oldest release RESTest "
                        + "supports and one of the two where the generator it used to ask for is "
                        + "optional")
                .startsWith(EXPECTED_RELEASE);
        assertThat(lineOf(probe, "generator="))
                .describedAs("This gate is only worth anything on a runtime that cannot provide "
                        + ONCE_ASKED_FOR + ". Asking the runtime for the generator is the only "
                        + "honest way to establish that: the module it used to live in, "
                        + "jdk.random, is equally absent from the module list of Java 23 and "
                        + "later, where the generators were folded into java.base and everything "
                        + "works. A gate that looked for the module name would therefore pass on a "
                        + "modern image while proving nothing at all")
                .isEqualTo("absent");
    }

    @Test
    @DisplayName("a whole run gets through on a plain Java runtime, and keeps what it was asked to")
    void a_run_completes_on_a_plain_java_runtime() throws Exception {
        Container.ExecResult run = jre.execInContainer("java", "-cp", classPathInsideTheContainer(),
                "io.restest.cli.Restest", "run", SPECIFICATION, "--url", NOWHERE,
                "--budget", BUDGET, "--out", "/tmp/out", "--store");

        assertThat(run.getStderr())
                .describedAs("the failure this gate exists for: asking for a generator by a name "
                        + "the runtime does not have, which ended the run before it began")
                .doesNotContain("random number generator");
        assertThat(run.getStdout())
                .describedAs("the run read the document and worked out what it could try, which is "
                        + "everything that used to happen after the tool had already died. It "
                        + "said:%n%s%n%s", run.getStdout(), run.getStderr())
                .contains("operations can be tested, seed")
                .contains("report written to")
                // The database driver loads a library compiled for the machine it runs on, which is
                // the other thing a small runtime image is capable of not having.
                .contains("run stored in");
        assertThat(run.getExitCode())
                .describedAs("3 is 'nothing could be tested', which is the truth: nothing was "
                        + "listening. 4 would mean RESTest itself broke, which is what this gate "
                        + "watches for. It said:%n%s%n%s", run.getStdout(), run.getStderr())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("the same number produces the same requests on both runtimes, not merely on each")
    void the_same_seed_produces_the_same_requests_on_both_runtimes() throws Exception {
        String inTheContainer = lineOf(probeInsideTheContainer(), "requests=");
        String onThisMachine = Probe.requestsFor(specificationOnThisMachine().toString(), SEED);

        assertThat(inTheContainer)
                .describedAs("A seed is worth printing only if it means the same run wherever it "
                        + "is used. Two runtimes, two different sets of Java modules, one number - "
                        + "and the requests have to match request for request and value for value. "
                        + "This is the assertion the whole increment rests on; everything else says "
                        + "the tool starts, and this says a seed still means something once it "
                        + "has.%nInside:  %s%nOutside: %s", inTheContainer, onThisMachine)
                .isEqualTo(onThisMachine)
                .isNotBlank();
    }

    /** Runs the probe in the container: what Java it is, what it can provide, what it would send. */
    private static Container.ExecResult probeInsideTheContainer() throws Exception {
        return jre.execInContainer("java", "-cp", classPathInsideTheContainer(),
                Probe.class.getName(), SPECIFICATION, Long.toString(SEED));
    }

    /** One of the probe's answers, by the name it printed it under. */
    private static String lineOf(Container.ExecResult probe, String name) {
        return probe.getStdout().lines()
                .filter(line -> line.startsWith(name))
                .map(line -> line.substring(name.length()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the probe printed no '" + name + "' line. "
                        + "It said:\n" + probe.getStdout() + "\n" + probe.getStderr()));
    }

    /**
     * The part that runs inside the container, and the same code the test runs outside it.
     *
     * <p>Three answers, printed one per line: which Java this is, whether it can provide the
     * generator RESTest used to ask for by name, and the requests it would send for a given number.
     * The third is the interesting one - computed here so that both runtimes compute it the same
     * way, leaving the runtime as the only difference between the two answers.
     *
     * <p>Deliberately mentions nothing of the class that holds it: it is started as a program of its
     * own inside the container, and a reference to the surrounding test would drag a test framework
     * into a place that has no reason to need one.
     */
    public static final class Probe {

        private Probe() {
        }

        public static void main(String[] arguments) {
            System.out.println("java=" + System.getProperty("java.version"));
            System.out.println("generator=" + whetherThisRuntimeHasIt());
            System.out.println("requests=" + requestsFor(arguments[0], Long.parseLong(arguments[1])));
        }

        /** Whether this runtime can still provide the generator that used to be asked for by name. */
        private static String whetherThisRuntimeHasIt() {
            try {
                RandomGeneratorFactory.of(ONCE_ASKED_FOR).create(1L);
                return "present";
            } catch (RuntimeException notOnThisRuntime) {
                return "absent";
            }
        }

        /**
         * The first requests a run would send, as text.
         *
         * <p>What each request is made of, never the identifier it is labelled with: those are drawn
         * fresh for every test case on purpose, so that two runs happening at once cannot hand out
         * the same one, and comparing them would compare the one part of a run that is meant to
         * differ.
         */
        static String requestsFor(String specification, long seed) {
            ApiModel model = new SwaggerSpecificationParser().parse(specification);
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, seed);
            return IntStream.range(0, COMPARED)
                    .mapToObj(drawn -> generator.generate().map(Probe::describe).orElse("(none)"))
                    .collect(Collectors.joining(" | "));
        }

        private static String describe(TestCase testCase) {
            return testCase.operation() + " " + testCase.parameterValues().stream()
                    .map(value -> value.name() + "=" + value.value())
                    .sorted()
                    .collect(Collectors.joining(",", "[", "]"));
        }
    }

    /** Where the copied class path ends up, in the order this machine had it. */
    private static String classPathInsideTheContainer() {
        String[] here = System.getProperty("java.class.path").split(File.pathSeparator);
        StringBuilder there = new StringBuilder();
        for (int entry = 0; entry < here.length; entry++) {
            there.append(entry == 0 ? "" : ":")
                    .append(INSIDE).append("/cp/").append(entry).append('/')
                    .append(Path.of(here[entry]).getFileName());
        }
        return there.toString();
    }

    private static Path specificationOnThisMachine() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                StockJreRunTest.class.getResource("/pet-shelter.yaml"),
                "pet-shelter.yaml is not on the test class path").toURI());
    }
}
