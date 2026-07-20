import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      component: () => import('../views/login/Login.vue')
    },
    {
      path: '/',
      component: () => import('../layout/AdminLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          component: () => import('../views/dashboard/Dashboard.vue'),
          meta: { title: '数据看板' }
        },
        { path: 'product', component: () => import('../views/product/ProductList.vue') },
        { path: 'product/edit/:id?', component: () => import('../views/product/ProductEdit.vue') },
        { path: 'category', component: () => import('../views/category/CategoryList.vue') },
        { path: 'supplier', component: () => import('../views/supplier/SupplierList.vue') },
        {
          path: 'settlement',
          component: () => import('../views/settlement/SettlementView.vue'),
          meta: { title: '经营结算' }
        },
        { path: 'order', component: () => import('../views/order/OrderList.vue') },
        { path: 'logistics', component: () => import('../views/logistics/LogisticsList.vue') },
        { path: 'order/:id', component: () => import('../views/order/OrderDetail.vue') },
        { path: 'refund', component: () => import('../views/refund/RefundList.vue') },
        { path: 'user', redirect: '/user/children' },
        {
          path: 'user/children',
          component: () => import('../views/user/UserList.vue'),
          meta: { title: '子女管理', role: 'CHILD' }
        },
        {
          path: 'user/parents',
          component: () => import('../views/user/UserList.vue'),
          meta: { title: '长辈管理', role: 'PARENT' }
        },
        {
          path: 'member-points',
          component: () => import('../views/member-points/MemberPointsManagement.vue'),
          meta: { title: '积分管理' }
        },
        { path: 'coupon', component: () => import('../views/coupon/CouponList.vue') },
        { path: 'review', component: () => import('../views/review/ReviewList.vue') }
      ]
    }
  ]
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (!token && to.path !== '/login') {
    next('/login')
  } else {
    next()
  }
})

export default router
