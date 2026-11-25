package mmm.server;

public final class ApiFailure extends RuntimeException {
  public final int status; public final String code;
  public ApiFailure(int status,String code,String message){super(message);this.status=status;this.code=code;}
  static ApiFailure unauthorized(){return new ApiFailure(401,"UNAUTHORIZED","로그인이 만료되었습니다. 다시 로그인하세요.");}
  static ApiFailure forbidden(){return new ApiFailure(403,"FORBIDDEN","이 작업을 수행할 권한이 없습니다.");}
  static ApiFailure missing(){return new ApiFailure(404,"NOT_FOUND","곡 또는 기록을 찾을 수 없습니다.");}
  static ApiFailure invalid(String message){return new ApiFailure(400,"INVALID_INPUT",message);}
  static ApiFailure conflict(String message){return new ApiFailure(409,"VERSION_CONFLICT",message);}
}
