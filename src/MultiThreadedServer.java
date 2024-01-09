import java.io.*;
import java.net.*;
import java.net.Socket;
import java.nio.file.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultiThreadedServer {
    static ServerConfig serverConfig;
    static int port;
    static int maxThreads;
    static String rootDirectory;
    static String defaultPage;

    // Static block for initialization
    static {
        serverConfig = new ServerConfig("config.ini");
        port = serverConfig.getPort();
        maxThreads = serverConfig.getMaxThreads();
        rootDirectory = serverConfig.getRootDirectory();
        defaultPage = serverConfig.getDefaultPage();
    }

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port);

            // limiting the number of threads
            ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected!\n");

                    // Using thread pool to handle client connections
                    threadPool.execute(new ClientHandler(clientSocket, rootDirectory, defaultPage));
                } catch (IOException ex) {
                    System.out.println("Server exception: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }

        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;
    private String rootDirectory;
    private String defaultPage;

    public ClientHandler(Socket socket, String rootDir, String defaultPg) {
        this.clientSocket = socket;
        this.rootDirectory = rootDir;
        this.defaultPage = defaultPg;
    }

    @Override
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();

            String requestLine = in.readLine(); // Read the first line of the request

            if (requestLine == null || !requestLine.contains("HTTP")) {
                sendErrorResponse(out, 400, "Bad Request");
                return;
            }

            // Create an instance of HTTPRequest to parse the incoming request
            HTTPRequest request = new HTTPRequest(in);

            String method = request.getType();
            if (!method.equals("GET")) {
                sendErrorResponse(out, 501, "Not Implemented");
                return;
            }

            String requestedFile = request.getRequestedPage().equals("/") ? defaultPage : request.getRequestedPage();
            Path filePath = Paths.get(rootDirectory, requestedFile);

            if (!Files.exists(filePath)) {
                sendErrorResponse(out, 404, "Not Found");
                return;
            }

            String contentType = determineContentType(filePath);
            byte[] fileContent = Files.readAllBytes(filePath);

            sendSuccessResponse(out, contentType, fileContent);
        } catch (Exception e) {
            try {
                sendErrorResponse(clientSocket.getOutputStream(), 500, "Internal Server Error");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void sendErrorResponse(OutputStream out, int statusCode, String statusMessage) throws IOException {
        PrintWriter writer = new PrintWriter(out, true);
        writer.println("HTTP/1.1 " + statusCode + " " + statusMessage);
        writer.println("Content-Type: text/html");
        writer.println();
        writer.println("<html><body><h1>" + statusMessage + "</h1></body></html>");
    }

    private void sendSuccessResponse(OutputStream out, String contentType, byte[] content) throws IOException {
        PrintWriter writer = new PrintWriter(out, true);
        writer.println("HTTP/1.1 200 OK");
        writer.println("Content-Type: " + contentType);
        writer.println("Content-Length: " + content.length);
        writer.println();
        out.write(content);
        out.flush();
    }

    private String determineContentType(Path filePath) {
        String fileName = filePath.toString().toLowerCase();
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "text/html";
        } else if (fileName.matches(".*\\.(jpg|jpeg|png|gif|bmp)$")) {
            return "image/" + getExtension(fileName);
        } else if (fileName.endsWith(".ico")) {
            return "image/x-icon";
        } else {
            return "application/octet-stream"; // default for other file types
        }
    }

    private String getExtension(String fileName) {
        int lastIndex = fileName.lastIndexOf('.');
        if (lastIndex > 0) {
            return fileName.substring(lastIndex + 1);
        }
        return "";
    }
}



