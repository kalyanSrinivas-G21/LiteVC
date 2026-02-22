package lvc.cli.commands;

import lvc.cli.Command;
import lvc.core.ObjectService;
import lvc.core.TreeService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class CommitCommand implements Command {

    private final String workingDirPath;
    private final ObjectService objectService;
    private final TreeService treeService;

    public CommitCommand() {
        this.workingDirPath = System.getProperty("user.dir");
        this.objectService = new ObjectService(this.workingDirPath);
        this.treeService = new TreeService(this.workingDirPath);
    }

    public CommitCommand(String workingDirPath) {
        this.workingDirPath = workingDirPath;
        this.objectService = new ObjectService(this.workingDirPath);
        this.treeService = new TreeService(this.workingDirPath);
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 3 || !args[1].equals("-m")) {
            System.out.println("Usage: lvc commit -m \"<message>\"");
            return;
        }

        String message = args[2];

        try {
            // 1. Build the Tree from the Index and get its hash
            String treeHash = treeService.writeTree();

            // 2. Get the Parent Commit Hash (if it exists)
            String parentHash = getCurrentHeadHash();

            // 3. Construct the Commit Content
            String commitContent = buildCommitString(treeHash, parentHash, message);

            // 4. Hash and write the Commit Object using our dynamic service
            String commitHash = objectService.writeObject("commit", commitContent.getBytes());

            // 5. Update the branch reference to point to this new commit
            updateHeadReference(commitHash);

            System.out.println("[" + commitHash.substring(0, 7) + "] " + message);

        } catch (IllegalStateException e) {
            System.err.println("Error: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Fatal: Failed to write commit data - " + e.getMessage());
        }
    }

    private String buildCommitString(String treeHash, String parentHash, String message) {
        StringBuilder builder = new StringBuilder();
        builder.append("tree ").append(treeHash).append("\n");

        // If there is a parent, link it! This creates the DAG.
        if (parentHash != null && !parentHash.isEmpty()) {
            builder.append("parent ").append(parentHash).append("\n");
        }

        // Git uses UNIX timestamp + timezone offset
        long epochSeconds = Instant.now().getEpochSecond();
        String timezoneOffset = ZonedDateTime.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("Z"));

        String timestamp = epochSeconds + " " + timezoneOffset;

        // Hardcoding the author for now. You could read this from a config file later!
        String authorLine = "Author <author@litevc.local> " + timestamp;

        builder.append("author ").append(authorLine).append("\n");
        builder.append("committer ").append(authorLine).append("\n");
        builder.append("\n"); // Blank line before the message
        builder.append(message).append("\n");

        return builder.toString();
    }

    private String getCurrentHeadHash() throws IOException {
        Path masterRefPath = Paths.get(workingDirPath, ".lvc", "refs", "heads", "main");
        if (Files.exists(masterRefPath)) {
            return Files.readString(masterRefPath).trim();
        }
        return null; // This is the initial commit!
    }

    private void updateHeadReference(String commitHash) throws IOException {
        // Ensure the refs/heads directories exist
        File headsDir = new File(workingDirPath, ".lvc/refs/heads");
        if (!headsDir.exists()) {
            headsDir.mkdirs();
        }

        // Write the commit hash into the 'main' branch file
        Path masterRefPath = Paths.get(workingDirPath, ".lvc", "refs", "heads", "main");
        Files.writeString(masterRefPath, commitHash + "\n");

        // Ensure the global HEAD file points to this branch
        Path headFilePath = Paths.get(workingDirPath, ".lvc", "HEAD");
        Files.writeString(headFilePath, "ref: refs/heads/main\n");
    }
}