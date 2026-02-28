package lvc.cli.commands;

import lvc.cli.Command;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class BranchCommand implements Command {

    private final String workingDirPath;

    public BranchCommand() {
        this.workingDirPath = System.getProperty("user.dir");
    }

    public BranchCommand(String workingDirPath) {
        this.workingDirPath = workingDirPath;
    }

    @Override
    public void execute(String[] args) {
        try {
            // If the user just types "lvc branch", list the branches
            if (args.length == 1) {
                listBranches();
                return;
            }

            // Otherwise, create the new branch
            String branchName = args[1];
            createBranch(branchName);

        } catch (IOException e) {
            System.err.println("Fatal: Failed to process branch - " + e.getMessage());
        }
    }

    private void listBranches() throws IOException {
        Path headsDir = Paths.get(workingDirPath, ".lvc", "refs", "heads");
        if (!Files.exists(headsDir)) return;

        // Figure out which branch we are currently checked out on
        String currentBranch = "";
        Path headPath = Paths.get(workingDirPath, ".lvc", "HEAD");
        if (Files.exists(headPath)) {
            String headContent = Files.readString(headPath).trim();
            if (headContent.startsWith("ref: refs/heads/")) {
                currentBranch = headContent.substring(16);
            }
        }

        // Loop through all files in .lvc/refs/heads/ and print them
        try (Stream<Path> paths = Files.list(headsDir)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                String name = path.getFileName().toString();
                if (name.equals(currentBranch)) {
                    // Print the current branch in green with an asterisk!
                    System.out.println("* \033[32m" + name + "\033[0m");
                } else {
                    System.out.println("  " + name);
                }
            }
        }
    }

    private void createBranch(String branchName) throws IOException {
        // 1. Get the hash of the current commit we are on
        String currentHash = getCurrentHeadHash();

        if (currentHash == null) {
            System.err.println("fatal: Not a valid object name: 'HEAD'. (Cannot create a branch without any commits)");
            return;
        }

        // 2. Ensure we don't accidentally overwrite an existing branch
        File branchFile = new File(workingDirPath, ".lvc/refs/heads/" + branchName);
        if (branchFile.exists()) {
            System.err.println("fatal: A branch named '" + branchName + "' already exists.");
            return;
        }

        // 3. Create the pointer! (Just write the hash into a text file)
        Files.writeString(branchFile.toPath(), currentHash + "\n");

        // Note: Real Git stays completely silent on successful branch creation
    }

    /**
     * Resolves the HEAD file to figure out exactly which commit hash we are currently looking at.
     */
    private String getCurrentHeadHash() throws IOException {
        Path headPath = Paths.get(workingDirPath, ".lvc", "HEAD");
        if (!Files.exists(headPath)) return null;

        String headContent = Files.readString(headPath).trim();

        if (headContent.startsWith("ref: ")) {
            // We are on a branch. Follow the branch file to get the actual hash.
            Path refPath = Paths.get(workingDirPath, ".lvc", headContent.substring(5));
            if (Files.exists(refPath)) {
                return Files.readString(refPath).trim();
            }
            return null; // The branch exists but has no commits yet
        } else {
            // We are in a "detached HEAD" state (pointing directly to a commit hash)
            return headContent;
        }
    }
}