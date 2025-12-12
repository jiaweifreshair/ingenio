package com.ingenio.backend.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.auth.AuthResponse;
import com.ingenio.backend.dto.auth.GoogleOAuthUserInfo;
import com.ingenio.backend.dto.auth.GitHubOAuthUserInfo;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * OAuth统一服务
 * 提供Google和GitHub OAuth登录功能
 *
 * <p>参考WechatService的成熟模式，统一OAuth流程：</p>
 * <ol>
 *   <li>使用code换取access_token</li>
 *   <li>使用access_token获取用户信息</li>
 *   <li>查找或创建系统用户</li>
 *   <li>生成SaToken并返回</li>
 * </ol>
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final UserMapper userMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${sa-token.timeout}")
    private Long tokenTimeout;

    @Value("${ingenio.tenant.default-tenant-id}")
    private String defaultTenantId;

    // Google OAuth配置
    @Value("${oauth.google.client-id}")
    private String googleClientId;

    @Value("${oauth.google.client-secret}")
    private String googleClientSecret;

    @Value("${oauth.google.redirect-uri}")
    private String googleRedirectUri;

    // GitHub OAuth配置
    @Value("${oauth.github.client-id}")
    private String githubClientId;

    @Value("${oauth.github.client-secret}")
    private String githubClientSecret;

    @Value("${oauth.github.redirect-uri}")
    private String githubRedirectUri;

    // Google API URLs
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    // GitHub API URLs
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USER_URL = "https://api.github.com/user";

    /**
     * Google OAuth登录
     *
     * @param code Google授权码
     * @return 认证响应（包含Token和用户信息）
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse loginWithGoogle(String code) {
        log.info("开始Google OAuth登录: code={}", code);

        try {
            // 1. 使用code换取access_token
            String accessToken = exchangeGoogleAccessToken(code);
            log.info("获取Google access_token成功");

            // 2. 使用access_token获取用户信息
            GoogleOAuthUserInfo googleUser = fetchGoogleUserInfo(accessToken);
            log.info("获取Google用户信息成功: email={}, name={}", googleUser.getEmail(), googleUser.getName());

            // 3. 查找或创建用户
            UserEntity user = findOrCreateUserByOAuth(
                    "google",
                    googleUser.getId(),
                    googleUser.getEmail(),
                    googleUser.getName(),
                    googleUser.getPicture()
            );

            // 4. 生成SaToken
            StpUtil.login(user.getId().toString());
            String token = StpUtil.getTokenValue();

            log.info("Google OAuth登录成功: userId={}, username={}", user.getId(), user.getUsername());

            AuthResponse response = new AuthResponse();
            response.setToken(token);
            response.setUserId(user.getId().toString());
            response.setUsername(user.getUsername());
            response.setEmail(user.getEmail());
            response.setRole(user.getRole());
            response.setExpiresIn(tokenTimeout);
            return response;

        } catch (Exception e) {
            log.error("Google OAuth登录失败: code={}", code, e);
            throw new RuntimeException("Google登录失败: " + e.getMessage(), e);
        }
    }

    /**
     * GitHub OAuth登录
     *
     * @param code GitHub授权码
     * @return 认证响应（包含Token和用户信息）
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse loginWithGitHub(String code) {
        log.info("开始GitHub OAuth登录: code={}", code);

        try {
            // 1. 使用code换取access_token
            String accessToken = exchangeGitHubAccessToken(code);
            log.info("获取GitHub access_token成功");

            // 2. 使用access_token获取用户信息
            GitHubOAuthUserInfo githubUser = fetchGitHubUserInfo(accessToken);
            log.info("获取GitHub用户信息成功: login={}, email={}", githubUser.getLogin(), githubUser.getEmail());

            // 3. 查找或创建用户
            UserEntity user = findOrCreateUserByOAuth(
                    "github",
                    githubUser.getId().toString(),
                    githubUser.getEmail(),
                    githubUser.getName() != null ? githubUser.getName() : githubUser.getLogin(),
                    githubUser.getAvatarUrl()
            );

            // 4. 生成SaToken
            StpUtil.login(user.getId().toString());
            String token = StpUtil.getTokenValue();

            log.info("GitHub OAuth登录成功: userId={}, username={}", user.getId(), user.getUsername());

            AuthResponse response = new AuthResponse();
            response.setToken(token);
            response.setUserId(user.getId().toString());
            response.setUsername(user.getUsername());
            response.setEmail(user.getEmail());
            response.setRole(user.getRole());
            response.setExpiresIn(tokenTimeout);
            return response;

        } catch (Exception e) {
            log.error("GitHub OAuth登录失败: code={}", code, e);
            throw new RuntimeException("GitHub登录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用code换取Google access_token
     *
     * @param code 授权码
     * @return access_token
     */
    private String exchangeGoogleAccessToken(String code) {
        try {
            // 构建请求参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", code);
            params.add("client_id", googleClientId);
            params.add("client_secret", googleClientSecret);
            params.add("redirect_uri", googleRedirectUri);
            params.add("grant_type", "authorization_code");

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            // 发送POST请求
            ResponseEntity<String> response = restTemplate.postForEntity(GOOGLE_TOKEN_URL, request, String.class);

            // 解析响应
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String accessToken = jsonNode.get("access_token").asText();

            log.debug("Google access_token换取成功");
            return accessToken;

        } catch (Exception e) {
            log.error("Google access_token换取失败", e);
            throw new RuntimeException("获取Google访问令牌失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用code换取GitHub access_token
     *
     * @param code 授权码
     * @return access_token
     */
    private String exchangeGitHubAccessToken(String code) {
        try {
            // 构建请求参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", code);
            params.add("client_id", githubClientId);
            params.add("client_secret", githubClientSecret);
            params.add("redirect_uri", githubRedirectUri);

            // 设置请求头（GitHub要求Accept: application/json）
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Accept", "application/json");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            // 发送POST请求
            ResponseEntity<String> response = restTemplate.postForEntity(GITHUB_TOKEN_URL, request, String.class);

            // 解析响应
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String accessToken = jsonNode.get("access_token").asText();

            log.debug("GitHub access_token换取成功");
            return accessToken;

        } catch (Exception e) {
            log.error("GitHub access_token换取失败", e);
            throw new RuntimeException("获取GitHub访问令牌失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用access_token获取Google用户信息
     *
     * @param accessToken 访问令牌
     * @return Google用户信息
     */
    private GoogleOAuthUserInfo fetchGoogleUserInfo(String accessToken) {
        try {
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 发送GET请求
            ResponseEntity<String> response = restTemplate.exchange(
                    GOOGLE_USERINFO_URL,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            // 解析响应
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            GoogleOAuthUserInfo userInfo = objectMapper.treeToValue(jsonNode, GoogleOAuthUserInfo.class);

            log.debug("Google用户信息获取成功: email={}", userInfo.getEmail());
            return userInfo;

        } catch (Exception e) {
            log.error("获取Google用户信息失败", e);
            throw new RuntimeException("获取Google用户信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用access_token获取GitHub用户信息
     *
     * @param accessToken 访问令牌
     * @return GitHub用户信息
     */
    private GitHubOAuthUserInfo fetchGitHubUserInfo(String accessToken) {
        try {
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("Accept", "application/vnd.github.v3+json");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 发送GET请求
            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_USER_URL,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            // 解析响应
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            GitHubOAuthUserInfo userInfo = objectMapper.treeToValue(jsonNode, GitHubOAuthUserInfo.class);

            log.debug("GitHub用户信息获取成功: login={}", userInfo.getLogin());
            return userInfo;

        } catch (Exception e) {
            log.error("获取GitHub用户信息失败", e);
            throw new RuntimeException("获取GitHub用户信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据OAuth信息查找或创建用户
     *
     * <p>逻辑与WechatService.findOrCreateUser类似：</p>
     * <ol>
     *   <li>通过OAuth提供商+OpenID查找绑定记录</li>
     *   <li>如果已绑定，返回用户</li>
     *   <li>如果未绑定，创建新用户</li>
     * </ol>
     *
     * @param provider OAuth提供商（google/github）
     * @param openId OAuth用户唯一标识
     * @param email 用户邮箱
     * @param name 用户姓名
     * @param avatarUrl 头像URL
     * @return 用户实体
     */
    @Transactional(rollbackFor = Exception.class)
    public UserEntity findOrCreateUserByOAuth(String provider, String openId, String email, String name, String avatarUrl) {
        // 1. 查找是否已有该邮箱的用户
        QueryWrapper<UserEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("email", email);
        UserEntity existingUser = userMapper.selectOne(queryWrapper);

        if (existingUser != null) {
            // 已存在该邮箱的用户，直接返回
            log.info("用户已存在: userId={}, email={}", existingUser.getId(), email);
            return existingUser;
        }

        // 2. 创建新用户
        String username = generateOAuthUsername(provider, openId);

        UserEntity newUser = UserEntity.builder()
                .tenantId(UUID.fromString(defaultTenantId))
                .username(username)
                .email(email != null ? email : username + "@ingenio.ai") // 如果没有邮箱，生成临时邮箱
                .displayName(name)
                .avatarUrl(avatarUrl)
                .role(UserEntity.Role.USER.getValue())
                .status(UserEntity.Status.ACTIVE.getValue())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        int inserted = userMapper.insert(newUser);
        if (inserted == 0) {
            throw new RuntimeException("创建用户失败");
        }

        log.info("创建新OAuth用户成功: userId={}, username={}, provider={}",
                newUser.getId(), newUser.getUsername(), provider);

        return newUser;
    }

    /**
     * 生成OAuth用户名
     *
     * @param provider OAuth提供商
     * @param openId OAuth用户唯一标识
     * @return 用户名
     */
    private String generateOAuthUsername(String provider, String openId) {
        // 使用provider前缀 + openId的前10位作为用户名
        String prefix = provider.toLowerCase();
        String idPart = openId.length() > 10 ? openId.substring(0, 10) : openId;
        return prefix + "_" + idPart;
    }
}
