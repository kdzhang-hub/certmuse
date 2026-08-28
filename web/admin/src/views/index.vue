<template>
  <div class="home">
    <section class="hero-panel">
      <div class="hero-copy">
        <h1>CertMuse 运营控制台</h1>
        <p>
          统一管理认证学习内容、题库、学习任务与学习成效，为内容运营与教学协作提供清晰的工作台。
        </p>
      </div>
    </section>

    <div class="content-grid">
      <section class="section-card">
        <div class="section-head">
          <div>
            <h2>核心工作区</h2>
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
    name: '内容运营',
    version: 'v6.0.0',
    summary: '维护课程目录、题库与认证资料，让学习内容可以持续迭代和复用。',
    tags: ['课程', '题库', '资料'],
  },
  {
    name: '学习洞察',
    version: 'v6.0.0',
    summary: '查看诊断、练习与学习任务的结果，为课程优化和学习支持提供依据。',
    tags: ['诊断', '进度', '分析'],
  }
];

const capabilityGroups = [
  {
    title: '学习体验',
    items: ['学习目标与入门引导', '诊断评估与错题复盘', '练习、任务与进度追踪']
  },
  {
    title: '运营能力',
    items: ['课程与题目维护', '学习资料与资源管理', '学习结果与反馈洞察']
  },
  {
    title: '平台基础',
    items: ['身份认证与权限控制', '模块化服务边界', '可扩展的 AI 能力接口']
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
