package org.dromara.system.service;

import cn.hutool.core.collection.CollUtil;
import org.dromara.common.core.enums.EntryType;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.domain.vo.SysRoleVo;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Applies the entry invariants shared by login and role maintenance.
 */
@Component
public class AuthEntryPolicy {

    public static final String STUDENT_ROLE_KEY = "student";
    public static final String STUDENT_ENTRY_PERMISSION = "certmuse:student";
    public static final String ADMIN_ENTRY_PERMISSION = "certmuse:entry:admin";
    private static final String ALL_PERMISSION = "*:*:*";

    /**
     * Resolves a required entry from enabled roles and their effective permissions.
     *
     * @param roles enabled roles for one account
     * @param permissions effective menu permissions for one account
     * @return the only valid entry type
     */
    public EntryType resolve(List<SysRoleVo> roles, Set<String> permissions) {
        if (CollUtil.isEmpty(roles)) {
            throw invalidConfiguration();
        }
        EnumSet<EntryType> entryTypes = EnumSet.noneOf(EntryType.class);
        for (SysRoleVo role : roles) {
            try {
                entryTypes.add(EntryType.valueOf(role.getEntryType()));
            } catch (Exception ignored) {
                throw invalidConfiguration();
            }
        }
        if (entryTypes.size() != 1) {
            throw invalidConfiguration();
        }
        EntryType entryType = entryTypes.iterator().next();
        if (entryType == EntryType.LEARNING) {
            boolean hasStudentRole = roles.stream().anyMatch(role -> STUDENT_ROLE_KEY.equals(role.getRoleKey()));
            if (!hasStudentRole || !permissions.contains(STUDENT_ENTRY_PERMISSION)) {
                throw invalidConfiguration();
            }
        } else if (!permissions.contains(ADMIN_ENTRY_PERMISSION) && !permissions.contains(ALL_PERMISSION)) {
            throw invalidConfiguration();
        }
        return entryType;
    }

    /**
     * Validates an account after role maintenance. Accounts with no enabled roles remain disabled by configuration.
     *
     * @param roles enabled roles for one account
     * @param permissions effective menu permissions for one account
     */
    public void validateOptional(List<SysRoleVo> roles, Set<String> permissions) {
        if (CollUtil.isNotEmpty(roles)) {
            resolve(roles, permissions);
        }
    }

    private ServiceException invalidConfiguration() {
        return new ServiceException("账号入口配置无效");
    }
}
