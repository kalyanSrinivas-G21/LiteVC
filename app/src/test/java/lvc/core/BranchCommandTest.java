package lvc.core;

import lvc.cli.commands.AddCommand;
import lvc.cli.commands.BranchCommand;
import lvc.cli.commands.CommitCommand;
import lvc.cli.commands.InitCommand;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BranchCommandTest {

    @TempDir
    Path tempDir;

    private File workingDir;
    private final PrintStream standardOut = System.out;
    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errStreamCaptor = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() {
        workingDir = tempDir.toFile();
        new InitCommand(workingDir.getAbsolutePath()).execute(new String[]{"init"});
        System.setOut(new PrintStream(outputStreamCaptor));
        System.setErr(new PrintStream(errStreamCaptor));
    }

    @AfterEach
    void tearDown() {
        System.setOut(standardOut);
        System.setErr(System.err);
    }

    @Test
    void branchFailsOnEmptyRepository() {
        new BranchCommand(workingDir.getAbsolutePath()).execute(new String[]{"branch", "feature"});
        assertTrue(errStreamCaptor.toString().contains("fatal: Not a valid object name: 'HEAD'"),
                "Should prevent branching before the first commit");
    }

    @Test
    void createBranchMakesPointerFile() throws Exception {
        // Setup: Make a commit
        File file = new File(workingDir, "test.txt");
        Files.writeString(file.toPath(), "data");
        new AddCommand(workingDir.getAbsolutePath()).execute(new String[]{"add", "."});
        new CommitCommand(workingDir.getAbsolutePath()).execute(new String[]{"commit", "-m", "Init"});

        // Action: Create branch
        new BranchCommand(workingDir.getAbsolutePath()).execute(new String[]{"branch", "dev"});

        // Assert: The file should exist and match the main branch's hash
        Path mainPath = Paths.get(workingDir.getAbsolutePath(), ".lvc", "refs", "heads", "main");
        Path devPath = Paths.get(workingDir.getAbsolutePath(), ".lvc", "refs", "heads", "dev");

        assertTrue(Files.exists(devPath), "Branch file should be created");
        assertEquals(Files.readString(mainPath), Files.readString(devPath), "Branches should point to the exact same commit initially");
    }

    @Test
    void listBranchesHighlightsCurrentBranch() throws Exception {
        // Setup: Commit and branch
        File file = new File(workingDir, "test.txt");
        Files.writeString(file.toPath(), "data");
        new AddCommand(workingDir.getAbsolutePath()).execute(new String[]{"add", "."});
        new CommitCommand(workingDir.getAbsolutePath()).execute(new String[]{"commit", "-m", "Init"});
        new BranchCommand(workingDir.getAbsolutePath()).execute(new String[]{"branch", "feature-ui"});

        outputStreamCaptor.reset();

        // Action: List branches
        new BranchCommand(workingDir.getAbsolutePath()).execute(new String[]{"branch"});

        // Assert
        String output = outputStreamCaptor.toString();
        assertTrue(output.contains("* \033[32mmain\033[0m"), "Should highlight 'main' in green with an asterisk");
        assertTrue(output.contains("  feature-ui"), "Should list the new branch normally");
    }
}
