package lvc.cli.commands;

import lvc.cli.Command;
import lvc.core.ObjectService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

public class LogCommand implements Command {

    private final String workingDirPath;
    private final ObjectService objectService;

    public LogCommand() {
        this.workingDirPath = System.getProperty("user.dir");
        this.objectService = new ObjectService(this.workingDirPath);
    }

    public LogCommand(String workingDirPath) {
        this.workingDirPath = workingDirPath;
        this.objectService = new ObjectService(this.workingDirPath);
    }

    @Override
    public void execute(String[] args) {
        boolean showGraph = args.length > 1 && args[1].equals("--graph");

        try {
            if (showGraph) {
                renderGraphLog();
            } else {
                renderStandardLog();
            }
        } catch (Exception e) {
            System.err.println("Error reading log: " + e.getMessage());
        }
    }

    // ==========================================
    // --- THE NEW ASCII GRAPH DAG RENDERER ---
    // ==========================================

    private void renderGraphLog() throws Exception {
        // 1. Max-Heap Priority Queue to sort commits by timestamp (newest first)
        PriorityQueue<CommitNode> queue = new PriorityQueue<>((a, b) -> Long.compare(b.timestamp, a.timestamp));
        Set<String> visited = new HashSet<>();
        List<String> activeColumns = new ArrayList<>(); // Tracks the vertical lines

        // 2. Load the HEAD of every branch to start the graph
        Path headsDir = Paths.get(workingDirPath, ".lvc", "refs", "heads");
        if (!Files.exists(headsDir)) {
            System.out.println("fatal: no commits found.");
            return;
        }

        try (Stream<Path> paths = Files.list(headsDir)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (Files.isRegularFile(path)) {
                    String hash = Files.readString(path).trim();
                    if (!visited.contains(hash)) {
                        queue.add(buildCommitNode(hash));
                        visited.add(hash);
                        activeColumns.add(hash);
                    }
                }
            }
        }

        // 3. Traverse the DAG using Topological Sort
        while (!queue.isEmpty()) {
            CommitNode current = queue.poll();

            // Find where this commit's vertical line is
            int colIndex = activeColumns.indexOf(current.hash);
            if (colIndex == -1) {
                activeColumns.add(current.hash);
                colIndex = activeColumns.size() - 1;
            }

            // Draw the ASCII Graph Row
            StringBuilder graphLine = new StringBuilder();
            for (int i = 0; i < activeColumns.size(); i++) {
                if (i == colIndex) {
                    graphLine.append("\033[31m*\033[0m "); // Red asterisk for the current commit
                } else {
                    graphLine.append("\033[34m|\033[0m "); // Blue line for ongoing branches
                }
            }

            // Print the graph visuals + the commit message
            System.out.println(graphLine.toString() + " \033[33m" + current.hash.substring(0, 7) + "\033[0m " + current.message);

            // 4. Graph Maintenance: Replace the current commit in the column with its parent
            if (!current.parents.isEmpty()) {
                String firstParent = current.parents.get(0);
                activeColumns.set(colIndex, firstParent); // The vertical line continues with the parent

                // If it's a merge commit, add the second parent as a new column!
                for (int i = 1; i < current.parents.size(); i++) {
                    activeColumns.add(current.parents.get(i));
                }

                // Add parents to the queue to be processed
                for (String parentHash : current.parents) {
                    if (!visited.contains(parentHash)) {
                        queue.add(buildCommitNode(parentHash));
                        visited.add(parentHash);
                    }
                }
            } else {
                // Initial commit reached for this branch, drop the column
                activeColumns.remove(colIndex);
            }
        }
    }

    private static class CommitNode {
        String hash;
        long timestamp;
        String message;
        List<String> parents = new ArrayList<>();

        public CommitNode(String hash, long timestamp, String message) {
            this.hash = hash;
            this.timestamp = timestamp;
            this.message = message;
        }
    }

    private CommitNode buildCommitNode(String hash) {
        String content = objectService.readObjectContentAsString(hash);
        long timestamp = 0;
        String message = "";
        List<String> parents = new ArrayList<>();
        boolean isMessage = false;

        for (String line : content.split("\n")) {
            if (isMessage) {
                message = line.trim(); // Just grab the first line of the message for the graph
                break;
            } else if (line.isEmpty()) {
                isMessage = true;
            } else if (line.startsWith("parent ")) {
                parents.add(line.substring(7).trim());
            } else if (line.startsWith("author ")) {
                // Extract the unix timestamp from the author line
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    try {
                        timestamp = Long.parseLong(parts[parts.length - 2]);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        CommitNode node = new CommitNode(hash, timestamp, message);
        node.parents = parents;
        return node;
    }

    private void renderStandardLog() throws Exception {
        // 1. Figure out where to start by reading the branch pointer
        String currentHash = getCurrentHeadHash();

        if (currentHash == null) {
            System.out.println("fatal: your current branch 'main' does not have any commits yet");
            return;
        }

        // 2. Traverse the DAG backward!
        while (currentHash != null && !currentHash.isEmpty()) {
            // Decompress the commit object
            String commitContent = objectService.readObjectContentAsString(currentHash);

            // Parse it, print it, and return the parent hash to continue the loop
            currentHash = parseAndPrintCommit(currentHash, commitContent);
        }
    }

    private String getCurrentHeadHash() throws IOException {
        Path masterRefPath = Paths.get(workingDirPath, ".lvc", "refs", "heads", "main");
        if (Files.exists(masterRefPath)) {
            return Files.readString(masterRefPath).trim();
        }
        return null; // No commits exist yet
    }

    private String parseAndPrintCommit(String hash, String content) {
        String[] lines = content.split("\n");
        String parentHash = null;
        String authorLine = "";
        StringBuilder message = new StringBuilder();
        boolean isMessage = false;

        // Parse the commit string line by line
        for (String line : lines) {
            if (isMessage) {
                message.append("    ").append(line).append("\n");
            } else if (line.isEmpty()) {
                // In a Git commit, a blank line separates the headers from the message body
                isMessage = true;
            } else if (line.startsWith("parent ")) {
                parentHash = line.substring(7).trim(); // Extract the parent hash!
            } else if (line.startsWith("author ")) {
                authorLine = line.substring(7); // Extract the author line
            }
        }

        // Print the Commit Hash (Git usually prints this in yellow, so we use an ANSI escape code!)
        System.out.println("\033[33mcommit " + hash + "\033[0m");

        // Format and print the Author and Date
        formatAndPrintAuthorLine(authorLine);

        // Print the message
        System.out.println("\n" + message.toString());

        // Returning the parent hash allows the while-loop to continue traversing backward
        return parentHash;
    }

    private void formatAndPrintAuthorLine(String authorLine) {
        // Author line format: Name <email> 1710345600 +0530
        int lastBracket = authorLine.lastIndexOf('>');
        if (lastBracket != -1 && authorLine.length() > lastBracket + 2) {
            String nameAndEmail = authorLine.substring(0, lastBracket + 1);
            String timeInfo = authorLine.substring(lastBracket + 2).trim();
            String[] timeParts = timeInfo.split(" ");

            if (timeParts.length == 2) {
                try {
                    // Convert the UNIX timestamp back into a human-readable date
                    long epoch = Long.parseLong(timeParts[0]);
                    ZonedDateTime dateTime = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault());
                    String formattedDate = dateTime.format(DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy Z"));

                    System.out.println("Author: " + nameAndEmail);
                    System.out.println("Date:   " + formattedDate);
                    return;
                } catch (NumberFormatException ignored) {}
            }
        }
        // Fallback if parsing fails
        System.out.println("Author: " + authorLine);
    }
}