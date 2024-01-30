import java.io.*;
import java.util.*;
import java.nio.file.Paths;

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
        if (configRootPath.startsWith("~")) {
            String homeDirectory = System.getProperty("user.home");
            // Ensure we add a separator between the home directory and the subsequent path
            configRootPath = homeDirectory + File.separator + configRootPath.substring(1);
        }

        // Normalize the path to ensure it's correct for the current OS and remove redundant name elements
        return Paths.get(configRootPath).normalize().toString();
    }

    public int getPort() { return port; }
    public String getRootDirectory() { return rootDirectory; }
    public String getDefaultPage() { return defaultPage; }
    public int getMaxThreads() { return maxThreads; }
}