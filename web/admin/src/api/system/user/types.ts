import type { PostVO } from '@/api/system/post/types';
import type { RoleVO } from '@/api/system/role/types';
import type { EntryType, UserType } from '@/api/types';

/**
 * 用户信息
 */
export interface UserInfo {
  user: UserVO;
  roles: string[];
  permissions: string[];
  entryType: EntryType;
}

/**
 * 用户查询对象类型
 */
export interface UserQuery extends PageQuery {
  userName?: string;
  nickName?: string;
  phoneNumber?: string;
  status?: string;
  deptId?: string;
  roleId?: string;
  userIds?: string | string[];
}

/**
 * 用户返回对象
 */
export interface UserVO extends BaseEntity {
  userId: string;
  tenantId: string;
  deptId: string;
  userName: string;
  nickName: string;
  userType: UserType;
  email: string;
  phoneNumber: string;
  gender: string;
  avatar?: string | number;
  avatarUrl?: string;
  status: string;
  delFlag: string;
  loginIp: string;
  loginDate: string;
  remark: string;
  deptName: string;
  /** 详情接口可能返回嵌套部门 */
  dept?: { deptName?: string };
  roles: RoleVO[];
  roleIds: any;
  postIds: any;
  roleId: any;
  admin: boolean;
}

/**
 * 用户表单类型
 */
export interface UserForm {
  id?: string;
  userId?: string;
  deptId?: string;
  userName: string;
  nickName?: string;
  password: string;
  phoneNumber?: string;
  email?: string;
  gender?: string;
  status: string;
  remark?: string;
  avatar?: string | number;
  postIds: string[];
  roleIds: string[];
}

/**
 * 个人资料表单类型
 */
export interface UserProfileForm {
  nickName?: string;
  phoneNumber?: string;
  email?: string;
  gender?: string;
  avatar?: string | number;
}

export interface UserInfoVO {
  user: UserVO;
  roles: RoleVO[];
  roleIds: string[];
  posts: PostVO[];
  postIds: string[];
  roleGroup: string;
  postGroup: string;
}

export interface ResetPwdForm {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}
