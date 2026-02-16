package lvc.cli.commands;

import lvc.cli.Command;
import lvc.core.ObjectService;

import java.io.File;

public class HashObjectCommand implements Command {

    private final String workingDirPath;
    private final ObjectService objectService;

    public HashObjectCommand() {
        this.workingDirPath = System.getProperty("user.dir");
        this.objectService = new ObjectService(this.workingDirPath);
    }

    public HashObjectCommand(String workingDirPath) {
        this.workingDirPath = workingDirPath;
        this.objectService = new ObjectService(this.workingDirPath);
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: lvc hash-object <file>");
            return;
        }

        String filePath = args[1];
        File targetFile = new File(workingDirPath, filePath);

        if (!targetFile.exists() || !targetFile.isFile()) {
            System.err.println("Fatal: Cannot open '" + filePath + "': No such file");
            return;
        }

        try {
            // Delegate the heavy lifting to the ObjectService
            String hash = objectService.hashAndWriteBlob(targetFile);

            // Print the resulting hash to the terminal, just like Git
            System.out.println(hash);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
