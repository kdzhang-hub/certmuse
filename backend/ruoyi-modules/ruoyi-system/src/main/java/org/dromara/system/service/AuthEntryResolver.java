package org.dromara.system.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.enums.EntryType;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.domain.vo.SysRoleVo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** Resolves the only permitted application entry from enabled roles. */
@Service
@RequiredArgsConstructor
public class AuthEntryResolver {

    private final ISysRoleService roleService;
    private final ISysPermissionService permissionService;
    private final AuthEntryPolicy entryPolicy;

    /**
     * Resolves the entry and verifies its required role and permission.
     *
     * @param userId user identifier
     * @return resolved entry type
     */
    public EntryType resolve(Long userId) {
        List<SysRoleVo> roles = roleService.selectRolesByUserId(userId).stream()
            .filter(role -> SystemConstants.NORMAL.equals(role.getStatus()))
            .toList();
        Set<String> permissions = permissionService.getMenuPermission(userId);
        return entryPolicy.resolve(roles, permissions);
    }

    /**
     * Verifies that the supplied enabled roles belong to one entry type.
     *
     * @param roleIds roles to be assigned to one account
     */
    public void validateSingleEntry(Set<Long> roleIds) {
        List<SysRoleVo> roles = roleService.selectRoleByIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new ServiceException("角色不存在、已停用或无权访问");
        }
        Set<String> entryTypes = roles.stream().map(SysRoleVo::getEntryType).collect(java.util.stream.Collectors.toSet());
        if (entryTypes.size() != 1 || entryTypes.contains(null)) {
            throw new ServiceException("一个账号只能分配同一入口类型的角色");
        }
    }
}
