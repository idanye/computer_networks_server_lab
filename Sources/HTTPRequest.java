import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class HTTPRequest {
    private String method; // we support GET, POST, HEAD, TRACE
    private String requestedPage;
    private Map<String, String> headers; // The header names are stored as lowercase.
    private byte[] body;
    private Map<String, String> parameters;

    //Constructor
    public HTTPRequest(BufferedReader reader) throws IOException, BadRequestException {
        this.method = "";
        this.requestedPage = "";
        this.headers = new HashMap<>();
        this.body = new byte[0];
        this.parameters = new HashMap<>();

        // we read the first line to get the request type and path
        String line = reader.readLine();
        if (line == null) {
            throw new BadRequestException();
        }
        logMessage("> " + line);
        parseRequestLine(line);

        // read the rest of the headers
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            logMessage("> " + line);
            parseHeaderLine(line);
        }

        readBody(reader);
    }
    public void logMessage(String message) {
        synchronized (Util.lock) {
            Util.logBuilder.append(message).append("\n");
        }
    }
    private void readBody(BufferedReader reader) throws IOException, BadRequestException {
        // Parse Content-Length
        int contentLength = 0;

        String transfer_encoding = headers.get("transfer-encoding");
        if (transfer_encoding != null) {
            if (headers.get("content-length") != null) {
                throw new BadRequestException();
            }
            if (!transfer_encoding.equals("chunked")) {
                throw new BadRequestException();
            }
            readChunkedBody(reader);
            return;
        }

        try {
            contentLength = Integer.parseInt(headers.getOrDefault("content-length", "0"));
        } catch (NumberFormatException e) {
            throw new BadRequestException();
        }

        char[] buffer = new char[contentLength];
        reader.read(buffer, 0, contentLength);

        this.body = Util.charArrayToBytes(buffer);
    }
    
    private void readChunkedBody(BufferedReader reader) throws IOException, BadRequestException{
        ByteArrayOutputStream bodyBuilder = new ByteArrayOutputStream();

        while (true) {
            String line = reader.readLine();
            if (line.length() == 0) {
                break;
            }

            int chunkLength = 0;
            try {
                chunkLength = Integer.parseInt(line, 16);
            } catch (NumberFormatException e) {
                throw new BadRequestException();
            }

            char[] chunk = new char[chunkLength];
            reader.read(chunk, 0, chunkLength);
            reader.readLine(); // Skip the '\r\n' after the chunk
            bodyBuilder.write(Util.charArrayToBytes(chunk));

            if (chunkLength == 0) {
                break;
            }
        }

        this.body = bodyBuilder.toByteArray();
    }

    private void parseRequestLine(String line) throws BadRequestException {
        //example: GET /index.html HTTP/1.1 > we split to 3 parts
        String[] parts = line.split(" ");

        if (parts.length != 3) {
            throw new BadRequestException();
        }

        this.method = parts[0];
        // Split the requested page and query string
        String[] pathAndQuery = parts[1].split("\\?");
        this.requestedPage = isRequestGoingOutsideRoot(pathAndQuery[0]) ;

        // Parse query string for GET request
        if (pathAndQuery.length > 1) {
            this.parameters = Util.parseQueryString(pathAndQuery[1]);
        }
    }

    //check that we don't go out of the root. if we found .. we just ignore it and remove it from the path
    private String isRequestGoingOutsideRoot(String path) {
        String[] parts = path.split("[/\\\\]");
        LinkedList<String> newPath = new LinkedList<>();
        for (String part : parts) {
            if (part.equals("..") && !newPath.isEmpty()) {
                newPath.pollLast();
            } else {
                newPath.add(part);
            }
        }
        return "/" + String.join("/", newPath);
    }

    private void parseHeaderLine(String line) throws BadRequestException {
        String[] parts = line.split(": ");
        if (parts.length != 2) {
            throw new BadRequestException();
        }
        headers.put(parts[0].trim().toLowerCase(), parts[1].trim());
    }

    // Getters for all the properties
    public String getMethod() {
        return method;
    }
    public String getRequestedPage() {
        return requestedPage;
    }
    public int getContentLength() {
        // Extract content length from headers and convert to integer
        try {
            return Integer.parseInt(headers.getOrDefault("content-length", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    public byte[] getBody() {
        return body;
    }
    public Map<String, String> getParameters() {
        return parameters;
    }
    public Map<String, String> getHeaders() {
        return headers;
    }

}
