package lvc.benchmark;

import lvc.core.ObjectService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class StorageBenchmark {

    private static final String BENCHMARK_DIR = System.getProperty("user.dir") + "/.lvc-benchmark";
    private static final String CORPUS_DIR = System.getProperty("user.dir") + "/app/src";

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("🚀 LiteVC Storage & Deduplication Benchmark");
        System.out.println("==================================================\n");

        try {
            cleanBenchmarkDir();
            ObjectService objectService = new ObjectService(BENCHMARK_DIR);

            // Phase 1: Gather Corpus (All Java files in your src directory)
            List<Path> sourceFiles = getCorpusFiles(Paths.get(CORPUS_DIR));
            if (sourceFiles.isEmpty()) {
                System.out.println("Error: No source files found to benchmark in " + CORPUS_DIR);
                return;
            }

            long rawSizeBytes = calculateTotalSize(sourceFiles);
            System.out.printf("Test Corpus: %d Java Source Files\n", sourceFiles.size());
            System.out.printf("Raw Uncompressed Size: %d bytes (%.2f KB)\n\n", rawSizeBytes, rawSizeBytes / 1024.0);

            // Phase 2: Test Zlib Compression (Writing 1x)
            System.out.println("--- Phase 1: Zlib Compression Test ---");
            for (Path p : sourceFiles) {
                objectService.hashAndWriteBlob(p.toFile());
            }

            long compressedSizeBytes = getDirectorySize(Paths.get(BENCHMARK_DIR, ".lvc", "objects"));
            double compressionRatio = (1.0 - ((double) compressedSizeBytes / rawSizeBytes)) * 100;

            System.out.printf("LiteVC Stored Size: %d bytes (%.2f KB)\n", compressedSizeBytes, compressedSizeBytes / 1024.0);
            System.out.printf("Storage Reduction: %.2f%%\n\n", compressionRatio);

            // Phase 3: Test Content Deduplication (Simulating 10 exact copies / commits)
            System.out.println("--- Phase 2: Content Deduplication Test ---");
            System.out.println("Simulating a user duplicating the entire codebase 10 times...");

            long simulatedRawSize = rawSizeBytes * 10;

            // Re-writing the exact same files 10 times
            for (int i = 0; i < 10; i++) {
                for (Path p : sourceFiles) {
                    objectService.hashAndWriteBlob(p.toFile()); // Hashes will match, writes will be skipped!
                }
            }

            long finalStoredSizeBytes = getDirectorySize(Paths.get(BENCHMARK_DIR, ".lvc", "objects"));
            double dedupeRatio = (1.0 - ((double) finalStoredSizeBytes / simulatedRawSize)) * 100;

            System.out.printf("Simulated Raw Size (10x): %d bytes (%.2f KB)\n", simulatedRawSize, simulatedRawSize / 1024.0);
            System.out.printf("LiteVC Stored Size:       %d bytes (%.2f KB)\n", finalStoredSizeBytes, finalStoredSizeBytes / 1024.0);
            System.out.printf("Effective Space Saved:    %.2f%%\n", dedupeRatio);

            System.out.println("\n✅ Benchmark Complete. The DAG deduplication is O(1) space for unmodified files.");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Clean up the temporary benchmark database
            cleanBenchmarkDir();
        }
    }

    private static List<Path> getCorpusFiles(Path startDir) throws IOException {
        List<Path> files = new ArrayList<>();
        if (!Files.exists(startDir)) return files;

        try (Stream<Path> stream = Files.walk(startDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(files::add);
        }
        return files;
    }

    private static long calculateTotalSize(List<Path> files) throws IOException {
        long size = 0;
        for (Path p : files) {
            size += Files.size(p);
        }
        return size;
    }

    private static long getDirectorySize(Path dir) throws IOException {
        if (!Files.exists(dir)) return 0;
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(p -> p.toFile().length())
                    .sum();
        }
    }

    private static void cleanBenchmarkDir() {
        File dir = new File(BENCHMARK_DIR);
        if (dir.exists()) {
            deleteRecursively(dir);
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}