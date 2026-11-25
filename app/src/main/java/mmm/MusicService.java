package mmm;

import java.io.*;
import java.time.*;
import java.util.*;
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
    if (repository instanceof RemoteRepository remote) return remote.serverToday();
    return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
  }

  public String user() {
    return user;
  }

  public void logout() throws IOException {
    if (repository instanceof RemoteRepository remote) remote.logoutRemote();
    user = null;
  }

  public boolean remote() { return repository instanceof RemoteRepository; }

  public void reauthenticate(String id,char[] password) throws Exception {
    if (!(repository instanceof RemoteRepository remote)) throw new IOException("원격 모드에서 다시 로그인하세요.");
    if (user != null && !user.equals(id)) throw new IOException("초안 작성자와 같은 계정으로 로그인하세요.");
    user = remote.login(id,password);
  }

  public String storageDescription() {
    return repository instanceof RemoteRepository remote ? "서버에 저장 · " + remote.endpoint() : "이 기기에 저장 · 공동 기능은 로컬 모의";
  }

  public void register(String id, String nickname, char[] password, char[] confirmation)
      throws Exception {
    PasswordCodec.validate(id, nickname, password);
    if (!Arrays.equals(password, confirmation)) throw new IllegalArgumentException("비밀번호 확인이 일치하지 않습니다.");
    if (repository instanceof RemoteRepository remote) { remote.signup(id,nickname,password); return; }
    PasswordCodec.Digest digest=PasswordCodec.create(password);
    repository.register(new Account(id,nickname,digest.salt(),digest.hash()));
  }

  public void login(String id, char[] password) throws Exception {
    user = null;
    if (repository instanceof RemoteRepository remote) { user=remote.login(id,password); return; }
    Account a = repository.account(id).orElseThrow(() -> new IOException("아이디 또는 비밀번호를 확인하세요."));
    if (!PasswordCodec.verify(password,a.salt(),a.hash()))
      throw new IOException("아이디 또는 비밀번호를 확인하세요.");
    user = id;
  }

  public List<Song> songs() throws IOException {
    requireLogin();
    return repository.songs(user);
  }

  private void requireLogin() throws IOException {
    if (user == null) throw new IOException("계정 로그인이 필요합니다.");
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

  public List<EnsembleData.Member> participants(String songId) throws IOException {
    if (repository instanceof RemoteRepository remote) return remote.participants(songId);
    Song song = access(songId);
    List<EnsembleData.Member> members = new ArrayList<>();
    for (String id : new TreeSet<>(song.members()))
      members.add(
          new EnsembleData.Member(
              id,
              repository.account(id).map(Account::nickname).orElse(id),
              id.equals(song.owner())));
    return List.copyOf(members);
  }

  public Phrase save(Phrase draft) throws IOException {
    if (repository instanceof RemoteRepository remote) {
      requireLogin();
      if (!draft.author().equals(user)) throw new IOException("다른 참여자의 기록은 변경할 수 없습니다.");
      return remote.save(draft,draft.version());
    }
    Song song = access(draft.songId());
    if (!draft.author().equals(user)) throw new IOException("다른 참여자의 기록은 변경할 수 없습니다.");
    if (!draft.date().equals(today()))
      throw new IOException("날짜가 바뀌었습니다. 오늘 기록만 저장할 수 있습니다. 초안은 유지됩니다.");
    if (song.shared() && draft.bpm() != song.bpm()) throw new IOException("공동곡은 공통 BPM을 사용합니다.");
    if (draft.empty()) throw new IOException("음 또는 명시적인 쉼을 먼저 입력하세요.");
    return repository.save(draft, draft.version());
  }

  public Song create(String name, boolean shared, int bpm, int instrument) throws IOException {
    requireLogin();
    if (repository instanceof RemoteRepository remote) return remote.create(name.strip(),shared,bpm,instrument);
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
