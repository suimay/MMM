package mmm;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import javax.sound.midi.*;
import mmm.EmotionCatalog.*;
import mmm.Model.*;

public final class EmotionCatalogTest {
  private static final List<String> FILES =
      List.of("catalog.properties", "tags.tsv", "sources.tsv", "evidence.tsv", "chords.tsv", "links.tsv");
  private static int passed;

  @FunctionalInterface
  private interface Checked {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface Mutation {
    void apply(Path directory) throws Exception;
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
    passed++;
  }

  private static void rejects(Class<? extends Exception> type, Checked action, String message)
      throws Exception {
    try {
      action.run();
    } catch (Exception ex) {
      if (!type.isInstance(ex)) throw new AssertionError(message + ": wrong exception", ex);
      passed++;
      return;
    }
    throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    EmotionCatalog catalog = EmotionCatalog.load();
    check(catalog.metadata().schemaVersion().equals("1.0.0"), "known schema version");
    check(catalog.metadata().datasetKind().equals("literature_seed"), "literature seed identified");
    check(catalog.metadata().listeningValidation().equals("not_started"), "no invented listening validation");
    check(catalog.tags().size() == 10 && catalog.chords().size() == 7, "complete initial catalogue");
    check(catalog.evidence().size() == 9 && catalog.links().size() == 9, "nine explicit measured links");
    check(catalog.tag("nostalgia_longing").labelKo().equals("그리움"), "UTF-8 Korean label preserved");
    check(catalog.tag("positive_valence").dimension().equals("valence"), "label and measured construct distinct");
    check(catalog.sources().stream().allMatch(source -> source.participantsN() > 0), "source sample sizes retained");
    for (Link link : catalog.links()) {
      Chord chord = catalog.chord(link.chordId());
      Tag tag = catalog.tag(link.tagId());
      Evidence item = catalog.evidence(link.evidenceId());
      check(catalog.source(item.sourceId()).id().equals(item.sourceId()), "source reference resolves");
      check(chord.quality().equals(item.chordQuality()), "evidence belongs to the right chord quality");
      check(tag.dimension().equals(item.measure()), "tag preserves the measured construct");
      check(link.supportStatus().equals("direct_measure") && tag.availability().equals("candidate"), "supported candidate only");
      check(item.mean() >= item.scaleMin() && item.mean() <= item.scaleMax(), "original scale bounds retained");
      check(item.sd() >= 0 && !item.aggregation().isBlank(), "variation and pooled conditions retained");
    }

    List<String> nostalgiaOrder = List.of("c_minor", "c_major_seventh", "c_minor_seventh");
    check(ids(catalog.candidates("nostalgia_longing")).equals(nostalgiaOrder), "nostalgia candidates retain file order");
    check(catalog.candidates(null).equals(catalog.chords()), "null filter shows all samples");
    check(catalog.candidates("").equals(catalog.chords()), "empty filter shows all samples");
    check(catalog.candidates("unknown_tag").isEmpty(), "unknown filter has no candidates");
    check(catalog.linksFor("unknown_chord").isEmpty(), "unknown chord has no links");
    check(catalog.tagsFor("unknown_chord").isEmpty(), "unknown chord has no tags");
    for (Tag tag : catalog.tags())
      if (tag.availability().equals("pending_validation"))
        check(catalog.candidates(tag.id()).isEmpty(), "unvalidated tag does not silently recommend samples");
    check(
        catalog.tagsFor("c_major").stream().map(Tag::id).toList()
            .equals(List.of("happiness_joy", "positive_valence")),
        "chord exposes its exact supported tags");
    rejects(IllegalArgumentException.class, () -> catalog.chord("missing"), "unknown chord lookup fails");
    rejects(IllegalArgumentException.class, () -> catalog.tag("missing"), "unknown tag lookup fails");
    rejects(IllegalArgumentException.class, () -> catalog.source("missing"), "unknown source lookup fails");
    rejects(IllegalArgumentException.class, () -> catalog.evidence("missing"), "unknown evidence lookup fails");

    Map<String, List<Integer>> expectedPitches =
        Map.of(
            "c_major", List.of(60, 64, 67),
            "c_minor", List.of(60, 63, 67),
            "c_major_seventh", List.of(60, 64, 67, 71),
            "c_minor_seventh", List.of(60, 63, 67, 70),
            "c_augmented", List.of(60, 64, 68),
            "c_diminished", List.of(60, 63, 66),
            "c_dominant_seventh", List.of(60, 64, 67, 70));
    for (Chord chord : catalog.chords()) {
      check(chord.notes().equals(expectedPitches.get(chord.id())), "correct C4-root pitch spelling " + chord.symbol());
      check(chord.program() == 0 && chord.bpm() == 60 && chord.velocity() == 72, "controlled preview sound settings");
      check(chord.durationTicks() == Model.BAR, "preview holds four beats");
      check(
          chord.inversion().equals("root_position") && chord.voicing().equals("close")
              && chord.stimulusContext().equals("standalone"),
          "explicit sample arrangement");
      check(
          chord.validationStatus().equals("not_validated")
              && chord.equivalenceToResearchStimulus().equals("not_equivalent"),
          "sample does not claim experiment equivalence or validation");
      List<NoteEvent> notes = catalog.previewNotes(chord.id());
      check(notes.stream().allMatch(note -> note.start() == 0 && note.duration() == Model.BAR), "simultaneous sustained chord model");
      Phrase phrase =
          new Phrase("emotion-test", "tester", LocalDate.of(2026, 11, 23), 0,
              chord.bpm(), chord.program(), "", notes);
      Sequence sequence =
          MidiEngine.sequence(List.of(new MidiEngine.Part(phrase, 0, 0, 100)), Model.BAR);
      Set<Integer> onPitches = new HashSet<>();
      Set<Integer> offPitches = new HashSet<>();
      for (Track track : sequence.getTracks())
        for (int i = 0; i < track.size(); i++) {
          MidiEvent event = track.get(i);
          if (!(event.getMessage() instanceof ShortMessage message)) continue;
          if (message.getCommand() == ShortMessage.NOTE_ON && message.getData2() > 0) {
            check(event.getTick() == 0 && message.getData2() == chord.velocity(), "MIDI notes enter together at original velocity");
            onPitches.add(message.getData1());
          }
          if (message.getCommand() == ShortMessage.NOTE_OFF) {
            check(event.getTick() == Model.BAR, "MIDI chord ends after four beats");
            offPitches.add(message.getData1());
          }
        }
      check(onPitches.equals(new HashSet<>(chord.notes())) && onPitches.equals(offPitches), "MIDI preview contains all and only the chord pitches");
    }

    rejects(UnsupportedOperationException.class, () -> catalog.tags().clear(), "immutable tags");
    rejects(UnsupportedOperationException.class, () -> catalog.sources().clear(), "immutable sources");
    rejects(UnsupportedOperationException.class, () -> catalog.evidence().clear(), "immutable evidence");
    rejects(UnsupportedOperationException.class, () -> catalog.chords().clear(), "immutable chords");
    rejects(UnsupportedOperationException.class, () -> catalog.links().clear(), "immutable links");
    rejects(UnsupportedOperationException.class, () -> catalog.chord("c_major").notes().add(72), "immutable chord pitches");
    rejects(UnsupportedOperationException.class, () -> catalog.candidates("nostalgia_longing").clear(), "immutable candidate result");
    rejects(UnsupportedOperationException.class, () -> catalog.linksFor("c_major").clear(), "immutable chord link result");
    rejects(UnsupportedOperationException.class, () -> catalog.tagsFor("c_major").clear(), "immutable chord tag result");
    rejects(UnsupportedOperationException.class, () -> catalog.previewNotes("c_major").clear(), "immutable preview model");

    Path fixture = fixture();
    try {
      EmotionCatalog local = EmotionCatalog.load(fixture);
      check(local.chords().equals(catalog.chords()) && local.evidence().equals(catalog.evidence()), "filesystem and packaged catalogues agree");
      cell(fixture, "evidence.tsv", "le2016a_minor7_nostalgia", "mean", "4.0");
      check(ids(EmotionCatalog.load(fixture).candidates("nostalgia_longing")).equals(nostalgiaOrder), "reported means never sort or rank candidate samples");
    } finally {
      remove(fixture);
    }

    bad(
        directory -> {
          Path file = directory.resolve("catalog.properties");
          Files.writeString(file, Files.readString(file).replace("schema_version=1.0.0", "schema_version=2.0.0"));
        },
        "unknown schema rejected");
    bad(
        directory -> Files.writeString(directory.resolve("catalog.properties"), "schema_version=1.0.0\n", StandardOpenOption.APPEND),
        "duplicate metadata rejected");
    bad(
        directory -> {
          Path file = directory.resolve("tags.tsv");
          Files.writeString(file, Files.readString(file).replaceFirst("label_ko", "label"));
        },
        "wrong header rejected");
    bad(
        directory -> {
          Path file = directory.resolve("tags.tsv");
          Files.writeString(file, Files.readAllLines(file).get(1) + "\n", StandardOpenOption.APPEND);
        },
        "duplicate IDs rejected");
    bad(
        directory -> Files.write(directory.resolve("tags.tsv"), new byte[] {(byte) 0xc3, (byte) 0x28}),
        "malformed UTF-8 rejected");
    bad(directory -> cell(directory, "evidence.tsv", "le2016a_major_happiness", "source_id", "missing"), "dangling source rejected");
    bad(directory -> cell(directory, "links.tsv", "c_major", "evidence_id", "missing"), "dangling evidence rejected");
    bad(directory -> cell(directory, "links.tsv", "c_major", "tag_id", "dreaminess"), "pending tag cannot gain candidate links");
    bad(directory -> cell(directory, "links.tsv", "c_major", "evidence_id", "le2016a_major_valence"), "different measured construct rejected");
    bad(directory -> cell(directory, "evidence.tsv", "le2016a_major_happiness", "chord_quality", "minor"), "different chord quality rejected");
    bad(directory -> cell(directory, "evidence.tsv", "le2016a_major_happiness", "mean", "5.1"), "out-of-scale mean rejected");
    bad(directory -> cell(directory, "evidence.tsv", "le2016a_major_happiness", "sd", "-0.1"), "negative deviation rejected");
    bad(directory -> cell(directory, "evidence.tsv", "le2016a_major_happiness", "scale_min", "5"), "empty scale range rejected");
    bad(directory -> cell(directory, "evidence.tsv", "le2016a_major_happiness", "mean", "NaN"), "nonfinite evidence rejected");
    bad(directory -> cell(directory, "links.tsv", "c_major", "support_status", "assumed"), "unrecognized evidence status rejected");
    bad(directory -> cell(directory, "chords.tsv", "c_major", "validation_status", "validated"), "unearned validation rejected");
    bad(directory -> cell(directory, "chords.tsv", "c_major", "equivalence_to_research_stimulus", "equivalent"), "unearned equivalence rejected");
    bad(directory -> cell(directory, "chords.tsv", "c_major", "midi_notes", "60,63,67"), "mislabeled major chord rejected");
    bad(directory -> cell(directory, "chords.tsv", "c_major", "duration_ticks", "1921"), "unplayable model duration rejected");
    System.out.println("PASS: " + passed + " emotion provenance, filtering, MIDI and malformed-data checks");
  }

  private static List<String> ids(List<Chord> chords) {
    return chords.stream().map(Chord::id).toList();
  }

  private static Path fixture() throws IOException {
    Path directory = Files.createTempDirectory("mmm-emotion-test-");
    for (String file : FILES)
      try (InputStream input = EmotionCatalogTest.class.getResourceAsStream("/mmm/emotion/" + file)) {
        if (input == null) throw new IOException("Missing test resource " + file);
        Files.copy(input, directory.resolve(file));
      }
    return directory;
  }

  private static void cell(Path directory, String file, String rowId, String column, String value)
      throws IOException {
    Path path = directory.resolve(file);
    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    int columnIndex = Arrays.asList(lines.getFirst().split("\t", -1)).indexOf(column);
    if (columnIndex < 0) throw new AssertionError("Missing fixture column " + column);
    for (int i = 1; i < lines.size(); i++) {
      String[] cells = lines.get(i).split("\t", -1);
      if (!cells[0].equals(rowId)) continue;
      cells[columnIndex] = value;
      lines.set(i, String.join("\t", cells));
      Files.write(path, lines, StandardCharsets.UTF_8);
      return;
    }
    throw new AssertionError("Missing fixture row " + rowId);
  }

  private static void bad(Mutation mutation, String message) throws Exception {
    Path directory = fixture();
    try {
      mutation.apply(directory);
      rejects(IOException.class, () -> EmotionCatalog.load(directory), message);
    } finally {
      remove(directory);
    }
  }

  private static void remove(Path directory) throws IOException {
    try (var paths = Files.walk(directory)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
    }
  }
}
