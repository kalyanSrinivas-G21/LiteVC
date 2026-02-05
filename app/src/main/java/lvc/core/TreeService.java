package lvc.core;

import java.util.Map;
import java.util.TreeMap;

public class TreeService {

    private final ObjectService objectService;
    private final IndexService indexService;

    public TreeService(String workingDirPath) {
        this.objectService = new ObjectService(workingDirPath);
        this.indexService = new IndexService(workingDirPath);
    }

    /**
     * Reads the index, builds the folder hierarchy, and writes all necessary
     * Tree objects to the database. Returns the SHA-256 hash of the Root Tree.
     */
    public String writeTree() {
        Map<String, String> indexEntries = indexService.getEntries();
        if (indexEntries.isEmpty()) {
            throw new IllegalStateException("Cannot create a tree from an empty index.");
        }

        // 1. Build the virtual directory structure in memory
        TreeNode root = new TreeNode();
        for (Map.Entry<String, String> entry : indexEntries.entrySet()) {
            root.addPath(entry.getKey(), entry.getValue());
        }

        // 2. Recursively write the trees to the database and get the root hash
        return writeNode(root);
    }

    /**
     * Recursively travels down the folder structure. It saves the deepest folders first,
     * so it can pass their generated hashes up to the parent folders.
     */
    private String writeNode(TreeNode node) {
        StringBuilder treeContent = new StringBuilder();

        for (Map.Entry<String, TreeNode> entry : node.children.entrySet()) {
            String name = entry.getKey();
            TreeNode child = entry.getValue();

            if (child.isBlob()) {
                // 100644 is Git's standard mode for standard files
                treeContent.append("100644 blob ")
                        .append(child.blobHash).append("\t").append(name).append("\n");
            } else {
                // It's a folder! We must save the child tree first to get its hash
                String childTreeHash = writeNode(child);

                // 040000 is Git's standard mode for directories
                treeContent.append("040000 tree ")
                        .append(childTreeHash).append("\t").append(name).append("\n");
            }
        }

        // Pass the formatted string to our dynamic ObjectService!
        byte[] treeBytes = treeContent.toString().getBytes();
        return objectService.writeObject("tree", treeBytes);
    }

    /**
     * A simple helper class to represent a file or a folder.
     */
    private static class TreeNode {
        // TreeMap keeps the contents alphabetically sorted!
        Map<String, TreeNode> children = new TreeMap<>();
        String blobHash = null; // If this is not null, this node is a file.

        boolean isBlob() {
            return blobHash != null;
        }

        // Takes a path like "src/app/Main.java" and builds the tree nodes
        void addPath(String path, String hash) {
            String[] parts = path.split("/");
            TreeNode current = this;

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                // If the node doesn't exist yet, create it
                current.children.putIfAbsent(part, new TreeNode());
                current = current.children.get(part);

                // If this is the last part of the path, it's the file! Attach the hash.
                if (i == parts.length - 1) {
                    current.blobHash = hash;
                }
            }
        }
    }
}