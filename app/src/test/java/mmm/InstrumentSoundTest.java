package mmm;

import java.time.*;
import java.util.*;
import java.util.List;
import javax.sound.midi.*;
import javax.swing.*;
import mmm.Model.*;

public final class InstrumentSoundTest {
  private static int checks;
  private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
  private static Phrase phrase(int program,int pitch){return new Phrase("sound","tester",LocalDate.of(2026,11,16),0,90,program,"",List.of(new NoteEvent(0,120,pitch,100)));}
  private static List<ShortMessage> notes(Sequence sequence) {
    List<ShortMessage> result=new ArrayList<>();for(Track track:sequence.getTracks())for(int i=0;i<track.size();i++)
      if(track.get(i).getMessage() instanceof ShortMessage m && m.getCommand()==ShortMessage.NOTE_ON)result.add(m);
    return result;
  }
  private static void send(MidiEngine.PlaybackRouter router,int command,int channel,int a,int b) throws Exception {
    ShortMessage message=new ShortMessage();message.setMessage(command,channel,a,b);router.send(message,-1);
  }
  private static void drum(MidiEngine.PlaybackRouter router,int channel,int pitch) throws Exception {
    send(router,ShortMessage.CONTROL_CHANGE,channel,0,120);send(router,ShortMessage.PROGRAM_CHANGE,channel,0,0);send(router,ShortMessage.NOTE_ON,channel,pitch,100);
  }
  public static void main(String[] args) throws Exception {
    check(Arrays.equals(MMM.PROGRAMS,new int[]{0,33,24,128}),"Four correct instrument identifiers");
    check(ArtAssets.instrumentName(33).equals("베이스") && ArtAssets.instrumentName(128).equals("드럼"),"Instrument names match sounds");
    Sequence exported=MidiEngine.sequence(List.of(new MidiEngine.Part(phrase(128,36),0,2,100),new MidiEngine.Part(phrase(33,28),0,3,100)),1920);
    List<ShortMessage> events=notes(exported);
    check(events.stream().anyMatch(m->m.getChannel()==9&&m.getData1()==36),"Drum exports use GM percussion channel");
    check(events.stream().anyMatch(m->m.getChannel()==3&&m.getData1()==28),"Bass retains melodic member channel");
    List<ShortMessage> messages=new ArrayList<>();Receiver target=new Receiver(){public void send(MidiMessage message,long stamp){if(message instanceof ShortMessage m)messages.add((ShortMessage)m.clone());}public void close(){}};
    MidiEngine.PlaybackRouter router=new MidiEngine.PlaybackRouter(target);
    router.configure(List.of(new MidiEngine.Part(phrase(128,36),0,0,100),new MidiEngine.Part(phrase(128,38),0,1,100),new MidiEngine.Part(phrase(33,28),0,2,100)),Map.of(0,100,1,50,2,100),80,0);
    drum(router,0,36);drum(router,1,38);send(router,ShortMessage.CONTROL_CHANGE,2,0,0);send(router,ShortMessage.PROGRAM_CHANGE,2,33,0);send(router,ShortMessage.NOTE_ON,2,28,100);
    check(messages.stream().anyMatch(m->m.getCommand()==ShortMessage.NOTE_ON&&m.getChannel()==9&&m.getData1()==36&&m.getData2()==80),"Live kick uses percussion and master level");
    check(messages.stream().anyMatch(m->m.getCommand()==ShortMessage.NOTE_ON&&m.getChannel()==9&&m.getData1()==38&&m.getData2()==40),"Second drummer has independent level");
    check(messages.stream().anyMatch(m->m.getCommand()==ShortMessage.PROGRAM_CHANGE&&m.getChannel()==2&&m.getData1()==33),"Live bass selects fingered bass voice");
    int before=messages.size();router.level(0,0);
    check(messages.subList(before,messages.size()).stream().noneMatch(m->m.getCommand()==ShortMessage.NOTE_OFF&&m.getData1()==38),"Muting one drummer leaves the other drummer sounding");
    long count=messages.stream().filter(m->m.getCommand()==ShortMessage.NOTE_ON).count();drum(router,0,36);
    check(messages.stream().filter(m->m.getCommand()==ShortMessage.NOTE_ON).count()==count,"Muted drummer does not trigger hits");
    router.level(0,100);drum(router,0,36);
    check(messages.stream().filter(m->m.getCommand()==ShortMessage.NOTE_ON).count()==count+1,"Drummer can be unmuted during playback");
    router.clearVoices();messages.clear();drum(router,0,38);drum(router,1,38);send(router,ShortMessage.NOTE_OFF,0,38,0);
    check(messages.stream().noneMatch(m->m.getCommand()==ShortMessage.NOTE_OFF),"Shared drum note ownership prevents premature release");
    send(router,ShortMessage.NOTE_OFF,1,38,0);
    check(messages.stream().filter(m->m.getCommand()==ShortMessage.NOTE_OFF).count()==1,"Last drummer releases the shared note once");
    JComboBox<String> choices=new JComboBox<>(MMM.INSTRUMENT_NAMES);MMM.selectInstrument(choices,12);
    check(MMM.instrumentProgram(choices)==12 && choices.getItemCount()==5,"Legacy marimba records preserve their original sound");
    MMM.selectInstrument(choices,10);check(MMM.instrumentProgram(choices)==10,"Legacy music-box records preserve their original sound");
    new Settings(50,Model.DRUMS,true);check(phrase(Model.DRUMS,38).instrument()==128,"Percussion persists as a distinct instrument");
    boolean rejected=false;try{phrase(129,38);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Invalid instrument remains rejected");
    System.out.println("PASS: "+checks+" instrument identity, MIDI percussion, mixing and legacy checks");
  }
}
