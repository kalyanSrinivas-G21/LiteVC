package lvc.core;

import lvc.cli.commands.*;
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

public class CheckoutCommandTest {

    @TempDir
    Path tempDir;

    private File workingDir;
    private final PrintStream standardOut = System.out;
    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() {
        workingDir = tempDir.toFile();
        new InitCommand(workingDir.getAbsolutePath()).execute(new String[]{"init"});
        System.setOut(new PrintStream(outputStreamCaptor));
    }

    @AfterEach
    void tearDown() {
        System.setOut(standardOut);
    }

    @Test
    void checkoutRestoresFilesFromPreviousBranch() throws Exception {
        // 1. Setup 'main': Create a file with V1
        File appFile = new File(workingDir, "App.java");
        Files.writeString(appFile.toPath(), "Version 1");
        new AddCommand(workingDir.getAbsolutePath()).execute(new String[]{"add", "."});
        new CommitCommand(workingDir.getAbsolutePath()).execute(new String[]{"commit", "-m", "V1 commit"});

        // 2. Branch out
        new BranchCommand(workingDir.getAbsolutePath()).execute(new String[]{"branch", "legacy"});

        // 3. Update 'main': Change the file to V2
        Files.writeString(appFile.toPath(), "Version 2 (New Feature)");
        new AddCommand(workingDir.getAbsolutePath()).execute(new String[]{"add", "."});
        new CommitCommand(workingDir.getAbsolutePath()).execute(new String[]{"commit", "-m", "V2 commit"});

        // Verify it actually says V2 right now
        assertEquals("Version 2 (New Feature)", Files.readString(appFile.toPath()));

        // 4. ACTION: Time travel back to 'legacy' branch
        new CheckoutCommand(workingDir.getAbsolutePath()).execute(new String[]{"checkout", "legacy"});

        // 5. ASSERT: Did the file on the hard drive actually revert to V1?
        assertEquals("Version 1", Files.readString(appFile.toPath()), "Checkout should overwrite file contents with the older tree state!");

        // 6. ASSERT: Did the HEAD pointer update?
        String headContent = Files.readString(Paths.get(workingDir.getAbsolutePath(), ".lvc", "HEAD")).trim();
        assertEquals("ref: refs/heads/legacy", headContent, "HEAD should now point to the legacy branch");
    }
}
