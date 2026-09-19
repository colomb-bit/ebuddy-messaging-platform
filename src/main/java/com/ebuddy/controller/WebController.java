package com.ebuddy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the lightweight browser client without requiring JWT authentication. */
@Controller
public class WebController {
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}
