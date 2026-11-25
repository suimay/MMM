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
  private PlaybackRouter router;
  private List<Part> playbackParts=List.of();

  public synchronized void open() {
    try {
      synth = MidiSystem.getSynthesizer();
      synth.open();
      sequencer = MidiSystem.getSequencer(false);
      sequencer.open();
      router=new PlaybackRouter(synth.getReceiver());
      sequencer.getTransmitter().setReceiver(router);
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
    if(router!=null)router.master(value);
  }

  public synchronized void on(int pitch, int instrument) {
    on(pitch,instrument,100);
  }

  public synchronized void on(int pitch,int instrument,int velocity) {
    if (volume > 0 && synth != null && synth.isOpen()) {
      MidiChannel channel=synth.getChannels()[instrument==Model.DRUMS?9:0];
      channel.controlChange(7,127);channel.programChange(instrument==Model.DRUMS?0:instrument);
      channel.noteOn(pitch,Math.max(1,Math.min(127,Math.round(velocity*volume/100f))));
    }
  }

  public synchronized void off(int pitch) {
    if (synth != null && synth.isOpen()) {synth.getChannels()[0].noteOff(pitch);synth.getChannels()[9].noteOff(pitch);}
  }

  public synchronized void stop() {
    loaded = false;
    paused = false;
    if (sequencer != null && sequencer.isOpen()) sequencer.stop();
    if(router!=null)router.clearVoices();
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
    return sequence(parts,length,false);
  }
  static Sequence sequence(List<Part> parts,int length,boolean playback) throws InvalidMidiDataException {
    Sequence seq = new Sequence(Sequence.PPQ, Model.PPQ);
    for (Part p : parts) {
      Track t = seq.createTrack();
      boolean drum=p.phrase().instrument()==Model.DRUMS;
      int ch = !playback && drum?9:p.channel();
      if(playback)add(t,ShortMessage.CONTROL_CHANGE,ch,0,drum?120:0,p.offset());
      add(t, ShortMessage.PROGRAM_CHANGE, ch, drum?0:p.phrase().instrument(), 0, p.offset());
      for (NoteEvent n : p.phrase().notes()) {
        if (!playback && p.volume() == 0) continue;
        add(
            t,
            ShortMessage.NOTE_ON,
            ch,
            n.pitch(),
            playback?p.phrase().effectiveVelocity(n):Math.max(1, p.phrase().effectiveVelocity(n) * p.volume() / 100),
            p.offset() + n.start());
        add(t, ShortMessage.NOTE_OFF, ch, n.pitch(), 0, p.offset() + n.start() + n.gateDuration());
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
    play(parts, length, bpm, Map.of());
  }

  public synchronized void play(List<Part> parts, int length, int bpm, Map<Integer, Integer> levels)
      throws Exception {
    playFrom(parts, length, bpm, levels, 0);
  }

  public synchronized void playFrom(
      List<Part> parts, int length, int bpm, Map<Integer, Integer> levels, long startTick)
      throws Exception {
    stop();
    if (sequencer == null || !sequencer.isOpen()) throw new IllegalStateException(status);
    playbackParts=List.copyOf(parts);router.configure(parts,levels,volume,startTick);
    sequencer.setSequence(sequence(parts, length,true));
    sequencer.setTempoInBPM(bpm);
    sequencer.setTickPosition(Math.max(0, Math.min(startTick, length - 1)));
    sequencer.setLoopStartPoint(Math.max(0, Math.min(startTick, length - 1)));
    sequencer.setLoopEndPoint(-1);
    loaded = true;
    paused = false;
    sequencer.setLoopCount(looping ? Sequencer.LOOP_CONTINUOUSLY : 0);
    router.applyLevels();
    sequencer.start();
  }

  public synchronized void partLevel(int channel, int level) {
    if (channel < 0 || channel > 15 || channel == 9) return;
    if(router!=null)router.level(channel,level);
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
      if(router!=null)router.position(playbackParts,sequencer.getTickPosition());
      paused = true;
    }
  }

  public synchronized void seek(long tick) {
    if (loaded && sequencer != null) {
      if (synth != null) for (MidiChannel c : synth.getChannels()) if (c != null) c.allSoundOff();
      if(router!=null)router.position(playbackParts,tick);
      sequencer.setTickPosition(Math.max(0, Math.min(tick, sequencer.getTickLength() - 1)));
    }
  }

  public synchronized void loop(boolean enabled) {
    looping = enabled;
    if (sequencer != null && sequencer.isOpen())
      sequencer.setLoopCount(enabled ? Sequencer.LOOP_CONTINUOUSLY : 0);
  }

  public synchronized boolean looping() {
    return looping;
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

  /** Keep logical member levels separate while routing percussion to GM channel 10. */
  static final class PlaybackRouter implements Receiver {
    private final Receiver target;
    private final boolean[] drums=new boolean[16];
    private final int[] levels=new int[16];
    private final Map<Integer,Deque<Integer>> owners=new HashMap<>();
    private final Map<Integer,Integer> sounding=new HashMap<>();
    private int master=100;
    PlaybackRouter(Receiver target){this.target=target;Arrays.fill(levels,100);}
    synchronized void master(int value){master=Math.max(0,Math.min(100,value));}
    synchronized void configure(List<Part> parts,Map<Integer,Integer> values,int volume,long tick) {
      Arrays.fill(levels,100);Set<Integer> assigned=new HashSet<>();
      for(Part part:parts)if(assigned.add(part.channel()))levels[part.channel()]=part.volume();
      values.forEach((ch,value)->levels[ch]=value);master(volume);position(parts,tick);
    }
    synchronized void position(List<Part> parts,long tick) {
      clearVoices();Arrays.fill(drums,false);long[] latest=new long[16];Arrays.fill(latest,Long.MIN_VALUE);
      for(Part part:parts)if(part.offset()<=tick && part.offset()>=latest[part.channel()]) {
        latest[part.channel()]=part.offset();drums[part.channel()]=part.phrase().instrument()==Model.DRUMS;
      }
      applyLevels();
    }
    synchronized void clearVoices(){owners.clear();sounding.clear();}
    synchronized void level(int channel,int value) {
      levels[channel]=Math.max(0,Math.min(100,value));
      if(levels[channel]==0)for(int key:new ArrayList<>(owners.keySet()))if(key/128==channel)
        while(owners.containsKey(key))off(channel,key%128);
      applyLevel(channel);
    }
    private void applyLevel(int channel){if(channel!=9)emit(ShortMessage.CONTROL_CHANGE,channel,7,levels[channel]*127/100);}
    synchronized void applyLevels(){for(int channel=0;channel<16;channel++)applyLevel(channel);emit(ShortMessage.CONTROL_CHANGE,9,7,127);}
    private void emit(int command,int channel,int a,int b) {
      try{ShortMessage message=new ShortMessage();message.setMessage(command,channel,a,b);target.send(message,-1);}catch(InvalidMidiDataException error){throw new IllegalArgumentException(error);}
    }
    private void off(int channel,int pitch) {
      int key=channel*128+pitch;Deque<Integer> queue=owners.get(key);if(queue==null||queue.isEmpty())return;
      int actual=queue.removeFirst();if(queue.isEmpty())owners.remove(key);
      int voice=actual*128+pitch,count=sounding.getOrDefault(voice,0);
      if(count<=1){sounding.remove(voice);emit(ShortMessage.NOTE_OFF,actual,pitch,0);}else sounding.put(voice,count-1);
    }
    @Override public synchronized void send(MidiMessage message,long stamp) {
      if(!(message instanceof ShortMessage m)){target.send(message,stamp);return;}
      int channel=m.getChannel(),command=m.getCommand(),a=m.getData1(),b=m.getData2();
      if(command==ShortMessage.CONTROL_CHANGE && a==0){drums[channel]=b==120;applyLevel(channel);if(!drums[channel])emit(command,channel,a,0);return;}
      if(command==ShortMessage.NOTE_OFF || command==ShortMessage.NOTE_ON && b==0){off(channel,a);return;}
      int actual=drums[channel]?9:channel;
      if(command==ShortMessage.NOTE_ON) {
        if(levels[channel]==0||master==0)return;
        owners.computeIfAbsent(channel*128+a,key->new ArrayDeque<>()).addLast(actual);sounding.merge(actual*128+a,1,Integer::sum);
        emit(command,actual,a,Math.max(1,drums[channel]?b*levels[channel]*master/10000:b*master/100));return;
      }
      if(command==ShortMessage.PROGRAM_CHANGE){emit(command,actual,drums[channel]?0:a,0);return;}
      if(command==ShortMessage.CONTROL_CHANGE && a==7)return;
      emit(command,actual,a,b);
    }
    @Override public void close(){clearVoices();}
  }
}
