package mmm;

import java.lang.reflect.*;
import java.time.temporal.TemporalAccessor;
import java.util.*;

/** Small strict JSON codec for the dependency-free Java HTTP client. Server uses Jackson. */
public final class WireJson {
  private WireJson() {}
  public static String write(Object value) { StringBuilder out=new StringBuilder(); append(out,value); return out.toString(); }
  private static void append(StringBuilder out,Object v) {
    if(v==null) out.append("null");
    else if(v instanceof String || v instanceof Enum<?> || v instanceof TemporalAccessor || v instanceof UUID) quote(out,v.toString());
    else if(v instanceof Boolean || v instanceof Integer || v instanceof Long) out.append(v);
    else if(v instanceof Map<?,?> map) {
      out.append('{'); boolean first=true;
      for(var e:map.entrySet()) { if(!first)out.append(','); first=false;quote(out,e.getKey().toString());out.append(':');append(out,e.getValue()); }
      out.append('}');
    } else if(v instanceof Iterable<?> list) {
      out.append('[');boolean first=true;for(Object e:list){if(!first)out.append(',');first=false;append(out,e);}out.append(']');
    } else if(v.getClass().isRecord()) {
      out.append('{');boolean first=true;
      for(RecordComponent part:v.getClass().getRecordComponents()) {
        if(!first)out.append(',');first=false;quote(out,part.getName());out.append(':');
        try {append(out,part.getAccessor().invoke(v));} catch(ReflectiveOperationException ex){throw new IllegalArgumentException("JSON 변환 실패",ex);}
      }
      out.append('}');
    } else throw new IllegalArgumentException("지원하지 않는 JSON 값");
  }
  private static void quote(StringBuilder out,String s) {
    out.append('"');
    for(int i=0;i<s.length();i++) {
      char c=s.charAt(i);
      switch(c) {
        case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");case '\r' -> out.append("\\r");case '\t' -> out.append("\\t");
        default -> {if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}
      }
    }out.append('"');
  }
  public static Object read(String input) {
    if(input.length()>4_000_000)throw new IllegalArgumentException("서버 응답이 너무 큽니다.");
    Parser parser=new Parser(input);Object value=parser.value(0);parser.space();if(parser.pos!=input.length())throw parser.bad();return value;
  }
  @SuppressWarnings("unchecked") public static Map<String,Object> object(Object v) {
    if(!(v instanceof Map<?,?>))throw new IllegalArgumentException("JSON 객체가 필요합니다.");return (Map<String,Object>)v;
  }
  public static List<?> list(Object v) {if(!(v instanceof List<?> a))throw new IllegalArgumentException("JSON 배열이 필요합니다.");return a;}
  public static String string(Map<String,Object> m,String key) {if(!(m.get(key) instanceof String s))throw new IllegalArgumentException("서버 문자열 형식 오류: "+key);return s;}
  public static int integer(Map<String,Object> m,String key) {
    if(!(m.get(key) instanceof Long n) || n<Integer.MIN_VALUE || n>Integer.MAX_VALUE)throw new IllegalArgumentException("서버 숫자 형식 오류: "+key);return n.intValue();
  }
  public static boolean bool(Map<String,Object> m,String key) {if(!(m.get(key) instanceof Boolean b))throw new IllegalArgumentException("서버 설정 형식 오류: "+key);return b;}
  private static final class Parser {
    final String text;int pos;
    Parser(String text){this.text=text;}
    IllegalArgumentException bad(){return new IllegalArgumentException("올바르지 않은 JSON 응답입니다.");}
    void space(){while(pos<text.length() && " \r\n\t".indexOf(text.charAt(pos))>=0)pos++;}
    boolean eat(char c){space();if(pos<text.length()&&text.charAt(pos)==c){pos++;return true;}return false;}
    Object value(int depth) {
      if(depth>32)throw bad();space();if(pos>=text.length())throw bad();char c=text.charAt(pos);
      if(c=='"')return string();
      if(c=='{'){
        pos++;Map<String,Object> map=new LinkedHashMap<>();if(eat('}'))return map;
        do {space();if(pos>=text.length()||text.charAt(pos)!='"')throw bad();String key=string();if(!eat(':')||map.containsKey(key))throw bad();map.put(key,value(depth+1));if(eat('}'))return map;}while(eat(','));throw bad();
      }
      if(c=='['){pos++;List<Object> list=new ArrayList<>();if(eat(']'))return list;do{list.add(value(depth+1));if(eat(']'))return list;}while(eat(','));throw bad();}
      for(String token:List.of("true","false","null"))if(text.startsWith(token,pos)){pos+=token.length();return token.equals("null")?null:token.equals("true");}
      int start=pos;if(c=='-')pos++;if(pos>=text.length())throw bad();
      if(text.charAt(pos)=='0')pos++;else{if(text.charAt(pos)<'1'||text.charAt(pos)>'9')throw bad();while(pos<text.length()&&Character.isDigit(text.charAt(pos)))pos++;}
      try{return Long.parseLong(text.substring(start,pos));}catch(NumberFormatException ex){throw bad();}
    }
    String string(){
      pos++;StringBuilder out=new StringBuilder();
      while(pos<text.length()){
        char c=text.charAt(pos++);if(c=='"')return out.toString();if(c<32)throw bad();
        if(c!='\\'){out.append(c);continue;}if(pos>=text.length())throw bad();
        char escaped=text.charAt(pos++);
        switch(escaped){
          case '"','\\','/' -> out.append(escaped);case 'b' -> out.append('\b');case 'f' -> out.append('\f');
          case 'n' -> out.append('\n');case 'r' -> out.append('\r');case 't' -> out.append('\t');
          case 'u' -> {if(pos+4>text.length())throw bad();try{out.append((char)Integer.parseInt(text.substring(pos,pos+4),16));}catch(NumberFormatException ex){throw bad();}pos+=4;}
          default -> throw bad();
        }
      }throw bad();
    }
  }
}
