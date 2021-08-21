package es.us.isa.restest.util;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static es.us.isa.restest.inputs.semantic.ARTEInputGenerator.LIMIT;
import static es.us.isa.restest.util.CSVManager.*;
import static es.us.isa.restest.util.FileManager.*;
import static org.junit.Assert.*;

public class CSVManagerTest {

    @Test
    public void testCreateCSV() {
        String dirPath = "src/test/resources/csvData/";
        String file = "csvManagerSample.csv";
        deleteFile(dirPath+file);
        createDir(dirPath);
        createFileIfNotExists(dirPath+file);
        createCSVwithHeader(dirPath+file, "criterionType,rootPath,element,isCovered");
        assertTrue("The file should exist", checkIfExists(dirPath+file));
        assertFalse("The file should not be empty", readCSV("src/test/resources/csvData/csvManagerSample.csv").isEmpty());
    }

    @Test
    public void testReadCSVWithHeader() {
        String path = "src/test/resources/csvData/csvManagerReadSample.csv";
        List<List<String>> csv;
        csv = readCSV(path);
        assertEquals("The second value of the first row should be 'field2", "field2", csv.get(0).get(1));
        assertEquals("The third value of the second row should be 'value3", "value3", csv.get(1).get(2));
    }

    @Test
    public void testReadCSVWithoutHeader() {
        String path = "src/test/resources/csvData/csvManagerReadSample.csv";
        List<List<String>> csv;
        csv = readCSV(path, false);
        assertEquals("The second value of the first row should be 'value2", "value2", csv.get(0).get(1));
        assertEquals("The third value of the second row should be 'value6", "value6", csv.get(1).get(2));
    }

    @Test
    public void testAddRowToCSV() {
        testCreateCSV();
        String path = "src/test/resources/csvData/csvManagerSample.csv";
        String row = "RESPONSE_BODY_PROPERTIES,pets->GET->200,name,true";
        writeCSVRow(path, row);
        assertTrue("The file should exist", checkIfExists(path));
        String fileContent = readFile(path);
        assertNotNull("The content of the CSV should be readable", fileContent);
        assertTrue("The CSV should contain the row just added", fileContent.contains(row));
    }

    @Test
    public void testCollectionToCSV() {
        String path = "src/test/resources/csvData/csvCollectionSample.csv";
        Set<String> collection = new HashSet<>();
        collection.add("value1");
        collection.add("value2");
        collection.add("value3");

        collectionToCSV(path, collection);
        assertTrue("The file should exist", checkIfExists(path));
        List<String> readValues = readValues(path);
        assertEquals("Error reading the file", collection.size(), readValues.size());
        assertTrue("The CSV should contain the introduced values", readValues.containsAll(collection));

    }

    @Test
    public void testSetToCSVWithLimit() {

        if (LIMIT != null) {
            String path = "src/test/resources/csvData/csvCollectionWithLimitSample.csv";

            Set<String> collection = new HashSet<>();
            for(int i = 0; i< LIMIT + 10; i++) {
                collection.add(Integer.toString(i));
            }

            setToCSVWithLimit(path, collection);
            assertTrue("The file should exist", checkIfExists(path));

            List<String> readValues = readValues(path);
            assertTrue("LIMIT not properly applied", readValues.size() == LIMIT);

        }


    }

}
