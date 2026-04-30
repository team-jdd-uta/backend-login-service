package com.teamuta.loginservice.controller;

import com.teamuta.loginservice.dto.LoginRequest;
import com.teamuta.loginservice.dto.LoginResponse;
import com.teamuta.loginservice.service.LoginService;
import com.teamuta.loginservice.service.LoginService.LoginFailureException;
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
    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(loginService.login(request.getUsername(), request.getPassword()));
        } catch (LoginFailureException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(false, null, exception.getMessage()));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(false, null, "로그인에 실패했습니다."));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@RequestBody LoginRequest request) {
        try {
            LoginResponse.User user = loginService.register(request.getUsername(), request.getPassword());
            if (user == null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new LoginResponse(false, null, "이미 존재하는 계정입니다."));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(new LoginResponse(true, user));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new LoginResponse(false, null, "회원가입에 실패했습니다."));
        }
    }
}
