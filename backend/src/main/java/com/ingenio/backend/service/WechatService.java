package com.ingenio.backend.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.auth.WxCallbackRequest;
import com.ingenio.backend.dto.auth.WxQrcodeResponse;
import com.ingenio.backend.dto.auth.WxScanStatusResponse;
import com.ingenio.backend.dto.auth.WxUserInfo;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.entity.WxUserBindingEntity;
import com.ingenio.backend.mapper.UserMapper;
import com.ingenio.backend.mapper.WxUserBindingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 微信登录服务
 * 提供微信扫码登录功能的核心业务逻辑
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatService {

    private final RedissonClient redissonClient;
    private final WxUserBindingMapper wxUserBindingMapper;
    private final UserMapper userMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${sa-token.timeout}")
    private Long tokenTimeout;

    @Value("${ingenio.tenant.default-tenant-id}")
    private String defaultTenantId;

    @Value("${wechat.appid}")
    private String wechatAppId;

    @Value("${wechat.secret}")
    private String wechatSecret;

    @Value("${wechat.redirect-uri}")
    private String wechatRedirectUri;

    // Redis键前缀
    private static final String REDIS_KEY_PREFIX = "wx:qrcode:";
    private static final String REDIS_KEY_USER_ID_SUFFIX = ":userId";

    // 二维码过期时间（秒）
    private static final int QRCODE_EXPIRES_IN = 1800; // 30分钟

    // 微信API URL
    private static final String WX_QRCODE_API = "https://open.weixin.qq.com/connect/qrconnect";
    private static final String WX_ACCESS_TOKEN_API = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private static final String WX_USER_INFO_API = "https://api.weixin.qq.com/sns/userinfo";

    /**
     * 生成微信登录二维码
     *
     * @return 二维码响应DTO
     */
    public WxQrcodeResponse generateWechatQrcode() {
        // 1. 生成唯一场景值（UUID）
        String sceneStr = "ingenio-login-" + UUID.randomUUID();

        // 2. 生成微信登录二维码URL
        String qrcodeUrl = String.format(
                "%s?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_login&state=%s#wechat_redirect",
                WX_QRCODE_API,
                wechatAppId,
                wechatRedirectUri,
                sceneStr
        );

        // 3. 在Redis中初始化状态为pending
        String redisKey = REDIS_KEY_PREFIX + sceneStr;
        RBucket<String> bucket = redissonClient.getBucket(redisKey);
        bucket.set(WxScanStatusResponse.Status.PENDING.getValue(), Duration.ofSeconds(QRCODE_EXPIRES_IN));

        log.info("生成微信登录二维码成功: sceneStr={}, expiresIn={}秒", sceneStr, QRCODE_EXPIRES_IN);

        return WxQrcodeResponse.builder()
                .qrcodeUrl(qrcodeUrl)
                .sceneStr(sceneStr)
                .expiresIn(QRCODE_EXPIRES_IN)
                .build();
    }

    /**
     * 检查微信扫码状态（轮询接口）
     *
     * @param sceneStr 场景值
     * @return 扫码状态响应DTO
     */
    public WxScanStatusResponse checkWechatScanStatus(String sceneStr) {
        String redisKey = REDIS_KEY_PREFIX + sceneStr;
        RBucket<String> bucket = redissonClient.getBucket(redisKey);

        // 获取状态
        String status = bucket.get();

        // 场景值无效或已过期
        if (status == null) {
            log.warn("场景值无效或已过期: sceneStr={}", sceneStr);
            return WxScanStatusResponse.builder()
                    .status(WxScanStatusResponse.Status.EXPIRED.getValue())
                    .build();
        }

        // 如果状态为confirmed，返回Token和用户信息
        if (WxScanStatusResponse.Status.CONFIRMED.getValue().equals(status)) {
            String userIdKey = redisKey + REDIS_KEY_USER_ID_SUFFIX;
            RBucket<String> userIdBucket = redissonClient.getBucket(userIdKey);
            String userId = userIdBucket.get();

            if (userId == null) {
                log.error("用户ID不存在: sceneStr={}", sceneStr);
                throw new RuntimeException("登录失败：用户信息丢失");
            }

            // 生成Token
            StpUtil.login(userId);
            String token = StpUtil.getTokenValue();

            // 获取用户信息
            UserEntity user = userMapper.selectById(UUID.fromString(userId));
            if (user == null) {
                log.error("用户不存在: userId={}", userId);
                throw new RuntimeException("用户不存在");
            }

            // 删除Redis中的临时数据
            bucket.delete();
            userIdBucket.delete();

            log.info("微信登录成功: userId={}, username={}", userId, user.getUsername());

            return WxScanStatusResponse.builder()
                    .status(WxScanStatusResponse.Status.CONFIRMED.getValue())
                    .token(token)
                    .userInfo(WxScanStatusResponse.UserInfo.builder()
                            .id(user.getId().toString())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .avatar(user.getAvatarUrl())
                            .build())
                    .build();
        }

        // 返回当前状态
        return WxScanStatusResponse.builder()
                .status(status)
                .build();
    }

    /**
     * 处理微信回调（内部接口）
     *
     * @param request 回调请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleWechatCallback(WxCallbackRequest request) {
        String code = request.getCode();
        String state = request.getState();

        log.info("处理微信回调: code={}, state={}", code, state);

        try {
            // 1. 验证state（防CSRF攻击）
            String redisKey = REDIS_KEY_PREFIX + state;
            RBucket<String> bucket = redissonClient.getBucket(redisKey);
            String status = bucket.get();

            if (status == null) {
                log.error("场景值无效: state={}", state);
                throw new RuntimeException("场景值无效或已过期");
            }

            // 2. 使用code换取access_token
            String accessTokenUrl = String.format(
                    "%s?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
                    WX_ACCESS_TOKEN_API,
                    wechatAppId,
                    wechatSecret,
                    code
            );

            ResponseEntity<String> tokenResponse = restTemplate.getForEntity(accessTokenUrl, String.class);
            JsonNode tokenJson = objectMapper.readTree(tokenResponse.getBody());

            String accessToken = tokenJson.get("access_token").asText();
            String openid = tokenJson.get("openid").asText();

            log.info("获取access_token成功: openid={}", openid);

            // 3. 使用access_token获取用户信息
            String userInfoUrl = String.format(
                    "%s?access_token=%s&openid=%s&lang=zh_CN",
                    WX_USER_INFO_API,
                    accessToken,
                    openid
            );

            ResponseEntity<String> userInfoResponse = restTemplate.getForEntity(userInfoUrl, String.class);
            JsonNode userInfoJson = objectMapper.readTree(userInfoResponse.getBody());

            WxUserInfo wxUserInfo = objectMapper.treeToValue(userInfoJson, WxUserInfo.class);

            log.info("获取微信用户信息成功: openid={}, nickname={}", wxUserInfo.getOpenid(), wxUserInfo.getNickname());

            // 4. 查找或创建用户
            UserEntity user = findOrCreateUser(wxUserInfo);

            // 5. 更新Redis状态为confirmed，并存储userId
            bucket.set(WxScanStatusResponse.Status.CONFIRMED.getValue(), Duration.ofSeconds(60));
            RBucket<String> userIdBucket = redissonClient.getBucket(redisKey + REDIS_KEY_USER_ID_SUFFIX);
            userIdBucket.set(user.getId().toString(), Duration.ofSeconds(60));

            log.info("微信回调处理成功: userId={}, username={}", user.getId(), user.getUsername());

        } catch (Exception e) {
            log.error("处理微信回调失败: code={}, state={}", code, state, e);
            throw new RuntimeException("微信登录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据微信用户信息查找或创建用户
     *
     * @param wxUserInfo 微信用户信息
     * @return 用户实体
     */
    @Transactional(rollbackFor = Exception.class)
    public UserEntity findOrCreateUser(WxUserInfo wxUserInfo) {
        // 1. 查找是否已有绑定记录
        Optional<WxUserBindingEntity> bindingOpt = wxUserBindingMapper.findByWxOpenid(wxUserInfo.getOpenid());

        if (bindingOpt.isPresent()) {
            // 已绑定，返回用户并更新登录信息
            WxUserBindingEntity binding = bindingOpt.get();
            wxUserBindingMapper.updateLastLoginTime(wxUserInfo.getOpenid(), Instant.now());

            log.info("用户已绑定微信: userId={}, openid={}", binding.getUserId(), wxUserInfo.getOpenid());

            UserEntity user = userMapper.selectById(binding.getUserId());
            if (user == null) {
                throw new RuntimeException("绑定的用户不存在");
            }
            return user;
        }

        // 2. 未绑定，创建新用户
        UserEntity newUser = UserEntity.builder()
                .tenantId(UUID.fromString(defaultTenantId))
                .username("wx_" + wxUserInfo.getOpenid().substring(0, 10)) // 使用openid前10位作为用户名
                .email("wx_" + wxUserInfo.getOpenid() + "@ingenio.ai") // 生成临时邮箱
                .displayName(wxUserInfo.getNickname())
                .avatarUrl(wxUserInfo.getHeadimgurl())
                .role(UserEntity.Role.USER.getValue())
                .status(UserEntity.Status.ACTIVE.getValue())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        int inserted = userMapper.insert(newUser);
        if (inserted == 0) {
            throw new RuntimeException("创建用户失败");
        }

        log.info("创建新用户成功: userId={}, username={}", newUser.getId(), newUser.getUsername());

        // 3. 创建绑定记录
        WxUserBindingEntity binding = WxUserBindingEntity.builder()
                .userId(newUser.getId())
                .wxOpenid(wxUserInfo.getOpenid())
                .wxUnionid(wxUserInfo.getUnionid())
                .wxNickname(wxUserInfo.getNickname())
                .wxAvatarUrl(wxUserInfo.getHeadimgurl())
                .wxCountry(wxUserInfo.getCountry())
                .wxProvince(wxUserInfo.getProvince())
                .wxCity(wxUserInfo.getCity())
                .wxSex(wxUserInfo.getSex())
                .wxLanguage(wxUserInfo.getLanguage())
                .bindTime(Instant.now())
                .lastLoginTime(Instant.now())
                .loginCount(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        int bindingInserted = wxUserBindingMapper.insert(binding);
        if (bindingInserted == 0) {
            throw new RuntimeException("创建微信绑定记录失败");
        }

        log.info("创建微信绑定记录成功: userId={}, openid={}", newUser.getId(), wxUserInfo.getOpenid());

        return newUser;
    }
}
