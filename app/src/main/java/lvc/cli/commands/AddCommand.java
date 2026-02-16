package lvc.cli.commands;

import lvc.cli.Command;
import lvc.core.IndexService;
import lvc.core.ObjectService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class AddCommand implements Command {

    private final String workingDirPath;
    private final ObjectService objectService;
    private final IndexService indexService;

    public AddCommand() {
        this.workingDirPath = System.getProperty("user.dir");
        this.objectService = new ObjectService(this.workingDirPath);
        this.indexService = new IndexService(this.workingDirPath);
    }

    // For testing injection
    public AddCommand(String workingDirPath) {
        this.workingDirPath = workingDirPath;
        this.objectService = new ObjectService(this.workingDirPath);
        this.indexService = new IndexService(this.workingDirPath);
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: lvc add <file-or-directory>");
            return;
        }

        String pathArg = args[1];
        Path repoRoot = Paths.get(workingDirPath);

        // Resolve the target path (handles both "." and specific files like "src/App.java")
        Path targetPath = repoRoot.resolve(pathArg).normalize();

        if (!Files.exists(targetPath)) {
            System.err.println("Fatal: pathspec '" + pathArg + "' did not match any files");
            return;
        }

        try (Stream<Path> paths = Files.walk(targetPath)) {
            paths.filter(Files::isRegularFile)
                    // CRITICAL: Never add the .lvc directory itself!
                    .filter(p -> !p.toString().contains(File.separator + ".lvc" + File.separator)
                            && !p.toString().endsWith(File.separator + ".lvc"))
                    .forEach(this::processFile);

        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
        }
    }

    private void processFile(Path filePath) {
        Path repoRoot = Paths.get(workingDirPath);

        // 1. Calculate the path relative to the repository root
        String relativePath = repoRoot.relativize(filePath).toString();

        try {
            // 2. Hash and compress the file via ObjectService
            String hash = objectService.hashAndWriteBlob(filePath.toFile());

            // 3. Add to the staging area via IndexService
            indexService.addEntry(relativePath, hash);

            // Optional: Print what was added, though real Git stays silent on success
            // System.out.println("Staged: " + relativePath);

        } catch (Exception e) {
            System.err.println("Failed to stage " + relativePath + ": " + e.getMessage());
        }
    }
}