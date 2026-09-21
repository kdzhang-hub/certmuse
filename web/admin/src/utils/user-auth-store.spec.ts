import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { LoginData } from '@/api/types';
import { getInfo as getUserInfo, login as loginApi } from '@/api/login';
import { setToken } from '@/utils/auth';
import { useUserStore } from '@/store/modules/user';

vi.mock('@/api/login', () => ({
  getInfo: vi.fn(),
  login: vi.fn(),
  logout: vi.fn()
}));

vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(() => null),
  removeToken: vi.fn(),
  setToken: vi.fn()
}));

const credentials = {
  username: 'learner',
  password: 'secret',
  clientId: 'client',
  grantType: 'password'
} as LoginData;

describe('user auth entry store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('拒绝仍使用旧 userType 的登录响应', async () => {
    vi.mocked(loginApi).mockResolvedValue({
      data: {
        access_token: 'token',
        userType: 'app_user'
      }
    } as never);

    await expect(useUserStore().login(credentials)).rejects.toThrow('登录响应缺少有效的入口信息');
    expect(setToken).not.toHaveBeenCalled();
  });

  it('登录入口与身份查询不一致时拒绝继续会话', async () => {
    vi.mocked(loginApi).mockResolvedValue({
      data: {
        access_token: 'token',
        client_id: 'client',
        entryType: 'LEARNING',
        expire_in: 3600,
        firstLogin: false
      }
    } as never);
    vi.mocked(getUserInfo).mockResolvedValue({
      data: {
        entryType: 'ADMIN',
        permissions: [],
        roles: ['admin'],
        user: {
          avatarUrl: '',
          nickName: '管理员',
          userId: 1,
          userName: 'admin'
        }
      }
    } as never);

    const store = useUserStore();
    await store.login(credentials);
    expect(store.entryType).toBe('LEARNING');
    await expect(store.getInfo()).rejects.toThrow('登录身份与当前会话不一致');
  });
});
