import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
/* Layout */
import Layout from '@/layout/index.vue';
import PublicLayout from '@/layout/PublicLayout.vue';

/**
 * Note: 路由配置项
 *
 * hidden: true                     // 当设置 true 的时候该路由不会再侧边栏出现 如401，login等页面，或者如一些编辑页面/edit/1
 * alwaysShow: true                 // 当你一个路由下面的 children 声明的路由大于1个时，自动会变成嵌套的模式--如组件页面
 *                                  // 只有一个时，会将那个子路由当做根路由显示在侧边栏--如引导页面
 *                                  // 若你想不管路由下面的 children 声明的个数都显示你的根路由
 *                                  // 你可以设置 alwaysShow: true，这样它就会忽略之前定义的规则，一直显示根路由
 * redirect: noRedirect             // 当设置 noRedirect 的时候该路由在面包屑导航中不可被点击
 * name:'router-name'               // 设定路由的名字，一定要填写不然使用<keep-alive>时会出现各种问题
 * query: '{"id": 1, "name": "ry"}' // 访问路由的默认传递参数
 * roles: ['admin', 'common']       // 访问路由的角色权限
 * permissions: ['a:a:a', 'b:b:b']  // 访问路由的菜单权限
 * meta : {
    noCache: true                   // 如果设置为true，则不会被 <keep-alive> 缓存(默认 false)
    title: 'title'                  // 设置该路由在侧边栏和面包屑中展示的名字
    icon: 'svg-name'                // 设置该路由的图标，对应路径src/assets/icons/svg
    breadcrumb: false               // 如果设置为false，则不会在breadcrumb面包屑中显示
    activeMenu: '/system/user'      // 当路由设置了该属性，则会高亮相对应的侧边栏。
  }
 */

// 公共路由
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    component: PublicLayout,
    // 公共展示页只由根路径访问，不能被 permission store 拼进后台侧栏。
    hidden: true,
    redirect: '/home',
    children: [
      {
        path: 'home',
        component: () => import('@/views/public/HomePage.vue'),
        name: 'PublicHome',
        meta: { title: '首页', public: true }
      },
      {
        path: 'qualifications',
        component: () => import('@/views/public/PublicGuidePage.vue'),
        name: 'PublicQualifications',
        props: { guide: 'qualifications' },
        meta: { title: '资格', public: true }
      },
      {
        path: 'qualifications/:code',
        component: () => import('@/views/public/QualificationDetailPage.vue'),
        name: 'PublicQualificationDetail',
        meta: { title: '资格详情', public: true }
      },
      {
        path: 'exam-introduction',
        component: () => import('@/views/public/PublicGuidePage.vue'),
        name: 'PublicExamIntroduction',
        props: { guide: 'exam-introduction' },
        meta: { title: '考试介绍', public: true }
      },
      {
        path: 'qualification-fit-test',
        component: () => import('@/views/public/QualificationFitTestPage.vue'),
        name: 'PublicQualificationFitTest',
        meta: { title: '资格适合度测试', public: true }
      }
    ]
  },
  {
    path: '/redirect',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect/index.vue')
      }
    ]
  },
  {
    path: '/social-callback',
    hidden: true,
    component: () => import('@/layout/components/SocialCallback/index.vue')
  },
  {
    path: '/login',
    component: () => import('@/views/login.vue'),
    hidden: true
  },
  {
    path: '/register',
    component: () => import('@/views/register.vue'),
    hidden: true
  },
  {
    path: '/401',
    component: () => import('@/views/error/401.vue'),
    hidden: true
  },
  {
    path: '/admin',
    component: Layout,
    redirect: '/admin/index',
    children: [
      {
        path: 'index',
        component: () => import('@/views/index.vue'),
        name: 'AdminIndex',
        meta: { title: '首页', icon: 'dashboard', affix: true }
      }
    ]
  },
  {
    path: '/user',
    component: Layout,
    hidden: true,
    redirect: 'noredirect',
    children: [
      {
        path: 'profile',
        component: () => import('@/views/system/user/profile/index.vue'),
        name: 'Profile',
        meta: { title: '个人中心', icon: 'user' }
      }
    ]
  },
  // 考纲管理回退路由：菜单数据尚未就绪时仍保留与动态菜单一致的导航层级。
  {
    path: '/content',
    component: Layout,
    hidden: true,
    redirect: '/content/knowledge',
    meta: { title: '内容中心' },
    children: [
      {
        path: 'knowledge',
        component: () => import('@/views/certmuse/catalog/knowledge/index.vue'),
        name: 'CertmuseKnowledge',
        meta: { title: '考纲管理', activeMenu: '/content/knowledge' }
      }
    ]
  },
  {
    path: '/content/collections/new',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '',
        component: () => import('@/views/certmuse/question/collection/new.vue'),
        name: 'CertmuseCollectionNew',
        meta: { title: '新建题集', activeMenu: '/content/collections' }
      }
    ]
  },
  // These legacy detail routes used to render duplicate placeholder pages.
  // Their implemented actions now live in the corresponding management views.
  {
    path: '/content/resources/edit/:id',
    hidden: true,
    redirect: to => ({ path: '/content/resources', query: to.query, hash: to.hash })
  },
  {
    path: '/content/resources/preview/:id',
    hidden: true,
    redirect: to => ({ path: '/content/resources', query: to.query, hash: to.hash })
  },
  {
    path: '/content/questions/edit/:id',
    hidden: true,
    redirect: to => ({ path: '/content/questions', query: to.query, hash: to.hash })
  },
  {
    path: '/content/questions/preview/:id',
    hidden: true,
    redirect: to => ({ path: '/content/questions', query: to.query, hash: to.hash })
  },
  {
    path: '/review-rules/content-review/detail/:id',
    hidden: true,
    redirect: to => ({ path: '/review-rules/content-review', query: to.query, hash: to.hash })
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/error/404.vue'),
    hidden: true
  }
];

// 动态路由，基于用户权限动态去加载
export const dynamicRoutes: RouteRecordRaw[] = [];

/**
 * 创建路由
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.VITE_APP_CONTEXT_PATH),
  routes: constantRoutes,
  // 刷新时，滚动条位置还原
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition;
    }
    return { top: 0 };
  }
});

export default router;
