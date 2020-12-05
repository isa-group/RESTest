package es.us.isa.restest.e2e;

import es.us.isa.restest.main.CreateTestConf;
import org.junit.Test;

import static es.us.isa.restest.util.FileManager.checkIfExists;
import static org.junit.Assert.assertTrue;

public class CreateTestConfTest {

    @Test
    public void testCreateTestConf() {
        String[] args = {};
        CreateTestConf.main(args);

        assertTrue(checkIfExists("src/test/resources/Folder/testConf.yaml"));
    }

    @Test
    public void testCreateTestConfWithArgs() {
        String[] args = {"src/test/resources/BigOven/spec.yaml", "/article/{term}:GET,PUT", "/grocerylist/item/{guid}:DELETE", "/recipe/review/{reviewId}/replies:ALL"};
        CreateTestConf.main(args);

        assertTrue(checkIfExists("src/test/resources/BigOven/testConf.yaml"));
    }

}
