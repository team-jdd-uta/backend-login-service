package com.teamuta.loginservice.controller;

import com.teamuta.loginservice.dto.LoginRequest;
import com.teamuta.loginservice.dto.LoginResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/*
curl -X POST http://localhost:8081/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'
*/



@RestController
public class LoginController {
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse.User user = new LoginResponse.User("admin", request.getUsername());
        return ResponseEntity.ok(new LoginResponse(true, user));
    }
}
