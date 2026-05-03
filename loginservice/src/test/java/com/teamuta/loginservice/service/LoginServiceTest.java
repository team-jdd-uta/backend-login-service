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
import com.teamuta.loginservice.service.LoginService.LoginFailureException;
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
        when(cognitoAuthService.createUser("member1", "Password123!")).thenReturn("cognito-sub-1");

        LoginResponse.User user = loginService.register("member1", "Password123!");

        assertThat(user.username()).isEqualTo("member1");
        verify(cognitoAuthService).createUser("member1", "Password123!");
        verify(loginRepository).saveUser(eq(user.id()), eq("member1"), eq("cognito-sub-1"), any(String.class), any(LocalDateTime.class));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(loginRepository).saveOutboxEvent(any(String.class), eq("user"), eq(user.id()), eq("UserRegistered"), payload.capture(), eq("PENDING"), anyLong());
        assertThat(payload.getValue()).contains("\"cognitoSub\":\"cognito-sub-1\"");
        assertThat(payload.getValue()).contains("\"eventVersion\":2");
        verify(cognitoAuthService, never()).deleteUser("member1");
    }

    @Test
    void registerDeletesCognitoUserWhenLocalTransactionFails() {
        LoginService loginService = new LoginService(loginRepository, objectMapper, cognitoAuthService);
        when(loginRepository.existsByUsername("member1")).thenReturn(false);
        when(cognitoAuthService.createUser("member1", "Password123!")).thenReturn("cognito-sub-1");
        doThrow(new IllegalStateException("db down"))
                .when(loginRepository)
                .saveUser(any(String.class), eq("member1"), any(String.class), any(String.class), any(LocalDateTime.class));

        assertThatThrownBy(() -> loginService.register("member1", "Password123!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");

        verify(cognitoAuthService).createUser("member1", "Password123!");
        verify(cognitoAuthService).deleteUser("member1");
    }

    @Test
    void findByCognitoSubReturnsMappedInternalUser() {
        LoginService loginService = new LoginService(loginRepository, objectMapper, cognitoAuthService);
        when(loginRepository.findByCognitoSub("cognito-sub-1")).thenReturn(Optional.of(new UserRecord("user-1", "member1", "cognito-sub-1")));

        Optional<UserRecord> user = loginService.findByCognitoSub("cognito-sub-1");

        assertThat(user).contains(new UserRecord("user-1", "member1", "cognito-sub-1"));
    }

    @Test
    void loginReturnsLocalUserAndCognitoTokens() {
        LoginService loginService = new LoginService(loginRepository, objectMapper, cognitoAuthService);
        CognitoAuthService.AuthTokens authTokens = new CognitoAuthService.AuthTokens("access-token", "id-token", "refresh-token", 3600);
        when(cognitoAuthService.login("member1", "Password123!")).thenReturn(authTokens);
        when(loginRepository.findByUsername("member1")).thenReturn(Optional.of(new UserRecord("user-1", "member1", "cognito-sub-1")));

        LoginResponse response = loginService.login("member1", "Password123!");

        assertThat(response.success()).isTrue();
        assertThat(response.user()).isEqualTo(new LoginResponse.User("user-1", "member1"));
        assertThat(response.tokens()).isEqualTo(new LoginResponse.Tokens("access-token", "id-token", "refresh-token", 3600));
    }

    @Test
    void loginReportsMissingLocalUserAsAccountSetupProblem() {
        LoginService loginService = new LoginService(loginRepository, objectMapper, cognitoAuthService);
        CognitoAuthService.AuthTokens authTokens = new CognitoAuthService.AuthTokens("access-token", "id-token", "refresh-token", 3600);
        when(cognitoAuthService.login("member1", "Password123!")).thenReturn(authTokens);
        when(loginRepository.findByUsername("member1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login("member1", "Password123!"))
                .isInstanceOf(LoginFailureException.class)
                .hasMessage("계정 정보가 아직 준비되지 않았습니다. 잠시 후 다시 시도해주세요.");
    }
}
