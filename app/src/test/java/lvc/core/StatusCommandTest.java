package lvc.core;

import lvc.cli.commands.AddCommand;
import lvc.cli.commands.InitCommand;
import lvc.cli.commands.StatusCommand;
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

public class StatusCommandTest {

    @TempDir
    Path tempDir;

    private File workingDir;
    private final PrintStream standardOut = System.out;
    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() {
        workingDir = tempDir.toFile();
        // Initialize repo
        new InitCommand(workingDir.getAbsolutePath()).execute(new String[]{"init"});
        // Capture terminal output
        System.setOut(new PrintStream(outputStreamCaptor));
    }

    @AfterEach
    void tearDown() {
        System.setOut(standardOut);
    }

    @Test
    void statusIdentifiesUntrackedFiles() throws Exception {
        // Setup: Create a completely new file
        File newFile = new File(workingDir, "new-feature.txt");
        Files.writeString(newFile.toPath(), "Untracked content");

        // Action
        new StatusCommand(workingDir.getAbsolutePath()).execute(new String[]{"status"});

        // Assert
        String output = outputStreamCaptor.toString();
        assertTrue(output.contains("Untracked files:"), "Should display the untracked header");
        assertTrue(output.contains("new-feature.txt"), "Should list the untracked file");
    }

    @Test
    void statusIdentifiesModifiedFiles() throws Exception {
        // Setup: Create file -> Add it -> Modify it
        File file = new File(workingDir, "config.json");
        Files.writeString(file.toPath(), "{ \"version\": 1 }");

        new AddCommand(workingDir.getAbsolutePath()).execute(new String[]{"add", "config.json"});

        // Modify the file after staging
        Files.writeString(file.toPath(), "{ \"version\": 2 }");

        // Action
        outputStreamCaptor.reset(); // Clear previous output from the add command if any
        new StatusCommand(workingDir.getAbsolutePath()).execute(new String[]{"status"});

        // Assert
        String output = outputStreamCaptor.toString();
        assertTrue(output.contains("Changes not staged for commit:"), "Should show modified section");
        assertTrue(output.contains("modified: config.json"), "Should list the file as modified");
    }

    @Test
    void statusIdentifiesDeletedFiles() throws Exception {
        // Setup: Create file -> Add it -> Delete it from disk
        File file = new File(workingDir, "temp.txt");
        Files.writeString(file.toPath(), "Delete me");

        new AddCommand(workingDir.getAbsolutePath()).execute(new String[]{"add", "temp.txt"});

        // Delete the physical file
        file.delete();

        // Action
        outputStreamCaptor.reset();
        new StatusCommand(workingDir.getAbsolutePath()).execute(new String[]{"status"});

        // Assert
        String output = outputStreamCaptor.toString();
        assertTrue(output.contains("Changes not staged for commit:"));
        assertTrue(output.contains("deleted:  temp.txt"), "Should list the file as deleted");
    }
}