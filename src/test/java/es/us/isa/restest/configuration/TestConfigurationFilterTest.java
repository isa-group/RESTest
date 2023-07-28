package es.us.isa.restest.configuration;

import io.swagger.v3.oas.models.PathItem.HttpMethod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

class TestConfigurationFilterTest {

    /**
     * Here we make sure that when we try to parse a filter description with non-wildcard path and each supported method
     * we get no errors and a TestConfigurationFilter containing all required methods.
     *
     * Non-wildcard path is a valid path to an endpoint, like /search, /pet/list, etc.
     */
    @DisplayName("All methods in filter descriptions are parsed correctly when path is not a wildcard")
    @ParameterizedTest
    @MethodSource("methods")
    public void parsesAllMethodsWhenPathIsNotWildcard(String methodDescription, List<HttpMethod> expectedHttpMethods) {
        final var filterDescription = "/path:" + methodDescription;

        final TestConfigurationFilter filter = Assertions.assertDoesNotThrow(
                () -> TestConfigurationFilter.parse(filterDescription),
                "An unexpected exception was thrown while parsing '" + filterDescription + "'");

        Assertions.assertEquals(expectedHttpMethods.size(), filter.getMethods().size());
        expectedHttpMethods.forEach(
                expectedMethod -> Assertions.assertTrue(
                        filter.getMethods().contains(expectedMethod),
                        String.format("After parsing '%s' filter must contain method %s, but it doesn't!",
                                filterDescription, expectedMethod)
                )
        );
    }

    /**
     * Here we make sure that when we try to parse a filter description with wildcard path and each supported method
     * we get no errors and a TestConfigurationFilter containing all required methods.
     *
     * A wildcard path is a path description that matches any path
     */
    @DisplayName("All methods in filter descriptions are parsed correctly when path is a wildcard")
    @ParameterizedTest
    @MethodSource("methods")
    public void parsesAllMethodsWhenPathIsWildcard(String methodDescription, List<HttpMethod> expectedHttpMethods) {
        final var filterDescription = TestConfigurationFilter.ALL_PATHS_WILDCARD + ":" + methodDescription;

        final TestConfigurationFilter filter = Assertions.assertDoesNotThrow(
                () -> TestConfigurationFilter.parse(filterDescription),
        "An unexpected exception was thrown while parsing '" + filterDescription + "'");

        Assertions.assertEquals(expectedHttpMethods.size(), filter.getMethods().size());
        expectedHttpMethods.forEach(
                expectedMethod -> Assertions.assertTrue(
                        filter.getMethods().contains(expectedMethod),
                        String.format("After parsing '%s' filter must contain method %s, but it doesn't!",
                                filterDescription, expectedMethod)
                )
        );
    }

    static Stream<Arguments> methods() {
        return Stream.of(
                Arguments.of("get",    List.of(HttpMethod.GET)),
                Arguments.of("post",   List.of(HttpMethod.POST)),
                Arguments.of("put",    List.of(HttpMethod.PUT)),
                Arguments.of("patch",  List.of(HttpMethod.PATCH)),
                Arguments.of("delete", List.of(HttpMethod.DELETE)),
                Arguments.of("all",    List.of(HttpMethod.GET,
                                                         HttpMethod.POST,
                                                         HttpMethod.PUT,
                                                         HttpMethod.PATCH,
                                                         HttpMethod.DELETE))
        );
    }

    @DisplayName("Parsing fails when filter description has invalid format")
    @ParameterizedTest
    @ValueSource(strings = {
            "", "  ",
            ":", " : ",
            "/path:",
            ":get",
            "/path:get,hello",
            "/path:get:get"
    })
    public void assertInvalidFormatThrows(String filterDescription) {
        Assertions.assertThrows(Exception.class, () -> TestConfigurationFilter.parse(filterDescription),
                "An exception was expected to be thrown while parsing '" + filterDescription + "'");
    }

    /**
     * When we parse a filter description with wildcard path the resulting filter has to have null in its 'path' field.
     */
    @DisplayName("Wildcard path is parsed into null")
    @Test
    public void assertWildcardPathLeadsToNullPath() {
        final var filterDescription = TestConfigurationFilter.ALL_PATHS_WILDCARD + ":all";
        Assertions.assertNull(
                TestConfigurationFilter.parse(filterDescription).getPath(),
                "A path was expected to be null after parsing " + filterDescription
        );
    }

    /**
     * When we parse a filter description with non-wildcard path then resulting filter has to have the exact same path,
     * but with all leading and trailing spaces removed.
     */
    @DisplayName("Non-wildcard is trimmed and then taken as is")
    @ParameterizedTest
    @ValueSource(strings = {"  /path  ", "/path"})
    public void assertNonWildcardIsParsedCorrectly(String path) {
        final var filterDescription = path + ":all";
        Assertions.assertEquals(path.trim(), TestConfigurationFilter.parse(filterDescription).getPath());
    }
}