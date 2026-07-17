package com.mgaray.ragserver.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class FileUtils {

    public static boolean exists(String filename) {
        Path path = Path.of(filename);
        return  Files.exists(path);
    }

    public static String readFile(String fileName) {
        try {
            Path path = Path.of(fileName);
            return Files.readString(path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> readJsonFile(String fileName) {
        return JsonUtils.parse(readFile(fileName));
    }

    public static List<Map<String, Object>> readJsonlFile(String fileName) {
        return JsonUtils.parseJsonl(readFile(fileName));
    }

}
