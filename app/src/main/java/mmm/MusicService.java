package mmm;

import java.io.*;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import mmm.Model.*;

public final class MusicService {
  public final Repository repository;
  private final Clock clock;
  public final boolean demo;
  private String user;

  public MusicService(Repository repository, Clock clock, boolean demo) {
    this.repository = repository;
    this.clock = clock;
    this.demo = demo;
  }

  public LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
  }

  public String user() {
    return user;
  }

  public void logout() {
    user = null;
  }

  public void register(String id, String nickname, char[] password, char[] confirmation)
      throws Exception {
    if (!id.matches("[A-Za-z0-9_]{3,24}"))
      throw new IllegalArgumentException("아이디는 영문·숫자·밑줄 3~24자입니다.");
    if (nickname.isBlank() || nickname.length() > 20)
      throw new IllegalArgumentException("닉네임은 1~20자입니다.");
    if (password.length < 8 || !Arrays.equals(password, confirmation))
      throw new IllegalArgumentException("비밀번호는 8자 이상이며 확인과 같아야 합니다.");
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    String encoded = Base64.getEncoder().encodeToString(salt);
    repository.register(new Account(id, nickname, encoded, hash(password, encoded)));
  }

  private static String hash(char[] password, String salt) throws GeneralSecurityException {
    PBEKeySpec spec = new PBEKeySpec(password, Base64.getDecoder().decode(salt), 120000, 256);
    try {
      return Base64.getEncoder()
          .encodeToString(
              SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                  .generateSecret(spec)
                  .getEncoded());
    } finally {
      spec.clearPassword();
    }
  }

  public void login(String id, char[] password) throws Exception {
    user = null;
    Account a = repository.account(id).orElseThrow(() -> new IOException("아이디 또는 비밀번호를 확인하세요."));
    if (!MessageDigest.isEqual(hash(password, a.salt()).getBytes(), a.hash().getBytes()))
      throw new IOException("아이디 또는 비밀번호를 확인하세요.");
    user = id;
  }

  public List<Song> songs() throws IOException {
    requireLogin();
    return repository.songs(user);
  }

  private void requireLogin() throws IOException {
    if (user == null) throw new IOException("로컬 계정 로그인이 필요합니다.");
  }

  public Song access(String id) throws IOException {
    return songs().stream()
        .filter(s -> s.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IOException("이 곡에 접근할 권한이 없습니다."));
  }

  public List<Phrase> phrases(String id) throws IOException {
    access(id);
    return repository.phrases(id);
  }

  public Phrase save(Phrase draft) throws IOException {
    Song song = access(draft.songId());
    if (!draft.author().equals(user)) throw new IOException("다른 참여자의 기록은 변경할 수 없습니다.");
    if (!draft.date().equals(today()))
      throw new IOException("날짜가 바뀌었습니다. 오늘 기록만 저장할 수 있습니다. 초안은 유지됩니다.");
    if (song.shared() && draft.bpm() != song.bpm()) throw new IOException("공동곡은 공통 BPM을 사용합니다.");
    return repository.save(draft, draft.version());
  }

  public Song create(String name, boolean shared, int bpm, int instrument) throws IOException {
    requireLogin();
    Song s =
        new Song(
            UUID.randomUUID().toString(),
            name.strip(),
            user,
            shared,
            bpm,
            instrument,
            Set.of(user));
    repository.createSong(s);
    return s;
  }

  public void join(String code) throws IOException {
    requireLogin();
    repository.redeem(code, user, clock.instant());
  }

  public String invite(String id) throws IOException {
    access(id);
    return repository.invite(id, user, clock.instant().plus(Duration.ofDays(7)));
  }

  public Song updateSong(Song expected, String name, int bpm, int instrument) throws IOException {
    requireLogin();
    return repository.updateSong(expected, name, bpm, instrument, user);
  }

  public void leave(String id) throws IOException {
    access(id);
    repository.leave(id, user);
  }

  public void seed() throws Exception {
    if (!demo) return;
    for (String id : List.of("demo", "star", "moon"))
      if (repository.account(id).isEmpty())
        register(
            id,
            id.equals("demo") ? "나 (시연)" : id.equals("star") ? "별 (샘플)" : "달 (샘플)",
            "demo1234".toCharArray(),
            "demo1234".toCharArray());
    repository.seedDemo(today());
  }
}
