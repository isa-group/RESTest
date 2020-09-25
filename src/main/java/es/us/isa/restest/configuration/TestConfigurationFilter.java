package es.us.isa.restest.configuration;

import java.util.ArrayList;
import java.util.Collection;

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

	private String path = null; // Path to test (null for all)
	private Collection<HttpMethod> methods; // Methods to test

	public TestConfigurationFilter() {
	}

	public TestConfigurationFilter(String path, Collection<HttpMethod> methods) {
		this.path = path;
		this.methods = methods;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public Collection<HttpMethod> getMethods() {
		return methods;
	}

	public void setMethods(Collection<HttpMethod> methods) {
		this.methods = methods;
	}

	public void addGetMethod() {
		if (methods == null)
			methods = new ArrayList<>();

		methods.add(HttpMethod.GET);

	}

	public void addPostMethod() {
		if (methods == null)
			methods = new ArrayList<>();

		methods.add(HttpMethod.POST);

	}

	public void addPutMethod() {
		if (methods == null)
			methods = new ArrayList<>();

		methods.add(HttpMethod.PUT);

	}

	public void addDeleteMethod() {
		if (methods == null)
			methods = new ArrayList<>();

		methods.add(HttpMethod.DELETE);

	}

	public void addAllMethods() {
		this.addGetMethod();
		this.addPostMethod();
		this.addPutMethod();
		this.addDeleteMethod();
	}
}
