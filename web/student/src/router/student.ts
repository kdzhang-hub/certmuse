import type { RouteRecordRaw } from 'vue-router';
import Layout from '@/layout/index.vue';
import { usePermissionStore } from '@/store/modules/permission';

/**
 * 学生端的静态菜单。
 *
 * 每项可见功能都使用 RuoYi 原生的「Layout 父路由 + 一个默认子页面」结构。
 * Sidebar 会把只有一个可见子路由的项目自动扁平为一级菜单；详情、答题等
 * 页面保持隐藏，既不出现在菜单中，也不改变已有 URL。Mock 模式下不依赖
 * `/system/menu/getRouters`。这里始终使用应用内路径；正式构建的静态资源前缀
 * 由 VITE_APP_ASSET_BASE 单独控制，不改变 `/learning/**` 路由。
 */
export const studentRoutes: RouteRecordRaw[] = [
  {
    // 学生端的 Logo、错误页和标签页都会回到这里。不要落到管理端的根入口。
    path: '/',
    redirect: '/learning/home',
    hidden: true
  },
  {
    path: '/learning',
    redirect: '/learning/home',
    hidden: true
  },
  {
    path: '/learning/home',
    component: Layout,
    meta: { title: '学习首页', icon: 'dashboard' },
    children: [
      {
        path: '',
        component: () => import('@/views/student/dashboard/index.vue'),
        name: 'StudentDashboard',
        meta: { title: '学习首页', icon: 'dashboard', affix: true }
      }
    ]
  },
  {
    path: '/learning/tasks',
    component: Layout,
    meta: { title: '今日任务', icon: 'guide' },
    children: [
      {
        path: '',
        component: () => import('@/views/student/tasks/index.vue'),
        name: 'StudentTasks',
        meta: { title: '今日任务', icon: 'guide' }
      },
      {
        path: ':taskId',
        component: () => import('@/views/student/tasks/detail.vue'),
        name: 'StudentTaskDetail',
        hidden: true,
        meta: { title: '任务详情', activeMenu: '/learning/tasks' }
      }
    ]
  },
  {
    path: '/learning/question-bank',
    component: Layout,
    redirect: '/learning/question-bank',
    meta: { title: '题库', icon: 'edit' },
    children: [
      {
        path: '',
        component: () => import('@/views/student/question-bank/index.vue'),
        name: 'StudentQuestionBank',
        meta: { title: '题库', icon: 'edit' }
      },
      {
        path: 'knowledge-practice',
        component: () => import('@/views/student/question-bank/knowledge-practice/index.vue'),
        name: 'StudentKnowledgePractice',
        hidden: true,
        meta: { title: '知识点练习', activeMenu: '/learning/question-bank' }
      },
      {
        path: 'past-papers',
        component: () => import('@/views/student/question-bank/past-papers/index.vue'),
        name: 'StudentPastPapers',
        hidden: true,
        meta: { title: '历年真题', activeMenu: '/learning/question-bank' }
      },
      {
        path: 'past-papers/exam',
        component: () => import('@/views/student/question-bank/past-papers/exam.vue'),
        name: 'StudentPastPaperExam',
        hidden: true,
        meta: { title: '真题考试', activeMenu: '/learning/question-bank', focusMode: true, noCache: true }
      },
      {
        path: 'past-papers/result',
        component: () => import('@/views/student/question-bank/past-papers/result.vue'),
        name: 'StudentPastPaperExamResult',
        hidden: true,
        meta: { title: '真题考试结果', activeMenu: '/learning/question-bank', noCache: true }
      },
      {
        path: 'mock-exams',
        component: () => import('@/views/student/question-bank/mock-exams/index.vue'),
        name: 'StudentMockExams',
        hidden: true,
        meta: { title: '模拟试卷', activeMenu: '/learning/question-bank' }
      },
      {
        path: 'mock-exams/exam',
        component: () => import('@/views/student/question-bank/mock-exams/exam.vue'),
        name: 'StudentSimulationExam',
        hidden: true,
        meta: { title: '模拟考试', activeMenu: '/learning/question-bank', focusMode: true, noCache: true }
      },
      {
        path: 'mock-exams/result',
        component: () => import('@/views/student/question-bank/mock-exams/result.vue'),
        name: 'StudentSimulationExamResult',
        hidden: true,
        meta: { title: '模拟考试结果', activeMenu: '/learning/question-bank', noCache: true }
      }
    ]
  },
  {
    path: '/learning/mistakes',
    component: Layout,
    meta: { title: '错题复习', icon: 'bug' },
    children: [
      {
        path: '',
        component: () => import('@/views/student/mistakes/index.vue'),
        name: 'StudentMistakes',
        meta: { title: '错题复习', icon: 'bug' }
      },
      {
        path: ':mistakeId',
        component: () => import('@/views/student/mistakes/detail.vue'),
        name: 'StudentMistakeDetail',
        hidden: true,
        meta: { title: '错题订正', activeMenu: '/learning/mistakes' }
      }
    ]
  },
  {
    path: '/learning/progress',
    component: Layout,
    meta: { title: '学习画像', icon: 'chart' },
    children: [
      {
        path: '',
        component: () => import('@/views/student/progress/index.vue'),
        name: 'StudentProgress',
        meta: { title: '学习画像', icon: 'chart' }
      }
    ]
  },
  {
    path: '/learning/history',
    component: Layout,
    meta: { title: '学习记录', icon: 'documentation' },
    children: [
      {
        path: '',
        component: () => import('@/views/student/history/index.vue'),
        name: 'StudentHistory',
        meta: { title: '学习记录', icon: 'documentation' }
      }
    ]
  },
  {
    path: '/learning/resources',
    component: Layout,
    meta: { title: '学习资料', icon: 'list' },
    children: [
      {
        path: '',
        component: () => import('@/views/student/resources/index.vue'),
        name: 'StudentResources',
        meta: { title: '学习资料', icon: 'list' }
      }
    ]
  },
  {
    path: '/learning/settings',
    redirect: '/learning/account',
    hidden: true
  },
  {
    path: '/learning/goal-required',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '',
        component: () => import('@/views/student/goal-required/index.vue'),
        name: 'StudentGoalRequired',
        hidden: true,
        meta: { title: '设置学习目标' }
      }
    ]
  },
  {
    path: '/learning/session',
    component: Layout,
    hidden: true,
    children: [
      {
        path: ':kind',
        component: () => import('@/views/student/session/index.vue'),
        name: 'StudentSession',
        hidden: true,
        meta: { title: '答题练习', activeMenu: '/learning/question-bank', noCache: true }
      }
    ]
  },
  {
    path: '/learning/result',
    component: Layout,
    hidden: true,
    children: [
      {
        path: ':kind',
        component: () => import('@/views/student/result/index.vue'),
        name: 'StudentResult',
        hidden: true,
        meta: { title: '练习结果', activeMenu: '/learning/question-bank' }
      }
    ]
  },
  {
    path: '/learning/account',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '',
        component: () => import('@/views/student/account/index.vue'),
        name: 'StudentAccount',
        hidden: true,
        meta: { title: '个人中心' }
      }
    ]
  },
  {
    path: '/learning/onboarding',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '',
        component: () => import('@/views/student/onboarding/index.vue'),
        name: 'StudentOnboarding',
        hidden: true,
        meta: { title: '设置学习目标' }
      }
    ]
  },
  {
    path: '/learning/diagnostic',
    component: Layout,
    hidden: true,
    children: [
      {
        path: 'intro',
        component: () => import('@/views/student/diagnostic/intro.vue'),
        name: 'StudentDiagnosticIntro',
        hidden: true,
        meta: { title: '首次诊断说明', noCache: true }
      },
      {
        path: 'session',
        component: () => import('@/views/student/diagnostic/session.vue'),
        name: 'StudentDiagnosticSession',
        hidden: true,
        meta: { title: '首次诊断', sessionKind: 'diagnostic', focusMode: true, noCache: true }
      }
    ]
  },
  {
    path: '/learning/diagnostic/result',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '',
        component: () => import('@/views/student/diagnostic/result.vue'),
        name: 'StudentDiagnosticResult',
        hidden: true,
        meta: { title: '诊断结果生成', noCache: true }
      }
    ]
  },
  {
    path: '/learning/diagnostic/report',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '',
        component: () => import('@/views/student/diagnostic/report.vue'),
        name: 'StudentDiagnosticReport',
        hidden: true,
        meta: { title: '首次诊断报告', noCache: true }
      }
    ]
  },
  {
    path: '/learning/onboarding-error',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '',
        component: () => import('@/views/student/onboarding/error.vue'),
        name: 'StudentOnboardingError',
        hidden: true,
        meta: { title: '首次流程异常', noCache: true }
      }
    ]
  }
];

/**
 * 学生端菜单来自本地静态路由。Mock 预览和正式认证模式共用同一份菜单，
 * 正式模式不应请求仅供管理端使用的 `/system/menu/getRouters`。
 */
export const seedStudentRoutes = () => {
  const permissionStore = usePermissionStore();
  permissionStore.setRoutes(studentRoutes);
  permissionStore.setSidebarRouters(studentRoutes);
  permissionStore.setDefaultRoutes(studentRoutes);
  permissionStore.setTopbarRoutes(studentRoutes);
};
