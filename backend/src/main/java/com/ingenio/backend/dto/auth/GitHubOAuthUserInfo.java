package com.ingenio.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GitHub OAuth用户信息DTO
 * 对应GitHub User API响应结构
 *
 * @author Ingenio Team
 * @since 1.0.0
 * @see <a href="https://api.github.com/user">GitHub User API</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubOAuthUserInfo {

    /**
     * GitHub用户唯一标识
     */
    @JsonProperty("id")
    private Long id;

    /**
     * GitHub用户名（login）
     */
    @JsonProperty("login")
    private String login;

    /**
     * 用户显示名称
     */
    @JsonProperty("name")
    private String name;

    /**
     * 用户邮箱
     */
    @JsonProperty("email")
    private String email;

    /**
     * 用户头像URL
     */
    @JsonProperty("avatar_url")
    private String avatarUrl;

    /**
     * 个人简介
     */
    @JsonProperty("bio")
    private String bio;

    /**
     * 公司/组织
     */
    @JsonProperty("company")
    private String company;

    /**
     * 所在地
     */
    @JsonProperty("location")
    private String location;

    /**
     * 个人网站
     */
    @JsonProperty("blog")
    private String blog;

    /**
     * GitHub个人主页URL
     */
    @JsonProperty("html_url")
    private String htmlUrl;
}
