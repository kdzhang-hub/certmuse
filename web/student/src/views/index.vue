<template>
  <div class="home">
    <section class="hero-panel">
      <div class="hero-copy">
        <h1>CertMuse 学习中心</h1>
        <p>
          从学习目标、诊断评估到练习复盘，围绕认证准备建立连贯、可追踪的学习旅程。
        </p>
      </div>
    </section>

    <div class="content-grid">
      <section class="section-card">
        <div class="section-head">
          <div>
            <h2>学习路径</h2>
          </div>
        </div>
        <div class="product-list">
          <article v-for="product in products" :key="product.name" class="product-card">
            <div class="product-top">
              <div>
                <h3>{{ product.name }}</h3>
                <p>{{ product.summary }}</p>
              </div>
              <span class="product-version">{{ product.version }}</span>
            </div>
            <div class="product-tags">
              <el-tag v-for="tag in product.tags" :key="tag" effect="plain">{{ tag }}</el-tag>
            </div>
          </article>
        </div>
      </section>

      <section class="section-card capability-card">
        <div class="section-head">
          <div>
            <h2>能力地图</h2>
          </div>
        </div>
        <div class="capability-groups">
          <article v-for="group in capabilityGroups" :key="group.title" class="capability-group">
            <h3>{{ group.title }}</h3>
            <ul>
              <li v-for="item in group.items" :key="item">{{ item }}</li>
            </ul>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup name="Index" lang="ts">
const products = [
  {
    name: '目标与诊断',
    version: 'v6.0.0',
    summary: '明确学习目标，完成诊断评估，找到下一步最值得投入的知识点。',
    tags: ['目标', '诊断', '建议'],
  },
  {
    name: '练习与复盘',
    version: 'v6.0.0',
    summary: '围绕练习、错题与学习任务形成反馈闭环，让进步更有迹可循。',
    tags: ['练习', '错题', '进度'],
  }
];

const capabilityGroups = [
  {
    title: '认证准备',
    items: ['学习目标与入门引导', '章节学习与资源浏览', '诊断与薄弱项识别']
  },
  {
    title: '持续练习',
    items: ['题目练习与即时反馈', '错题整理与重点回看', '任务提醒与学习记录']
  },
  {
    title: '学习成果',
    items: ['阶段结果与学习历史', '完成进度与能力变化', '下一步学习建议']
  }
];

</script>

<style lang="scss" scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.hero-panel,
.section-card {
  border-radius: 8px;
  border: 1px solid var(--app-surface-border);
  background: var(--app-surface-bg);
  box-shadow: var(--app-shadow-sm);
  backdrop-filter: blur(18px);
}

.hero-panel {
  display: block;
  padding: 30px;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 14px;

  h1 {
    margin: 0;
    font-size: 32px;
    line-height: 1.25;
    color: var(--app-text-title);
  }

  p {
    margin: 0;
    max-width: 760px;
    color: var(--app-text-muted);
    font-size: 15px;
    line-height: 1.8;
  }
}

.hero-badge {
  display: inline-flex;
  width: fit-content;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(53, 109, 255, 0.12);
  color: var(--app-accent-strong);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-stats {
  display: grid;
  gap: 12px;
}

.stat-card {
  padding: 18px 20px;
  border-radius: 22px;
  background: var(--app-elevated-soft-bg);
  border: 1px solid var(--app-surface-border);
  display: flex;
  flex-direction: column;
  gap: 6px;

  strong {
    color: var(--app-text-title);
    font-size: 24px;
  }

  span {
    color: var(--app-text-muted);
    font-size: 13px;
  }
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(300px, 0.85fr);
  gap: 20px;
}

.section-card {
  padding: 24px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;

  h2 {
    margin: 6px 0 0;
    color: var(--app-text-title);
    font-size: 26px;
    letter-spacing: -0.03em;
  }
}

.section-kicker {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.product-list,
.capability-groups {
  display: grid;
  gap: 16px;
}

.product-card {
  padding: 22px;
  border-radius: 8px;
  background: var(--app-elevated-soft-bg);
  border: 1px solid var(--app-surface-border);
  transition:
    transform 0.25s ease,
    box-shadow 0.25s ease,
    border-color 0.25s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--app-shadow-sm);
    border-color: rgba(53, 109, 255, 0.2);
  }
}

.product-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;

  h3 {
    margin: 0 0 8px;
    color: var(--app-text-title);
    font-size: 22px;
  }

  p {
    margin: 0;
    color: var(--app-text-muted);
    line-height: 1.8;
  }
}

.product-version {
  flex-shrink: 0;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(53, 109, 255, 0.12);
  color: var(--app-accent-strong);
  font-size: 12px;
  font-weight: 700;
}

.product-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.capability-card {
}

.capability-group {
  padding: 18px 18px 18px 20px;
  border-radius: 8px;
  background: var(--app-elevated-soft-bg);
  border: 1px solid var(--app-surface-border);

  h3 {
    margin: 0 0 12px;
    color: var(--app-text-title);
    font-size: 18px;
  }

  ul {
    margin: 0;
    padding: 0;
    list-style: none;
    display: grid;
    gap: 10px;
  }

  li {
    position: relative;
    padding-left: 16px;
    color: var(--app-text-muted);
    line-height: 1.7;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 10px;
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--app-accent-strong);
      box-shadow: 0 0 0 5px rgba(53, 109, 255, 0.12);
    }
  }
}

@media (max-width: 960px) {
  .hero-panel,
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .hero-panel,
  .section-card {
    padding: 20px;
    border-radius: 8px;
  }

  .product-top {
    flex-direction: column;
  }
}

</style>
