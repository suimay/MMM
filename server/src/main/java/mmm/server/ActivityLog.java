package mmm.server;

import java.time.LocalDate;
import java.util.*;
import org.slf4j.*;
import org.springframework.transaction.support.*;

/** Request-local allowlisted metadata; no credentials, request/response bodies or exception text. */
public final class ActivityLog {
  private ActivityLog() {}
  private static final Logger LOG=LoggerFactory.getLogger("mmm.activity");
  private static final ThreadLocal<Context> CURRENT=new ThreadLocal<>();

  public enum Event {
    SIGNUP,LOGIN,LOGOUT,SESSION_READ,SONG_LIST,SONG_CREATE,SONG_UPDATE,MEMBERS_READ,
    INVITE_CREATE,INVITE_REDEEM,MEMBER_LEAVE,RECORD_READ,RECORD_SAVE,RECORD_CREATE,
    RECORD_UPDATE,RECORD_REPLAY,SETTINGS_READ,SETTINGS_UPDATE,HEALTH,UNKNOWN_REQUEST
  }
  static final class Context {
    final String traceId=UUID.randomUUID().toString();
    final long started=System.nanoTime();
    final String method,route;
    final boolean mutation;
    Event event;
    String user="-",attemptedUser="-",error="-";
    UUID songId,requestId;
    LocalDate date;
    Integer expectedVersion,version,notes,rests,expressions;
    boolean committed,changed,replay;
    Context(Event event,String method,String route,boolean mutation){this.event=event;this.method=method;this.route=route;this.mutation=mutation;}
  }
  static Context begin(Event event,String method,String route,boolean mutation){
    Context context=new Context(event,method,route,mutation);CURRENT.set(context);return context;
  }
  static void clear(){CURRENT.remove();}
  private static String userId(String value){return value!=null&&value.matches("[A-Za-z0-9_]{3,24}")?value:"INVALID";}
  public static void user(String value){Context c=CURRENT.get();if(c!=null)c.user=userId(value);}
  public static void attemptedUser(String value){Context c=CURRENT.get();if(c!=null)c.attemptedUser=userId(value);}
  public static void song(UUID value){Context c=CURRENT.get();if(c!=null)c.songId=value;}
  public static void expectedVersion(Integer value){Context c=CURRENT.get();if(c!=null)c.expectedVersion=value;}
  public static void version(int value){Context c=CURRENT.get();if(c!=null)c.version=value;}
  public static void record(LocalDate date,UUID requestId,int expected,int notes,int rests,int expressions){
    Context c=CURRENT.get();if(c!=null){c.date=date;c.requestId=requestId;c.expectedVersion=expected;c.notes=notes;c.rests=rests;c.expressions=expressions;}
  }
  public static void event(Event value){Context c=CURRENT.get();if(c!=null)c.event=value;}
  public static void failure(String code){
    Context c=CURRENT.get();if(c!=null)c.error=code!=null&&code.matches("[A-Z][A-Z0-9_]{0,63}")?code:"ERROR";
  }
  /** Success is confirmed by the transaction manager, never by reaching the last SQL statement. */
  public static void afterCommit(boolean changed,Integer version,boolean replay){
    Context c=CURRENT.get();
    if(c==null||!TransactionSynchronizationManager.isActualTransactionActive()
        ||!TransactionSynchronizationManager.isSynchronizationActive())return;
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
      @Override public void afterCommit(){c.committed=true;c.changed=changed;c.replay=replay;if(version!=null)c.version=version;}
    });
  }
  static void finish(Context c,int status){
    if(c.event==Event.HEALTH&&status<400)return;
    boolean failed=status>=400;
    String result=failed?"FAILURE":c.replay?"REPLAY":c.mutation&&!c.committed?"UNCONFIRMED":"SUCCESS";
    String code=failed?(c.error.equals("-")?"HTTP_"+status:c.error):result.equals("UNCONFIRMED")?"COMMIT_UNCONFIRMED":"OK";
    String line="event="+c.event+" result="+result+" user="+c.user+" attemptedUser="+c.attemptedUser
        +" songId="+value(c.songId)+" date="+value(c.date)+" expectedVersion="+value(c.expectedVersion)
        +" version="+value(c.version)+" requestId="+value(c.requestId)
        +" noteCount="+value(c.notes)+" restCount="+value(c.rests)+" expressionCount="+value(c.expressions)
        +" committed="+c.committed+" changed="+c.changed+" http="+status+" code="+code
        +" method="+c.method+" route="+c.route+" durationMs="+Math.max(0,(System.nanoTime()-c.started)/1_000_000)
        +" traceId="+c.traceId;
    if(status>=500)LOG.error(line);else if(failed||result.equals("UNCONFIRMED"))LOG.warn(line);else LOG.info(line);
  }
  private static String value(Object value){return value==null?"-":value.toString();}
}
