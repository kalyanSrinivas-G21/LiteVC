package lvc.core;

import lvc.cli.commands.AddCommand;
import lvc.cli.commands.CommitCommand;
import lvc.cli.commands.InitCommand;
import lvc.core.ObjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class CommitCommandTest {

    @TempDir
    Path tempDir;

    private File workingDir;
    private final PrintStream standardOut = System.out;
    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() {
        workingDir = tempDir.toFile();
        // Initialize the repo before every test
        new InitCommand(workingDir.getAbsolutePath()).execute(new String[]{"init"});
        System.setOut(new PrintStream(outputStreamCaptor));
    }

    @AfterEach
    void tearDown() {
        System.setOut(standardOut);
    }

    @Test
    void initialCommitCreatesObjectAndUpdateBranch() throws Exception {
        // Setup: Create and stage a file
        File testFile = new File(workingDir, "app.js");
        Files.writeString(testFile.toPath(), "console.log('Hello');");
        new AddCommand(workingDir.getAbsolutePath()).execute(new String[]{"add", "app.js"});

        // Action: Create the initial commit
        new CommitCommand(workingDir.getAbsolutePath()).execute(new String[]{"commit", "-m", "Initial commit"});

        // Assert 1: The branch pointer 'main' should now exist and hold a hash
        Path mainRefPath = Paths.get(workingDir.getAbsolutePath(), ".lvc", "refs", "heads", "main");
        assertTrue(Files.exists(mainRefPath), "The main branch file should be created");

        String commitHash = Files.readString(mainRefPath).trim();
        assertEquals(64, commitHash.length(), "Should contain a valid 64-char SHA-256 hash");

        // Assert 2: Terminal output should match format "[hash] message"
        String output = outputStreamCaptor.toString().trim();
        assertTrue(output.contains("[" + commitHash.substring(0, 7) + "] Initial commit"));

        // Assert 3: Decompress the commit object and verify it has NO parent
        ObjectService objectService = new ObjectService(workingDir.getAbsolutePath());
        String commitContent = objectService.readObjectContentAsString(commitHash);
        assertTrue(commitContent.contains("tree "), "Commit must point to a tree");
        assertFalse(commitContent.contains("parent "), "Initial commit should NOT have a parent");
    }

    @Test
    void secondCommitLinksToParentCorrectly() throws Exception {
        // 1. Make the first commit
        File file1 = new File(workingDir, "file1.txt");
        Files.writeString(file1.toPath(), "V1");
        new AddCommand(workingDir.getAbsolutePath()).execute(new String[]{"add", "."});
        new CommitCommand(workingDir.getAbsolutePath()).execute(new String[]{"commit", "-m", "First"});

        // Grab the first commit's hash
        Path mainRefPath = Paths.get(workingDir.getAbsolutePath(), ".lvc", "refs", "heads", "main");
        String firstCommitHash = Files.readString(mainRefPath).trim();

        // 2. Make the second commit
        File file2 = new File(workingDir, "file2.txt");
        Files.writeString(file2.toPath(), "V2");
        new AddCommand(workingDir.getAbsolutePath()).execute(new String[]{"add", "."});
        new CommitCommand(workingDir.getAbsolutePath()).execute(new String[]{"commit", "-m", "Second"});

        // Grab the new second commit's hash
        String secondCommitHash = Files.readString(mainRefPath).trim();

        // 3. Assert the DAG linkage!
        assertNotEquals(firstCommitHash, secondCommitHash, "Commit hashes should be different");

        ObjectService objectService = new ObjectService(workingDir.getAbsolutePath());
        String secondCommitContent = objectService.readObjectContentAsString(secondCommitHash);

        assertTrue(secondCommitContent.contains("parent " + firstCommitHash),
                "The second commit MUST contain a parent pointer to the first commit!");
    }
}
