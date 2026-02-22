package lvc.cli.commands;

import lvc.cli.Command;
import lvc.core.IndexService;
import lvc.core.ObjectService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class StatusCommand implements Command {

    private final String workingDirPath;
    private final IndexService indexService;
    private final ObjectService objectService;

    public StatusCommand() {
        this.workingDirPath = System.getProperty("user.dir");
        this.indexService = new IndexService(this.workingDirPath);
        this.objectService = new ObjectService(this.workingDirPath);
    }

    @Override
    public void execute(String[] args) {
        Map<String, String> indexEntries = indexService.getEntries();

        List<String> modifiedNotStaged = new ArrayList<>();
        List<String> deletedNotStaged = new ArrayList<>();
        List<String> untrackedFiles = new ArrayList<>();

        Path repoRoot = Paths.get(workingDirPath);
        Map<String, File> workingDirFiles = new HashMap<>();

        // 1. Scan the working directory (ignoring .lvc)
        try (Stream<Path> paths = Files.walk(repoRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains(File.separator + ".lvc" + File.separator)
                            && !p.toString().endsWith(File.separator + ".lvc"))
                    .forEach(path -> {
                        String relativePath = repoRoot.relativize(path).toString().replace("\\", "/");
                        workingDirFiles.put(relativePath, path.toFile());
                    });
        } catch (IOException e) {
            System.err.println("Error scanning directory: " + e.getMessage());
            return;
        }

        // 2. Compare Working Directory to Index
        for (Map.Entry<String, File> entry : workingDirFiles.entrySet()) {
            String relativePath = entry.getKey();
            File file = entry.getValue();

            if (!indexEntries.containsKey(relativePath)) {
                // Not in index = Untracked
                untrackedFiles.add(relativePath);
            } else {
                // In index. Is the hash different?
                String currentHash = objectService.calculateHash(file);
                String stagedHash = indexEntries.get(relativePath);

                if (!currentHash.equals(stagedHash)) {
                    modifiedNotStaged.add(relativePath);
                }
            }
        }

        // 3. Find Deleted files (In index, but missing from Working Directory)
        for (String indexFile : indexEntries.keySet()) {
            if (!workingDirFiles.containsKey(indexFile)) {
                deletedNotStaged.add(indexFile);
            }
        }

        // 4. Print the results to mirror Git's output
        printStatus(modifiedNotStaged, deletedNotStaged, untrackedFiles, indexEntries.isEmpty());
    }

    private void printStatus(List<String> modified, List<String> deleted, List<String> untracked, boolean isIndexEmpty) {
        System.out.println("On branch main\n");

        // Note: "Changes to be committed" requires comparing Index to HEAD Tree.
        // We will implement that part when we build the Commit command!
        if (!isIndexEmpty) {
            System.out.println("Changes to be committed:");
            System.out.println("  (use \"lvc commit\" to save these changes)");
            // Stub for now. Once we have commits, we will list staged files here.
            System.out.println("  [Staged files are tracked in .lvc/index]\n");
        }

        if (!modified.isEmpty() || !deleted.isEmpty()) {
            System.out.println("Changes not staged for commit:");
            System.out.println("  (use \"lvc add <file>...\" to update what will be committed)");
            for (String file : modified) {
                System.out.println("        modified: " + file);
            }
            for (String file : deleted) {
                System.out.println("        deleted:  " + file);
            }
            System.out.println();
        }

        if (!untracked.isEmpty()) {
            System.out.println("Untracked files:");
            System.out.println("  (use \"lvc add <file>...\" to include in what will be committed)");
            for (String file : untracked) {
                System.out.println("        " + file);
            }
            System.out.println();
        }

        if (modified.isEmpty() && deleted.isEmpty() && untracked.isEmpty() && isIndexEmpty) {
            System.out.println("nothing to commit, working tree clean");
        }
    }

    //Used for testing purpose
    public StatusCommand(String workingDirPath) {
        this.workingDirPath = workingDirPath;
        this.indexService = new IndexService(this.workingDirPath);
        this.objectService = new ObjectService(this.workingDirPath);
    }
}