import java.io.*;
import java.net.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiThreadedServer {
    public static AtomicInteger activeConnections = new AtomicInteger(0);

    public static void main(String[] args) {
        try {
            ServerConfig.init("config.ini");
        } catch (Exception e) {
            System.out.println("Failed reading config file: " + e.getMessage());
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(ServerConfig.getInstance().getPort())) {
            System.out.println("Server is listening on port " + ServerConfig.getInstance().getPort());
            System.out.println("rootDirectory: " + ServerConfig.getInstance().getRootDirectory());
            // limiting the number of threads
            ExecutorService threadPool = Executors.newFixedThreadPool(ServerConfig.getInstance().getMaxThreads());

            while (true) {
                if (activeConnections.get() >= ServerConfig.getInstance().getMaxThreads()) {
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
                threadPool.execute(new ClientHandler(clientSocket, clientId));
            
            }

        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;
    private int clientId;

    public ClientHandler(Socket socket, int clientId) {
        this.clientSocket = socket;
        this.clientId = clientId;
    }

    @Override
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();
            //System.out.println("Client ID " + clientId + " started interaction.");
            //check that the multithreading works for the edge case where all threads are in use
            //Thread.sleep(20*1000);
            try {
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
            } catch (BadRequestException e) {
                sendErrorResponse(out, 400, "Bad Request");
            }
        } 
        catch (Exception e) {
            System.out.println("Error handling client ID " + clientId + ": " + e);
        } finally {
            try {
                //System.out.println("Client ID " + clientId + " completed interaction.");
                clientSocket.close();
            } catch (IOException ex) {
                System.out.println("Error closing connection for client ID " + clientId + ": " + ex.getMessage());
            }
            finally{
                int currentClients = MultiThreadedServer.activeConnections.decrementAndGet(); // Decrement the counter
                //System.out.println("Client ID " + clientId + " disconnected. Current clients: " + currentClients);
            }
        }
    }

    private void handleGetRequest(HTTPRequest request, OutputStream out) throws IOException {
        String homeDirectory = System.getProperty("user.home");
        //System.out.println("Current home directory: "+ homeDirectory);
        String correctedRootDirectory = ServerConfig.instance.getRootDirectory().replaceFirst("^~", homeDirectory);
        String requestedFile = request.getRequestedPage().equals("/") ? ServerConfig.instance.getDefaultPage() : request.getRequestedPage();

        // Normalize the requested path to avoid path traversal vulnerabilities.
        Path requestPath = Paths.get(correctedRootDirectory).resolve(requestedFile).normalize();

        // Path filePath = Paths.get(correctedRootDirectory, requestedFile);

        // Security check: Ensure the requested path does not lead outside the intended directory
        if (!requestPath.startsWith(Paths.get(correctedRootDirectory))) {
            System.out.println("Security violation: " + requestPath);
            sendErrorResponse(out, 403, "Forbidden");
            return;
        }

        System.out.println("Request:\n" + request.getType() + " " + request.getRequestedPage() + " HTTP/1.1" + "\r\n");

        if (!Files.exists(requestPath) || Files.isDirectory(requestPath)) {
            System.out.println("File not found: " + requestPath);
            sendErrorResponse(out, 404, "404 Not Found");
            return;
        }

        // Handling favicon.ico request
        if (requestedFile.equals("/favicon.ico")) {
            serverFavicon(out, correctedRootDirectory);
            return;
        }

        String contentType = determineContentType(requestPath);
        byte[] fileContent = Files.readAllBytes(requestPath);
        sendSuccessResponse(out, contentType, fileContent);
    }


    private void handlePostRequest(HTTPRequest request, OutputStream out) throws IOException {
        System.out.println("Request:\n" + request.getType() + " " + request.getRequestedPage() + " HTTP/1.1" + "\r\n");
        System.out.println("Content-length: " + request.getContentLength());
        // the post fata is the parameters from HTTPRequest
        Map<String, String> postData = request.getParameters();

        // For debugging: Print each parameter and its value
        postData.forEach((key, value) -> System.out.println(key + ": " + value));

        // Respond back to the client
        String responseMessage = "<!DOCTYPE html>"
                + "<html><center><head><title>POST Data Received</title>"
                + "<style>"
                + "  body { font-family: Arial, sans-serif; margin: 20px; background-color: #ADD8E6; }"
                + "  h1 { color: #333366; }"
                + "  p { color: #666666; }"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<h1>Received POST Data</h1>"
                + "<p>" + postData.toString() + "</p>"
                + "</body></center></html>";
        sendSuccessResponse(out, "text/html", responseMessage.getBytes());
    }


    private void handleHeadRequest(HTTPRequest request, OutputStream out) throws IOException {
        System.out.println("Request:\n" + request.getType() + " " + request.getRequestedPage() + " HTTP/1.1" + "\r\n");

        String homeDirectory = System.getProperty("user.home");
        String correctedRootDirectory = ServerConfig.instance.getRootDirectory().replaceFirst("^~", homeDirectory);

        String requestedFile = request.getRequestedPage().equals("/") ? ServerConfig.instance.getDefaultPage() : request.getRequestedPage();
        Path filePath = Paths.get(correctedRootDirectory, requestedFile);

        if (!Files.exists(filePath)) {
            sendErrorResponse(out, 404, "404 Not Found");
            return;
        }

        String contentType = determineContentType(filePath);
        out.write(Util.StringToBytes("HTTP/1.1 200 OK\r\n"));
        out.write(Util.StringToBytes("Content-Type: " + contentType + "\r\n"));
        out.write(Util.StringToBytes("Content-Length: " + Files.size(filePath) + "\r\n"));
        out.write(Util.StringToBytes("\r\n")); // No body is sent for HEAD request
    }

    private void handleTraceRequest(HTTPRequest request, OutputStream out) throws IOException {
        // Start the response with the status line
        out.write(Util.StringToBytes("HTTP/1.1 200 OK\r\n"));

        // Specify the content type of the response
        out.write(Util.StringToBytes("Content-Type: message/http\r\n"));

        // Echoing back the received request line (assuming you reconstruct it or have it stored)
        out.write(Util.StringToBytes("TRACE " + request.getRequestedPage() + " HTTP/1.1\r\n"));

        // Echoing back all the headers of the received request
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            out.write(Util.StringToBytes(header.getKey() + ": " + header.getValue() + "\r\n"));
        }

        out.flush();
    }

    private void sendErrorResponse(OutputStream out, int statusCode, String statusMessage) throws IOException {
        out.write(Util.StringToBytes("HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n"));
        out.write(Util.StringToBytes("Content-Type: text/html\r\n"));
        out.write(Util.StringToBytes("\r\n"));
        out.write(Util.StringToBytes("<html><body><h1>" + statusMessage + "</h1></body></html>\r\n"));
        out.flush();
    }

    private void sendSuccessResponse(OutputStream out, String contentType, byte[] content) throws IOException {
        out.write("HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Length: " + content.length + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("\r\n").getBytes(StandardCharsets.UTF_8));;
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
            return "icon";
        } else {
            return "application/octet-stream"; // all other file types
        }
    }

    private String getExtension(String fileName) {
        int lastIndex = fileName.lastIndexOf('.');
        if (lastIndex > 0) {
            return fileName.substring(lastIndex + 1);
        }
        return "";
    }

    private void serverFavicon(OutputStream out, String correctedRootDirectory) throws IOException {
        Path faviconPath = Paths.get(correctedRootDirectory, "favicon.ico");
        if (Files.exists(faviconPath)) {
            byte[] faviconContent = Files.readAllBytes(faviconPath);
            sendSuccessResponse(out, "image/x-icon", faviconContent);
        } else {
            sendErrorResponse(out, 404, "404 Not Found");
        }
    }
}



