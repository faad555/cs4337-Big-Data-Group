package cs4337.group_8.AuthenticationMicroservice.services;

import com.auth0.jwt.exceptions.JWTDecodeException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cs4337.group_8.AuthenticationMicroservice.POJOs.GoogleAuthorizationResponse;
import cs4337.group_8.AuthenticationMicroservice.POJOs.GoogleUserDetails;
import cs4337.group_8.AuthenticationMicroservice.entities.TokenEntity;
import cs4337.group_8.AuthenticationMicroservice.exceptions.AuthenticationException;
import cs4337.group_8.AuthenticationMicroservice.exceptions.ValidateTokenException;
import cs4337.group_8.AuthenticationMicroservice.repositories.TokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.sql.Timestamp;
import java.util.Date;

@Service
@Slf4j
public class GoogleAuthService {

    private final String clientSecret;
    private final String clientId;
    private final TokenRepository tokenRepository;
    private static final long SECOND_IN_MILLISECONDS = 1000;

    public GoogleAuthService(@Value("${CLIENT_SECRET}") String clientSecret,
                             @Value("${CLIENT_ID}") String clientId,
                             TokenRepository tokenRepository) {
        this.clientSecret = clientSecret;
        this.clientId = clientId;
        this.tokenRepository = tokenRepository;
    }

    public String refreshAccessToken(String refreshToken, int userId) throws AuthenticationException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED); // The content type will contain the form data in the URL

        MultiValueMap<String, String> params = getPayloadForGoogleRefresh(refreshToken);
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<String> responseFromGoogle = requestRefreshToken(requestEntity);

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(responseFromGoogle.getBody());
            GoogleAuthorizationResponse tokenDetails = objectMapper.treeToValue(jsonNode, GoogleAuthorizationResponse.class);

            TokenEntity tokenEntity = tokenRepository.getTokenEntityByUserId(userId)
                    .orElseThrow(() -> new AuthenticationException("No token found for user, please login again"));
            tokenEntity.setCurrentAccessToken(tokenDetails.getAccess_token());
            tokenEntity.setExpirationTimeAccessToken(new Timestamp(System.currentTimeMillis() + (SECOND_IN_MILLISECONDS * tokenDetails.getExpires_in())));
            tokenRepository.save(tokenEntity);

            return tokenDetails.getAccess_token();
        } catch (JsonProcessingException e) {
            throw new AuthenticationException("Failed to refresh access token");
        }
    }

    public MultiValueMap<String, String> getPayloadForGoogleRefresh(String refreshToken) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", this.clientId);
        params.add("client_secret", this.clientSecret);
        params.add("refresh_token", refreshToken);
        params.add("grant_type", "refresh_token");
        return params;
    }

    public ResponseEntity<String> requestRefreshToken(HttpEntity<MultiValueMap<String, String>> requestPayload) {
        RestTemplate restTemplate = new RestTemplate();
        String googleRefreshTokenURL = "https://oauth2.googleapis.com/token";
        return restTemplate.postForEntity(googleRefreshTokenURL, requestPayload, String.class);
    }

    public boolean isRefreshTokenExpiredForAccessToken(String accessToken) {
        try {
            TokenEntity tokenEntity = tokenRepository.getTokenEntityByCurrentAccessToken(accessToken)
                    .orElseThrow(() -> new ValidateTokenException("No token found, please login again"));

            Date expirationTime = tokenEntity.getExpirationTimeRefreshToken();
            return expirationTime.before(new Date());
        } catch (JWTDecodeException e) {
            log.error("Invalid Access token provided: " + e.getMessage());
            return true;
        }
    }

    public GoogleUserDetails getGoogleProfileDetails(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setBearerAuth(accessToken);

        HttpEntity<String> requestEntity = new HttpEntity<>(httpHeaders);

        String url = "https://www.googleapis.com/oauth2/v2/userinfo";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            GoogleUserDetails authenticatedUser = objectMapper.treeToValue(jsonNode, GoogleUserDetails.class);
            return authenticatedUser;
        } catch (JsonProcessingException e) {
            throw new AuthenticationException("Failed to parse provided user details");
        }
    }

    public GoogleAuthorizationResponse getOauthInformationFromGrantCode(String grantCode) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        setInformationParameters(grantCode, params);


        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);

        try {
            String url = "https://oauth2.googleapis.com/token";
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            GoogleAuthorizationResponse userTokenDetails = objectMapper.treeToValue(jsonNode, GoogleAuthorizationResponse.class);
            return userTokenDetails;
        } catch (JsonProcessingException e) {
            throw new AuthenticationException("Failed to get access token");
        } catch (HttpClientErrorException e) {
            throw new AuthenticationException("Invalid grant code");
        }

    }

    private void setInformationParameters(String code, MultiValueMap<String, String> params) {
        // User authorization code
        params.add("code", code);
        params.add("redirect_uri", "http://localhost:8082/grantcode");
        params.add("client_id", this.clientId);
        params.add("client_secret", this.clientSecret);

        // Information we retrieve
        params.add("scope", "https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fuserinfo.profile");
        params.add("scope", "https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fuserinfo.email");
        params.add("scope", "openid");
        params.add("grant_type", "authorization_code");
    }
}