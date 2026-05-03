package com.teamuta.loginservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.util.Map;

@Service
public class CognitoAuthService {
    private final CognitoIdentityProviderClient cognitoClient;
    private final String userPoolId;
    private final String clientId;

    @Autowired
    public CognitoAuthService(
            @Value("${cognito.region}") String region,
            @Value("${cognito.user-pool-id}") String userPoolId,
            @Value("${cognito.client-id}") String clientId
    ) {
        this(CognitoIdentityProviderClient.builder().region(Region.of(region)).build(), userPoolId, clientId);
    }

    CognitoAuthService(CognitoIdentityProviderClient cognitoClient, String userPoolId, String clientId) {
        this.cognitoClient = cognitoClient;
        this.userPoolId = userPoolId;
        this.clientId = clientId;
    }

    public String createUser(String username, String password) {
        assertConfigured();
        AdminCreateUserResponse response = cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .temporaryPassword(password)
                .messageAction(MessageActionType.SUPPRESS)
                .build());
        cognitoClient.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .password(password)
                .permanent(true)
                .build());
        String sub = response.user().attributes().stream()
                .filter(attribute -> "sub".equals(attribute.name()))
                .map(attribute -> attribute.value())
                .findFirst()
                .orElseGet(() -> cognitoClient.adminGetUser(AdminGetUserRequest.builder()
                                .userPoolId(userPoolId)
                                .username(username)
                                .build())
                        .userAttributes()
                        .stream()
                        .filter(attribute -> "sub".equals(attribute.name()))
                        .map(attribute -> attribute.value())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Cognito user sub was not returned")));
        return sub;
    }

    public void deleteUser(String username) {
        assertConfigured();
        cognitoClient.adminDeleteUser(AdminDeleteUserRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .build());
    }

    public AuthTokens login(String username, String password) {
        assertConfigured();
        AuthenticationResultType result;
        try {
            result = cognitoClient.adminInitiateAuth(AdminInitiateAuthRequest.builder()
                    .userPoolId(userPoolId)
                    .clientId(clientId)
                    .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                    .authParameters(Map.of(
                            "USERNAME", username,
                            "PASSWORD", password
                    ))
                    .build()).authenticationResult();
        } catch (NotAuthorizedException | UserNotFoundException exception) {
            throw new LoginService.LoginFailureException("가입되지 않았거나 비밀번호가 올바르지 않습니다.", exception);
        }

        return new AuthTokens(result.accessToken(), result.idToken(), result.refreshToken(), result.expiresIn());
    }

    private void assertConfigured() {
        if (userPoolId == null || userPoolId.isBlank() || clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Cognito user pool and client id must be configured");
        }
    }

    public record AuthTokens(String accessToken, String idToken, String refreshToken, Integer expiresIn) {
    }
}
