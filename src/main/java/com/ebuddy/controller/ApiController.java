package com.ebuddy.controller;
import com.ebuddy.service.*; import com.ebuddy.web.ApiModels; import jakarta.validation.Valid; import org.springframework.http.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api") public class ApiController { final AuthService auth; final MessageService messages; public ApiController(AuthService a,MessageService m){auth=a;messages=m;} private UUID user(Authentication a){return (UUID)a.getPrincipal();}
 @PostMapping({"/auth/login","/auth/signin"}) public ApiModels.LoginResponse login(@Valid @RequestBody ApiModels.LoginRequest r){return auth.login(r);}
 @PostMapping("/auth/signup") @ResponseStatus(HttpStatus.CREATED) public ApiModels.J2meLogin signup(@Valid @RequestBody ApiModels.RegisterRequest r){return auth.register(r);}
 @PostMapping("/messages") @ResponseStatus(HttpStatus.CREATED) public ApiModels.MessageView send(Authentication a,@Valid @RequestBody ApiModels.SendRequest r){return messages.send(user(a),r);}
 @PostMapping("/messages/{id}/receipt") @ResponseStatus(HttpStatus.NO_CONTENT) public void receipt(Authentication a,@PathVariable UUID id,@Valid @RequestBody ApiModels.ReceiptRequest r){messages.receipt(user(a),id,r.state());}
 @GetMapping("/sync") public ApiModels.SyncView sync(Authentication a,@RequestParam(defaultValue="0")long cursor,@RequestParam(defaultValue="20")int limit){var x=messages.sync(user(a),cursor,limit);String next=x.isEmpty()?Long.toString(cursor):Long.toString(x.get(x.size()-1).sequence()==null?cursor:x.get(x.size()-1).sequence());return new ApiModels.SyncView(x,next,x.size()>=Math.min(limit,20));}
}
