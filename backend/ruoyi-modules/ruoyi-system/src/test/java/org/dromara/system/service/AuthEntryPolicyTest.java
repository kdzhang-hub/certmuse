package org.dromara.system.service;

import org.dromara.common.core.enums.EntryType;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.domain.vo.SysRoleVo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthEntryPolicyTest {

    private final AuthEntryPolicy policy = new AuthEntryPolicy();

    @Test
    void resolvesAdminWhenEntryPermissionExists() {
        EntryType entryType = policy.resolve(
            List.of(role("admin", "ADMIN")),
            Set.of(AuthEntryPolicy.ADMIN_ENTRY_PERMISSION));

        assertEquals(EntryType.ADMIN, entryType);
    }

    @Test
    void resolvesSuperAdminWhenWildcardPermissionExists() {
        EntryType entryType = policy.resolve(
            List.of(role("superadmin", "ADMIN")),
            Set.of("*:*:*"));

        assertEquals(EntryType.ADMIN, entryType);
    }

    @Test
    void resolvesLearningOnlyWithStudentRoleAndPermission() {
        EntryType entryType = policy.resolve(
            List.of(role(AuthEntryPolicy.STUDENT_ROLE_KEY, "LEARNING")),
            Set.of(AuthEntryPolicy.STUDENT_ENTRY_PERMISSION));

        assertEquals(EntryType.LEARNING, entryType);
    }

    @Test
    void rejectsMixedEntries() {
        assertThrows(ServiceException.class, () -> policy.resolve(
            List.of(role("admin", "ADMIN"), role(AuthEntryPolicy.STUDENT_ROLE_KEY, "LEARNING")),
            Set.of(AuthEntryPolicy.ADMIN_ENTRY_PERMISSION, AuthEntryPolicy.STUDENT_ENTRY_PERMISSION)));
    }

    @Test
    void rejectsLearningAccountWithoutStudentEntryPermission() {
        assertThrows(ServiceException.class, () -> policy.resolve(
            List.of(role(AuthEntryPolicy.STUDENT_ROLE_KEY, "LEARNING")),
            Set.of()));
    }

    @Test
    void allowsNoRolesDuringRoleRevocation() {
        assertDoesNotThrow(() -> policy.validateOptional(List.of(), Set.of()));
    }

    private SysRoleVo role(String roleKey, String entryType) {
        SysRoleVo role = new SysRoleVo();
        role.setRoleKey(roleKey);
        role.setEntryType(entryType);
        return role;
    }
}
