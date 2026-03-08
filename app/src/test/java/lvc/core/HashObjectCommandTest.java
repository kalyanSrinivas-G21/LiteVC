package lvc.core;

import lvc.cli.commands.HashObjectCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class HashObjectCommandTest {

    @TempDir
    Path tempDir;

    private final PrintStream standardOut = System.out;
    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();

    @BeforeEach
    public void setUp() {
        // Hijack System.out before each test to capture print statements
        System.setOut(new PrintStream(outputStreamCaptor));
    }

    @AfterEach
    public void tearDown() {
        // Restore normal terminal output after the test finishes
        System.setOut(standardOut);
    }

    @Test
    void executePrintsHashToTerminal() throws Exception {
        File workingDir = tempDir.toFile();

        // Setup a test file
        File testFile = new File(workingDir, "hello.txt");
        Files.writeString(testFile.toPath(), "Testing the CLI!");

        // Initialize the command with our test directory
        HashObjectCommand command = new HashObjectCommand(workingDir.getAbsolutePath());

        // Run the command exactly as the App.java router would
        command.execute(new String[]{"hash-object", "hello.txt"});

        // Capture what was printed to the terminal
        String printedOutput = outputStreamCaptor.toString().trim();

        // The only thing printed should be the 64-character hash
        assertEquals(64, printedOutput.length(), "CLI should print exactly the 64-character hash");

        // Verify the object was actually saved using the printed hash
        String dirName = printedOutput.substring(0, 2);
        String fileName = printedOutput.substring(2);
        File objectFile = new File(workingDir, ".lvc/objects/" + dirName + "/" + fileName);

        assertTrue(objectFile.exists(), "The object file should exist based on the printed hash");
    }

    @Test
    void executeFailsGracefullyOnMissingFile() {
        File workingDir = tempDir.toFile();

        // Temporarily hijack System.err to capture error messages
        ByteArrayOutputStream errCaptor = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errCaptor));

        HashObjectCommand command = new HashObjectCommand(workingDir.getAbsolutePath());
        command.execute(new String[]{"hash-object", "does-not-exist.txt"});

        System.setErr(System.err); // Restore standard error

        String errorOutput = errCaptor.toString().trim();
        assertTrue(errorOutput.contains("Fatal: Cannot open 'does-not-exist.txt'"),
                "Should print a fatal error message when file is missing");
    }
}