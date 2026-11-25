package mmm;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.sound.midi.*;
import mmm.Model.*;

/** v0.2 acceptance: representation, actual MIDI, migration and atomic saves. */
public final class ExpressionTest {
  private static int count;
  private static final LocalDate DAY = LocalDate.of(2026, 11, 16);
  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
    count++;
  }
  interface Task { void run() throws Exception; }
  static void rejects(Task task, String message) throws Exception {
    try { task.run(); } catch (Exception expected) { count++; return; }
    throw new AssertionError(message);
  }
  static Phrase phrase(List<NoteEvent> notes, List<RestEvent> rests, List<ExpressionRange> expressions) {
    return new Phrase("song", "tester", DAY, 0, 90, 0, "", notes, rests, expressions);
  }
  static List<MidiEvent> messages(Sequence sequence, int command) {
    List<MidiEvent> events = new ArrayList<>();
    for (Track track : sequence.getTracks())
      for (int i = 0; i < track.size(); i++)
        if (track.get(i).getMessage() instanceof ShortMessage m && m.getCommand() == command)
          events.add(track.get(i));
    return events;
  }
  public static void main(String[] args) throws Exception {
    NoteEvent staccato = new NoteEvent(0, 120, 60, 90, Articulation.STACCATO);
    check(staccato.duration() == 120 && staccato.gateDuration() == 60, "staccato logical and gate durations");
    check(new NoteEvent(0, 480, 60, 90).articulation() == Articulation.NORMAL, "legacy note default");
    rejects(() -> new RestEvent(-120, 120), "negative rest");
    rejects(() -> new RestEvent(0, 60), "sub-grid rest");
    rejects(() -> new RestEvent(1800, 240), "rest crosses bar");
    rejects(() -> new ExpressionRange(0, 0, ExpressionKind.CRESCENDO, 40, 100), "empty expression");
    rejects(() -> new ExpressionRange(60, 480, ExpressionKind.CRESCENDO, 40, 100), "unaligned expression");
    rejects(() -> new ExpressionRange(0, 480, ExpressionKind.CRESCENDO, 100, 40), "reversed dynamics");
    rejects(() -> phrase(List.of(staccato), List.of(new RestEvent(0, 120)), List.of()), "note/rest collision");
    rejects(() -> phrase(List.of(), List.of(new RestEvent(0, 480), new RestEvent(240, 480)), List.of()), "rest collision");
    ExpressionRange range = new ExpressionRange(480, 1440, ExpressionKind.CRESCENDO, 40, 120);
    rejects(() -> phrase(List.of(), List.of(), List.of(range,
        new ExpressionRange(1200, 1920, ExpressionKind.CRESCENDO, 30, 80))), "overlapping ranges");
    List<NoteEvent> notes = List.of(staccato, new NoteEvent(480, 240, 62, 85),
        new NoteEvent(960, 240, 64, 85), new NoteEvent(1440, 480, 67, 85));
    Phrase f = phrase(notes, List.of(new RestEvent(120, 360)), List.of(range));
    check(f.effectiveVelocity(notes.get(0)) == 90, "before range unchanged");
    check(f.effectiveVelocity(notes.get(1)) == 40, "range start exact");
    check(f.effectiveVelocity(notes.get(2)) == 80, "range interpolates");
    check(f.effectiveVelocity(notes.get(3)) == 85, "range end exclusive");
    check(f.visualNotes().get(2).velocity() == 80 && f.notes().get(2).velocity() == 85,
        "drawing shares dynamics without mutating nominal velocity");
    Sequence seq = MidiEngine.sequence(List.of(new MidiEngine.Part(f, 1920, 2, 100)), 3840);
    List<MidiEvent> on = messages(seq, ShortMessage.NOTE_ON), off = messages(seq, ShortMessage.NOTE_OFF);
    check(on.size() == 4 && off.size() == 4, "rest generates no note messages");
    check(off.get(0).getTick() == 1980 && on.get(1).getTick() == 2400, "staccato gate and next onset independent");
    check(((ShortMessage) on.get(2).getMessage()).getData2() == 80, "MIDI uses same interpolated velocity");
    check(((ShortMessage) on.get(2).getMessage()).getChannel() == 2, "MIDI part channel preserved");
    Phrase silence = phrase(List.of(), List.of(new RestEvent(0, 1920)), List.of());
    Sequence silentSequence = MidiEngine.sequence(List.of(new MidiEngine.Part(silence, 0, 0, 100)), 1920);
    check(!silence.empty() && messages(silentSequence, ShortMessage.NOTE_ON).isEmpty()
        && silentSequence.getTickLength() == 1920, "explicit silent record has full duration");
    check(phrase(List.of(), List.of(), List.of()).empty(), "unentered bar distinct from explicit rest");
    String twenty = "🎵".repeat(10) + " ".repeat(10);
    Phrase memo = new Phrase("song", "tester", DAY, 0, 90, 0, twenty, notes, f.rests(), f.expressions());
    check(memo.memo().codePointCount(0, memo.memo().length()) == 20, "memo counts spaces and code points");
    rejects(() -> new Phrase("song", "tester", DAY, 0, 90, 0, twenty + "!", notes), "21 code points");
    rejects(() -> new Phrase("song", "tester", DAY, 0, 90, 0, "a\nb", notes), "multiline memo");
    List<RestEvent> mutable = new ArrayList<>(f.rests());
    Phrase immutable = phrase(notes, mutable, List.of(range));
    mutable.clear();
    check(immutable.rests().size() == 1, "draft defensively copies rests");
    rejects(() -> immutable.expressions().clear(), "immutable expressions");
    Path root = Files.createTempDirectory("mmm-expression-test-");
    LocalRepository repo = new LocalRepository(root);
    Clock clock = Clock.fixed(DAY.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    MusicService service = new MusicService(repo, clock, false);
    service.register("tester", "검증", "test1234".toCharArray(), "test1234".toCharArray());
    service.login("tester", "test1234".toCharArray());
    repo.createSong(new Song("song", "표현 검사", "tester", false, 90, 0, Set.of("tester")));
    Phrase saved = service.save(memo);
    check(saved.version() == 1, "first save version");
    check(new LocalRepository(root).phrases("song").equals(List.of(saved)), "all expression data survives restart");
    check(Files.readString(repo.file()).contains("schema=3"), "new schema rejects older application writers");
    byte[] beforeConflict = Files.readAllBytes(repo.file());
    rejects(() -> service.save(memo), "stale expression draft rejected");
    check(Arrays.equals(beforeConflict, Files.readAllBytes(repo.file())), "conflict preserves stored bytes");
    check(memo.notes().equals(notes) && memo.rests().equals(f.rests()), "failure preserves draft");
    Phrase restSaved = service.save(new Phrase("song", "tester", DAY, 1, 90, 0, "静", List.of(), silence.rests(), List.of()));
    check(restSaved.version() == 2 && !restSaved.empty(), "service accepts rest-only bar");
    rejects(() -> service.save(new Phrase("song", "tester", DAY, 2, 90, 0, "", List.of())), "empty bar rejected");
    MusicService tomorrow = new MusicService(repo, Clock.offset(clock, Duration.ofDays(1)), false);
    tomorrow.login("tester", "test1234".toCharArray());
    rejects(() -> tomorrow.save(restSaved), "midnight change rejected");
    check(repo.phrases("song").equals(List.of(restSaved)), "date rejection preserves record");
    // A hand-written v1 fixture, including an implicit gap. Migration must never infer expression/rest.
    Path legacyDir = Files.createTempDirectory("mmm-schema1-");
    String old = "schema=1\nphrase.song/tester/2026-11-16=song|tester|2026-11-16|1|90|0||" + "|0,120,60,90;480,480,64,80\n";
    Files.writeString(legacyDir.resolve("mmm.properties"), old);
    LocalRepository legacy = new LocalRepository(legacyDir);
    Phrase migrated = legacy.phrases("song").get(0);
    check(migrated.notes().get(0).duration() == 120 && migrated.notes().get(0).articulation() == Articulation.NORMAL,
        "short legacy notes never inferred as staccato");
    check(migrated.rests().isEmpty() && migrated.expressions().isEmpty(), "legacy gaps never inferred as rests");
    check(Files.readString(legacy.file()).equals(old), "reading does not migrate or modify source");
    legacy.save(migrated, 1);
    check(Files.readString(legacyDir.resolve("mmm.properties.bak")).equals(old), "migration backs up exact v1 bytes");
    check(new LocalRepository(legacyDir).phrases("song").get(0).notes().equals(migrated.notes()), "migrated notes preserved");
    byte[] valid = Files.readAllBytes(legacy.file());
    Files.writeString(legacy.file(), Files.readString(legacy.file()).replace("NORMAL", "UNKNOWN"));
    byte[] corrupt = Files.readAllBytes(legacy.file());
    rejects(() -> legacy.settings("tester", new Settings(80, 0, true)), "unknown articulation blocks writes");
    check(Arrays.equals(corrupt, Files.readAllBytes(legacy.file())), "corrupt expression input preserved");
    Files.write(legacy.file(), valid);
    Path midi = root.resolve("expressions.mid");
    MidiEngine.export(midi, List.of(new MidiEngine.Part(f, 0, 0, 100)), 1920, 90);
    check(messages(MidiSystem.getSequence(midi.toFile()), ShortMessage.NOTE_OFF).get(0).getTick() == 60,
        "exported staccato gate round-trips");
    int[] durations = {1920, 960, 480, 240, 120, 360};
    ScorePanel.Symbol[] symbols = ScorePanel.Symbol.values();
    for (int i = 0; i < durations.length; i++)
      check(ScorePanel.symbol(durations[i]) == symbols[i], "notation duration " + durations[i]);
    Theme.install();
    check(Theme.font(14, false).getFamily().contains("Pretendard")
        && Theme.font(14, false).canDisplayUpTo("오늘의 한 마디") == -1, "bundled Korean Pretendard loads");
    List<Phrase> scoreBars = new ArrayList<>();
    scoreBars.add(f);
    for (int d : durations)
      scoreBars.add(phrase(List.of(new NoteEvent(0, d, 60, 90, Articulation.STACCATO)),
          d <= 960 ? List.of(new RestEvent(960, d)) : List.of(), List.of()));
    ScorePanel score = new ScorePanel("표현·쉼 기보 검증", scoreBars);
    score.setSize(score.getPreferredSize());
    BufferedImage image = new BufferedImage(score.getWidth(), score.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics(); score.paint(graphics); graphics.dispose();
    Path evidence = Path.of("build", "evidence"); Files.createDirectories(evidence);
    ImageIO.write(image, "png", evidence.resolve("v02-score.png").toFile());
    check(f.notes().equals(notes), "printing preserves source notation data");
    System.out.println("PASS: " + count + " v0.2 model, MIDI, notation and migration checks");
  }
}
