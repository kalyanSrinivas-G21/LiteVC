package lvc.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IndexService {

    private final Path indexPath;
    // We use a TreeMap so the index is automatically sorted alphabetically by file path
    private final Map<String, String> entries = new TreeMap<>();

    public IndexService(String workingDirPath) {
        this.indexPath = Paths.get(workingDirPath, ".lvc", "index");
        loadIndex();
    }

    /**
     * Reads the .lvc/index file from disk and loads it into the entries map.
     */
    private void loadIndex() {
        File indexFile = indexPath.toFile();
        if (!indexFile.exists()) {
            return; // No index yet, which is fine (empty staging area)
        }

        try (Stream<String> lines = Files.lines(indexPath)) {
            lines.forEach(line -> {
                // Format is: "filePath hash"
                String[] parts = line.split(" ", 2);
                if (parts.length == 2) {
                    entries.put(parts[0], parts[1]);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to read the .lvc/index file.", e);
        }
    }

    /**
     * Writes the current state of the entries map back to the .lvc/index file.
     */
    private void saveIndex() {
        try {
            // Convert the map back into a list of "filePath hash" strings
            Iterable<String> lines = entries.entrySet().stream()
                    .map(entry -> entry.getKey() + " " + entry.getValue())
                    .collect(Collectors.toList());

            Files.write(indexPath, lines);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to the .lvc/index file.", e);
        }
    }

    /**
     * Adds or updates a file in the staging area.
     * * @param relativeFilePath The path of the file relative to the repo root
     * @param hash The SHA-256 hash of the file's blob
     */
    public void addEntry(String relativeFilePath, String hash) {
        // Standardize path separators to forward slashes (important for Windows vs Linux compatibility)
        String standardizedPath = relativeFilePath.replace("\\", "/");

        entries.put(standardizedPath, hash);
        saveIndex();
    }

    /**
     * Removes a file from the staging area (useful for an eventual 'lvc rm' command).
     */
    public void removeEntry(String relativeFilePath) {
        String standardizedPath = relativeFilePath.replace("\\", "/");
        if (entries.remove(standardizedPath) != null) {
            saveIndex();
        }
    }

    /**
     * Gets a read-only view of the current staging area.
     */
    public Map<String, String> getEntries() {
        return Map.copyOf(entries);
    }
}