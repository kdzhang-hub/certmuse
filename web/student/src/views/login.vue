<template>
  <main class="handoff-page">
    <div class="ambient ambient-left" aria-hidden="true"></div>
    <div class="ambient ambient-right" aria-hidden="true"></div>

    <section class="handoff-card" aria-live="polite">
      <div class="brand-mark" aria-hidden="true">知</div>
      <p class="brand-name">知沐 · CertMuse</p>
      <h1>正在前往知沐统一登录</h1>
      <p class="description">登录后会根据账号身份进入学习端，并恢复你刚才访问的学习页面。</p>

      <div class="progress-row">
        <span class="spinner" aria-hidden="true"></span>
        <span>正在安全跳转，请稍候…</span>
      </div>

      <button type="button" class="retry-button" @click="redirectToUnifiedLogin">立即前往</button>
      <p class="tip">学员注册也在统一登录页面完成</p>
    </section>
  </main>
</template>

<script setup lang="ts">
const router = useRouter();

const STUDENT_FALLBACK = '/learning/home';
const STUDENT_PATHS = ['/learning'];

const sanitizeStudentRedirect = (raw: unknown): string => {
  const source = Array.isArray(raw) ? raw[0] : raw;
  if (typeof source !== 'string' || !source.trim()) return STUDENT_FALLBACK;

  let candidate = source.trim();
  try {
    candidate = decodeURIComponent(candidate);
  } catch {
    return STUDENT_FALLBACK;
  }

  if (!candidate.startsWith('/') || candidate.startsWith('//') || candidate.includes('\\')) {
    return STUDENT_FALLBACK;
  }

  try {
    const url = new URL(candidate, 'https://certmuse.local');
    const isStudentPath = STUDENT_PATHS.some(path => url.pathname === path || url.pathname.startsWith(`${path}/`));
    return url.origin === 'https://certmuse.local' && isStudentPath
      ? `${url.pathname}${url.search}${url.hash}`
      : STUDENT_FALLBACK;
  } catch {
    return STUDENT_FALLBACK;
  }
};

const redirectToUnifiedLogin = () => {
  const configuredEntry = import.meta.env.VITE_AUTH_ENTRY_URL;
  const authEntry = configuredEntry || (import.meta.env.DEV ? 'http://localhost:3000/login' : '/login');
  const url = new URL(authEntry, window.location.origin);
  url.searchParams.set('redirect', sanitizeStudentRedirect(router.currentRoute.value.query.redirect));
  if (router.currentRoute.value.query.reason === 'unlock') {
    url.searchParams.set('reason', 'unlock');
  }
  window.location.replace(url.toString());
};

onMounted(redirectToUnifiedLogin);
</script>

<style lang="scss" scoped>
.handoff-page {
  position: relative;
  display: grid;
  min-height: 100%;
  place-items: center;
  overflow: hidden;
  padding: 32px 20px;
  color: #22372a;
  background:
    linear-gradient(rgba(249, 247, 237, 0.9), rgba(241, 246, 237, 0.94)),
    repeating-linear-gradient(115deg, transparent 0 34px, rgba(51, 91, 64, 0.025) 34px 35px);
}

.ambient {
  position: absolute;
  border-radius: 999px;
  pointer-events: none;
  filter: blur(2px);
}

.ambient-left {
  width: 420px;
  height: 420px;
  left: -230px;
  bottom: -220px;
  border: 1px solid rgba(55, 101, 70, 0.16);
  box-shadow:
    0 0 0 52px rgba(55, 101, 70, 0.035),
    0 0 0 104px rgba(55, 101, 70, 0.02);
}

.ambient-right {
  width: 270px;
  height: 270px;
  top: -110px;
  right: -70px;
  background: rgba(220, 201, 146, 0.2);
}

.handoff-card {
  z-index: 1;
  width: min(100%, 460px);
  padding: 52px 48px 42px;
  text-align: center;
  border: 1px solid rgba(73, 104, 79, 0.14);
  border-radius: 28px;
  background: rgba(255, 255, 252, 0.88);
  box-shadow: 0 28px 80px rgba(45, 67, 50, 0.12);
  backdrop-filter: blur(18px);
}

.brand-mark {
  display: grid;
  width: 58px;
  height: 58px;
  margin: 0 auto 16px;
  place-items: center;
  color: #f8f6ea;
  font-family: 'STKaiti', 'KaiTi', serif;
  font-size: 30px;
  font-weight: 700;
  border-radius: 20px 20px 20px 6px;
  background: linear-gradient(145deg, #477858, #29523a);
  box-shadow: 0 12px 28px rgba(41, 82, 58, 0.22);
}

.brand-name {
  margin: 0;
  color: #5e765f;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

h1 {
  margin: 18px 0 12px;
  color: #23392b;
  font-family: 'STKaiti', 'KaiTi', 'Microsoft YaHei', sans-serif;
  font-size: clamp(26px, 5vw, 34px);
  line-height: 1.25;
}

.description {
  margin: 0 auto;
  color: #607065;
  font-size: 15px;
  line-height: 1.8;
}

.progress-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 11px;
  margin: 30px 0 22px;
  color: #3f624a;
  font-size: 14px;
  font-weight: 600;
}

.spinner {
  width: 19px;
  height: 19px;
  border: 2px solid #d7e1d6;
  border-top-color: #376547;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.retry-button {
  width: 100%;
  min-height: 46px;
  color: #fff;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
  border: 0;
  border-radius: 14px;
  background: #315d40;
  transition:
    transform 0.18s ease,
    background 0.18s ease;
}

.retry-button:hover {
  background: #274e35;
  transform: translateY(-1px);
}

.tip {
  margin: 18px 0 0;
  color: #89938b;
  font-size: 12px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 560px) {
  .handoff-card {
    padding: 42px 26px 34px;
    border-radius: 22px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation-duration: 1.8s;
  }
}
</style>
