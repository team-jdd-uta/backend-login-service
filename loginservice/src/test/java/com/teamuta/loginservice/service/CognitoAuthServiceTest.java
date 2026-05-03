package com.teamuta.loginservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.teamuta.loginservice.service.LoginService.LoginFailureException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import org.mockito.ArgumentCaptor;

class CognitoAuthServiceTest {

    @Test
    void loginMapsInvalidCredentialsToUserFacingFailure() {
        CognitoIdentityProviderClient cognitoClient = mock(CognitoIdentityProviderClient.class);
        CognitoAuthService cognitoAuthService = new CognitoAuthService(cognitoClient, "pool-id", "client-id");
        when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                .thenThrow(NotAuthorizedException.builder().message("Incorrect username or password.").build());

        assertThatThrownBy(() -> cognitoAuthService.login("missing@example.com", "Password123"))
                .isInstanceOf(LoginFailureException.class)
                .hasMessage("가입되지 않았거나 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void loginMapsMissingUserToUserFacingFailure() {
        CognitoIdentityProviderClient cognitoClient = mock(CognitoIdentityProviderClient.class);
        CognitoAuthService cognitoAuthService = new CognitoAuthService(cognitoClient, "pool-id", "client-id");
        when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("User does not exist.").build());

        assertThatThrownBy(() -> cognitoAuthService.login("missing@example.com", "Password123"))
                .isInstanceOf(LoginFailureException.class)
                .hasMessage("가입되지 않았거나 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void refreshUsesRefreshTokenAuthFlowAndKeepsExistingRefreshToken() {
        CognitoIdentityProviderClient cognitoClient = mock(CognitoIdentityProviderClient.class);
        CognitoAuthService cognitoAuthService = new CognitoAuthService(cognitoClient, "pool-id", "client-id");
        when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                .thenReturn(AdminInitiateAuthResponse.builder()
                        .authenticationResult(AuthenticationResultType.builder()
                                .accessToken("new-access-token")
                                .idToken("new-id-token")
                                .expiresIn(3600)
                                .build())
                        .build());

        CognitoAuthService.AuthTokens tokens = cognitoAuthService.refresh("refresh-token");

        ArgumentCaptor<AdminInitiateAuthRequest> requestCaptor = forClass(AdminInitiateAuthRequest.class);
        verify(cognitoClient).adminInitiateAuth(requestCaptor.capture());
        assertThat(requestCaptor.getValue().authFlow()).isEqualTo(AuthFlowType.REFRESH_TOKEN_AUTH);
        assertThat(requestCaptor.getValue().authParameters()).containsEntry("REFRESH_TOKEN", "refresh-token");
        assertThat(tokens).isEqualTo(new CognitoAuthService.AuthTokens("new-access-token", "new-id-token", "refresh-token", 3600));
    }
}
