package com.teamuta.loginservice.controller;

import com.teamuta.loginservice.dto.LoginRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/*
curl -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'
*/



@RestController
public class LoginController {
    @PostMapping("/login")
    public String login(@RequestBody LoginRequest request) {
        if ("admin".equals(request.getUsername()) && "password".equals(request.getPassword())) {
            return "Login successful!";
        } else {
            return "Invalid username or password.";
        }
    }
}
