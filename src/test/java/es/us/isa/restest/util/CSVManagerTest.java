package es.us.isa.restest.util;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        createFileIfNotExists(file);
        createFileWithHeader(dirPath+file, "criterionType,rootPath,element,isCovered");
        assertTrue("The file should exist", checkIfExists(dirPath+file));
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
        writeRow(path, row);
        assertTrue("The file should exist", checkIfExists(path));
        String fileContent = readFile(path);
        assertNotNull("The content of the CSV should be readable", fileContent);
        assertTrue("The CSV should contain the row just added", fileContent.contains(row));
    }
}
