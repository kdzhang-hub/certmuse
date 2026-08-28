package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.domain.model.LoginBody;
import org.dromara.common.core.enums.PushSourceEnum;
import org.dromara.common.core.enums.PushTypeEnum;
import org.dromara.common.core.enums.EntryType;
import org.dromara.common.core.utils.DateUtils;
import org.dromara.common.core.utils.MessageUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.ValidatorUtils;
import org.dromara.common.encrypt.annotation.ApiEncrypt;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.social.config.properties.SocialLoginConfigProperties;
import org.dromara.common.social.config.properties.SocialProperties;
import org.dromara.common.social.utils.SocialUtils;
import org.dromara.system.api.MessageService;
import org.dromara.system.api.domain.PushPayloadDTO;
import org.dromara.system.api.model.SocialLoginBody;
import org.dromara.system.domain.vo.SysClientVo;
import org.dromara.system.service.ISysClientService;
import org.dromara.system.service.ISysSocialService;
import org.dromara.web.domain.vo.LoginVo;
import org.dromara.web.domain.bo.LearnerRegisterBody;
import org.dromara.web.domain.vo.LearnerRegisterVo;
import org.dromara.web.service.IAuthStrategy;
import org.dromara.web.service.SysLoginService;
import org.dromara.web.service.SysRegisterService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 认证控制器，提供登录、注册、社交绑定和退出能力。
 *
 * @author Lion Li
 */
@Slf4j
@SaIgnore
@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final SocialProperties socialProperties;
    private final SysLoginService loginService;
    private final SysRegisterService registerService;
    private final ISysSocialService socialUserService;
    private final ISysClientService clientService;
    private final ScheduledExecutorService scheduledExecutorService;
    private final MessageService messageService;


    /**
     * 登录方法
     *
     * @param body 登录信息
     * @return 结果
     */
    @ApiEncrypt
    @PostMapping("/login")
    public R<LoginVo> login(@RequestBody String body) {
        rejectLoginIdentityFields(body);
        LoginBody loginBody = JsonUtils.parseObject(body, LoginBody.class);
        ValidatorUtils.validate(loginBody);
        // 授权类型和客户端id
        String clientId = loginBody.getClientId();
        String grantType = loginBody.getGrantType();
        SysClientVo client = clientService.queryByClientId(clientId);
        // 查询不到 client 或 client 内不包含 grantType
        if (ObjectUtil.isNull(client) || !StringUtils.contains(client.getGrantType(), grantType)) {
            log.info("客户端id: {} 认证类型：{} 异常!.", clientId, grantType);
            return R.fail(MessageUtils.message("auth.grant.type.error"));
        } else if (!SystemConstants.NORMAL.equals(client.getStatus())) {
            return R.fail(MessageUtils.message("auth.grant.type.blocked"));
        }
        // 登录
        LoginVo loginVo = IAuthStrategy.login(body, client, grantType);

        Long userId = LoginHelper.getUserId();
        String welcomeMessage = EntryType.LEARNING.name().equals(loginVo.getEntryType())
            ? "欢迎登录 CertMuse 学习平台"
            : "欢迎登录 CertMuse 运营控制台";
        scheduledExecutorService.schedule(() -> {
            messageService.publishMessage(
                List.of(userId),
                PushPayloadDTO.of(
                    PushTypeEnum.MESSAGE,
                    PushSourceEnum.BACKEND,
                    DateUtils.getTodayHour(new Date()) + "好，" + welcomeMessage,
                    null
                )
            );
        }, 5, TimeUnit.SECONDS);
        return R.ok(loginVo);
    }

    /**
     * 获取第三方绑定跳转地址。
     *
     * @param source 登录来源
     * @return 跳转地址
     */
    @GetMapping("/binding/{source}")
    public R<String> authBinding(@PathVariable("source") String source) {
        SocialLoginConfigProperties obj = socialProperties.getType().get(source);
        if (ObjectUtil.isNull(obj)) {
            return R.fail(source + "平台账号暂不支持");
        }
        AuthRequest authRequest = SocialUtils.getAuthRequest(source, socialProperties);
        String authorizeUrl = authRequest.authorize(AuthStateUtils.createState());
        return R.data(authorizeUrl);
    }

    /**
     * 处理前端回调后的社交账号绑定。
     *
     * @param loginBody 请求体
     * @return 操作结果
     */
    @PostMapping("/social/callback")
    public R<Void> socialCallback(@RequestBody SocialLoginBody loginBody) {
        // 校验token
        StpUtil.checkLogin();
        // 获取第三方登录信息
        AuthResponse<AuthUser> response = SocialUtils.loginAuth(
            loginBody.getSource(), loginBody.getSocialCode(),
            loginBody.getSocialState(), socialProperties);
        AuthUser authUserData = response.getData();
        // 判断授权响应是否成功
        if (!response.ok()) {
            return R.fail(response.getMsg());
        }
        loginService.socialRegister(authUserData);
        return R.ok();
    }


    /**
     * 取消当前用户的社交账号授权。
     *
     * @param socialId socialId
     * @return 操作结果
     */
    @DeleteMapping(value = "/unlock/{socialId}")
    public R<Void> unlockSocial(@PathVariable Long socialId) {
        // 校验token
        StpUtil.checkLogin();
        Boolean rows = socialUserService.deleteWithValidById(socialId);
        return rows ? R.ok() : R.fail("取消授权失败");
    }


    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        loginService.logout();
        return R.ok("退出成功");
    }

    /**
     * 用户注册。
     *
     * @param user 注册信息
     * @return 操作结果
     */
    @ApiEncrypt
    @PostMapping("/register")
    public R<LearnerRegisterVo> register(@RequestBody String body) {
        LearnerRegisterBody user = parseLearnerRegisterBody(body);
        ValidatorUtils.validate(user);
        return R.ok(registerService.register(user));
    }

    /** Parses registration using a fixed field allowlist so identity fields cannot be smuggled in. */
    private LearnerRegisterBody parseLearnerRegisterBody(String body) {
        var node = JsonUtils.getJsonMapper().readTree(body);
        Set<String> allowed = Set.of("username", "password", "code", "uuid", "clientId", "grantType");
        if (!node.isObject() || node.propertyStream().anyMatch(entry -> !allowed.contains(entry.getKey()))) {
            throw new ServiceException("注册请求包含不允许的字段");
        }
        return JsonUtils.parseObject(body, LearnerRegisterBody.class);
    }

    /** Rejects client-supplied identity, role and permission claims before authentication. */
    private void rejectLoginIdentityFields(String body) {
        var node = JsonUtils.getJsonMapper().readTree(body);
        if (!node.isObject() || node.propertyStream().anyMatch(entry -> {
            String field = entry.getKey().toLowerCase();
            return field.equals("usertype") || field.equals("entrytype") || field.equals("userid")
                || field.equals("roleid") || field.equals("roleids") || field.contains("permission") || field.equals("perms");
        })) {
            throw new ServiceException("登录请求包含不允许的身份字段");
        }
    }

}
