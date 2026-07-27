package com.mgaray.ragserver.common.notused;

import com.mgaray.ragserver.common.JsonUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FileUtils {

    public static boolean exists(String filename) {
        Path path = Path.of(filename);
        return  Files.exists(path);
    }

    public static String readFile(String filename) {
        //System.out.print(".");
        try {
            Path path = Path.of(filename);
            return Files.readString(path);
        } catch (Exception e) {
            throw new RuntimeException("filename: " + filename, e);
        }
    }

    public static List<String> listFolder(String folder) {
        try {
            List<String> folderList = new ArrayList<>();
            Path path = Path.of(folder);
            for (Path result : Files.list(path).toList()) {
                folderList.add(result.toString());
            }
            return folderList;
        } catch (Exception e) {
            throw new RuntimeException("folder: " + folder, e);
        }
    }

    //creates folder structure if it does not already exist
    public static void writeFile(String filename, String content) {
        try {
            Path path = Path.of(filename);
            createDirectories(path);
            Files.writeString(path, content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeEmbedding(String filename, float[] embedding) {
        writeBytes(filename, toBytes(embedding));
    }

    public static float[] readEmbedding(String filename) {
        return toFloatArray(readBytes(filename));
    }

    public static float[] toFloatArray(byte[] bytes) {
        if (bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException("Byte array length must be a multiple of " + Float.BYTES);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] values = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getFloat();
        }
        return values;
    }

    public static byte[] readBytes(String filename) {
        try {
            Path path = Path.of(filename);
            return Files.readAllBytes(path);
        } catch (Exception e) {
            return null;
            //throw new RuntimeException(e);
        }
    }

    public static byte[] toBytes(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    //creates folder structure if it does not already exist
    public static void writeBytes(String filename, byte[] bytes) {
        try {
            Path path = Path.of(filename);
            createDirectories(path);
            Files.write(path, bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void createDirectories(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }


    public static Map<String, Object> readJsonFile(String fileName) {
        return JsonUtils.parse(readFile(fileName));
    }

    public static List<Map<String, Object>> readJsonlFile(String fileName) {
        return JsonUtils.parseJsonl(readFile(fileName));
    }

}
