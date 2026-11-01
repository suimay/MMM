package mmm;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import mmm.Model.*;

public final class EnsembleDataTest {
  static int count;

  static void check(boolean ok, String why) {
    if (!ok) throw new AssertionError(why);
    count++;
  }

  public static void main(String[] args) throws Exception {
    LocalDate day = LocalDate.of(2026, 11, 1);
    Clock clock =
        Clock.fixed(
            day.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    MusicService service =
        new MusicService(
            new LocalRepository(Files.createTempDirectory("mmm-ensemble-test-")), clock, true);
    service.seed();
    service.login("demo", "demo1234".toCharArray());
    List<EnsembleData.Member> members = service.participants("together");
    List<Phrase> records = service.phrases("together");
    check(members.size() == 3, "three attributed sample composers");
    check(members.stream().anyMatch(m -> m.id().equals("demo") && m.owner()), "owner identity");
    check(
        members.stream().anyMatch(m -> m.id().equals("star") && m.nickname().contains("별")),
        "nickname attached to correct id");
    Map<String, Integer> all = new LinkedHashMap<>();
    members.forEach(m -> all.put(m.id(), 80));
    List<MidiEngine.Part> single = EnsembleData.parts(records, members, all, day, false);
    check(single.size() == 3, "same-day three parts");
    check(single.stream().allMatch(p -> p.offset() == 0), "same day simultaneous start");
    check(
        single.stream().map(p -> p.phrase().author()).distinct().count() == 3,
        "parts retain independent authors");
    check(
        single.stream()
            .filter(p -> p.phrase().author().equals("star"))
            .allMatch(p -> p.phrase().instrument() == 12),
        "friend instrument remains marimba");
    List<MidiEngine.Part> week = EnsembleData.parts(records, members, all, day, true);
    check(week.size() == 21, "seven days times three composers");
    check(
        week.stream().mapToInt(MidiEngine.Part::offset).max().orElseThrow() == 6 * Model.BAR,
        "seventh day aligns to seventh bar");
    Map<String, Integer> only = new HashMap<>(all);
    only.put("star", 0);
    only.put("moon", 0);
    List<MidiEngine.Part> solo = EnsembleData.parts(records, members, only, day, false);
    check(
        solo.size() == 1 && solo.get(0).phrase().author().equals("demo"),
        "solo excludes other composers for export");
    check(
        EnsembleData.parts(records, members, Map.of(), day, false).isEmpty(),
        "missing or muted parts are silent");
    List<EnsembleData.AttributedNote> notes = EnsembleData.notes(records, "star", day, false);
    check(notes.size() == 2, "friend record exact individual notes");
    check(
        notes.get(0).author().equals("star") && notes.get(0).note().pitch() == 48,
        "note retains composer and pitch");
    check(
        notes.get(1).note().start() == 960 && notes.get(1).note().duration() == 960,
        "note retains exact rhythm");
    check(
        EnsembleData.notes(records, "moon", day, true).size() == 28, "week note list attribution");
    Set<Integer> channels = new HashSet<>();
    for (int i = 0; i < 15; i++) channels.add(EnsembleData.channel(i));
    check(
        channels.size() == 15 && !channels.contains(9), "fifteen distinct non-percussion channels");
    try {
      EnsembleData.channel(15);
      throw new AssertionError("overflow");
    } catch (IllegalArgumentException expected) {
      count++;
    }
    Map<String, Integer> bad = new HashMap<>(all);
    bad.put("demo", 101);
    try {
      EnsembleData.parts(records, members, bad, day, false);
      throw new AssertionError("level overflow");
    } catch (IllegalArgumentException expected) {
      count++;
    }
    for (String file :
        List.of(
            "instrument-0",
            "instrument-10",
            "instrument-12",
            "instrument-24",
            "avatar-demo",
            "avatar-star",
            "avatar-moon"))
      check(
          ArtAssets.class.getResource("/images/" + file + ".png") != null,
          "packaged local asset " + file);
    check(ArtAssets.instrument(0, 32).getIconWidth() == 32, "image icon size");
    check(ArtAssets.avatar("custom_user", 36).getIconHeight() == 36, "new-user avatar fallback");
    check(
        service.phrases("together").equals(records),
        "mixing/inspecting never modifies other people's records");
    System.out.println(
        "PASS: " + count + " attributed ensemble routing and local image asset checks");
  }
}
