package lvc.core;

// To find the root folder of the project, incase of sub folders.
import java.io.File;

public class Repository {

    public static File findRepositoryRoot() {

        File currentDir = new File(System.getProperty("user.dir"));

        while (currentDir != null) {

            File repoDir = new File(currentDir, ".lvc");

            if (repoDir.exists() && repoDir.isDirectory()) {
                return currentDir;
            }

            currentDir = currentDir.getParentFile();
        }

        return null;
    }

}
