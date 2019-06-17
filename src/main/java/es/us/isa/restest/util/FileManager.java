package es.us.isa.restest.util;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class FileManager {

    public static boolean createFileIfNotExists(String path) {
        boolean created = false;
        File file = new File(path);
        try {
            created = file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return created;
    }

    public static boolean checkIfExists(String path) {
        File file = new File(path);
        return file.exists();
    }

    public static boolean deleteFile(String path) {
        File file = new File(path);
        return file.delete();
    }

    // Create target dir if it does not exist
    public static boolean createDir(String targetDir) {
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
            System.err.println("Error deleting dir");
            e.printStackTrace();
        }
    }

}
