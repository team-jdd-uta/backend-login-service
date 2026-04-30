package com.teamuta.loginservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.teamuta.loginservice.service.LoginService.LoginFailureException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

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
}
