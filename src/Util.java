import java.nio.charset.StandardCharsets;

public class Util {
  public static byte[] StringToBytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
}
