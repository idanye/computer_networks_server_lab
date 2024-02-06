import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HTTPRequest {
    private String type; // GET, POST, HEAD, TRACE
    private String requestedPage;
    private Map<String, String> headers;
    private String body;
    private Map<String, String> parameters;

    //Constructor
    public HTTPRequest(BufferedReader reader) throws IOException, BadRequestException {
        this.type = "";
        this.requestedPage = "";
        this.headers = new HashMap<>();
        this.body = "";
        this.parameters = new HashMap<>();

        // read the first line to get the request type and path
        String line = reader.readLine();
        if (line == null) {
            throw new BadRequestException();
        }
        parseRequestLine(line);


        // read headers
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            parseHeaderLine(line);
        }

        readBody(reader);
    }

    private void readBody(BufferedReader reader) throws IOException {
        // Parse Content-Length
        int contentLength = 0;
        try {
            contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
        } catch (NumberFormatException e) {

        }

        char[] buffer = new char[contentLength];
        reader.read(buffer, 0, contentLength);
        this.body = new String(buffer);

        // Parse body
        parseBody();
    }
    private void parseBody() {
        // Implement parsing logic for different content types
        String contentType = headers.get("Content-Type");
        if ("application/x-www-form-urlencoded".equalsIgnoreCase(contentType)) {
            parseUrlEncodedBody();
        } else if (contentType != null && contentType.startsWith("multipart/form-data")) {
            parseMultipartFormData();
        }
    }
    private void parseUrlEncodedBody() {
        // Parse URL-encoded body
        String[] pairs = this.body.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                this.parameters.put(keyValue[0], keyValue[1]);
            }
        }
    }

    //not sure if we need it?
    private void parseMultipartFormData() {
        // Implement parsing for multipart/form-data
        // This will require processing the body to extract files and parameters
        // Handle file uploads and other form fields
    }

    private void parseRequestLine(String line) throws BadRequestException {
        // Example Request-Line: "GET /index.html HTTP/1.1"
        String[] parts = line.split(" ");

        if (parts.length != 3) {
            throw new BadRequestException("Bad request details");
        }
        
        this.type = parts[0];
        // Split the requested page and query string
        String[] pathAndQuery = parts[1].split("\\?");
        this.requestedPage = pathAndQuery[0];
        if (!isLegalPageRequest(requestedPage)) {
            throw new BadRequestException("Bad request details");
        }
        // Parse query string for GET requests
        if ("GET".equalsIgnoreCase(this.type) && pathAndQuery.length > 1) {
            parseQueryString(pathAndQuery[1]);
        }
    }

    private boolean isLegalPageRequest(String path) {
        String[] parts = path.split("[/\\\\]");
        for (String part : parts) {
            if (part == "..") {
                return false;
            }
        }

        return true;
    }

    private void parseQueryString(String queryString) throws BadRequestException {
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            
            if (keyValue.length != 2) {
                throw new BadRequestException("Bad request details");
            }

            this.parameters.put(keyValue[0], keyValue[1]); // Consider URL decoding
        }
    }

    private void parseHeaderLine(String line) throws BadRequestException {
        // Example Header-Line: "Referer: http://example.com/"
        String[] parts = line.split(": ");
        if (parts.length != 2) {
            throw new BadRequestException("Bad request details");
        }
        headers.put(parts[0].trim(), parts[1].trim());
    }



    // Getters for all the properties
    public String getType() {
        return type;
    }
    public String getRequestedPage() {
        return requestedPage;
    }
    public int getContentLength() {
        // Extract content length from headers and convert to integer
        try {
            return Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getReferer() {
        // Extract the referer from headers
        return headers.getOrDefault("Referer", "");
    }

    public String getUserAgent() {
        // Extract the user agent from headers
        return headers.getOrDefault("User-Agent", "");
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    // Add more functionality as needed
    public Map<String, String> getHeaders() {
        return headers;
    }
}
