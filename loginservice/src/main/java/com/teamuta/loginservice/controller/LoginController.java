package com.teamuta.loginservice.controller;

import com.teamuta.loginservice.dto.LoginRequest;
import com.teamuta.loginservice.dto.LoginResponse;
import com.teamuta.loginservice.dto.InternalUserResponse;
import com.teamuta.loginservice.dto.RegisterRequest;
import com.teamuta.loginservice.dto.RefreshTokenRequest;
import com.teamuta.loginservice.service.LoginService;
import com.teamuta.loginservice.service.LoginService.LoginFailureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/*
curl -X POST http://localhost:8081/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'
*/



@RestController
public class LoginController {
    private final LoginService loginService;
    private final String internalToken;

    public LoginController(LoginService loginService,
                           @Value("${internal.auth-token:}") String internalToken) {
        this.loginService = loginService;
        this.internalToken = internalToken;
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
    public ResponseEntity<LoginResponse> register(@RequestBody RegisterRequest request) {
        try {
            LoginResponse.User user = loginService.register(request.getEmail(), request.getNickname(), request.getPassword());
            if (user == null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new LoginResponse(false, null, "이미 존재하는 계정입니다."));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(new LoginResponse(true, user));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new LoginResponse(false, null, "회원가입에 실패했습니다."));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            return ResponseEntity.ok(loginService.refresh(request.refreshToken()));
        } catch (LoginFailureException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(false, null, exception.getMessage()));
        }
    }

    @PostMapping("/logout/{userId}")
    public ResponseEntity<Void> logout(@PathVariable String userId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/internal/users/by-cognito-sub/{sub}")
    public ResponseEntity<InternalUserResponse> findByCognitoSub(@PathVariable String sub,
                                                                 @RequestHeader(value = "X-Internal-Token", required = false) String providedToken) {
        if (internalToken == null || internalToken.isBlank() || !internalToken.equals(providedToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return loginService.findByCognitoSub(sub)
                .map(user -> ResponseEntity.ok(new InternalUserResponse(user.id(), user.name(), user.email())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
