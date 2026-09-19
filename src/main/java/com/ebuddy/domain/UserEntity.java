package com.ebuddy.domain;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="app_user") public class UserEntity {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="user_id") UUID id;
 @Column(nullable=false,length=32) String username; @Column(name="username_norm",nullable=false,length=32,unique=true) String usernameNorm; @Column(name="display_name",nullable=false,length=80) String displayName; @Column(name="password_hash",length=255) String passwordHash; @Column(name="phone_e164",length=20,unique=true) String phoneE164;
 @Enumerated(EnumType.STRING) @Column(nullable=false) UserState state=UserState.active; @Column(name="created_at",nullable=false) Instant createdAt=Instant.now(); @Column(name="updated_at",nullable=false) Instant updatedAt=Instant.now(); @Column(name="last_seen_at") Instant lastSeenAt;
 protected UserEntity(){} public UserEntity(String u,String d,String p){username=u;usernameNorm=u.toLowerCase();displayName=d;passwordHash=p;}
 public UUID getId(){return id;} public String getUsername(){return username;} public String getUsernameNorm(){return usernameNorm;} public String getDisplayName(){return displayName;} public String getPasswordHash(){return passwordHash;} public UserState getState(){return state;}
}
