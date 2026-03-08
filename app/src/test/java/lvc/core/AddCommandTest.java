package lvc.core;

import lvc.cli.commands.AddCommand;
import lvc.cli.commands.InitCommand;
import lvc.core.IndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AddCommandTest {

    @TempDir
    Path tempDir;

    private File workingDir;

    @BeforeEach
    void setUp() {
        workingDir = tempDir.toFile();
        // Initialize the repo structure before each test
        new InitCommand(workingDir.getAbsolutePath()).execute(new String[]{"init"});
    }

    @Test
    void addSingleFileUpdatesIndexAndObjects() throws Exception {
        // Setup: Create a file
        File testFile = new File(workingDir, "hello.txt");
        Files.writeString(testFile.toPath(), "Hello LiteVC");

        // Action: Run lvc add hello.txt
        AddCommand addCommand = new AddCommand(workingDir.getAbsolutePath());
        addCommand.execute(new String[]{"add", "hello.txt"});

        // Assert 1: The index should contain the file
        IndexService indexService = new IndexService(workingDir.getAbsolutePath());
        Map<String, String> entries = indexService.getEntries();

        assertTrue(entries.containsKey("hello.txt"), "hello.txt should be in the staging area");
        String hash = entries.get("hello.txt");
        assertNotNull(hash);

        // Assert 2: The object file should actually exist in .lvc/objects
        File objectFile = new File(workingDir, ".lvc/objects/" + hash.substring(0, 2) + "/" + hash.substring(2));
        assertTrue(objectFile.exists(), "The compressed blob should be saved to disk");
    }

    @Test
    void addCurrentDirectoryRecursivelyAddsFiles() throws Exception {
        // Setup: Create nested directories and files
        File srcDir = new File(workingDir, "src");
        srcDir.mkdir();
        File mainFile = new File(srcDir, "Main.java");
        Files.writeString(mainFile.toPath(), "public class Main {}");

        File rootFile = new File(workingDir, "README.md");
        Files.writeString(rootFile.toPath(), "# LiteVC");

        // Action: Run lvc add .
        AddCommand addCommand = new AddCommand(workingDir.getAbsolutePath());
        addCommand.execute(new String[]{"add", "."});

        // Assert: Index should contain both files with standardized paths
        IndexService indexService = new IndexService(workingDir.getAbsolutePath());
        Map<String, String> entries = indexService.getEntries();

        assertEquals(2, entries.size(), "Should have exactly two files staged");
        assertTrue(entries.containsKey("README.md"));
        assertTrue(entries.containsKey("src/Main.java"));
    }
}
