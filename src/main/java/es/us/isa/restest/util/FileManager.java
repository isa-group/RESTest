package es.us.isa.restest.util;

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

    public static boolean removeFile(String path) {
        File file = new File(path);
        return file.delete();
    }

}
