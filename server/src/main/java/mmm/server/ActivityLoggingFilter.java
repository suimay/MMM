package mmm.server;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import mmm.server.ActivityLog.Event;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ActivityLoggingFilter extends OncePerRequestFilter {
  private static final String UUID_PATTERN="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
  private static final Pattern SONG=Pattern.compile("^/api/songs/("+UUID_PATTERN+")(/.*)?$");
  private record Route(Event event,String template,UUID song){}
  private static Route route(String method,String path){
    Event direct=switch(method+" "+path){
      case "POST /api/auth/signup"->Event.SIGNUP;case "POST /api/auth/login"->Event.LOGIN;
      case "POST /api/auth/logout"->Event.LOGOUT;case "GET /api/me"->Event.SESSION_READ;
      case "GET /api/songs"->Event.SONG_LIST;case "POST /api/songs"->Event.SONG_CREATE;
      case "POST /api/invites/redeem"->Event.INVITE_REDEEM;
      case "GET /api/me/settings"->Event.SETTINGS_READ;case "PUT /api/me/settings"->Event.SETTINGS_UPDATE;
      case "GET /api/health"->Event.HEALTH;default->null;
    };
    if(direct!=null)return new Route(direct,path,null);
    Matcher match=SONG.matcher(path);
    if(match.matches()){
      UUID id=UUID.fromString(match.group(1));String suffix=Objects.toString(match.group(2),"");
      Event event=switch(method+" "+suffix){
        case "PATCH "->Event.SONG_UPDATE;case "GET /members"->Event.MEMBERS_READ;
        case "POST /invites"->Event.INVITE_CREATE;case "DELETE /members/me"->Event.MEMBER_LEAVE;
        case "GET /records"->Event.RECORD_READ;default->null;
      };
      if(event!=null)return new Route(event,"/api/songs/{id}"+suffix,id);
      if(method.equals("PUT")&&suffix.matches("/records/[0-9]{4}-[0-9]{2}-[0-9]{2}/me"))
        return new Route(Event.RECORD_SAVE,"/api/songs/{id}/records/{date}/me",id);
    }
    return new Route(Event.UNKNOWN_REQUEST,"/api/[unmatched]",null);
  }
  @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getRequestURI().startsWith("/api/")&&!request.getRequestURI().equals("/api");}
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
    String method=Set.of("GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS").contains(request.getMethod())?request.getMethod():"OTHER";
    Route route=route(method,request.getRequestURI());
    ActivityLog.Context context=ActivityLog.begin(route.event(),method,route.template(),!Set.of("GET","HEAD","OPTIONS").contains(method));
    ActivityLog.song(route.song());response.setHeader("X-MMM-Trace-Id",context.traceId);
    boolean completed=false;
    try{chain.doFilter(request,response);completed=true;}
    finally {
      try{if(!completed)ActivityLog.failure("UNHANDLED_ERROR");ActivityLog.finish(context,completed?response.getStatus():500);}
      finally{ActivityLog.clear();}
    }
  }
}
