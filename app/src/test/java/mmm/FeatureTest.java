package mmm;

import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.sound.midi.*;
import mmm.Model.*;

public final class FeatureTest {
  static int count;

  static void check(boolean ok, String why) {
    if (!ok) throw new AssertionError(why);
    count++;
  }

  interface Task {
    void run() throws Exception;
  }

  static void rejects(Task task, String reason) throws Exception {
    try {
      task.run();
    } catch (Exception e) {
      count++;
      return;
    }
    throw new AssertionError(reason);
  }

  public static void main(String[] args) throws Exception {
    LocalDate day = LocalDate.of(2026, 11, 1);
    Clock clock =
        Clock.fixed(
            day.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    Path folder = Files.createTempDirectory("mmm-features-");
    LocalRepository repo = new LocalRepository(folder);
    MusicService s = new MusicService(repo, clock, true);
    s.register("demo", "사용자", "demo1234".toCharArray(), "demo1234".toCharArray());
    s.login("demo", "demo1234".toCharArray());
    repo.createSong(new Song("first", "직접 바꾼 이름", "demo", false, 110, 24, Set.of("demo")));
    Phrase custom =
        repo.save(
            new Phrase(
                "first",
                "demo",
                day,
                0,
                110,
                24,
                "내 소중한 기록",
                List.of(new NoteEvent(0, 1920, 76, 80))),
            0);
    s.seed();
    check(repo.phrases("first").contains(custom), "sample migration preserves user's today");
    check(s.access("first").name().equals("직접 바꾼 이름"), "sample migration preserves song settings");
    check(s.songs().size() == 4, "two personal and two shared sample songs");
    check(repo.phrases("first").size() == 14, "14 personal sample days");
    check(repo.phrases("together").size() == 42, "14 days times three ensemble parts");
    List<Phrase> before = repo.phrases("together");
    s.seed();
    check(repo.phrases("together").equals(before), "idempotent samples");
    Song joint = s.access("together");
    Song changed = s.updateSong(joint, "합주 새 이름", 120, 12);
    check(
        changed.bpm() == 120 && s.access("together").name().equals("합주 새 이름"),
        "owner update persists");
    check(repo.phrases("together").equals(before), "metadata update preserves notes");
    rejects(() -> s.updateSong(joint, "오래된 창", 90, 0), "stale metadata update");
    String invite = s.invite("together");
    s.login("star", "demo1234".toCharArray());
    rejects(() -> s.updateSong(changed, "변경 금지", 100, 0), "non-owner change denied");
    rejects(() -> s.invite("together"), "non-owner invite denied");
    s.leave("together");
    rejects(() -> s.access("together"), "leave revokes access");
    check(repo.phrases("together").equals(before), "leave preserves contributions");
    s.join(invite);
    check(s.access("together").members().contains("star"), "valid invite restores membership");
    rejects(() -> s.join(invite), "duplicate membership denied");
    s.login("demo", "demo1234".toCharArray());
    rejects(() -> s.leave("together"), "owner cannot orphan song");
    String expired = repo.invite("together", "demo", clock.instant());
    s.login("star", "demo1234".toCharArray());
    s.leave("together");
    rejects(() -> s.join(expired), "exact expiry boundary rejected");
    s.join(invite);
    Settings settings = new Settings(65, 24, true, true, 8);
    repo.settings("demo", settings);
    check(
        new LocalRepository(folder).settings("demo").equals(settings),
        "metronome/count-in settings persist");
    check(
        Files.size(folder.resolve("mmm.properties.bak")) > 0, "previous valid file backup exists");
    Path midi = folder.resolve("roundtrip.mid");
    MidiEngine.export(midi, List.of(new MidiEngine.Part(custom, 0, 0, 100)), 1920, 110);
    Sequence sequence = MidiSystem.getSequence(midi.toFile());
    check(sequence.getTickLength() == 1920, "export MIDI exact length");
    boolean tempo = false, signature = false;
    for (Track track : sequence.getTracks())
      for (int i = 0; i < track.size(); i++)
        if (track.get(i).getMessage() instanceof MetaMessage m) {
          if (m.getType() == 0x51) {
            byte[] d = m.getData();
            int value = ((d[0] & 255) << 16) | ((d[1] & 255) << 8) | (d[2] & 255);
            tempo = value == 60000000 / 110;
          }
          if (m.getType() == 0x58) signature = m.getData()[0] == 4;
        }
    check(tempo && signature, "MIDI BPM and time signature roundtrip");
    check(
        ScorePanel.diatonic(60) == 0
            && ScorePanel.diatonic(64) == 2
            && ScorePanel.diatonic(72) == 7,
        "staff pitch mapping");
    check(ScorePanel.beatX(960) - ScorePanel.beatX(480) == 200, "staff uses same start ticks");
    ScorePanel score = new ScorePanel("검증", List.of(custom));
    score.setSize(score.getPreferredSize());
    BufferedImage image = new BufferedImage(1000, 400, BufferedImage.TYPE_INT_RGB);
    score.paint(image.getGraphics());
    PageFormat format = new PageFormat();
    check(
        score.print(image.getGraphics(), format, 0) == Printable.PAGE_EXISTS,
        "printable first page");
    check(
        score.print(image.getGraphics(), format, 1) == Printable.NO_SUCH_PAGE,
        "print pagination ends");
    check(repo.phrases("first").contains(custom), "render/export do not mutate original");
    System.out.println(
        "PASS: " + count + " sample preservation, management, invitation, export, score checks");
  }
}
