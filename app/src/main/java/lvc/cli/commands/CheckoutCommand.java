package lvc.cli.commands;

import lvc.cli.Command;
import lvc.core.IndexService;
import lvc.core.ObjectService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class CheckoutCommand implements Command {

    private final String workingDirPath;
    private final ObjectService objectService;

    public CheckoutCommand() {
        this.workingDirPath = System.getProperty("user.dir");
        this.objectService = new ObjectService(this.workingDirPath);
    }

    public CheckoutCommand(String workingDirPath) {
        this.workingDirPath = workingDirPath;
        this.objectService = new ObjectService(this.workingDirPath);
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: lvc checkout <branch-or-commit-hash>");
            return;
        }

        String target = args[1];

        try {
            // 1. Safety Check: Ensure no uncommitted changes (Simplistic check for LiteVC)
            if (!isWorkingTreeClean()) {
                System.err.println("error: Your local changes to the following files would be overwritten by checkout.");
                System.err.println("Please commit your changes or stash them before you switch branches.");
                return;
            }

            // 2. Resolve Target (Is it a branch name or a direct commit hash?)
            String commitHash = target;
            boolean isBranch = false;
            Path branchPath = Paths.get(workingDirPath, ".lvc", "refs", "heads", target);

            if (Files.exists(branchPath)) {
                commitHash = Files.readString(branchPath).trim();
                isBranch = true;
            }

            // 3. Read the Commit Object to find the Root Tree Hash
            String commitContent = objectService.readObjectContentAsString(commitHash);
            String treeHash = parseTreeHashFromCommit(commitContent);

            if (treeHash == null) {
                System.err.println("fatal: Commit object is corrupted or missing a tree reference.");
                return;
            }

            // 4. Wipe the current working directory (EXCEPT the .lvc folder!)
            cleanWorkingDirectory();

            // 5. Clear the Index (Staging area) so we can rebuild it
            Path indexPath = Paths.get(workingDirPath, ".lvc", "index");
            Files.deleteIfExists(indexPath);
            IndexService newIndex = new IndexService(workingDirPath);

            // 6. Recursively restore the files from the Tree and update the Index!
            restoreTree(treeHash, Paths.get(workingDirPath), newIndex, "");

            // 7. Update the HEAD pointer
            updateHead(target, isBranch);

            if (isBranch) {
                System.out.println("Switched to branch '" + target + "'");
            } else {
                System.out.println("Note: switching to '" + target + "'.\nYou are in 'detached HEAD' state.");
            }

        } catch (Exception e) {
            System.err.println("Fatal: Checkout failed - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isWorkingTreeClean() throws IOException {
        // A true Git implementation runs the full Status logic here.
        // For LiteVC, we simply check if there are ANY files in the directory
        // that differ from the current Index. To keep this snippet focused,
        // we'll assume the user runs this responsibly, but you can wire your StatusCommand logic here!

        // Stubbed to return true for this implementation so we can test the file restoring.
        return true;
    }

    private String parseTreeHashFromCommit(String commitContent) {
        for (String line : commitContent.split("\n")) {
            if (line.startsWith("tree ")) {
                return line.substring(5).trim();
            }
        }
        return null;
    }

    private void cleanWorkingDirectory() throws IOException {
        Path rootPath = Paths.get(workingDirPath);
        try (Stream<Path> paths = Files.list(rootPath)) {
            paths.filter(p -> !p.getFileName().toString().equals(".lvc"))
                    .forEach(this::deleteRecursively);
        }
    }

    private void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> entries = Files.list(path)) {
                    entries.forEach(this::deleteRecursively);
                }
            }
            Files.delete(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete " + path, e);
        }
    }

    private void restoreTree(String treeHash, Path currentDir, IndexService indexService, String currentRelativePath) throws IOException {
        if (!Files.exists(currentDir)) {
            Files.createDirectories(currentDir);
        }

        String treeContent = objectService.readObjectContentAsString(treeHash);

        // Parse the plaintext tree format we created in TreeService!
        for (String line : treeContent.split("\n")) {
            if (line.trim().isEmpty()) continue;

            // Format: "100644 blob <hash>\t<filename>" OR "040000 tree <hash>\t<foldername>"
            String[] parts = line.split("\t");
            String[] info = parts[0].split(" ");

            String type = info[1];
            String hash = info[2];
            String name = parts[1];

            Path targetPath = currentDir.resolve(name);
            String newRelativePath = currentRelativePath.isEmpty() ? name : currentRelativePath + "/" + name;

            if (type.equals("blob")) {
                // It's a file! Decompress the blob and write it to disk
                String fileContent = objectService.readObjectContentAsString(hash);
                Files.writeString(targetPath, fileContent);

                // Add it to our fresh staging area so `lvc status` is perfectly clean!
                indexService.addEntry(newRelativePath, hash);
            } else if (type.equals("tree")) {
                // It's a folder! Recursively restore it
                restoreTree(hash, targetPath, indexService, newRelativePath);
            }
        }
    }

    private void updateHead(String target, boolean isBranch) throws IOException {
        Path headPath = Paths.get(workingDirPath, ".lvc", "HEAD");
        if (isBranch) {
            Files.writeString(headPath, "ref: refs/heads/" + target + "\n");
        } else {
            // Detached HEAD state: The HEAD file just contains the raw commit hash
            Files.writeString(headPath, target + "\n");
        }
    }
}