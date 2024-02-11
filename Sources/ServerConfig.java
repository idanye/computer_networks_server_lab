import java.io.*;
import java.util.*;
import java.nio.file.Paths;

public class ServerConfig {
    public static ServerConfig instance;
    private int port;
    private String rootDirectory;
    private String defaultPage;
    private int maxThreads;

    public static void init(String configFilePath) throws Exception{
        instance = new ServerConfig(configFilePath);
    } 

    public ServerConfig(String configFilePath) throws Exception{
        Properties prop = new Properties();
        prop.load(new FileInputStream(configFilePath));

        port = Integer.parseInt(prop.getProperty("port"));
        rootDirectory = getRootDirectory(prop.getProperty("root"));
        defaultPage = prop.getProperty("defaultPage");
        maxThreads = Integer.parseInt(prop.getProperty("maxThreads"));
    }

    private String getRootDirectory(String configRootPath) {
        if (configRootPath.startsWith("~")) {
            String homeDirectory = System.getProperty("user.home");
            // adding a separator between the home directory and the path
            configRootPath = homeDirectory + File.separator + configRootPath.substring(1);
        }
        // we used normalize to ensure it's works for multiple OS like windows and linux.
        return Paths.get(configRootPath).normalize().toString();
    }
    public int getPort() { return port; }
    public String getRootDirectory() { return rootDirectory; }
    public String getDefaultPage() { return defaultPage; }
    public int getMaxThreads() { return maxThreads; }
}