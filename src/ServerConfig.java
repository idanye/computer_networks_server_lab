import java.io.*;
import java.util.*;
import java.nio.file.Paths;
import java.nio.file.Path;
public class ServerConfig {
    private int port;
    private String rootDirectory;
    private String defaultPage;
    private int maxThreads;

    public ServerConfig(String configFilePath) {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream(configFilePath));

            // Reading each property
            port = Integer.parseInt(prop.getProperty("port"));
            //rootDirectory = prop.getProperty("root");
            rootDirectory = resolveRootDirectory(prop.getProperty("root"));

            defaultPage = prop.getProperty("defaultPage");
            maxThreads = Integer.parseInt(prop.getProperty("maxThreads"));

        } catch (IOException ex) {
            System.out.println("Error reading config file: " + ex.getMessage());
        }
    }

    private String resolveRootDirectory(String configRootPath) {
        // Check if the path starts with "~" which indicates a path relative to the user's home directory
        if (configRootPath.startsWith("~")) {
            String homeDirectory = System.getProperty("user.home");
            configRootPath = configRootPath.replaceFirst("~", homeDirectory);
        }

        // Ensure the path is correctly formed, especially for Windows
        Path path = Paths.get(configRootPath).normalize();
        return path.toString();
    }
    public int getPort() { return port; }
    public String getRootDirectory() { return rootDirectory; }
    public String getDefaultPage() { return defaultPage; }
    public int getMaxThreads() { return maxThreads; }
}