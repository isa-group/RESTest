package es.us.isa.restest.util;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileManager {

    private static Logger logger = LogManager.getLogger(FileManager.class.getName());

    public static Boolean createFileIfNotExists(String path) {
        File file = new File(path);
        try {
            return file.createNewFile();
        } catch (IOException e) {
            logger.error("Exception: ", e);
        }
        return null;
    }

    public static Boolean checkIfExists(String path) {
        File file = new File(path);
        return file.exists();
    }

    public static Boolean deleteFile(String path) {
        File file = new File(path);
        return file.delete();
    }

    // Create target dir if it does not exist
    public static Boolean createDir(String targetDir) {
        if (targetDir.charAt(targetDir.length()-1) != '/')
            targetDir += "/";
        File dir = new File(targetDir);
        return dir.mkdirs();
    }

    public static void deleteDir(String path) {
        File file = new File(path);
        try {
            FileUtils.deleteDirectory(file);
        } catch (IOException e) {
            logger.error("Error deleting dir ");
            logger.error("Exception: ", e);
        }
    }

    public static String readFile(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)));
        } catch (IOException e) {
            logger.error("Exception: ", e);
        }
        return null;
    }

}
