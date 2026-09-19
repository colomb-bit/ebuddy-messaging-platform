package com.ebuddy.web;
import jakarta.servlet.http.HttpServletRequest; import org.springframework.http.*; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*; import org.springframework.security.authentication.BadCredentialsException; import java.util.UUID;
@RestControllerAdvice public class Errors {
 private ResponseEntity<ApiModels.ErrorView> body(String code,String msg,HttpStatus st,boolean retry,String id){String cat=st==HttpStatus.UNAUTHORIZED?"authentication_failed":st==HttpStatus.FORBIDDEN?"forbidden":st==HttpStatus.NOT_FOUND?"not_found":st==HttpStatus.CONFLICT?"conflict":st.is4xxClientError()?"invalid_request":"server_error";return ResponseEntity.status(st).body(new ApiModels.ErrorView(cat,code,msg,id,retry,retry?5:null));}
 private String id(HttpServletRequest r){String v=r.getHeader("X-Request-Id");return v==null||v.isBlank()?UUID.randomUUID().toString():v;}
 @ExceptionHandler(DomainException.class) ResponseEntity<?> domain(DomainException e,HttpServletRequest r){return body(e.code,e.getMessage(),e.status,e.retryable,id(r));}
 @ExceptionHandler({MethodArgumentNotValidException.class,IllegalArgumentException.class}) ResponseEntity<?> invalid(Exception e,HttpServletRequest r){return body("INVALID_REQUEST","Request validation failed",HttpStatus.BAD_REQUEST,false,id(r));}
 @ExceptionHandler(BadCredentialsException.class) ResponseEntity<?> auth(Exception e,HttpServletRequest r){return body("AUTH_REQUIRED","Authentication failed",HttpStatus.UNAUTHORIZED,false,id(r));}
 @ExceptionHandler(Exception.class) ResponseEntity<?> unknown(Exception e,HttpServletRequest r){return body("INTERNAL_ERROR","The server could not complete the request",HttpStatus.INTERNAL_SERVER_ERROR,true,id(r));}
}
