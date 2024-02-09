import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Util {
  public static byte[] StringToBytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
    private static final Object lock = new Object();
    static StringBuilder logBuilder = new StringBuilder();

    public static void writeToByteStreamAndLog(OutputStream out, String s) throws IOException {
        out.write(StringToBytes(s));
        // Aggregate log messages in a thread-safe manner
        synchronized (lock) {
            logBuilder.append("< ").append(s);
        }
    }

    // Call this method at the end of the thread's execution or in a finally block
    public static void flushLog() {
        synchronized (lock) {
            System.out.print(logBuilder.toString());
            logBuilder.setLength(0);
        }
    }

    //public static void writeToByteStreamAndLog(OutputStream out, String s) throws IOException{
    //out.write(StringToBytes(s));
    //System.out.print("< " + s);
  //}

  public static byte[] charArrayToBytes(char[] chars) {
    byte[] bytes = new byte[chars.length];
    
    for (int i = 0; i < chars.length; i++) {
      bytes[i] = (byte)chars[i];
    }
    return bytes;
  } 

  public static Map<String, String> parseQueryString(String queryString) throws BadRequestException {
    Map<String, String> params = new HashMap<String, String>();
    String[] pairs = queryString.split("&");
    for (String pair : pairs) {
        if (pair.length() == 0) {
          continue;
        }
        String[] keyValue = pair.split("=");
          
        if (keyValue.length > 2 || 
            // Workaround weird java 'split' behaviour 
            (keyValue.length == 1 && pair.charAt(pair.length() - 1) != '=')
          ) {
            throw new BadRequestException();
        }

        if (keyValue.length == 2) {
          params.put(keyValue[0], keyValue[1]);
        } else {
          params.put(keyValue[0], "");
        }
    }

    return params;
}
}
