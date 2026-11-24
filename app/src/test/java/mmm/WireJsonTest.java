package mmm;

import java.time.*;
import java.util.*;
import mmm.Model.*;

public final class WireJsonTest {
  private static int checks;
  private static void check(boolean value){if(!value)throw new AssertionError("JSON check "+checks);checks++;}
  private static void rejects(Runnable action){try{action.run();}catch(IllegalArgumentException ex){checks++;return;}throw new AssertionError("invalid JSON accepted");}
  public static void main(String[] args) {
    String text="한글 🎵 \" \\ \n\t\b";
    check(WireJson.read(WireJson.write(text)).equals(text));
    Map<String,Object> value=WireJson.object(WireJson.read("{\"a\":123,\"b\":false,\"c\":null,\"d\":[\"한글\",-2,0]}"));
    check(WireJson.integer(value,"a")==123);check(!WireJson.bool(value,"b"));check(value.containsKey("c")&&value.get("c")==null);
    check(WireJson.list(value.get("d")).size()==3);check(WireJson.read("\"\\uD83C\\uDFB5\"").equals("🎵"));
    rejects(()->WireJson.read("{\"a\":1,\"a\":2}"));rejects(()->WireJson.read("01"));rejects(()->WireJson.read("--1"));
    rejects(()->WireJson.read("[1,]"));rejects(()->WireJson.read("{\"a\":}"));rejects(()->WireJson.read("true false"));
    rejects(()->WireJson.read("\"\\x\""));rejects(()->WireJson.read("\"line\nfeed\""));rejects(()->WireJson.read("\"\\uZZZZ\""));
    rejects(()->WireJson.read("[".repeat(34)+"0"+"]".repeat(34)));rejects(()->WireJson.read(" ".repeat(4_000_001)));
    rejects(()->WireJson.integer(WireJson.object(WireJson.read("{\"n\":2147483648}")),"n"));
    rejects(()->WireJson.integer(value,"b"));rejects(()->WireJson.object(List.of()));
    rejects(()->WireJson.write(new Object()));
    Phrase phrase=new Phrase(UUID.randomUUID().toString(),"tester",LocalDate.of(2026,11,16),0,90,0,"",
        List.of(new NoteEvent(0,120,60,90,Articulation.STACCATO)),List.of(new RestEvent(120,1800)),List.of());
    Api.SaveBody save=Api.SaveBody.from(phrase,UUID.randomUUID(),0);
    check(WireJson.object(WireJson.read(WireJson.write(save))).containsKey("requestId"));
    check(save.model(phrase.songId(),phrase.author(),phrase.date()).equals(phrase));
    check(!new Api.Login("tester","secret-password").toString().contains("secret-password"));
    check(!new Api.AuthResult("secret-token",new Api.UserDto("tester","테스트"),phrase.date(),Instant.now()).toString().contains("secret-token"));
    PasswordCodec.Digest digest=PasswordCodec.create("test1234".toCharArray());
    check(PasswordCodec.verify("test1234".toCharArray(),digest.salt(),digest.hash()));
    check(!PasswordCodec.verify("bad12345".toCharArray(),digest.salt(),digest.hash()));
    check(PasswordCodec.sha256("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
    System.out.println("PASS: "+checks+" HTTP JSON and credential boundary checks");
  }
}
