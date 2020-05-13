/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased;

import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 *
 * @author japarejo
 */
class DummyTestCaseGenerator extends AbstractTestCaseGenerator{

    public DummyTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		super(spec, conf, nTests);		
	}

	Iterator<TestCase> iterator;
    
    public void setIterator(Iterator<TestCase> iterator){
        this.iterator=iterator;
    }
    
    @Override
    protected boolean hasNext() {
        return iterator.hasNext();
    }            

	@Override
	protected Collection<TestCase> generateOperationTestCases(Operation specOperation,
			es.us.isa.restest.configuration.pojos.Operation testOperation, String path, HttpMethod method) {
		// TODO Auto-generated method stub
		Iterable<TestCase> iterable = () -> iterator;
		List<TestCase> actualList = StreamSupport
				  .stream(iterable.spliterator(), false)
				  .collect(Collectors.toList());
		return actualList;
	}

	@Override
	protected TestCase generateNextTestCase(Operation specOperation,
			es.us.isa.restest.configuration.pojos.Operation testOperation, String path, HttpMethod method,
			String faultyReason) {
		return iterator.next();
	}
    
}
