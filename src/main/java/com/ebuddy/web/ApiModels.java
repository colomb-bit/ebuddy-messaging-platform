package com.ebuddy.web;
import jakarta.validation.constraints.*; import java.time.Instant; import java.util.*;
public final class ApiModels { private ApiModels(){}
 public record LoginRequest(@NotBlank @Size(max=32) String username,@NotBlank @Size(max=128) String password,@NotBlank @Pattern(regexp="android|j2me") String clientType,@Size(max=80) String deviceLabel){}
 public record RegisterRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{3,32}") String username,@NotBlank @Size(min=8,max=128) String password,@NotBlank @Size(max=80) String displayName){}
 public record LoginResponse(String token,long expiresAt,UserView user){}
 public record UserView(UUID id,String username,String displayName){}
 public record SendRequest(@NotNull UUID toUserId,@NotBlank @Size(max=64) String clientMessageId,@NotBlank @Size(max=4096) String body){}
 public record ReceiptRequest(@NotNull @Pattern(regexp="delivered|read") String state){}
 public record MessageView(UUID id,UUID fromUserId,UUID toUserId,String clientMessageId,Long sequence,String body,String status,Instant createdAt,Instant deliveredAt,Instant readAt){}
 public record SyncView(List<?> items,String nextCursor,boolean more){}
 public record ContactView(UserView user,String state,String alias){}
 public record ErrorView(String error,String code,String message,String requestId,boolean retryable,Integer retryAfter){}
 public record J2meLogin(String token,long expiresAt){}
 public record J2meMessage(String i,String f,String t,String b,long s,long c,String q){}
}
