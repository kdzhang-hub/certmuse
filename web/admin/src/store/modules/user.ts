import { to } from 'await-to-js';
import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { UserInfo } from '@/api/system/user/types';
import type { EntryType, LoginData, LoginResult } from '@/api/types';
import type { RuoYiAjaxResult } from '@/utils/api-types';
import { getInfo as getUserInfo, login as loginApi, logout as logoutApi } from '@/api/login';
import defAva from '@/assets/images/profile.jpg';
import { getToken, removeToken, setToken } from '@/utils/auth';
import { isEntryType } from '@/utils/auth-routing';

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken());
  const name = ref('');
  const nickname = ref('');
  const userId = ref<string | number>('');
  const avatar = ref('');
  const roles = ref<Array<string>>([]); // 用户角色编码集合 → 判断路由权限
  const permissions = ref<Array<string>>([]); // 用户权限编码集合 → 判断按钮权限
  const entryType = ref<EntryType>();
  const firstLogin = ref(false);

  /**
   * 登录
   * @param userInfo
   * @returns
   */
  const login = async (userInfo: LoginData): Promise<LoginResult> => {
    const [err, res] = await to(loginApi(userInfo));
    if (res) {
      const data = (res as RuoYiAjaxResult<LoginResult>).data;
      if (!data?.access_token || !isEntryType(data.entryType)) {
        return Promise.reject(new Error('登录响应缺少有效的入口信息'));
      }
      setToken(data.access_token);
      token.value = data.access_token;
      entryType.value = data.entryType;
      firstLogin.value = data.firstLogin === true;
      return data;
    }
    return Promise.reject(err ?? new Error('登录失败'));
  };

  // 获取用户信息
  const getInfo = async (): Promise<UserInfo> => {
    const [err, res] = await to(getUserInfo());
    if (res) {
      const data = (res as RuoYiAjaxResult<UserInfo>).data;
      if (!data?.user) {
        return Promise.reject(new Error('身份查询响应缺少用户信息'));
      }
      if (!isEntryType(data.entryType)) {
        return Promise.reject(new Error('身份查询响应缺少有效的入口信息'));
      }
      if (entryType.value && entryType.value !== data.entryType) {
        return Promise.reject(new Error('登录身份与当前会话不一致，请重新登录'));
      }
      const user = data.user;
      const profile = user.avatarUrl == '' || user.avatarUrl == null ? defAva : user.avatarUrl;

      if (data.roles && data.roles.length > 0) {
        // 验证返回的roles是否是一个非空数组
        roles.value = data.roles;
        permissions.value = data.permissions;
      } else {
        roles.value = ['ROLE_DEFAULT'];
      }
      name.value = user.userName;
      nickname.value = user.nickName;
      avatar.value = profile;
      userId.value = user.userId;
      entryType.value = data.entryType;
      return data;
    }
    return Promise.reject(err ?? new Error('获取身份信息失败'));
  };

  const clearSession = () => {
    token.value = '';
    roles.value = [];
    permissions.value = [];
    entryType.value = undefined;
    firstLogin.value = false;
    removeToken();
  };

  // 注销；即使服务端暂时不可用，也必须清理本地会话。
  const logout = async (): Promise<void> => {
    try {
      await logoutApi();
    } finally {
      clearSession();
    }
  };

  const setAvatar = (value: string) => {
    avatar.value = value;
  };

  return {
    userId,
    token,
    nickname,
    avatar,
    roles,
    permissions,
    entryType,
    firstLogin,
    login,
    getInfo,
    logout,
    clearSession,
    setAvatar
  };
});
