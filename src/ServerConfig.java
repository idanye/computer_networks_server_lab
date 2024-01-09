import java.io.*;
import java.util.*;

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
            rootDirectory = prop.getProperty("root");
            defaultPage = prop.getProperty("defaultPage");
            maxThreads = Integer.parseInt(prop.getProperty("maxThreads"));

        } catch (IOException ex) {
            System.out.println("Error reading config file: " + ex.getMessage());
        }
    }

    public int getPort() { return port; }
    public String getRootDirectory() { return rootDirectory; }
    public String getDefaultPage() { return defaultPage; }
    public int getMaxThreads() { return maxThreads; }
}