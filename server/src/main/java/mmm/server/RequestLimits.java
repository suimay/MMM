package mmm.server;

import java.io.*;
import java.time.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestLimits extends OncePerRequestFilter {
  private final Clock clock;
  public RequestLimits(Clock clock){this.clock=clock;}
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
      throws IOException,ServletException {
    response.setHeader("Cache-Control","no-store");
    response.setHeader("X-MMM-Today",LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))).toString());
    response.setHeader("X-Content-Type-Options","nosniff");
    if(!java.util.Set.of("POST","PUT","PATCH").contains(request.getMethod())) {chain.doFilter(request,response);return;}
    byte[] bytes=request.getInputStream().readNBytes(131073);
    if(bytes.length>131072) {
      ActivityLog.failure("REQUEST_TOO_LARGE");
      response.setStatus(413);response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"code\":\"REQUEST_TOO_LARGE\",\"message\":\"요청 크기 제한을 초과했습니다.\"}");return;
    }
    chain.doFilter(new HttpServletRequestWrapper(request){
      @Override public ServletInputStream getInputStream(){
        ByteArrayInputStream input=new ByteArrayInputStream(bytes);
        return new ServletInputStream(){
          public int read(){return input.read();}
          public int read(byte[] b,int off,int len){return input.read(b,off,len);}
          public boolean isFinished(){return input.available()==0;}
          public boolean isReady(){return true;}
          public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}
        };
      }
      @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
    },response);
  }
}
