import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Util {
  public static byte[] StringToBytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
    static final Object lock = new Object();

    public static void writeToByteStreamAndLog(StringBuilder log, OutputStream out, String s) throws IOException {
      out.write(StringToBytes(s));
      log.append("< ").append(s);
    }

    public static void printLogsToServer(StringBuilder log) {
        synchronized (lock) {
            System.out.print(log.toString());
        }
    }

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
