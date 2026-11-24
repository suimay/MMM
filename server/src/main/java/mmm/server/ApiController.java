package mmm.server;

import java.time.*;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import mmm.Api.*;
import mmm.Model.Settings;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final CollaborationService service;
  public ApiController(CollaborationService service){this.service=service;}
  private String actor(HttpServletRequest request){return service.authenticate(request.getHeader("Authorization")).id();}
  @GetMapping("/health") public Map<String,String> health(){return Map.of("status","ok");}
  @PostMapping("/auth/signup") @ResponseStatus(HttpStatus.CREATED)
  public UserDto signup(@RequestBody Signup body){return service.signup(body);}
  @PostMapping("/auth/login") public AuthResult login(@RequestBody Login body){return service.login(body);}
  @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest request){service.logout(request.getHeader("Authorization"));}
  @GetMapping("/me") public Me me(HttpServletRequest request){return new Me(service.authenticate(request.getHeader("Authorization")),service.today());}
  @GetMapping("/songs") public List<SongDto> songs(HttpServletRequest request){return service.songs(actor(request));}
  @PostMapping("/songs") @ResponseStatus(HttpStatus.CREATED)
  public SongDto create(HttpServletRequest request,@RequestBody CreateSong body){return service.create(actor(request),body);}
  @PatchMapping("/songs/{id}")
  public SongDto update(HttpServletRequest request,@PathVariable UUID id,@RequestBody UpdateSong body){return service.update(actor(request),id,body);}
  @GetMapping("/songs/{id}/members")
  public List<MemberDto> members(HttpServletRequest request,@PathVariable UUID id){return service.members(actor(request),id);}
  @PostMapping("/songs/{id}/invites") @ResponseStatus(HttpStatus.CREATED)
  public InviteDto invite(HttpServletRequest request,@PathVariable UUID id){return service.invite(actor(request),id);}
  @PostMapping("/invites/redeem")
  public SongDto redeem(HttpServletRequest request,@RequestBody Redeem body){return service.redeem(actor(request),body);}
  @DeleteMapping("/songs/{id}/members/me") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void leave(HttpServletRequest request,@PathVariable UUID id){service.leave(actor(request),id);}
  @GetMapping("/songs/{id}/records")
  public List<PhraseDto> records(HttpServletRequest request,@PathVariable UUID id,@RequestParam LocalDate from,@RequestParam LocalDate to){return service.records(actor(request),id,from,to);}
  @PutMapping("/songs/{id}/records/{date}/me")
  public PhraseDto save(HttpServletRequest request,@PathVariable UUID id,@PathVariable LocalDate date,@RequestBody SaveBody body){return service.save(actor(request),id,date,body);}
  @GetMapping("/me/settings") public Settings settings(HttpServletRequest request){return service.settings(actor(request));}
  @PutMapping("/me/settings") public Settings settings(HttpServletRequest request,@RequestBody Settings body){return service.settings(actor(request),body);}
}
