package es.us.isa.restest.e2e;

import es.us.isa.restest.main.CreateTestConf;
import es.us.isa.restest.util.PropertyManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static es.us.isa.restest.util.FileManager.*;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CreateTestConfTest {

    @Before
    public void resetSingleton() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field properties = PropertyManager.class.getDeclaredField("properties");
        properties.setAccessible(true);
        properties.set(null, null);

        Field experimentProperties = PropertyManager.class.getDeclaredField("experimentProperties");
        experimentProperties.setAccessible(true);
        experimentProperties.set(null, null);
    }

    @Test
    public void testCreateTestConf() throws NoSuchFieldException, IllegalAccessException {
        check();

        String[] args = {};
        CreateTestConf.main(args);

        Field confPathField = CreateTestConf.class.getDeclaredField("confPath");
        confPathField.setAccessible(true);
        String confPath = (String) confPathField.get(null);

        assertTrue(checkIfExists(confPath));
        deleteFile(confPath);
    }

    @Test
    public void testCreateTestConfWithArgs() {
        String[] args = {"src/test/resources/BigOven/spec.yaml", "/article/{term}:GET,PUT", "/grocerylist/item/{guid}:DELETE", "/recipe/review/{reviewId}/replies:ALL"};
        CreateTestConf.main(args);

        assertTrue(checkIfExists("src/test/resources/BigOven/testConf.yaml"));
    }


    private void check() throws NoSuchFieldException, IllegalAccessException {
        Field openApiSpecPathField = CreateTestConf.class.getDeclaredField("openApiSpecPath");
        openApiSpecPathField.setAccessible(true);
        String openApiSpecPath = (String) openApiSpecPathField.get(null);

        if (!openApiSpecPath.equals("src/test/resources/Folder/openapi.yaml"))
            fail("The CreateTestConf class should have 'src/test/resources/Folder/openapi.yaml' as the default Swagger path.\n" +
                    "This is to avoid testConf files being changed inadvertently when running the test suite.\n" +
                    "You may have changed it and forgot to set it back to its default value. Please, do so.");
    }

}
