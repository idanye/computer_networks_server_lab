import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HTTPRequest {
    private String type; // GET, POST, etc.
    private String requestedPage; // /, /index.html, etc.
    private boolean isImage; // Whether the requested page is an image
    private int contentLength; // Content-Length from the header
    private String referer; // Referer header
    private String userAgent; // User-Agent header
    private Map<String, String> parameters; // Holds parameters

    // Constructor that takes the request header and parses various components
    public HTTPRequest(BufferedReader reader) throws IOException {
        // Initialize variables
        this.type = "";
        this.requestedPage = "";
        this.isImage = false;
        this.contentLength = 0;
        this.referer = "";
        this.userAgent = "";
        this.parameters = new HashMap<>();

        // Read the first line to get the request type and path
        String line = reader.readLine();
        if (line != null) {
            parseRequestLine(line);
        }

        // Continue reading headers
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            parseHeaderLine(line);
        }
    }

    private void parseRequestLine(String line) {
        // Example Request-Line: "GET /index.html HTTP/1.1"
        String[] parts = line.split(" ");
        if (parts.length >= 2) {
            this.type = parts[0];
            this.requestedPage = parts[1];
            // Check if the requested page is an image
            if (requestedPage.matches(".*\\.(jpg|bmp|gif)$")) {
                this.isImage = true;
            }
        }
    }

    private void parseHeaderLine(String line) {
        // Example Header-Line: "Referer: http://example.com/"
        String[] parts = line.split(": ");
        if (parts.length >= 2) {
            switch (parts[0]) {
                case "Content-Length":
                    this.contentLength = Integer.parseInt(parts[1].trim());
                    break;
                case "Referer":
                    this.referer = parts[1];
                    break;
                case "User-Agent":
                    this.userAgent = parts[1];
                    break;
            }
        }
    }

    // Getters for all the properties
    public String getType() {
        return type;
    }

    public String getRequestedPage() {
        return requestedPage;
    }

    public boolean isImage() {
        return isImage;
    }

    public int getContentLength() {
        return contentLength;
    }

    public String getReferer() {
        return referer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    // Add more functionality as needed
}
