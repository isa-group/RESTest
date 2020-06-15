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

        assertTrue(checkIfExists("src/test/resources/Comments/testConf.yaml"));
    }

    @Test
    public void testCreateTestConfWithArgs() {
        String[] args = {"src/test/resources/AnApiOfIceAndFire/swagger.yaml", "/api/characters:GET,POST", "/api/houses:GET,PUT,DELETE", "/api/books:ALL"};
        CreateTestConf.main(args);

        assertTrue(checkIfExists("src/test/resources/AnApiOfIceAndFire/testConf.yaml"));
    }

}
