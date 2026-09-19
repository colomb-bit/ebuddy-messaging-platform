package com.ebuddy.config;
import jakarta.servlet.*; import jakarta.servlet.http.*; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter; import java.io.IOException; import java.util.UUID;
@Component public class RequestIdFilter extends OncePerRequestFilter { protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain c)throws ServletException,IOException{String id=r.getHeader("X-Request-Id");if(id==null||id.isBlank())id=UUID.randomUUID().toString();s.setHeader("X-Request-Id",id);c.doFilter(r,s);} }
