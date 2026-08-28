<template>
  <div class="login">
    <div class="login-shell">
      <section class="login-brand">
        <div class="brand-topline">
          <span class="brand-mark" aria-hidden="true"><i></i></span>
          <span class="brand-wordmark">
            <strong>知沐</strong>
            <small>CertMuse</small>
          </span>
        </div>

        <div class="brand-story">
          <span class="brand-pill">专注软考 · 让备考更有方向</span>
          <h1 class="brand-title">
            循考纲而学，
            <br />
            让备考更有方向。
          </h1>
          <p class="brand-desc">知沐专注软考学习，从考纲知识树、题库练习到诊断复盘，陪你把零散知识沉淀为真正的掌握。</p>
        </div>

        <div class="learning-path" aria-hidden="true">
          <span class="path-seed seed-one"></span>
          <span class="path-seed seed-two"></span>
          <span class="path-seed seed-three"></span>
          <span class="path-leaf leaf-one"></span>
          <span class="path-leaf leaf-two"></span>
          <span class="path-leaf leaf-three"></span>
          <span class="path-caption">考纲 · 练习 · 掌握</span>
        </div>
      </section>

      <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
        <div class="title-box">
          <div>
            <div class="form-brand">
              <span class="form-brand-mark" aria-hidden="true"></span>
              <span>
                知沐
                <em>CertMuse</em>
              </span>
            </div>
            <p class="eyebrow">CertMuse · 软考学习平台</p>
            <h3 class="title">继续你的软考备考</h3>
            <p class="subtitle">登录后查看软考课程、知识点掌握度与练习进度。</p>
          </div>
          <div class="title-actions">
            <router-link class="home-link" to="/home">返回首页</router-link>
            <lang-select />
          </div>
        </div>

        <el-alert
          v-if="routingError"
          class="routing-alert"
          :title="routingError"
          type="error"
          :closable="false"
          show-icon
        />

        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            type="text"
            size="large"
            auto-complete="off"
            :placeholder="$t('login.username')"
          >
            <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            size="large"
            auto-complete="off"
            :placeholder="$t('login.password')"
            @keyup.enter="handleLogin"
          >
            <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item v-if="captchaEnabled" prop="code" class="captcha-row">
          <el-input
            v-model="loginForm.code"
            size="large"
            auto-complete="off"
            :placeholder="$t('login.code')"
            @keyup.enter="handleLogin"
          >
            <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
          </el-input>
          <button
            class="login-code"
            type="button"
            :disabled="captchaLoading"
            :aria-label="captchaLoading ? '正在获取验证码' : '刷新验证码'"
            @click="getCode"
          >
            <img v-if="codeUrl" :src="codeUrl" class="login-code-img" alt="验证码，点击刷新" />
            <span v-else>{{ captchaLoading ? '正在获取…' : '点击刷新' }}</span>
          </button>
        </el-form-item>
        <p v-if="captchaError" class="captcha-tip" role="alert">{{ captchaError }}</p>

        <div class="form-meta">
          <el-checkbox v-model="loginForm.rememberMe">{{ $t('login.rememberPassword') }}</el-checkbox>
          <router-link class="register-link" :to="registerLink">没有账号？注册学员账号</router-link>
        </div>

        <el-form-item class="submit-row">
          <el-button :loading="loading" size="large" type="primary" class="submit-button" @click.prevent="handleLogin">
            <span v-if="!loading">{{ $t('login.login') }}</span>
            <span v-else>{{ $t('login.logging') }}</span>
          </el-button>
        </el-form-item>
        <p class="privacy-tip">账号信息仅用于身份认证，学习数据仅用于生成你的个性化备考建议。</p>
      </el-form>
    </div>

    <div class="el-login-footer">
      <span>© {{ currentYear }} 知沐 CertMuse · 专注软考学习</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { to } from 'await-to-js';
import { useI18n } from 'vue-i18n';
import type { EntryType, LoginData, LoginResult } from '@/api/types';
import { getCodeImg } from '@/api/login';
import { useUserStore } from '@/store/modules/user';
import { isEntryType, resolveEntryTarget, sanitizeRedirect } from '@/utils/auth-routing';

const currentYear = new Date().getFullYear();
const userStore = useUserStore();
const router = useRouter();
const { t } = useI18n();

const loginForm = ref<LoginData>({
  username: '',
  password: '',
  rememberMe: false,
  code: '',
  uuid: ''
} as LoginData);

const loginRules: ElFormRules = {
  username: [
    {
      required: true,
      trigger: 'blur',
      message: t('login.rule.username.required')
    }
  ],
  password: [
    {
      required: true,
      trigger: 'blur',
      message: t('login.rule.password.required')
    }
  ],
  code: [
    {
      required: true,
      trigger: 'change',
      message: t('login.rule.code.required')
    }
  ]
};

const codeUrl = ref('');
const loading = ref(false);
const loginSubmitting = ref(false);
const captchaEnabled = ref(true);
const captchaLoading = ref(false);
const captchaError = ref('');
const redirect = ref<string>();
const routingError = ref('');
const loginRef = ref<ElFormInstance>();
const registerLink = computed(() => {
  const query = redirect.value ? `?redirect=${encodeURIComponent(redirect.value)}` : '';
  return `/register${query}`;
});

watch(
  () => router.currentRoute.value,
  (newRoute: any) => {
    redirect.value = sanitizeRedirect(newRoute.query?.redirect);
  },
  { immediate: true }
);

const resolveAuthenticatedType = async (result: LoginResult): Promise<EntryType> => {
  const responseType = isEntryType(result.entryType) ? result.entryType : undefined;
  const userInfo = await userStore.getInfo();
  const verifiedType = userInfo.entryType;
  if (responseType && responseType !== verifiedType) {
    throw new Error('登录身份与当前会话不一致，请重新登录');
  }
  return verifiedType;
};

const routeAfterLogin = async (result: LoginResult) => {
  const entryType = await resolveAuthenticatedType(result);
  window.location.replace(resolveEntryTarget(entryType, redirect.value));
};

const handleLogin = () => {
  if (loading.value || loginSubmitting.value || captchaLoading.value) return;
  if (captchaEnabled.value && !loginForm.value.uuid) {
    void getCode();
    return;
  }
  loginSubmitting.value = true;
  loginRef.value?.validate(async (valid: boolean, fields: any) => {
    if (!valid) {
      loginSubmitting.value = false;
      console.log('error submit!', fields);
      return;
    }

    loading.value = true;
    routingError.value = '';
    if (loginForm.value.rememberMe) {
      localStorage.setItem('username', String(loginForm.value.username));
      localStorage.setItem('rememberMe', String(loginForm.value.rememberMe));
    } else {
      localStorage.removeItem('username');
      localStorage.removeItem('rememberMe');
    }
    localStorage.removeItem('password');
    try {
      const [loginError, loginResult] = await to(userStore.login(loginForm.value));
      if (!loginError && loginResult) {
        const [routingFailure] = await to(routeAfterLogin(loginResult));
        if (routingFailure) {
          userStore.clearSession();
          routingError.value = routingFailure instanceof Error ? routingFailure.message : '当前账号暂时无法进入系统';
          if (captchaEnabled.value) await getCode();
        }
      } else if (captchaEnabled.value) {
        await getCode();
      }
    } finally {
      loading.value = false;
      loginSubmitting.value = false;
    }
  });
};

const getCode = async () => {
  if (captchaLoading.value) return;
  captchaLoading.value = true;
  captchaError.value = '';
  loginForm.value.code = '';
  loginForm.value.uuid = '';
  codeUrl.value = '';
  try {
    const res = await getCodeImg();
    const { data } = res;
    captchaEnabled.value = data.captchaEnabled === undefined ? true : data.captchaEnabled;
    if (captchaEnabled.value) {
      if (!data.img || !data.uuid) {
        throw new Error('验证码响应不完整');
      }
      codeUrl.value = 'data:image/gif;base64,' + data.img;
      loginForm.value.uuid = data.uuid;
    }
  } catch {
    captchaEnabled.value = true;
    captchaError.value = '验证码暂时无法加载，请稍后点击图片重试。';
  } finally {
    captchaLoading.value = false;
  }
};

const getLoginData = () => {
  const username = localStorage.getItem('username');
  const rememberMe = localStorage.getItem('rememberMe');
  localStorage.removeItem('password');
  loginForm.value = {
    username: username ?? '',
    password: '',
    rememberMe: rememberMe === 'true'
  } as LoginData;
};

onMounted(() => {
  getCode();
  getLoginData();
  const route = router.currentRoute.value;
  if (route.query.reason === 'unlock') {
    ElMessage({
      message: '请先登录解锁更多功能',
      type: 'warning',
      duration: 3000,
      showClose: true
    });
  }
  if (route.query.registered === '1' && typeof route.query.username === 'string') {
    loginForm.value.username = route.query.username;
    loginForm.value.password = '';
    ElMessage.success('学员账号注册成功，请登录');
  }
});
</script>

<style lang="scss" scoped>
.login {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 44px 28px 88px;
  background:
    radial-gradient(circle at 8% 2%, rgba(205, 225, 194, 0.74), transparent 28%),
    radial-gradient(circle at 92% 92%, rgba(235, 208, 151, 0.36), transparent 24%),
    linear-gradient(135deg, #f3f4e9 0%, #f8f5ea 48%, #edf3e8 100%);

  &::before,
  &::after {
    content: '';
    position: absolute;
    border-radius: 50%;
    pointer-events: none;
  }

  &::before {
    width: 420px;
    height: 420px;
    left: -260px;
    bottom: -230px;
    border: 1px solid rgba(48, 91, 65, 0.14);
    box-shadow:
      0 0 0 56px rgba(48, 91, 65, 0.025),
      0 0 0 112px rgba(48, 91, 65, 0.02);
  }

  &::after {
    width: 180px;
    height: 180px;
    top: -82px;
    right: 8%;
    background: rgba(255, 255, 255, 0.38);
    filter: blur(2px);
  }
}

.login-shell {
  width: min(1160px, 100%);
  min-height: 650px;
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(380px, 0.92fr);
  align-items: stretch;
  position: relative;
  z-index: 1;
  overflow: hidden;
  border: 1px solid rgba(53, 82, 59, 0.1);
  border-radius: 34px;
  background: rgba(255, 255, 255, 0.42);
  box-shadow: 0 36px 90px rgba(54, 75, 51, 0.16);
}

.login-brand,
.login-form {
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.login-brand {
  position: relative;
  overflow: hidden;
  padding: 52px 56px 48px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  color: #f9f7ed;
  background:
    radial-gradient(circle at 86% 12%, rgba(226, 206, 142, 0.2), transparent 28%),
    linear-gradient(145deg, #234b37 0%, #315d45 50%, #3f6b50 100%);

  &::after {
    content: '';
    position: absolute;
    width: 320px;
    height: 320px;
    right: -170px;
    bottom: -170px;
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 50%;
    box-shadow:
      0 0 0 46px rgba(255, 255, 255, 0.025),
      0 0 0 92px rgba(255, 255, 255, 0.02);
  }
}

.brand-topline {
  display: flex;
  align-items: center;
  gap: 14px;
  position: relative;
  z-index: 1;
}

.brand-mark {
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  position: relative;
  border: 1px solid rgba(255, 255, 255, 0.26);
  border-radius: 14px 14px 14px 5px;
  background: rgba(255, 255, 255, 0.12);

  &::before,
  i {
    content: '';
    position: absolute;
    width: 13px;
    height: 20px;
    top: 10px;
    background: #dfe9c9;
  }

  &::before {
    left: 10px;
    border-radius: 13px 2px 13px 2px;
    transform: rotate(-22deg);
  }

  i {
    right: 10px;
    border-radius: 2px 13px 2px 13px;
    transform: rotate(22deg);
  }
}

.brand-wordmark {
  display: flex;
  align-items: baseline;
  gap: 9px;

  strong {
    font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
    font-size: 25px;
    font-weight: 700;
    letter-spacing: 0.12em;
  }

  small {
    color: rgba(245, 242, 224, 0.68);
    font-family: Georgia, 'Times New Roman', serif;
    font-size: 13px;
    letter-spacing: 0.12em;
  }
}

.brand-story {
  position: relative;
  z-index: 1;
}

.brand-pill {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid rgba(230, 222, 180, 0.3);
  color: rgba(242, 235, 207, 0.78);
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 11px;
  letter-spacing: 0.13em;
}

.brand-title {
  margin: 24px 0 18px;
  font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
  font-size: clamp(38px, 4vw, 53px);
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: 0.02em;
}

.brand-desc {
  margin: 0;
  max-width: 470px;
  color: rgba(238, 241, 224, 0.72);
  font-size: 14px;
  line-height: 1.95;
  letter-spacing: 0.04em;
}

.learning-path {
  height: 132px;
  position: relative;
  z-index: 1;
  border-top: 1px solid rgba(255, 255, 255, 0.12);

  &::before {
    content: '';
    position: absolute;
    left: 8px;
    right: 70px;
    top: 68px;
    height: 1px;
    background: linear-gradient(90deg, rgba(225, 232, 204, 0.12), rgba(225, 232, 204, 0.62), rgba(225, 232, 204, 0.08));
  }
}

.path-seed {
  position: absolute;
  top: 63px;
  width: 10px;
  height: 10px;
  border: 2px solid #d9dcae;
  border-radius: 50%;
  background: #315d45;
  box-shadow: 0 0 0 6px rgba(220, 223, 174, 0.08);
}

.seed-one {
  left: 11%;
}
.seed-two {
  left: 45%;
}
.seed-three {
  left: 78%;
}

.path-leaf {
  position: absolute;
  width: 21px;
  height: 34px;
  border: 1px solid rgba(224, 229, 193, 0.64);
  border-radius: 100% 0 100% 0;
  transform-origin: bottom left;
}

.leaf-one {
  left: 12%;
  top: 28px;
  transform: rotate(-31deg) scale(0.65);
}
.leaf-two {
  left: 46%;
  top: 22px;
  transform: rotate(-24deg) scale(0.82);
}
.leaf-three {
  left: 79%;
  top: 10px;
  transform: rotate(-18deg);
  background: rgba(224, 229, 193, 0.06);
}

.path-caption {
  position: absolute;
  left: 8px;
  bottom: 0;
  color: rgba(237, 240, 220, 0.48);
  font-size: 11px;
  letter-spacing: 0.28em;
}

.login-form {
  width: 100%;
  padding: 56px 54px 42px;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: rgba(255, 253, 247, 0.94);
}

.title-box {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 32px;

  .form-brand {
    display: none;
    align-items: center;
    gap: 9px;
    margin-bottom: 32px;
    color: #315d45;
    font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
    font-size: 20px;
    font-weight: 700;

    em {
      margin-left: 4px;
      color: #819180;
      font-family: Georgia, 'Times New Roman', serif;
      font-size: 12px;
      font-style: normal;
      font-weight: 400;
      letter-spacing: 0.08em;
    }
  }

  .form-brand-mark {
    width: 25px;
    height: 25px;
    border-radius: 9px 9px 9px 3px;
    background: #315d45;
  }

  .eyebrow {
    margin: 0 0 10px;
    color: #82957f;
    font-family: Georgia, 'Times New Roman', serif;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.18em;
    text-transform: uppercase;
  }

  .title {
    margin: 0;
    color: #27372e;
    font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
    font-weight: 600;
    font-size: 29px;
    letter-spacing: 0.03em;
  }

  .subtitle {
    margin: 12px 0 0;
    color: #829087;
    font-size: 14px;
    line-height: 1.7;
  }

  :deep(.lang-select--style) {
    line-height: 0;
    color: #78867d;
    padding: 10px;
    border-radius: 14px;
    background: #f4f4eb;
    border: 1px solid #e2e4d8;
  }
}

.title-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.home-link {
  padding: 8px 10px;
  border: 1px solid #e2e4d8;
  border-radius: 12px;
  color: #476453;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  text-decoration: none;

  &:hover,
  &:focus-visible {
    color: #315d45;
    border-color: #adc0af;
    background: #f4f7f1;
  }
}

.login-form .el-input {
  height: 48px;
}

.login-form .input-icon {
  height: 46px;
  width: 14px;
  margin-left: 0;
}

.captcha-row {
  :deep(.el-form-item__content) {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 122px;
    gap: 12px;
  }
}

.form-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: -2px 0 20px;
}

.routing-alert {
  margin-bottom: 18px;
}

.captcha-tip {
  margin: -14px 2px 17px;
  color: #c45656;
  font-size: 12px;
  line-height: 1.5;
}

.register-link {
  color: #315d45;
  font-size: 13px;
  font-weight: 600;

  &:hover {
    color: #47775a;
  }
}

.submit-row {
  margin-bottom: 0;
}

.privacy-tip {
  margin: 16px 6px 0;
  color: #879289;
  font-size: 11px;
  line-height: 1.65;
  text-align: center;
}

.submit-button {
  width: 100%;
  height: 50px;
  border: 0;
  border-radius: 14px;
  background: linear-gradient(135deg, #315d45, #47775a);
  box-shadow: 0 16px 30px rgba(49, 93, 69, 0.2);
  letter-spacing: 0.2em;

  &:hover,
  &:focus {
    background: linear-gradient(135deg, #294f3b, #3e6c50);
  }
}

.login-form :deep(.el-input__wrapper) {
  min-height: 48px;
  padding: 1px 15px;
  background-color: #fbfaf4;
  border-radius: 14px;
  box-shadow: 0 0 0 1px #cfd6cc inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px rgba(49, 93, 69, 0.42) inset,
    0 0 0 4px rgba(49, 93, 69, 0.09);
}

.login-form :deep(.el-input__inner) {
  color: #34463b;
  font-weight: 500;
  caret-color: #315d45;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: #7c887f;
  opacity: 1;
  font-weight: 400;
}

.login-form :deep(.el-input__prefix-inner),
.login-form :deep(.input-icon) {
  color: #718078;
}

.login-form :deep(.el-checkbox__label) {
  color: #4f6056;
  font-weight: 500;
}

.login-form :deep(.el-checkbox__inner) {
  border-color: #aeb9b0;
}

.login-code {
  height: 48px;
  width: 100%;
  padding: 0;
  box-sizing: border-box;
  border-radius: 14px;
  overflow: hidden;
  color: #718078;
  font: inherit;
  background: #fbfaf4;
  border: 1px solid #cfd6cc;
  cursor: pointer;

  &:hover:not(:disabled),
  &:focus-visible:not(:disabled) {
    border-color: #96ad9b;
    outline: none;
  }

  &:disabled {
    cursor: wait;
    opacity: 0.72;
  }

  span {
    display: grid;
    height: 100%;
    place-items: center;
    font-size: 12px;
  }

  img {
    vertical-align: middle;
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.el-login-footer {
  height: 40px;
  line-height: 40px;
  position: fixed;
  bottom: 0;
  width: 100%;
  text-align: center;
  color: rgba(71, 91, 75, 0.62);
  font-size: 12px;
  letter-spacing: 0.1em;
}

.login-code-img {
  height: 48px;
  padding-left: 0;
}

@media (max-width: 960px) {
  .login {
    padding: 24px 14px 80px;
  }

  .login-shell {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .login-brand {
    padding: 36px 38px;
  }

  .brand-title {
    font-size: 36px;
  }

  .learning-path {
    display: none;
  }
}

@media (max-width: 640px) {
  .login-brand {
    display: none;
  }

  .login-form {
    min-height: 560px;
    padding: 34px 24px 28px;
  }

  .title-box {
    gap: 16px;

    .form-brand {
      display: flex;
    }
  }

  .captcha-row :deep(.el-form-item__content) {
    grid-template-columns: minmax(0, 1fr) 110px;
  }
}
</style>
