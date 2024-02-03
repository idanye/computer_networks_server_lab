import java.io.*;
import java.net.*;
import java.net.Socket;
import java.nio.file.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiThreadedServer {
    static ServerConfig serverConfig;
    static int port;
    static int maxThreads;
    static String rootDirectory;
    static String defaultPage;

    static AtomicInteger activeConnections = new AtomicInteger(0);


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
                if (activeConnections.get() >= maxThreads) {
                    System.out.println("Connection limit exceeded. Denying new connections.");
                    // Optionally, you might want to sleep for a bit here to prevent a tight loop
                    try {
                        Thread.sleep(100); // Sleep for 100 milliseconds
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }

                Socket clientSocket = serverSocket.accept();
                int clientId = activeConnections.incrementAndGet(); // Use this as a unique ID
                System.out.println("Client connected. Current clients: " + activeConnections.get() + ". Client ID: " + clientId);
                threadPool.execute(new ClientHandler(clientSocket, rootDirectory, defaultPage, clientId)); // Pass clientId to ClientHandler
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
    private int clientId;


    public ClientHandler(Socket socket, String rootDir, String defaultPg, int clientId) {
        this.clientSocket = socket;
        this.rootDirectory = rootDir;
        this.defaultPage = defaultPg;
        this.clientId = clientId;
    }
    @Override
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();
            System.out.println("Client ID " + clientId + " started interaction.");

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
            System.out.println("Error handling client ID " + clientId + ": " + e.getMessage());
        } finally {
            try {
                System.out.println("Client ID " + clientId + " completed interaction.");
                clientSocket.close();
            } catch (IOException ex) {
                System.out.println("Error closing connection for client ID " + clientId + ": " + ex.getMessage());
            }
            finally{
                int currentClients = MultiThreadedServer.activeConnections.decrementAndGet(); // Decrement the counter
                System.out.println("Client ID " + clientId + " disconnected. Current clients: " + currentClients);
            }
        }
    }

    private void handleGetRequest(HTTPRequest request, OutputStream out) throws IOException {
        String homeDirectory = System.getProperty("user.home");
        //System.out.println("Current home directory: "+ homeDirectory);
        String correctedRootDirectory = rootDirectory.replaceFirst("^~", homeDirectory);

        String requestedFile = request.getRequestedPage().equals("/") ? defaultPage : request.getRequestedPage();
        Path filePath = Paths.get(correctedRootDirectory, requestedFile);

        System.out.println("Request:\n" + request.getType() + " " + request.getRequestedPage() + " HTTP/1.1" + "\r\n");

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
        System.out.println("Request:\n" + request.getType() + " " + request.getRequestedPage() + " HTTP/1.1" + "\r\n");
        System.out.println("Content-length: " + request.getContentLength());
        // Use the parsed parameters from HTTPRequest
        Map<String, String> postData = request.getParameters();

        // For debugging: Print each parameter and its value
        postData.forEach((key, value) -> System.out.println(key + ": " + value));

        // Respond back to the client
        String responseMessage = "Received POST Data: " + postData.toString();
        sendSuccessResponse(out, "text/plain", responseMessage.getBytes());
    }


    private void handleHeadRequest(HTTPRequest request, OutputStream out) throws IOException {
        System.out.println("Request:\n" + request.getType() + " " + request.getRequestedPage() + " HTTP/1.1" + "\r\n");

        String homeDirectory = System.getProperty("user.home");
        String correctedRootDirectory = rootDirectory.replaceFirst("^~", homeDirectory);

        String requestedFile = request.getRequestedPage().equals("/") ? defaultPage : request.getRequestedPage();
        Path filePath = Paths.get(correctedRootDirectory, requestedFile);

        if (!Files.exists(filePath)) {
            sendErrorResponse(out, 404, "404 Not Found");
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

        // Start the response with the status line
        writer.print("HTTP/1.1 200 OK\r\n");

        // Specify the content type of the response
        writer.print("Content-Type: message/http\r\n");

        // Echoing back the received request line (assuming you reconstruct it or have it stored)
        writer.print("TRACE " + request.getRequestedPage() + " HTTP/1.1\r\n");

        // Echoing back all the headers of the received request
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            writer.print(header.getKey() + ": " + header.getValue() + "\r\n");
        }

        writer.flush();
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



