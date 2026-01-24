package lvc.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DeflaterOutputStream;

public class ObjectService {

    private final String workingDirPath;

    public ObjectService(String workingDirPath) {
        this.workingDirPath = workingDirPath;
    }

    /**
     * The new Generic Core: Hashes and writes ANY type of Git object (blob, tree, commit).
     */
    public String writeObject(String type, byte[] content) {
        try {
            // 1. Construct the dynamic Git header: "<type> <size>\0"
            String header = type + " " + content.length + "\0";
            byte[] headerBytes = header.getBytes();

            // 2. Combine header and contents
            byte[] storeBytes = new byte[headerBytes.length + content.length];
            System.arraycopy(headerBytes, 0, storeBytes, 0, headerBytes.length);
            System.arraycopy(content, 0, storeBytes, headerBytes.length, content.length);

            // 3. Hash and Save
            String sha256Hash = generateSha256(storeBytes);
            saveObject(sha256Hash, storeBytes);

            return sha256Hash;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + type + " object.", e);
        }
    }

    /**
     * Helper specifically for files. Reads the file and routes it to writeObject as a "blob".
     */
    public String hashAndWriteBlob(File targetFile) {
        try {
            byte[] fileContents = Files.readAllBytes(targetFile.toPath());
            return writeObject("blob", fileContents);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + targetFile.getName(), e);
        }
    }

    /**
     * Calculates the hash without saving to disk (Useful for the Status command).
     * Now also dynamic!
     */
    public String calculateHash(String type, byte[] content) {
        String header = type + " " + content.length + "\0";
        byte[] headerBytes = header.getBytes();

        byte[] storeBytes = new byte[headerBytes.length + content.length];
        System.arraycopy(headerBytes, 0, storeBytes, 0, headerBytes.length);
        System.arraycopy(content, 0, storeBytes, headerBytes.length, content.length);

        return generateSha256(storeBytes);
    }

    /**
     * Helper for Status command to check a file's hash without saving it.
     */
    public String calculateHash(File targetFile) {
        try {
            byte[] fileContents = Files.readAllBytes(targetFile.toPath());
            return calculateHash("blob", fileContents);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file for hashing: " + targetFile.getName(), e);
        }
    }

    private String generateSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bytes);

            StringBuilder hexString = new StringBuilder(2 * hashBytes.length);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found on this system.", e);
        }
    }

    private void saveObject(String hash, byte[] storeBytes) throws IOException {
        String dirName = hash.substring(0, 2);
        String fileName = hash.substring(2);

        File objectDir = new File(workingDirPath, ".lvc/objects/" + dirName);
        if (!objectDir.exists()) {
            objectDir.mkdirs();
        }

        File objectFile = new File(objectDir, fileName);
        if (objectFile.exists()) {
            return;
        }

        try (FileOutputStream fos = new FileOutputStream(objectFile);
             DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {
            dos.write(storeBytes);
        }
    }

    public String readObjectContentAsString(String hash) {
        String dirName = hash.substring(0, 2);
        String fileName = hash.substring(2);
        File objectFile = new File(workingDirPath, ".lvc/objects/" + dirName + "/" + fileName);

        if (!objectFile.exists()) {
            throw new RuntimeException("Fatal: Object not found: " + hash);
        }

        try (java.io.FileInputStream fis = new java.io.FileInputStream(objectFile);
             java.util.zip.InflaterInputStream iis = new java.util.zip.InflaterInputStream(fis);
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {

            // Read the decompressed bytes into a buffer
            byte[] buffer = new byte[1024];
            int len;
            while ((len = iis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }

            String rawContent = baos.toString("UTF-8");

            // The content looks like: "commit 214\0tree abc...\nparent def..."
            // We need to strip out the header ("commit <size>\0") to just get the text.
            int nullByteIndex = rawContent.indexOf('\0');
            if (nullByteIndex != -1) {
                return rawContent.substring(nullByteIndex + 1);
            }
            return rawContent;

        } catch (IOException e) {
            throw new RuntimeException("Failed to read and decompress object: " + hash, e);
        }
    }
}