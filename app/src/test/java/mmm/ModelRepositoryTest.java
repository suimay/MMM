package mmm;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.sound.midi.*;
import mmm.Model.*;

public final class ModelRepositoryTest {
  private static int passed;

  private static void check(boolean truth, String message) {
    if (!truth) throw new AssertionError(message);
    passed++;
  }

  @FunctionalInterface
  interface Checked {
    void run() throws Exception;
  }

  private static void rejects(Checked action, String message) throws Exception {
    try {
      action.run();
    } catch (Exception expected) {
      passed++;
      return;
    }
    throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory("mmm-test-");
    LocalDate day = LocalDate.of(2026, 11, 1);
    Clock clock =
        Clock.fixed(
            day.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    LocalRepository repo = new LocalRepository(root);
    MusicService service = new MusicService(repo, clock, true);
    service.seed();
    repo.createSong(new Song("model-test", "테스트 곡", "demo", false, 90, 0, Set.of("demo")));
    service.login("demo", "demo1234".toCharArray());
    check(service.today().equals(day), "Seoul clock");
    check(Model.monday(day).equals(LocalDate.of(2026, 10, 26)), "Monday boundary");
    rejects(() -> new NoteEvent(1800, 240, 60, 80), "overflow");
    rejects(() -> new NoteEvent(1, 120, 60, 80), "grid");
    rejects(() -> new NoteEvent(0, 120, 85, 80), "pitch");
    rejects(() -> new NoteEvent(0, 120, 60, 0), "velocity");
    List<NoteEvent> notes =
        new ArrayList<>(List.of(new NoteEvent(0, 480, 60, 80), new NoteEvent(480, 1440, 67, 90)));
    Phrase draft = new Phrase("model-test", "demo", day, 0, 90, 0, "한글 메모 🎵", notes);
    notes.clear();
    check(draft.notes().size() == 2, "defensive immutable copy");
    Phrase saved = service.save(draft);
    check(saved.version() == 1, "initial version");
    LocalRepository reopened = new LocalRepository(root);
    Phrase loaded =
        reopened.phrases("model-test").stream()
            .filter(p -> p.date().equals(day))
            .findFirst()
            .orElseThrow();
    check(loaded.equals(saved), "disk roundtrip every field");
    rejects(() -> service.save(draft), "stale write rejected");
    check(reopened.phrases("model-test").contains(saved), "conflict preserves original");
    Phrase second = service.save(saved);
    check(second.version() == 2, "increment version");
    rejects(
        () ->
            service.save(
                new Phrase("model-test", "demo", day.minusDays(1), 1, 90, 0, "", List.of())),
        "past forbidden");
    rejects(
        () ->
            service.save(
                new Phrase("model-test", "demo", day.plusDays(1), 0, 90, 0, "", List.of())),
        "future forbidden");
    rejects(
        () -> service.save(new Phrase("together", "star", day, 1, 90, 0, "", List.of())),
        "other author forbidden");
    rejects(
        () -> service.save(new Phrase("together", "demo", day, 0, 100, 0, "", List.of())),
        "ensemble BPM");
    rejects(
        () -> new Phrase("model-test", "demo", day, 0, 90, 0, "가".repeat(21), List.of()),
        "memo limit");
    rejects(() -> service.login("demo", "wrongwrong".toCharArray()), "wrong password");
    rejects(
        () -> service.register("demo", "복제", "abcdefgh".toCharArray(), "abcdefgh".toCharArray()),
        "duplicate account");
    service.register("new_user", "새 사용자", "abcdefgh".toCharArray(), "abcdefgh".toCharArray());
    service.login("demo", "demo1234".toCharArray());
    String invite = service.invite("together");
    service.login("new_user", "abcdefgh".toCharArray());
    rejects(() -> service.phrases("model-test"), "membership access");
    service.join(invite);
    check(service.songs().size() == 1, "join shared");
    rejects(() -> service.join(invite), "duplicate join");
    rejects(() -> service.join("EXPIRED-demo"), "expired mock");
    rejects(() -> service.join("bad"), "invalid code");
    Settings settings = new Settings(33, 12, false);
    repo.settings("new_user", settings);
    check(reopened.settings("new_user").equals(settings), "settings persist");
    Sequence week =
        MidiEngine.sequence(
            List.of(new MidiEngine.Part(saved, Model.BAR * 2, 0, 100)), 7 * Model.BAR);
    check(week.getTickLength() == 7 * Model.BAR, "empty days preserve seven bars");
    Track track = week.getTracks()[0];
    long on = -1, off = -1;
    for (int i = 0; i < track.size(); i++) {
      MidiEvent event = track.get(i);
      if (event.getMessage() instanceof ShortMessage m && m.getData1() == 60) {
        if (m.getCommand() == ShortMessage.NOTE_ON) on = event.getTick();
        if (m.getCommand() == ShortMessage.NOTE_OFF) off = event.getTick();
      }
    }
    check(on == 3840 && off == 4320, "shared model MIDI timing");
    Sequence group =
        MidiEngine.sequence(
            List.of(new MidiEngine.Part(saved, 0, 0, 100), new MidiEngine.Part(saved, 0, 1, 60)),
            Model.BAR);
    check(
        group.getTracks()[0].get(1).getTick() == group.getTracks()[1].get(1).getTick(),
        "ensemble aligned");
    Path failure = Files.createTempDirectory("mmm-failure-");
    LocalRepository broken = new LocalRepository(failure);
    Files.createDirectory(broken.file());
    rejects(() -> broken.save(draft, 0), "failed IO raises");
    check(draft.notes().size() == 2 && draft.memo().equals("한글 메모 🎵"), "failure preserves draft");
    Path corrupt = Files.createTempDirectory("mmm-corrupt-");
    Files.writeString(corrupt.resolve("mmm.properties"), "schema=99\n");
    rejects(() -> new LocalRepository(corrupt).settings("demo"), "unknown schema rejected");
    check(
        Files.readString(corrupt.resolve("mmm.properties")).equals("schema=99\n"),
        "invalid file preserved");
    Path damaged = Files.createTempDirectory("mmm-damaged-");
    Path damagedFile = damaged.resolve("mmm.properties");
    String bad = "schema=1\nphrase.first/demo/2026-11-01=truncated\n";
    Files.writeString(damagedFile, bad);
    LocalRepository damagedRepo = new LocalRepository(damaged);
    rejects(
        () -> damagedRepo.settings("demo", settings), "malformed record blocks unrelated write");
    check(Files.readString(damagedFile).equals(bad), "malformed data never overwritten");
    MusicService nextDay = new MusicService(repo, Clock.offset(clock, Duration.ofDays(1)), true);
    nextDay.login("demo", "demo1234".toCharArray());
    rejects(() -> nextDay.save(second), "midnight invalidates old open draft");
    MusicService normal =
        new MusicService(
            new LocalRepository(Files.createTempDirectory("mmm-normal-")), clock, false);
    normal.seed();
    check(
        normal.repository.account("demo").isEmpty(),
        "normal mode has no sample data or demo accounts");
    Sequence silent = MidiEngine.sequence(List.of(new MidiEngine.Part(saved, 0, 0, 0)), Model.BAR);
    int audible = 0;
    for (Track t : silent.getTracks())
      for (int j = 0; j < t.size(); j++)
        if (t.get(j).getMessage() instanceof ShortMessage m
            && m.getCommand() == ShortMessage.NOTE_ON) audible++;
    check(audible == 0, "zero part volume is silent");
    Path empty = Files.createTempDirectory("mmm-empty-");
    Files.createFile(empty.resolve("mmm.properties"));
    rejects(
        () -> new LocalRepository(empty).settings("demo", settings),
        "truncated empty existing store rejected");
    check(Files.size(empty.resolve("mmm.properties")) == 0, "empty damaged original preserved");
    System.out.println(
        "PASS: " + passed + " model, persistence, authorization, date and MIDI checks");
  }
}
