package org.dromara.web.service;

import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.Constants;
import org.dromara.common.core.constant.GlobalConstants;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.core.exception.user.CaptchaException;
import org.dromara.common.core.exception.user.CaptchaExpireException;
import org.dromara.common.core.exception.user.UserException;
import org.dromara.common.core.utils.MessageUtils;
import org.dromara.common.core.utils.ServletUtils;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.log.event.LoginInfoEvent;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.web.config.properties.CaptchaProperties;
import org.dromara.system.domain.vo.SysClientVo;
import org.dromara.system.domain.vo.SysRoleVo;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.SysUserRole;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.mapper.SysUserRoleMapper;
import org.dromara.system.mapper.SysMenuMapper;
import org.dromara.system.mapper.SysRoleMapper;
import org.dromara.system.service.AuthEntryPolicy;
import org.dromara.system.service.ISysClientService;
import org.dromara.system.service.ISysConfigService;
import org.dromara.web.domain.bo.LearnerRegisterBody;
import org.dromara.web.domain.vo.LearnerRegisterVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;

/**
 * 注册校验方法
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysRegisterService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final ISysClientService clientService;
    private final ISysConfigService configService;
    private final CaptchaProperties captchaProperties;

    /**
     * 注册
     *
     * @param registerBody 注册请求参数
     */
    @Transactional(rollbackFor = Exception.class)
    public LearnerRegisterVo register(LearnerRegisterBody registerBody) {
        String username = registerBody.getUsername();
        String password = registerBody.getPassword();
        validateRegistrationAllowed(registerBody);

        boolean captchaEnabled = captchaProperties.getEnable();
        // 验证码开关
        if (captchaEnabled) {
            validateCaptcha(username, registerBody.getCode(), registerBody.getUuid());
        }
        SysUser sysUser = new SysUser();
        sysUser.setUserName(username);
        sysUser.setNickName(username);
        sysUser.setPassword(BCrypt.hashpw(password));
        sysUser.setUserType(UserType.SYS_USER.getUserType());
        sysUser.setCreateBy(0L);
        sysUser.setUpdateBy(0L);

        boolean exist = userMapper.lambda()
            .eq(SysUser::getUserName, sysUser.getUserName())
            .exists();
        if (exist) {
            throw new UserException("user.register.save.error", username);
        }
        SysRoleVo studentRole = roleMapper.lambda()
            .eq(org.dromara.system.domain.SysRole::getRoleKey, AuthEntryPolicy.STUDENT_ROLE_KEY)
            .voOne();
        if (studentRole == null) {
            throw new UserException("user.register.error");
        }
        if (!SystemConstants.NORMAL.equals(studentRole.getStatus())
            || !"LEARNING".equals(studentRole.getEntryType())
            || !menuMapper.selectMenuPermsByRoleId(studentRole.getRoleId()).contains(AuthEntryPolicy.STUDENT_ENTRY_PERMISSION)) {
            throw new UserException("user.register.error");
        }
        try {
            if (userMapper.insert(sysUser) != 1) {
                throw new UserException("user.register.error");
            }
        } catch (DuplicateKeyException e) {
            throw new UserException("user.register.save.error", username);
        }
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(sysUser.getUserId());
        userRole.setRoleId(studentRole.getRoleId());
        if (userRoleMapper.insert(userRole) != 1) {
            throw new UserException("user.register.error");
        }
        recordLoginInfoAfterCommit(username, Constants.REGISTER, MessageUtils.message("user.register.success"));
        return new LearnerRegisterVo(username, AuthEntryPolicy.STUDENT_ROLE_KEY);
    }

    /** Verifies global switch, password client and the explicit registration whitelist. */
    private void validateRegistrationAllowed(LearnerRegisterBody body) {
        if (!configService.selectRegisterEnabled() || !"password".equals(body.getGrantType())) {
            throw new UserException("user.register.error");
        }
        SysClientVo client = clientService.queryByClientId(body.getClientId());
        boolean passwordGrant = client != null && client.getGrantTypeList() != null
            && client.getGrantTypeList().contains("password");
        String allowedClients = configService.selectConfigByKey("sys.account.registerClientIds");
        boolean whitelisted = Arrays.stream(StringUtils.blankToDefault(allowedClients, "").split("[,;\\r\\n]+"))
            .map(String::trim)
            .anyMatch(body.getClientId()::equals);
        if (!passwordGrant || !SystemConstants.NORMAL.equals(client.getStatus()) || !whitelisted) {
            throw new UserException("user.register.error");
        }
    }

    /**
     * 校验验证码
     *
     * @param username 用户名
     * @param code     验证码
     * @param uuid     唯一标识
     */
    public void validateCaptcha(String username, String code, String uuid) {
        String verifyKey = GlobalConstants.CAPTCHA_CODE_KEY + StringUtils.blankToDefault(uuid, "");
        String captcha = RedisUtils.getCacheObject(verifyKey);
        RedisUtils.deleteObject(verifyKey);
        if (captcha == null) {
            recordLoginInfo(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire"));
            throw new CaptchaExpireException();
        }
        if (!StringUtils.equalsIgnoreCase(code, captcha)) {
            recordLoginInfo(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error"));
            throw new CaptchaException();
        }
    }

    /**
     * 记录登录信息
     *
     * @param username 用户名
     * @param status   状态
     * @param message  消息内容
     */
    private void recordLoginInfo(String username, String status, String message) {
        LoginInfoEvent loginInfoEvent = new LoginInfoEvent();
        loginInfoEvent.setUsername(username);
        loginInfoEvent.setStatus(status);
        loginInfoEvent.setMessage(message);
        loginInfoEvent.setRequest(ServletUtils.getRequest());
        SpringUtils.context().publishEvent(loginInfoEvent);
    }

    /** Publishes a successful registration audit event only after the user transaction commits. */
    private void recordLoginInfoAfterCommit(String username, String status, String message) {
        Runnable publish = () -> recordLoginInfo(username, status, message);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

}
