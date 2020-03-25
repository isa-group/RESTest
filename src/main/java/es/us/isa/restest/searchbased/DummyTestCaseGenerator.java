/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased;

import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import java.util.Iterator;

/**
 *
 * @author japarejo
 */
class DummyTestCaseGenerator extends AbstractTestCaseGenerator{

    Iterator<TestCase> iterator;
    
    public void setIterator(Iterator<TestCase> iterator){
        this.iterator=iterator;
    }
    
    @Override
    protected boolean hasNext() {
        return iterator.hasNext();
    }        

    @Override
    protected TestCase generateNextTestCase(Operation specOperation, es.us.isa.restest.configuration.pojos.Operation testOperation, String path, HttpMethod method, Boolean faulty, Boolean ignoreDependencies) {
        return iterator.next();
    }
    
}
