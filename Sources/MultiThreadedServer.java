import java.io.*;
import java.net.*;
import java.net.Socket;
import java.nio.file.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiThreadedServer {
    public static AtomicInteger activeConnections = new AtomicInteger(0);
    public static void main(String[] args) {
        try {
            ServerConfig.init("../config.ini");
        } catch (Exception e) {
            System.out.println("failed reading config file (" + e + ")");
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(ServerConfig.instance.getPort())) {
            System.out.println("Server is listening on port " + ServerConfig.instance.getPort());
            System.out.println("rootDirectory: " + ServerConfig.instance.getRootDirectory());
            // limiting the number of threads to maxThreads from config.ini
            ExecutorService threadPool = Executors.newFixedThreadPool(ServerConfig.instance.getMaxThreads());
            try {
                while (true) {
                    if (activeConnections.get() >= ServerConfig.instance.getMaxThreads()) {
                        System.out.println("Connection limit exceeded. Denying new connections.");
                        try {
                            //put him to sleep for 1 seconds
                            Thread.sleep(100);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
    
                    Socket clientSocket = serverSocket.accept();
                    int clientId = activeConnections.getAndIncrement();
                    //for debugging:
                    //System.out.println("Client connected. Current clients: " + activeConnections.get() + ". Client ID: " + clientId);
                    threadPool.execute(new ClientHandler(clientSocket, clientId));     
                }
            } finally {
                threadPool.shutdown();
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

    public static int CHUNK_SIZE = 16;

    public ClientHandler(Socket socket, int clientId) {
        this.clientSocket = socket;
        this.clientId = clientId;
    }

    @Override
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();
            //debugging:
            //System.out.println("Client ID " + clientId + " started interaction.");
            try {
                // Parsing the incoming request using HTTPRequest
                HTTPRequest request = new HTTPRequest(in);
                switch (request.getMethod()) {
                    case "GET":
                        handleGetOrHeadRequest(request, out, false);
                        break;
                    case "POST":
                        handlePostRequest(request, out);
                        break;
                    case "HEAD":
                        handleGetOrHeadRequest(request, out, true);
                        break;
                    case "TRACE":
                        handleTraceRequest(request, out);
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
                Util.printLogsToServer();
                clientSocket.close();
            } catch (IOException ex) {
                System.out.println("Error closing connection for client ID " + clientId + ": " + ex.getMessage());
            }
            finally{
                int currentClients = MultiThreadedServer.activeConnections.decrementAndGet(); // Decrement the counter
                //System.out.println("Client ID " + clientId + " disconnected. Current clients using the server: " + currentClients);
            }
        }
    }

    private void handleGetOrHeadRequest(HTTPRequest request, OutputStream out, boolean isHead) throws IOException {
        String homeDirectory = System.getProperty("user.home");
        //System.out.println("Current home directory: "+ homeDirectory);
        String correctedRootDirectory = ServerConfig.instance.getRootDirectory().replaceFirst("^~", homeDirectory);

        String requestedFile = request.getRequestedPage().equals("/") ? ServerConfig.instance.getDefaultPage() : request.getRequestedPage();
        Path filePath = Paths.get(correctedRootDirectory, requestedFile);

        if (!Files.exists(filePath)) {
            System.out.println("File not found: " + filePath);
            sendErrorResponse(out, 404, "404 Not Found");
            return;
        }

        String contentType = determineContentType(filePath);
        if (isHead){
            Util.writeToByteStreamAndLog(out, "HTTP/1.1 200 OK\r\n");
            Util.writeToByteStreamAndLog(out, "Content-Type: " + contentType + "\r\n");
            Util.writeToByteStreamAndLog(out, "Content-Length: " + Files.size(filePath) + "\r\n");
            out.write(Util.StringToBytes("\r\n")); // No body is sent for HEAD request
        }else {
            byte[] fileContent = Files.readAllBytes(filePath);
            sendSuccessResponse(request, out, contentType, fileContent);
        }

    }
    private void handlePostRequest(HTTPRequest request, OutputStream out) throws IOException, BadRequestException {
        Map<String, String> urlParams = request.getParameters();

        String contentType = request.getHeaders().get("content-type");
        if (contentType == null || !contentType.equalsIgnoreCase("application/x-www-form-urlencoded")) {
            if (request.getBody().length > 0) {
                throw new BadRequestException();
            }
        }

        Map<String, String> bodyParams = Util.parseQueryString(new String(request.getBody()));

        // For debugging: Print each parameter and its value
        urlParams.forEach((key, value) -> System.out.println(key + ": " + value));

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
                + "<p> url params: " + urlParams.toString() + "</p>"
                + "<p> body params: " + bodyParams.toString() + "</p>"
                + "</body></center></html>";
        sendSuccessResponse(request, out, "text/html", responseMessage.getBytes());
    }

    private void handleTraceRequest(HTTPRequest request, OutputStream out) throws IOException {
        Util.writeToByteStreamAndLog(out, "HTTP/1.1 200 OK\r\n");
        Util.writeToByteStreamAndLog(out, "Content-Type: message/http\r\n");
        out.write(Util.StringToBytes("\r\n"));
        // the received request line
        Util.writeToByteStreamAndLog(out, "TRACE " + request.getRequestedPage() + " HTTP/1.1\r\n");
        // return all the headers of the received request
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            Util.writeToByteStreamAndLog(out, header.getKey() + ": " + header.getValue() + "\r\n");
        }
        out.flush();
    }

    private void sendErrorResponse(OutputStream out, int statusCode, String statusMessage) throws IOException {
        Util.writeToByteStreamAndLog(out, "HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n");
        Util.writeToByteStreamAndLog(out, "Content-Type: text/html\r\n");
        byte[] body = Util.StringToBytes("<html><body><h1>" + statusMessage + "</h1></body></html>\r\n");
        Util.writeToByteStreamAndLog(out, "Content-Length: " + body.length + "\r\n");
        out.write(Util.StringToBytes("\r\n"));
        out.write(body);
        out.flush();
    }

    private void sendSuccessResponse(HTTPRequest request, OutputStream out, String contentType, byte[] content) throws IOException {
        Util.writeToByteStreamAndLog(out, "HTTP/1.1 200 OK\r\n");
        Util.writeToByteStreamAndLog(out, "Content-Type: " + contentType + "\r\n");
        if (request.getHeaders().getOrDefault("chunked", "").equals("yes")) {
            Util.writeToByteStreamAndLog(out, "Transfer-Encoding: chunked\r\n");
            out.write(Util.StringToBytes("\r\n"));
            writeChunkedResponse(out, content);
        } else {
            Util.writeToByteStreamAndLog(out, "Content-Length: " + content.length + "\r\n");
            out.write(Util.StringToBytes("\r\n"));
            out.write(content);
        }
        out.flush();
    }

    private void writeChunkedResponse(OutputStream out, byte[] content) throws IOException{
        int i = 0;

        while (i < content.length) {
            if (content.length - i >= CHUNK_SIZE) {
                out.write(Util.StringToBytes(String.format("%x", CHUNK_SIZE) + "\r\n"));
                out.write(Arrays.copyOfRange(content, i, i + CHUNK_SIZE));
                out.write(Util.StringToBytes("\r\n"));
                i += CHUNK_SIZE;
                continue;
            }
            
            out.write(Util.StringToBytes(String.format("%x", content.length - i) + "\r\n"));
            out.write(Arrays.copyOfRange(content, i, content.length));
            out.write(Util.StringToBytes("\r\n"));
            i += (content.length - i);
        }
        out.write(Util.StringToBytes("0\r\n"));
        out.write(Util.StringToBytes("\r\n"));
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
}



