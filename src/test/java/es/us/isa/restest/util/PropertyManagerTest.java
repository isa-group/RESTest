package es.us.isa.restest.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PropertyManagerTest {

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
    public void shouldReadPropertyFromMainPropertiesFile() {
        String generator = PropertyManager.readProperty("generator");
        assertEquals("RT", generator);
    }

    @Test
    public void shouldNotReadPropertyFromMainPropertiesFile() {
        File f = new File("src/main/resources/config.properties");
        f.renameTo(new File("src/main/resources/config.properties1"));

        String generator = PropertyManager.readProperty("generator");
        assertNull(generator);
    }

    @Test
    public void shouldReadPropertyFromExperimentPropertiesFile() {
        String generator = PropertyManager.readProperty("src/test/resources/Bikewise/bikewise_test.properties", "generator");
        assertEquals("CBT", generator);
    }

    @Test
    public void shouldNotReadPropertyFromExperimentPropertiesFile() {
        String generator = PropertyManager.readProperty("unknown.properties", "generator");
        assertNull(generator);
    }

    @After
    public void renamePropertiesFile() {
        File f = new File("src/main/resources/config.properties1");
        if(f.exists()) {
            f.renameTo(new File("src/main/resources/config.properties"));
        }
    }
}
