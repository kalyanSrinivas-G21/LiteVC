package lvc.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void hashAndWriteBlobCreatesCompressedObjectWithHeader() throws Exception {
        File workingDir = tempDir.toFile();
        ObjectService service = new ObjectService(workingDir.getAbsolutePath());

        // 1. Setup: Create a dummy file in the temp directory
        File testFile = new File(workingDir, "test.txt");
        String fileContent = "Hello, LiteVC!";
        Files.writeString(testFile.toPath(), fileContent);

        // 2. Action: Hash and write the blob
        String hash = service.hashAndWriteBlob(testFile);

        // 3. Assert: Check that we got a 64-character SHA-256 hash back
        assertNotNull(hash);
        assertEquals(64, hash.length(), "SHA-256 hash should be exactly 64 characters long");

        // 4. Assert: Check that the file was actually saved in the correct 2/62 folder structure
        String dirName = hash.substring(0, 2);
        String fileName = hash.substring(2);
        File objectFile = new File(workingDir, ".lvc/objects/" + dirName + "/" + fileName);

        assertTrue(objectFile.exists(), "The compressed object file should exist on disk");

        // 5. Assert: Decompress the file and verify the Git blob format
        try (FileInputStream fis = new FileInputStream(objectFile);
             InflaterInputStream iis = new InflaterInputStream(fis)) {

            // Read the uncompressed bytes
            byte[] uncompressedBytes = iis.readAllBytes();
            String uncompressedString = new String(uncompressedBytes);

            // "Hello, LiteVC!" is exactly 14 bytes long
            String expectedContent = "blob 14\0" + fileContent;

            assertEquals(expectedContent, uncompressedString,
                    "The decompressed data should perfectly match the blob header + content");
        }
    }
}