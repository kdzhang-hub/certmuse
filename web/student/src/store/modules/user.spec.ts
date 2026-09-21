import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';

const api = vi.hoisted(() => ({ getInfo: vi.fn(), login: vi.fn(), logout: vi.fn() }));
const token = vi.hoisted(() => ({ remove: vi.fn(), set: vi.fn() }));

vi.mock('@/api/login', () => api);
vi.mock('@/utils/auth', () => ({ getToken: () => null, removeToken: token.remove, setToken: token.set }));
vi.mock('@/assets/images/profile.jpg', () => ({ default: 'default-avatar' }));

import { useUserStore } from './user';

describe('learner session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('persists a valid learner login identity only', async () => {
    api.login.mockResolvedValue({ data: { access_token: 'token-1', entryType: 'LEARNING' } });
    const store = useUserStore();
    await store.login({ clientId: 'student', grantType: 'password', username: 'learner' });
    expect(store.token).toBe('token-1');
    expect(store.entryType).toBe('LEARNING');
    expect(token.set).toHaveBeenCalledWith('token-1');
  });

  it.each([
    undefined,
    { access_token: '', entryType: 'LEARNING' },
    { access_token: 'token-1', entryType: 'UNTRUSTED' }
  ])('rejects an invalid login identity: %j', async data => {
    api.login.mockResolvedValue({ data });
    await expect(useUserStore().login({ clientId: 'student', grantType: 'password' })).rejects.toThrow(
      '登录响应缺少有效的入口信息'
    );
    expect(token.set).not.toHaveBeenCalled();
  });

  it('hydrates a valid learner profile with fallback role and avatar', async () => {
    api.getInfo.mockResolvedValue({
      data: {
        entryType: 'LEARNING',
        permissions: [],
        roles: [],
        user: { avatarUrl: null, nickName: '学习者', userId: 7, userName: 'learner' }
      }
    });
    const store = useUserStore();
    await store.getInfo();
    expect(store.entryType).toBe('LEARNING');
    expect(store.roles).toEqual(['ROLE_DEFAULT']);
    expect(store.avatar).toBe('default-avatar');
  });

  it('rejects malformed or identity-mismatched profiles', async () => {
    const store = useUserStore();
    api.getInfo.mockResolvedValueOnce({ data: { entryType: 'LEARNING' } });
    await expect(store.getInfo()).rejects.toBeNull();
    store.entryType = 'LEARNING';
    api.getInfo.mockResolvedValueOnce({
      data: { entryType: 'ADMIN', user: { avatarUrl: '', nickName: '管理员', userId: 1, userName: 'admin' } }
    });
    await expect(store.getInfo()).rejects.toThrow('登录身份与当前会话不一致，请重新登录');
  });

  it('rejects a profile with an unknown entry identity and forwards a failed login request', async () => {
    api.getInfo.mockResolvedValue({
      data: { entryType: 'UNTRUSTED', user: { avatarUrl: '', nickName: '未知身份', userId: 8, userName: 'unknown' } }
    });
    await expect(useUserStore().getInfo()).rejects.toThrow('身份查询响应缺少有效的入口信息');

    api.login.mockRejectedValueOnce(new Error('认证服务不可用'));
    await expect(useUserStore().login({ clientId: 'student', grantType: 'password' })).rejects.toThrow('认证服务不可用');
  });

  it('clears local session even when remote logout fails', async () => {
    const store = useUserStore();
    store.entryType = 'LEARNING';
    store.permissions = ['certmuse:learning:view'];
    store.roles = ['student'];
    store.token = 'token-1';
    api.logout.mockRejectedValue(new Error('offline'));
    await expect(store.logout()).rejects.toThrow('offline');
    expect(store.token).toBe('');
    expect(store.roles).toEqual([]);
    expect(store.entryType).toBeUndefined();
    expect(token.remove).toHaveBeenCalledOnce();
  });

  it('keeps supplied roles, permissions and avatar from a valid profile', async () => {
    api.getInfo.mockResolvedValue({
      data: {
        entryType: 'LEARNING',
        permissions: ['certmuse:learning:view'],
        roles: ['student'],
        user: { avatarUrl: '/avatar.png', nickName: '学习者二', userId: 'learner-2', userName: 'learner-2' }
      }
    });
    const store = useUserStore();
    await store.getInfo();
    expect(store.roles).toEqual(['student']);
    expect(store.permissions).toEqual(['certmuse:learning:view']);
    expect(store.avatar).toBe('/avatar.png');
  });
});
