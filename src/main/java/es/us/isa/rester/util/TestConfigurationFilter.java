package es.us.isa.rester.util;

import java.util.ArrayList;
import java.util.Collection;

import io.swagger.models.HttpMethod;


public class TestConfigurationFilter {

	private String path = null;						// Path to test (null for all)
	private Collection<HttpMethod> methods;			// Methods to test
	
	public TestConfigurationFilter() {}
	
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
		if (methods==null)
			methods = new ArrayList<HttpMethod>();
		
		methods.add(HttpMethod.GET);
		
	}
	
	public void addPostMethod() {
		if (methods==null)
			methods = new ArrayList<HttpMethod>();
		
		methods.add(HttpMethod.POST);
		
	}
	
	public void addPutMethod() {
		if (methods==null)
			methods = new ArrayList<HttpMethod>();
		
		methods.add(HttpMethod.PUT);
		
	}
	
	public void addDeleteMethod() {
		if (methods==null)
			methods = new ArrayList<HttpMethod>();
		
		methods.add(HttpMethod.DELETE);
		
	}
	
	public void addAllMethods() {
		this.addGetMethod();
		this.addPostMethod();
		this.addPutMethod();
		this.addDeleteMethod();
	}
}
