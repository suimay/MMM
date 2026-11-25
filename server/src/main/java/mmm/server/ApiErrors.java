package mmm.server;

import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import mmm.Api.ErrorDto;

@RestControllerAdvice
public class ApiErrors {
  private ResponseEntity<ErrorDto> response(int status,String code,String message){
    ActivityLog.failure(code);return ResponseEntity.status(status).body(new ErrorDto(code,message));
  }
  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<ErrorDto> notFound(Exception ex){return response(404,"NOT_FOUND","요청한 경로를 찾을 수 없습니다.");}
  @ExceptionHandler(ApiFailure.class)
  ResponseEntity<ErrorDto> failure(ApiFailure ex){return response(ex.status,ex.code,ex.getMessage());}
  @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class})
  ResponseEntity<ErrorDto> malformed(Exception ex){return response(400,"INVALID_INPUT","요청 형식과 필수 항목을 확인하세요.");}
  @ExceptionHandler({IllegalArgumentException.class,NullPointerException.class})
  ResponseEntity<ErrorDto> invalid(Exception ex){return response(400,"INVALID_INPUT","입력 범위와 필수 값을 확인하세요.");}
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorDto> duplicate(Exception ex){return response(409,"CONFLICT","중복된 값 또는 데이터 제약 충돌입니다. 최신 내용을 확인하세요.");}
  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ErrorDto> storage(Exception ex){return response(503,"STORAGE_UNAVAILABLE","저장소 작업에 실패했습니다. 초안을 유지하고 다시 시도하세요.");}
  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorDto> internal(Exception ex){return response(500,"INTERNAL_ERROR","요청을 완료하지 못했습니다. 초안을 유지하고 다시 시도하세요.");}
}
