package lvc.cli.commands;

import lvc.cli.Command;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class InitCommand implements Command {

    private final String workingDirPath;

    // Default constructor for standard CLI execution. Fixed Test Errors.
    public InitCommand() {
        this.workingDirPath = System.getProperty("user.dir");
    }

    // Constructor injected with a specific directory for testing instead of implicit working directory.
    public InitCommand(String workingDirPath) {
        this.workingDirPath = workingDirPath;
    }

    @Override
    public void execute(String[] args) {


        File repoDir = new File(workingDirPath, ".lvc");

        if (repoDir.exists()) {
            throw new RuntimeException("Repository already initialized.");
        }

        try {

            if (!repoDir.mkdir()) {
                throw new RuntimeException("Failed to create .lvc directory.");
            }

            File objectsDir = new File(repoDir, "objects");
            objectsDir.mkdirs();

            File refsHeadsDir = new File(repoDir, "refs/heads");
            refsHeadsDir.mkdirs();

            File headFile = new File(repoDir, "HEAD");

            try (FileWriter writer = new FileWriter(headFile)) {
                writer.write("ref: refs/heads/main");
            }

            System.out.println("Initialized empty LiteVC repository in " + repoDir.getAbsolutePath());

        } catch (IOException e) {
            throw new RuntimeException("Error initializing repository: " + e.getMessage(), e);
        }
    }
}