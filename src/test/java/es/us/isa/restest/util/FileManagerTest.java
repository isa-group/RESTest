package es.us.isa.restest.util;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

import static es.us.isa.restest.util.FileManager.*;

public class FileManagerTest {

    @Test
    public void deleteCreateAndDeleteDir() {
        String parentPath = "src/test/resources/file-manager";
        String path = "src/test/resources/file-manager/test/directory";
        deleteDir(parentPath);

        assertFalse(new File(parentPath).exists());

        createDir(path);

        assertTrue(new File(path).exists());

        deleteFile(path);

        assertFalse(new File(path).exists());
    }
}
