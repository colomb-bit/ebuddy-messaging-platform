package com.ebuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EbuddyApplication {
  public static void main(String[] args) { SpringApplication.run(EbuddyApplication.class, args); }
}
