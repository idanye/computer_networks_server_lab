import java.awt.*;
import java.io.*;
import java.net.*;
import java.net.Socket;
import java.nio.file.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
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
            System.out.println("rootDirectory: " + rootDirectory);
            // limiting the number of threads
            ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
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
            System.out.println("New client connected!\n");

            // Parsing the incoming request using HTTPRequest
            HTTPRequest request = new HTTPRequest(in);

            switch (request.getType()) {
                case "GET":
                    handleGetRequest(request, out);
                    break;
                case "POST":
                    handlePostRequest(request, out);
                    break;
                case "HEAD":
                    handleHeadRequest(request, out);
                    break;
                case "TRACE":
                    handleTraceRequest(request, out); // Updated to pass HTTPRequest object
                    break;
                default:
                    sendErrorResponse(out, 501, "Not Implemented");
            }
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

    private void handleGetRequest(HTTPRequest request, OutputStream out) throws IOException {
        String homeDirectory = System.getProperty("user.home");
        System.out.println("Current home directory: "+ homeDirectory);
        String correctedRootDirectory = rootDirectory.replaceFirst("^~", homeDirectory);

        String requestedFile = request.getRequestedPage().equals("/") ? defaultPage : request.getRequestedPage();
        Path filePath = Paths.get(correctedRootDirectory, requestedFile);

        System.out.println("Attempting to access file: " + filePath);

        if (!Files.exists(filePath)) {
            System.out.println("File not found: " + filePath);
            sendErrorResponse(out, 404, "404 Not Found");
            return;
        }

        // Handling favicon.ico request
        if (requestedFile.equals("/favicon.ico")) {
            serveFavicon(out, correctedRootDirectory);
            return;
        }

        String contentType = determineContentType(filePath);
        byte[] fileContent = Files.readAllBytes(filePath);
        sendSuccessResponse(out, contentType, fileContent);
    }


    private void handlePostRequest(HTTPRequest request, OutputStream out) throws IOException {
        // Use the parsed parameters from HTTPRequest
        Map<String, String> postData = request.getParameters();

        // For debugging: Print each parameter and its value
        postData.forEach((key, value) -> System.out.println(key + ": " + value));

        // Respond back to the client
        // You can create a response based on the postData
        String responseMessage = "Received POST Data: " + postData.toString();
        sendSuccessResponse(out, "text/plain", responseMessage.getBytes());
    }


    private void handleHeadRequest(HTTPRequest request, OutputStream out) throws IOException {
        String homeDirectory = System.getProperty("user.home");
        String correctedRootDirectory = rootDirectory.replaceFirst("^~", homeDirectory);

        String requestedFile = request.getRequestedPage().equals("/") ? defaultPage : request.getRequestedPage();
        Path filePath = Paths.get(correctedRootDirectory, requestedFile);

        System.out.println("Attempting to access file (HEAD request): " + filePath);

        if (!Files.exists(filePath)) {
            System.out.println("File not found (HEAD request): " + filePath); //debug information
            sendErrorResponse(out, 404, "Not Found");
            return;
        }

        String contentType = determineContentType(filePath);
        PrintWriter writer = new PrintWriter(out, true);
        writer.println("HTTP/1.1 200 OK");
        writer.println("Content-Type: " + contentType);
        writer.println("Content-Length: " + Files.size(filePath));
        writer.println(); // No body is sent for HEAD request
    }

    private void handleTraceRequest(HTTPRequest request, OutputStream out) throws IOException {
        PrintWriter writer = new PrintWriter(out, true);

        // Start the response with a success status line
        writer.println("HTTP/1.1 200 OK");

        // Specify the content type of the response
        writer.println("Content-Type: message/http");

        // Echoing back the received request line
        writer.println(request.getRequestedPage());

        // Echoing back all the headers of the received request
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            writer.println(header.getKey() + ": " + header.getValue());
        }
        // End of headers, followed by a blank line
        writer.println();
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

    private void serveFavicon(OutputStream out, String correctedRootDirectory) throws IOException {
        Path faviconPath = Paths.get(correctedRootDirectory, "favicon.ico");
        if (Files.exists(faviconPath)) {
            byte[] faviconContent = Files.readAllBytes(faviconPath);
            sendSuccessResponse(out, "image/x-icon", faviconContent);
        } else {
            sendErrorResponse(out, 404, "Not Found");
        }
    }
}



