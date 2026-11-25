package mmm;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import mmm.Model.*;

/**
 * UTF-8 Properties, schema 1, cross-process lock + atomic replacement. Never overwrites corrupt
 * input.
 */
public final class LocalRepository implements Repository {
  private final Path directory, file;

  public LocalRepository(Path directory) throws IOException {
    this.directory = directory;
    Files.createDirectories(directory);
    file = directory.resolve("mmm.properties");
  }

  public Path file() {
    return file;
  }

  private interface Transaction<T> {
    T run(Properties p) throws IOException;
  }

  private synchronized <T> T transact(boolean write, Transaction<T> action) throws IOException {
    try (FileChannel channel =
            FileChannel.open(
                directory.resolve("mmm.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        FileLock lock = channel.lock()) {
      Properties p = new Properties();
      if (Files.exists(file))
        try (Reader r = Files.newBufferedReader(file)) {
          p.load(r);
        } catch (Exception e) {
          throw new IOException("저장 파일을 읽을 수 없습니다. 원본을 보존했습니다.", e);
        }
      if (Files.exists(file) && !p.getProperty("schema", "").equals("1"))
        throw new IOException("지원하지 않는 저장 형식입니다.");
      validate(p);
      T result;
      try {
        result = action.run(p);
      } catch (RuntimeException e) {
        throw new IOException("데이터 검사 실패: " + e.getMessage(), e);
      }
      if (write) {
        p.setProperty("schema", "1");
        Path temp = Files.createTempFile(directory, "mmm-", ".tmp");
        try {
          try (Writer w = Files.newBufferedWriter(temp)) {
            p.store(w, "MMM local store - schema 1");
          }
          try (FileChannel f = FileChannel.open(temp, StandardOpenOption.WRITE)) {
            f.force(true);
          }
          if (Files.exists(file))
            Files.copy(
                file, directory.resolve("mmm.properties.bak"), StandardCopyOption.REPLACE_EXISTING);
          Files.move(
              temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
          Files.deleteIfExists(temp);
        }
      }
      return result;
    }
  }

  private static void validate(Properties p) throws IOException {
    try {
      for (String key : p.stringPropertyNames()) {
        String value = p.getProperty(key);
        if (key.startsWith("song.")) song(key.substring(5), value);
        else if (key.startsWith("phrase.")) {
          Phrase f = phrase(value);
          if (!key.equals("phrase." + f.key())) throw new IllegalArgumentException("기록 키 불일치");
        } else if (key.startsWith("user.")) {
          String[] f = fields(value);
          if (f.length != 3 || untext(f[0]).isBlank()) throw new IllegalArgumentException("계정 형식");
          Base64.getDecoder().decode(f[1]);
          Base64.getDecoder().decode(f[2]);
        } else if (key.startsWith("settings.")) {
          String[] f = fields(value);
          new Settings(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Boolean.parseBoolean(f[2]));
        } else if (key.startsWith("invite.")) {
          String[] f = fields(value);
          if (f.length != 2) throw new IllegalArgumentException("초대 형식");
          Instant.parse(f[1]);
        } else if (key.startsWith("meta.")) {
          /* Migration marker, no personal content. */
        } else if (!key.equals("schema")) throw new IllegalArgumentException("알 수 없는 필드");
      }
    } catch (RuntimeException e) {
      throw new IOException("저장 파일이 손상되었습니다. 원본을 보존했습니다. 백업을 복원하거나 별도 --data 폴더로 실행하세요.", e);
    }
  }

  private static String text(String x) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(x.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String untext(String x) {
    return new String(Base64.getUrlDecoder().decode(x), java.nio.charset.StandardCharsets.UTF_8);
  }

  private static String[] fields(String s) {
    return s.split("\\|", -1);
  }

  private static Song song(String id, String data) {
    String[] f = fields(data);
    return new Song(
        id,
        untext(f[0]),
        f[1],
        Boolean.parseBoolean(f[2]),
        Integer.parseInt(f[3]),
        Integer.parseInt(f[4]),
        new HashSet<>(Arrays.asList(f[5].split(","))));
  }

  private static String encode(Song s) {
    return text(s.name())
        + "|"
        + s.owner()
        + "|"
        + s.shared()
        + "|"
        + s.bpm()
        + "|"
        + s.instrument()
        + "|"
        + String.join(",", new TreeSet<>(s.members()));
  }

  public List<Song> songs(String user) throws IOException {
    return transact(
        false,
        p ->
            p.stringPropertyNames().stream()
                .filter(k -> k.startsWith("song."))
                .map(k -> song(k.substring(5), p.getProperty(k)))
                .filter(s -> s.members().contains(user))
                .sorted(Comparator.comparing(Song::name))
                .toList());
  }

  public Optional<Account> account(String id) throws IOException {
    return transact(
        false,
        p -> {
          String d = p.getProperty("user." + id);
          if (d == null) return Optional.empty();
          String[] f = fields(d);
          return Optional.of(new Account(id, untext(f[0]), f[1], f[2]));
        });
  }

  public void register(Account a) throws IOException {
    transact(
        true,
        p -> {
          if (p.containsKey("user." + a.id())) throw new IOException("이미 등록한 아이디입니다.");
          p.setProperty("user." + a.id(), text(a.nickname()) + "|" + a.salt() + "|" + a.hash());
          return null;
        });
  }

  public void createSong(Song s) throws IOException {
    transact(
        true,
        p -> {
          if (p.containsKey("song." + s.id())) throw new IOException("이미 존재하는 곡입니다.");
          p.setProperty("song." + s.id(), encode(s));
          return null;
        });
  }

  public void join(String id, String user) throws IOException {
    transact(
        true,
        p -> {
          String d = p.getProperty("song." + id);
          if (d == null) throw new IOException("유효하지 않은 초대 코드입니다.");
          Song s = song(id, d);
          if (!s.shared()) throw new IOException("개인 곡에는 참여할 수 없습니다.");
          if (s.members().contains(user)) throw new IOException("이미 참여 중인 곡입니다.");
          Set<String> m = new HashSet<>(s.members());
          m.add(user);
          p.setProperty(
              "song." + id,
              encode(new Song(s.id(), s.name(), s.owner(), true, s.bpm(), s.instrument(), m)));
          return null;
        });
  }

  private static Phrase phrase(String data) {
    String[] f = fields(data);
    List<NoteEvent> notes = new ArrayList<>();
    if (!f[8].isEmpty())
      for (String n : f[8].split(";")) {
        String[] v = n.split(",");
        notes.add(
            new NoteEvent(
                Integer.parseInt(v[0]),
                Integer.parseInt(v[1]),
                Integer.parseInt(v[2]),
                Integer.parseInt(v[3])));
      }
    return new Phrase(
        f[0],
        f[1],
        LocalDate.parse(f[2]),
        Integer.parseInt(f[3]),
        Integer.parseInt(f[4]),
        Integer.parseInt(f[5]),
        untext(f[6]),
        notes);
  }

  public List<Phrase> phrases(String id) throws IOException {
    return transact(
        false,
        p ->
            p.stringPropertyNames().stream()
                .filter(k -> k.startsWith("phrase." + id + "/"))
                .map(k -> phrase(p.getProperty(k)))
                .sorted(Comparator.comparing(Phrase::date))
                .toList());
  }

  public Phrase save(Phrase draft, int expected) throws IOException {
    return transact(
        true,
        p -> {
          String key = "phrase." + draft.key();
          int current = p.containsKey(key) ? phrase(p.getProperty(key)).version() : 0;
          if (current != expected)
            throw new IOException("다른 창에서 저장본이 바뀌었습니다. 초안을 유지했습니다. 재조회 후 다시 편집하세요.");
          Phrase f = draft.versioned(current + 1);
          StringJoiner n = new StringJoiner(";");
          for (NoteEvent e : f.notes())
            n.add(e.start() + "," + e.duration() + "," + e.pitch() + "," + e.velocity());
          p.setProperty(
              key,
              f.songId()
                  + "|"
                  + f.author()
                  + "|"
                  + f.date()
                  + "|"
                  + f.version()
                  + "|"
                  + f.bpm()
                  + "|"
                  + f.instrument()
                  + "|"
                  + text(f.memo())
                  + "||"
                  + n);
          return f;
        });
  }

  public Settings settings(String user) throws IOException {
    return transact(
        false,
        p -> {
          String[] v = p.getProperty("settings." + user, "80|0|true").split("\\|");
          return new Settings(
              Integer.parseInt(v[0]),
              Integer.parseInt(v[1]),
              Boolean.parseBoolean(v[2]),
              v.length > 3 && Boolean.parseBoolean(v[3]),
              v.length > 4 ? Integer.parseInt(v[4]) : 4);
        });
  }

  public void settings(String user, Settings s) throws IOException {
    transact(
        true,
        p -> {
          p.setProperty(
              "settings." + user,
              s.volume()
                  + "|"
                  + s.instrument()
                  + "|"
                  + s.countIn()
                  + "|"
                  + s.metronome()
                  + "|"
                  + s.countInBeats());
          return null;
        });
  }

  private static String encodePhrase(Phrase f) {
    StringJoiner notes = new StringJoiner(";");
    for (NoteEvent n : f.notes())
      notes.add(n.start() + "," + n.duration() + "," + n.pitch() + "," + n.velocity());
    return f.songId()
        + "|"
        + f.author()
        + "|"
        + f.date()
        + "|"
        + f.version()
        + "|"
        + f.bpm()
        + "|"
        + f.instrument()
        + "|"
        + text(f.memo())
        + "||"
        + notes;
  }

  public void seedDemo(LocalDate today) throws IOException {
    transact(
        true,
        p -> {
          String marker = "meta.samples.v2." + today;
          if (p.containsKey(marker)) return null;
          List<Song> samples =
              List.of(
                  new Song("first", "나의 작은 일주일", "demo", false, 90, 0, Set.of("demo")),
                  new Song("sunset", "노을 산책 · 샘플", "demo", false, 80, 24, Set.of("demo")),
                  new Song(
                      "together", "우리의 느린 오후", "demo", true, 90, 0, Set.of("demo", "star", "moon")),
                  new Song(
                      "weekend",
                      "주말의 작은 합주 · 샘플",
                      "demo",
                      true,
                      100,
                      12,
                      Set.of("demo", "star", "moon")));
          String[] memos = {
            "햇살 좋은 아침", "가벼운 발걸음", "커피 한 잔의 여유", "비 오는 창가", "문득 떠오른 기쁨", "친구와 느린 산책", "오늘도 수고했어"
          };
          int[][] melodies = {
            {60, 64, 67, 69},
            {62, 64, 69, 67},
            {64, 67, 72, 69},
            {60, 62, 64, 67},
            {67, 69, 72, 76},
            {64, 62, 60, 67},
            {60, 67, 64, 60}
          };
          for (Song sample : samples) {
            String k = "song." + sample.id();
            Song existing = p.containsKey(k) ? song(sample.id(), p.getProperty(k)) : sample;
            if (!p.containsKey(k)) p.setProperty(k, encode(sample));
            if (existing.shared() && existing.owner().equals("demo")) {
              Set<String> m = new HashSet<>(existing.members());
              m.addAll(sample.members());
              existing =
                  new Song(
                      existing.id(),
                      existing.name(),
                      existing.owner(),
                      true,
                      existing.bpm(),
                      existing.instrument(),
                      m);
              p.setProperty(k, encode(existing));
            }
            for (int ago = 13; ago >= 0; ago--) {
              LocalDate day = today.minusDays(ago);
              int pattern = day.getDayOfWeek().getValue() - 1;
              for (String author : sample.members()) {
                if (!existing.members().contains(author)) continue;
                List<NoteEvent> notes = new ArrayList<>();
                int program = existing.instrument();
                if (author.equals("star")) {
                  program = 12;
                  notes.add(new NoteEvent(0, 960, 48 + (pattern % 3) * 2, 58));
                  notes.add(new NoteEvent(960, 960, 55, 55));
                } else if (author.equals("moon")) {
                  program = 10;
                  for (int b = 0; b < 4; b++)
                    notes.add(new NoteEvent(b * 480 + 240, 240, new int[] {72, 76, 79, 81}[b], 46));
                } else
                  for (int b = 0; b < 4; b++)
                    notes.add(
                        new NoteEvent(
                            b * 480, b == 3 ? 480 : 360, melodies[pattern][b], 72 + b * 5));
                Phrase f =
                    new Phrase(
                        existing.id(),
                        author,
                        day,
                        1,
                        existing.bpm(),
                        program,
                        "샘플 · " + memos[pattern],
                        notes);
                p.putIfAbsent("phrase." + f.key(), encodePhrase(f));
              }
            }
          }
          p.setProperty(marker, "done");
          return null;
        });
  }

  public Song updateSong(Song expected, String name, int bpm, int instrument, String actor)
      throws IOException {
    return transact(
        true,
        p -> {
          String data = p.getProperty("song." + expected.id());
          if (data == null) throw new IOException("곡이 없습니다.");
          Song actual = song(expected.id(), data);
          if (!actual.owner().equals(actor)) throw new IOException("곡 소유자만 설정을 변경할 수 있습니다.");
          if (!actual.equals(expected)) throw new IOException("다른 창에서 곡 설정이 바뀌었습니다. 재조회하세요.");
          Song next =
              new Song(
                  actual.id(),
                  name.strip(),
                  actual.owner(),
                  actual.shared(),
                  bpm,
                  instrument,
                  actual.members());
          p.setProperty("song." + actual.id(), encode(next));
          return next;
        });
  }

  public void leave(String id, String actor) throws IOException {
    transact(
        true,
        p -> {
          String data = p.getProperty("song." + id);
          if (data == null) throw new IOException("곡이 없습니다.");
          Song s = song(id, data);
          if (s.owner().equals(actor)) throw new IOException("소유자는 탈퇴할 수 없습니다. 곡을 유지해 주세요.");
          if (!s.members().contains(actor)) throw new IOException("참여 중인 곡이 아닙니다.");
          Set<String> members = new HashSet<>(s.members());
          members.remove(actor);
          p.setProperty(
              "song." + id,
              encode(
                  new Song(id, s.name(), s.owner(), s.shared(), s.bpm(), s.instrument(), members)));
          return null;
        });
  }

  public String invite(String id, String actor, Instant expires) throws IOException {
    return transact(
        true,
        p -> {
          String data = p.getProperty("song." + id);
          if (data == null) throw new IOException("곡이 없습니다.");
          Song s = song(id, data);
          if (!s.shared() || !s.owner().equals(actor))
            throw new IOException("공동곡 소유자만 초대 코드를 만들 수 있습니다.");
          String code =
              "MMM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
          p.setProperty("invite." + code, id + "|" + expires);
          return code;
        });
  }

  public void redeem(String code, String actor, Instant now) throws IOException {
    transact(
        true,
        p -> {
          String value = p.getProperty("invite." + code);
          if (value == null) throw new IOException("유효하지 않은 초대 코드입니다. 소유자에게 새 코드를 요청하세요.");
          String[] f = fields(value);
          if (!now.isBefore(Instant.parse(f[1]))) throw new IOException("만료된 초대 코드입니다.");
          String data = p.getProperty("song." + f[0]);
          if (data == null) throw new IOException("곡이 없습니다.");
          Song s = song(f[0], data);
          if (s.members().contains(actor)) throw new IOException("이미 참여 중인 곡입니다.");
          if (s.members().size() >= 15) throw new IOException("합주는 최대 15명까지 지원합니다.");
          if (!p.containsKey("user." + actor)) throw new IOException("로컬 계정이 없습니다.");
          Set<String> members = new HashSet<>(s.members());
          members.add(actor);
          p.setProperty(
              "song." + s.id(),
              encode(
                  new Song(
                      s.id(), s.name(), s.owner(), s.shared(), s.bpm(), s.instrument(), members)));
          return null;
        });
  }
}
