import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/dashboard'
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { title: '登录' }
    },
    {
      path: '/',
      component: () => import('@/components/AppLayout.vue'),
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '首页', requiresAuth: true }
        },
        {
          path: 'documents',
          name: 'Documents',
          component: () => import('@/views/doc/DocumentListView.vue'),
          meta: { title: '文档', requiresAuth: true }
        },
        {
          path: 'kb',
          name: 'KnowledgeBase',
          component: () => import('@/views/kb/KnowledgeBaseView.vue'),
          meta: { title: '知识库', requiresAuth: true }
        },
        {
          path: 'kb/:id',
          name: 'KbDetail',
          component: () => import('@/views/kb/KbDetailView.vue'),
          meta: { title: '知识库详情', requiresAuth: true }
        },
        {
          path: 'doc/:id',
          name: 'DocDetail',
          component: () => import('@/views/doc/DocDetailView.vue'),
          meta: { title: '文档详情', requiresAuth: true }
        },
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('@/views/profile/ProfileView.vue'),
          meta: { title: '个人中心', requiresAuth: true }
        }
      ]
    },
    {
      path: '/chat',
      name: 'Chat',
      component: () => import('@/views/chat/ChatView.vue'),
      meta: { title: '问答', requiresAuth: true }
    }
  ]
})

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || 'RAG Assistant'} - RAG Assistant`

  const token = localStorage.getItem('token')

  if (to.path === '/login') {
    if (token) {
      next('/dashboard')
    } else {
      next()
    }
    return
  }

  if (to.meta.requiresAuth && !token) {
    next('/login')
    return
  }

  next()
})

export default router
