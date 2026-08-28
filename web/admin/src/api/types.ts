export type UserType = 'sys_user' | 'app_user';
export type EntryType = 'ADMIN' | 'LEARNING';

/** 学员注册表单；confirmPassword 仅用于前端校验。 */
export interface RegisterForm {
  username: string;
  password: string;
  confirmPassword: string;
  code?: string;
  uuid?: string;
}

export interface LearnerRegisterResult {
  username: string;
  roleKey: 'student';
}

/**
 * 登录请求
 */
export interface LoginData {
  username?: string;
  password?: string;
  rememberMe?: boolean;
  socialCode?: string;
  socialState?: string;
  source?: string;
  code?: string;
  uuid?: string;
  clientId: string;
  grantType: string;
}

/**
 * 登录响应
 */
export interface LoginResult {
  access_token: string;
  expire_in: number;
  client_id: string;
  scope?: string;
  entryType: EntryType;
  firstLogin: boolean;
}

export type OnboardingNextAction =
  | 'SET_GOAL'
  | 'START_DIAGNOSTIC'
  | 'CONTINUE_DIAGNOSTIC'
  | 'WAIT_PROCESSING'
  | 'ENTER_HOME'
  | 'RETRY_DIAGNOSTIC'
  | 'CONTACT_SUPPORT';

export interface OnboardingStatus {
  goalStatus: 'NONE' | 'ACTIVE' | 'PAUSED';
  diagnosticStatus: 'NOT_STARTED' | 'IN_PROGRESS' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  currentCertificationId?: string;
  activeSessionId?: string;
  nextAction: OnboardingNextAction;
}

/**
 * 验证码返回
 */
export interface VerifyCodeResult {
  captchaEnabled: boolean;
  uuid?: string;
  img?: string;
}

/**
 * 分页返回结果
 */
export interface PageResult<T = any> {
  total: number;
  rows: T[];
}
