package mmm;

import java.util.*;
import javax.sound.midi.*;
import mmm.Model.*;

/** All MIDI tracks are derived from the same NoteEvent objects as the canvas. */
public final class MidiEngine implements AutoCloseable {
  private Synthesizer synth;
  private Sequencer sequencer;
  private String status = "MIDI 준비 중";
  private int volume = 80;
  private boolean loaded, paused, looping;

  public synchronized void open() {
    try {
      synth = MidiSystem.getSynthesizer();
      synth.open();
      sequencer = MidiSystem.getSequencer(false);
      sequencer.open();
      sequencer.getTransmitter().setReceiver(synth.getReceiver());
      status = "MIDI 준비됨";
    } catch (Exception e) {
      status = "MIDI 장치를 열 수 없음 · 화면 편집/저장은 가능";
      close();
    }
  }

  public synchronized String status() {
    return status;
  }

  public synchronized void volume(int value) {
    volume = value;
  }

  public synchronized void on(int pitch, int instrument) {
    if (volume > 0 && synth != null && synth.isOpen()) {
      synth.getChannels()[0].programChange(instrument);
      synth.getChannels()[0].noteOn(pitch, Math.max(1, 100 * volume / 100));
    }
  }

  public synchronized void off(int pitch) {
    if (synth != null && synth.isOpen()) synth.getChannels()[0].noteOff(pitch);
  }

  public synchronized void stop() {
    loaded = false;
    paused = false;
    if (sequencer != null && sequencer.isOpen()) sequencer.stop();
    if (synth != null && synth.isOpen())
      for (MidiChannel c : synth.getChannels())
        if (c != null) {
          c.controlChange(64, 0);
          c.allNotesOff();
          c.allSoundOff();
        }
  }

  public record Part(Phrase phrase, int offset, int channel, int volume) {}

  public static Sequence sequence(List<Part> parts, int length) throws InvalidMidiDataException {
    Sequence seq = new Sequence(Sequence.PPQ, Model.PPQ);
    for (Part p : parts) {
      Track t = seq.createTrack();
      int ch = p.channel();
      add(t, ShortMessage.PROGRAM_CHANGE, ch, p.phrase().instrument(), 0, p.offset());
      for (NoteEvent n : p.phrase().notes()) {
        if (p.volume() == 0) continue;
        add(
            t,
            ShortMessage.NOTE_ON,
            ch,
            n.pitch(),
            Math.max(1, n.velocity() * p.volume() / 100),
            p.offset() + n.start());
        add(t, ShortMessage.NOTE_OFF, ch, n.pitch(), 0, p.offset() + n.start() + n.duration());
      }
    }
    Track end = seq.createTrack();
    MetaMessage marker = new MetaMessage();
    marker.setMessage(0x2f, new byte[0], 0);
    end.add(new MidiEvent(marker, length));
    return seq;
  }

  private static void add(Track track, int command, int channel, int a, int b, long tick)
      throws InvalidMidiDataException {
    ShortMessage message = new ShortMessage();
    message.setMessage(command, channel, a, b);
    track.add(new MidiEvent(message, tick));
  }

  public synchronized void play(List<Part> parts, int length, int bpm) throws Exception {
    stop();
    if (sequencer == null || !sequencer.isOpen()) throw new IllegalStateException(status);
    List<Part> scaled =
        parts.stream()
            .map(p -> new Part(p.phrase(), p.offset(), p.channel(), p.volume() * volume / 100))
            .toList();
    sequencer.setSequence(sequence(scaled, length));
    sequencer.setTempoInBPM(bpm);
    loaded = true;
    paused = false;
    sequencer.setLoopCount(looping ? Sequencer.LOOP_CONTINUOUSLY : 0);
    sequencer.start();
  }

  public synchronized void click(boolean accent) {
    if (volume > 0 && synth != null && synth.isOpen()) {
      MidiChannel drum = synth.getChannels()[9];
      drum.noteOff(37);
      drum.noteOn(37, (accent ? 90 : 55) * volume / 100);
    }
  }

  public synchronized void pauseResume() {
    if (!loaded || sequencer == null) return;
    if (paused) {
      sequencer.start();
      paused = false;
    } else if (sequencer.isRunning()) {
      sequencer.stop();
      if (synth != null) for (MidiChannel c : synth.getChannels()) if (c != null) c.allSoundOff();
      paused = true;
    }
  }

  public synchronized void seek(long tick) {
    if (loaded && sequencer != null) {
      if (synth != null) for (MidiChannel c : synth.getChannels()) if (c != null) c.allSoundOff();
      sequencer.setTickPosition(Math.max(0, Math.min(tick, sequencer.getTickLength() - 1)));
    }
  }

  public synchronized void loop(boolean enabled) {
    looping = enabled;
    if (sequencer != null && sequencer.isOpen())
      sequencer.setLoopCount(enabled ? Sequencer.LOOP_CONTINUOUSLY : 0);
  }

  public synchronized boolean playing() {
    return loaded && sequencer != null && sequencer.isRunning();
  }

  public synchronized boolean paused() {
    return paused;
  }

  public synchronized long cursor() {
    return loaded && sequencer != null ? sequencer.getTickPosition() : 0;
  }

  public synchronized long length() {
    return loaded && sequencer != null ? sequencer.getTickLength() : 0;
  }

  public static void export(java.nio.file.Path path, List<Part> parts, int length, int bpm)
      throws Exception {
    Sequence seq = sequence(parts, length);
    Track metadata = seq.createTrack();
    int micros = 60_000_000 / bpm;
    MetaMessage tempo = new MetaMessage();
    tempo.setMessage(
        0x51, new byte[] {(byte) (micros >> 16), (byte) (micros >> 8), (byte) micros}, 3);
    metadata.add(new MidiEvent(tempo, 0));
    MetaMessage signature = new MetaMessage();
    signature.setMessage(0x58, new byte[] {4, 2, 24, 8}, 4);
    metadata.add(new MidiEvent(signature, 0));
    java.nio.file.Path parent = path.toAbsolutePath().getParent(),
        temp = java.nio.file.Files.createTempFile(parent, "mmm-midi-", ".tmp");
    try {
      if (MidiSystem.write(seq, 1, temp.toFile()) <= 0)
        throw new java.io.IOException("MIDI 파일을 만들 수 없습니다.");
      java.nio.file.Files.move(
          temp,
          path,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } finally {
      java.nio.file.Files.deleteIfExists(temp);
    }
  }

  public synchronized long position() {
    return sequencer != null && sequencer.isOpen() && sequencer.isRunning()
        ? sequencer.getTickPosition()
        : -1;
  }

  public synchronized boolean ready() {
    return sequencer != null && sequencer.isOpen();
  }

  @Override
  public synchronized void close() {
    stop();
    if (sequencer != null) sequencer.close();
    if (synth != null) synth.close();
  }
}
