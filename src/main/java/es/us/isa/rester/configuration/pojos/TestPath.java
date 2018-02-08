
package es.us.isa.rester.configuration.pojos;

import java.util.List;

public class TestPath {

    private String testPath;
    private List<Operation> operations = null;

    public String getTestPath() {
        return testPath;
    }

    public void setTestPath(String testPath) {
        this.testPath = testPath;
    }

    public List<Operation> getOperations() {
        return operations;
    }

    public void setOperations(List<Operation> operations) {
        this.operations = operations;
    }

}
