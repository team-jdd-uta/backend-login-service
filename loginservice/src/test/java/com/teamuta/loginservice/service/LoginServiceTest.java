package com.teamuta.loginservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamuta.loginservice.dto.LoginResponse;
import com.teamuta.loginservice.repository.LoginRepository;
import com.teamuta.loginservice.repository.LoginRepository.UserRecord;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {
    @Mock
    private LoginRepository loginRepository;

    @Mock
    private CognitoAuthService cognitoAuthService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registerCreatesCognitoUserAndPersistsOutboxEvent() {
        LoginService loginService = new LoginService(loginRepository, objectMapper, cognitoAuthService);
        when(loginRepository.existsByUsername("member1")).thenReturn(false);

        LoginResponse.User user = loginService.register("member1", "Password123!");

        assertThat(user.username()).isEqualTo("member1");
        verify(cognitoAuthService).createUser("member1", "Password123!");
        verify(loginRepository).saveUser(eq(user.id()), eq("member1"), any(String.class), any(LocalDateTime.class));
        verify(loginRepository).saveOutboxEvent(any(String.class), eq("user"), eq(user.id()), eq("UserRegistered"), any(String.class), eq("PENDING"), anyLong());
        verify(cognitoAuthService, never()).deleteUser("member1");
    }

    @Test
    void registerDeletesCognitoUserWhenLocalTransactionFails() {
        LoginService loginService = new LoginService(loginRepository, objectMapper, cognitoAuthService);
        when(loginRepository.existsByUsername("member1")).thenReturn(false);
        doThrow(new IllegalStateException("db down"))
                .when(loginRepository)
                .saveUser(any(String.class), eq("member1"), any(String.class), any(LocalDateTime.class));

        assertThatThrownBy(() -> loginService.register("member1", "Password123!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");

        verify(cognitoAuthService).createUser("member1", "Password123!");
        verify(cognitoAuthService).deleteUser("member1");
    }

    @Test
    void loginReturnsLocalUserAndCognitoTokens() {
        LoginService loginService = new LoginService(loginRepository, objectMapper, cognitoAuthService);
        CognitoAuthService.AuthTokens authTokens = new CognitoAuthService.AuthTokens("access-token", "id-token", "refresh-token", 3600);
        when(cognitoAuthService.login("member1", "Password123!")).thenReturn(authTokens);
        when(loginRepository.findByUsername("member1")).thenReturn(Optional.of(new UserRecord("user-1", "member1")));

        LoginResponse response = loginService.login("member1", "Password123!");

        assertThat(response.success()).isTrue();
        assertThat(response.user()).isEqualTo(new LoginResponse.User("user-1", "member1"));
        assertThat(response.tokens()).isEqualTo(new LoginResponse.Tokens("access-token", "id-token", "refresh-token", 3600));
    }
}
