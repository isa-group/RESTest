package es.us.isa.restest.util;

import es.us.isa.restest.specification.OpenAPISpecification;
import org.hamcrest.MatcherAssert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import static es.us.isa.restest.util.AllureAuthManager.*;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;

public class AllureAuthManagerTest {

    static OpenAPISpecification testOas;
    static String firstHtml;
    static String secondHtml;

    @BeforeClass
    public static void setUp() {
        testOas = new OpenAPISpecification("src/test/resources/restest-test-resources/openapi.yaml");

        firstHtml = FileManager.readFile("src/test/resources/htmlData/htmlSample.html");
        secondHtml = FileManager.readFile("src/test/resources/htmlData/htmlSample1.html");
    }

    @Test
    public void shouldFindAuthProperties() {
        List<String> properties = findAuthProperties(testOas, "src/test/resources/restest-test-resources/configuration-model.yaml");
        MatcherAssert.assertThat(properties, contains("Parameter name"));
    }

    @Test
    public void shouldApplyConfidentialityFilter() throws IOException {
        confidentialityFilter(Collections.singletonList("Authorization"), "src/test/resources/htmlData");

        MatcherAssert.assertThat(Objects.requireNonNull(FileManager.readFile("src/test/resources/htmlData/htmlSample.html")), containsString("CENSORED"));
        MatcherAssert.assertThat(Objects.requireNonNull(FileManager.readFile("src/test/resources/htmlData/htmlSample1.html")), containsString("CENSORED"));
    }

    @AfterClass
    public static void tearDown() throws FileNotFoundException {
        PrintWriter pw = new PrintWriter(new File("src/test/resources/htmlData/htmlSample.html"));
        pw.print(firstHtml);
        pw.close();

        pw = new PrintWriter(new File("src/test/resources/htmlData/htmlSample1.html"));
        pw.print(secondHtml);
        pw.close();
    }
}
