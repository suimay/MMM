package mmm;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordCodec {
  private PasswordCodec() {}
  public record Digest(String salt, String hash) {
    @Override public String toString() { return "Digest[redacted]"; }
  }
  public static void validate(String id, String nickname, char[] password) {
    if (id == null || !id.matches("[A-Za-z0-9_]{3,24}")) throw new IllegalArgumentException("아이디는 영문·숫자·밑줄 3~24자입니다.");
    if (nickname == null || nickname.isBlank() || nickname.codePointCount(0,nickname.length()) > 20)
      throw new IllegalArgumentException("닉네임은 1~20자입니다.");
    if (password == null || password.length < 8 || password.length > 256)
      throw new IllegalArgumentException("비밀번호는 8~256자입니다.");
  }
  public static Digest create(char[] password) {
    byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
    String encoded = Base64.getEncoder().encodeToString(salt);
    return new Digest(encoded, hash(password, encoded));
  }
  public static String hash(char[] password, String salt) {
    PBEKeySpec spec = new PBEKeySpec(password, Base64.getDecoder().decode(salt),120000,256);
    try { return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded()); }
    catch (GeneralSecurityException ex) { throw new IllegalStateException("비밀번호 처리 실패", ex); }
    finally { spec.clearPassword(); }
  }
  public static boolean verify(char[] password, String salt, String expected) {
    return MessageDigest.isEqual(hash(password,salt).getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII));
  }
  public static String sha256(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
  }
}
