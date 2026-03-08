package lvc.core;

//These Tests where created using AI.
import lvc.cli.commands.InitCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class InitCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void initCreatesRepositoryStructure() {
        File workingDir = tempDir.toFile();

        // Inject the TempDir path into the command
        InitCommand init = new InitCommand(workingDir.getAbsolutePath());
        init.execute(new String[]{});

        File repoDir = new File(workingDir, ".lvc");
        File objectsDir = new File(repoDir, "objects");
        File refsHeadsDir = new File(repoDir, "refs/heads");
        File headFile = new File(repoDir, "HEAD");

        assertTrue(repoDir.exists());
        assertTrue(objectsDir.exists());
        assertTrue(refsHeadsDir.exists());
        assertTrue(headFile.exists());
    }

    @Test
    void initFailsIfRepositoryAlreadyExists() {
        File workingDir = tempDir.toFile();

        InitCommand init = new InitCommand(workingDir.getAbsolutePath());

        // First execution to set it up
        init.execute(new String[]{});

        // Second execution should throw the exception
        Exception exception = assertThrows(
                RuntimeException.class,
                () -> init.execute(new String[]{})
        );

        assertTrue(exception.getMessage().contains("already initialized"));
    }
}