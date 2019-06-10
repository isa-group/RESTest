package es.us.isa.restest.util;

import org.junit.Test;

import java.io.IOException;

import static es.us.isa.restest.util.CSVManager.*;

public class CSVManagerTest {

    @Test
    public void testCreateCSV() {
        createFileWithHeader("src/test/resources/csvData/csvSample.csv", "criterionType,rootPath,element,isCovered");
    }

    @Test
    public void testAddRowToCSV() {
        writeRow("src/test/resources/csvData/csvSample.csv", "RESPONSE_BODY_PROPERTIES,pets->GET->200,name,true");
    }
}
