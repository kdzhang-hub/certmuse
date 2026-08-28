<template>
  <div class="register-page">
    <div class="register-shell">
      <section class="brand-panel">
        <div class="brand-topline">
          <span class="brand-mark" aria-hidden="true"><i></i></span>
          <span class="brand-wordmark">
            <strong>知沐</strong>
            <small>CertMuse</small>
          </span>
        </div>

        <div class="brand-story">
          <span class="brand-pill">软考学习，从清晰的目标开始</span>
          <h1>
            创建你的
            <br />
            专属备考空间。
          </h1>
          <p>注册学员账号后，你可以建立软考目标、完成首次诊断，并获得围绕薄弱知识点生成的学习任务。</p>
        </div>

        <ol class="register-steps" aria-label="注册后的学习流程">
          <li>
            <b>01</b>
            <span>创建账号</span>
          </li>
          <li>
            <b>02</b>
            <span>设置目标</span>
          </li>
          <li>
            <b>03</b>
            <span>完成诊断</span>
          </li>
        </ol>
      </section>

      <el-form ref="registerRef" :model="registerForm" :rules="registerRules" class="register-form">
        <div class="title-box">
          <div>
            <div class="mobile-brand">
              <span></span>
              知沐
              <em>CertMuse</em>
            </div>
            <p class="eyebrow">CertMuse · 学员注册</p>
            <h2>创建学员账号</h2>
            <p>账号仅用于你的软考学习与进度记录。</p>
          </div>
          <lang-select />
        </div>

        <el-alert
          class="account-note"
          title="注册账号固定为学员身份，不能用于进入管理后台。"
          type="info"
          :closable="false"
          show-icon
        />

        <el-form-item prop="username">
          <el-input
            v-model="registerForm.username"
            size="large"
            autocomplete="username"
            placeholder="设置登录账号（2–30 个字符）"
          >
            <template #prefix><svg-icon icon-class="user" class="input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            show-password
            size="large"
            autocomplete="new-password"
            placeholder="设置密码（8–30 个字符）"
          >
            <template #prefix><svg-icon icon-class="password" class="input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            show-password
            size="large"
            autocomplete="new-password"
            placeholder="再次输入密码"
            @keyup.enter="handleRegister"
          >
            <template #prefix><svg-icon icon-class="password" class="input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item v-if="captchaEnabled" prop="code" class="captcha-row">
          <el-input
            v-model="registerForm.code"
            size="large"
            autocomplete="off"
            placeholder="验证码"
            @keyup.enter="handleRegister"
          >
            <template #prefix><svg-icon icon-class="validCode" class="input-icon" /></template>
          </el-input>
          <button class="register-code" type="button" aria-label="刷新验证码" @click="getCode">
            <img :src="codeUrl" alt="验证码" />
          </button>
        </el-form-item>

        <div class="form-meta">
          <span>已有账号？</span>
          <router-link :to="loginLink">返回登录</router-link>
        </div>

        <el-form-item class="submit-row">
          <el-button
            :loading="loading"
            size="large"
            type="primary"
            class="submit-button"
            @click.prevent="handleRegister"
          >
            <span v-if="!loading">注册学员账号</span>
            <span v-else>正在注册...</span>
          </el-button>
        </el-form-item>

        <p class="privacy-tip">注册成功后需要返回登录页完成认证。密码和验证码不会写入地址或本地存储。</p>
      </el-form>
    </div>

    <footer>© {{ currentYear }} 知沐 CertMuse · 专注软考学习</footer>
  </div>
</template>

<script setup lang="ts">
import { to } from 'await-to-js';
import type { RegisterForm } from '@/api/types';
import { getCodeImg, register } from '@/api/login';
import { sanitizeRedirect } from '@/utils/auth-routing';

const router = useRouter();
const currentYear = new Date().getFullYear();
const registerRef = ref<ElFormInstance>();
const loading = ref(false);
const captchaEnabled = ref(true);
const codeUrl = ref('');
const redirect = computed(() => sanitizeRedirect(router.currentRoute.value.query.redirect));
const loginLink = computed(() => (redirect.value ? `/login?redirect=${encodeURIComponent(redirect.value)}` : '/login'));

const registerForm = ref<RegisterForm>({
  username: '',
  password: '',
  confirmPassword: '',
  code: '',
  uuid: ''
});

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value !== registerForm.value.password) callback(new Error('两次输入的密码不一致'));
  else callback();
};

const registerRules: ElFormRules = {
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { min: 2, max: 30, message: '账号长度应为 2–30 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { min: 8, max: 30, message: '密码长度应为 8–30 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'change' }]
};

const getCode = async () => {
  const response = await getCodeImg();
  const data = response.data;
  captchaEnabled.value = data.captchaEnabled === undefined ? true : data.captchaEnabled;
  registerForm.value.code = '';
  if (captchaEnabled.value) {
    codeUrl.value = `data:image/gif;base64,${data.img ?? ''}`;
    registerForm.value.uuid = data.uuid;
  }
};

const handleRegister = () => {
  registerRef.value?.validate(async valid => {
    if (!valid || loading.value) return;
    loading.value = true;
    const [err, response] = await to(register(registerForm.value));
    if (err) {
      registerForm.value.password = '';
      registerForm.value.confirmPassword = '';
      loading.value = false;
      if (captchaEnabled.value) await getCode();
      return;
    }

    const username = response?.data?.username || registerForm.value.username;
    await router.replace({
      path: '/login',
      query: {
        registered: '1',
        username,
        ...(redirect.value ? { redirect: redirect.value } : {})
      }
    });
  });
};

onMounted(getCode);
</script>

<style lang="scss" scoped>
.register-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 34px 28px 76px;
  background:
    radial-gradient(circle at 8% 2%, rgba(205, 225, 194, 0.74), transparent 28%),
    radial-gradient(circle at 92% 92%, rgba(235, 208, 151, 0.36), transparent 24%),
    linear-gradient(135deg, #f3f4e9, #f8f5ea 48%, #edf3e8);
}

.register-shell {
  width: min(1160px, 100%);
  min-height: 690px;
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(420px, 0.92fr);
  position: relative;
  z-index: 1;
  overflow: hidden;
  border: 1px solid rgba(53, 82, 59, 0.1);
  border-radius: 34px;
  background: rgba(255, 255, 255, 0.42);
  box-shadow: 0 36px 90px rgba(54, 75, 51, 0.16);
}

.brand-panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  position: relative;
  overflow: hidden;
  padding: 48px 56px;
  color: #f9f7ed;
  background:
    radial-gradient(circle at 86% 12%, rgba(226, 206, 142, 0.2), transparent 28%),
    linear-gradient(145deg, #234b37, #315d45 50%, #3f6b50);
}

.brand-topline,
.brand-wordmark {
  display: flex;
  align-items: center;
}

.brand-topline {
  gap: 14px;
}

.brand-mark {
  width: 44px;
  height: 44px;
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
  align-items: baseline;
  gap: 9px;

  strong {
    font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
    font-size: 25px;
    letter-spacing: 0.12em;
  }

  small {
    color: rgba(245, 242, 224, 0.68);
    font-family: Georgia, serif;
    letter-spacing: 0.12em;
  }
}

.brand-story {
  position: relative;
  z-index: 1;

  .brand-pill {
    display: inline-flex;
    padding: 7px 12px;
    border: 1px solid rgba(230, 222, 180, 0.3);
    border-radius: 999px;
    color: rgba(242, 235, 207, 0.78);
    font-size: 11px;
    letter-spacing: 0.14em;
  }

  h1 {
    margin: 24px 0 18px;
    font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
    font-size: clamp(38px, 4vw, 52px);
    font-weight: 600;
    line-height: 1.35;
  }

  p {
    max-width: 470px;
    margin: 0;
    color: rgba(238, 241, 224, 0.74);
    font-size: 14px;
    line-height: 1.95;
  }
}

.register-steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin: 0;
  padding: 22px 0 0;
  border-top: 1px solid rgba(255, 255, 255, 0.13);
  list-style: none;

  li {
    display: flex;
    flex-direction: column;
    gap: 7px;
  }

  b {
    color: #dce5bd;
    font-family: Georgia, serif;
    font-size: 18px;
  }

  span {
    color: rgba(238, 241, 224, 0.62);
    font-size: 12px;
  }
}

.register-form {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 38px 50px 32px;
  background: rgba(255, 253, 247, 0.95);
}

.title-box {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 20px;

  .eyebrow {
    margin: 0 0 9px;
    color: #718674;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.16em;
  }

  h2 {
    margin: 0;
    color: #27372e;
    font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
    font-size: 29px;
    font-weight: 600;
  }

  h2 + p {
    margin: 10px 0 0;
    color: #718078;
    font-size: 13px;
  }

  :deep(.lang-select--style) {
    padding: 10px;
    border: 1px solid #dce1d7;
    border-radius: 14px;
    color: #65766c;
    background: #f4f4eb;
  }
}

.mobile-brand {
  display: none;
}

.account-note {
  margin-bottom: 18px;
  border-color: #d9e4d6;
  background: #f0f5ed;

  :deep(.el-alert__title) {
    color: #496052;
    font-size: 12px;
  }
}

.register-form :deep(.el-input__wrapper) {
  min-height: 48px;
  padding: 1px 15px;
  border-radius: 14px;
  background: #fbfaf4;
  box-shadow: 0 0 0 1px #cfd6cc inset;
}

.register-form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px rgba(49, 93, 69, 0.42) inset,
    0 0 0 4px rgba(49, 93, 69, 0.09);
}

.register-form :deep(.el-input__inner) {
  color: #34463b;
  font-weight: 500;
}

.register-form :deep(.el-input__inner::placeholder) {
  color: #7c887f;
  opacity: 1;
  font-weight: 400;
}

.input-icon {
  color: #718078;
}

.captcha-row :deep(.el-form-item__content) {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 122px;
  gap: 12px;
}

.register-code {
  height: 48px;
  overflow: hidden;
  padding: 0;
  border: 1px solid #cfd6cc;
  border-radius: 14px;
  background: #fbfaf4;
  cursor: pointer;

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.form-meta {
  display: flex;
  justify-content: flex-end;
  gap: 5px;
  margin: -2px 0 18px;
  color: #7a877e;
  font-size: 13px;

  a {
    color: #315d45;
    font-weight: 600;
  }
}

.submit-row {
  margin-bottom: 0;
}

.submit-button {
  width: 100%;
  height: 50px;
  border: 0;
  border-radius: 14px;
  background: linear-gradient(135deg, #315d45, #47775a);
  box-shadow: 0 16px 30px rgba(49, 93, 69, 0.2);
  letter-spacing: 0.12em;
}

.privacy-tip {
  margin: 14px 6px 0;
  color: #879289;
  font-size: 11px;
  line-height: 1.65;
  text-align: center;
}

footer {
  position: fixed;
  bottom: 12px;
  width: 100%;
  color: rgba(71, 91, 75, 0.62);
  font-size: 12px;
  letter-spacing: 0.1em;
  text-align: center;
}

@media (max-width: 960px) {
  .register-page {
    padding: 22px 14px 70px;
  }

  .register-shell {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    display: none;
  }

  .register-form {
    min-height: 680px;
    padding: 36px 28px;
  }

  .mobile-brand {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 25px;
    color: #315d45;
    font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
    font-size: 19px;
    font-weight: 700;

    span {
      width: 24px;
      height: 24px;
      border-radius: 8px 8px 8px 3px;
      background: #315d45;
    }

    em {
      color: #809087;
      font-family: Georgia, serif;
      font-size: 11px;
      font-style: normal;
      font-weight: 400;
    }
  }
}

@media (max-width: 560px) {
  .register-form {
    padding: 30px 20px;
  }

  .captcha-row :deep(.el-form-item__content) {
    grid-template-columns: minmax(0, 1fr) 110px;
  }
}
</style>
