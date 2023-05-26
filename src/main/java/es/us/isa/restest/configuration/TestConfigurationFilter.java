package es.us.isa.restest.configuration;

import java.util.*;

import io.swagger.v3.oas.models.PathItem.HttpMethod;

/**
 * TestConfiguration objects are key in RESTest. They include all the
 * information required to test an API (data dictionaries, authentication data,
 * etc), complementing the information provided by the API specification. This
 * class allow to define filters to describe the specific operations or paths to
 * be tested. A filter is composed of a path and a set of HTTP methods (GET,
 * POST...). (GET, POST...)
 */
public class TestConfigurationFilter {
	public static final String ALL_PATHS_WILDCARD = "*";
	private final String path; // Path to test (null for all)
	private final Set<HttpMethod> methods; // Methods to test

	public TestConfigurationFilter(String path, Set<HttpMethod> methods) {
		this.path = path;
		this.methods = Collections.unmodifiableSet(methods);
	}

	public String getPath() {
		return path;
	}

	public Collection<HttpMethod> getMethods() {
		return methods;
	}

	/**
	 * Parses a text description of a filter into an {@link TestConfigurationFilter} instance.
	 * <p>
	 * A filter can be described by string of form
	 * <pre>
	 * FilterDescription = Path ":" Method["," Method]…
	 *              Path = "*" | relative path to an individual endpoint
	 *            Method = "get" | "post" | "put" | "patch" | "delete" | "all"
	 * </pre>
	 * Some examples:
	 * <ol>
	 *     <li>{@code *:all} — matches all paths and all methods</li>
	 *     <li>{@code *:get,post,put,patch,delete} — same as {@code *:all}</li>
	 *     <li>{@code /pets/get:get,post} — a filter matching path '/pet/get' and methods GET and POST</li>
	 * </ol>
	 *
	 * Instances of this class are immutable
	 * @param filterDescription description of a filter in specified format, e.g. {@code /search:get,post}, {@code *:all}
	 * @return an instance of {@link TestConfigurationFilter}
	 * @throws IllegalArgumentException when passed filter description is invalid
	 */
	public static TestConfigurationFilter parse(final String filterDescription) {
		final String[] pathAndMethods = filterDescription.split(":");

		if (pathAndMethods.length != 2) {
			throw new IllegalArgumentException("Invalid format: a filter must be specified with the format 'path:HTTPMethod1,HTTPMethod2,...'");
		}

		final String path = pathAndMethods[0];
		if (path.isBlank()) throw newInvalidFormatException(filterDescription);

		final String methodsPart = pathAndMethods[1];
		if (methodsPart.isBlank()) throw newInvalidFormatException(filterDescription);

		final var filterBuilder = new Builder();
		filterBuilder.setPath(path.trim());

		for (String method : pathAndMethods[1].split(",")) {
			switch (method.toLowerCase()) {
				case "get":
					filterBuilder.addGetMethod();
					break;
				case "post":
					filterBuilder.addPostMethod();
					break;
				case "put":
					filterBuilder.addPutMethod();
					break;
				case "patch":
					filterBuilder.addPatchMethod();
					break;
				case "delete":
					filterBuilder.addDeleteMethod();
					break;
				case "all":
					filterBuilder.addAllMethods();
					break;
				default:
					throw newInvalidFormatException(filterDescription);
			}
		}
		return filterBuilder.build();
	}

	private static IllegalArgumentException newInvalidFormatException(String inputString) {
		return new IllegalArgumentException(
				String.format("'%s' has invalid format. A filter must be specified as 'path:method,method,…' " +
						"or '*:method,method', e.g. '/hello:get', '*:get'. " +
						"Valid methods are get, post, put, patch, delete, all.",
						inputString)
		);
	}

	/**
	 * Returns a brand new {@link Builder}
	 */
	public static Builder builder() { return new Builder(); }

	/**
	 * Fluent {@link TestConfigurationFilter} builder
	 * <p>
	 * Example usage:
	 * <pre>
	 *     TestConfigurationFilter filter = TestConfigurationFilter.builder()
	 *     		.setPath("/pet/list")
	 *     		.addGetMethod()
	 *     		.build();
	 * </pre>
	 */
	public static class Builder {
		private String path;
		private final Set<HttpMethod> methods = new HashSet<>();

		private Builder () {}

		public TestConfigurationFilter build() {
			if (methods.isEmpty())
				throw new IllegalArgumentException("No method was set to filter! You must specify at least one method for a filter");

			return new TestConfigurationFilter(this.path, this.methods);
		}

		public Builder setPath(String path) {
			Objects.requireNonNull(path, "Path must not be empty");
			if (path.isBlank()) throw new IllegalArgumentException("Path must not be blank");
			this.path = ALL_PATHS_WILDCARD.equals(path) ? null : path;
			return this;
		}

		public Builder addGetMethod() {
			this.methods.add(HttpMethod.GET);
			return this;
		}

		public Builder addPostMethod() {
			this.methods.add(HttpMethod.POST);
			return this;
		}

		public Builder addPutMethod() {
			this.methods.add(HttpMethod.PUT);
			return this;
		}

		public Builder addPatchMethod() {
			this.methods.add(HttpMethod.PATCH);
			return this;
		}

		public Builder addDeleteMethod() {
			this.methods.add(HttpMethod.DELETE);
			return this;
		}

		public Builder addAllMethods() {
			return this
					.addGetMethod()
					.addPostMethod()
					.addPutMethod()
					.addPatchMethod()
					.addDeleteMethod();
		}
	}
}
