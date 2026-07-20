<template>
  <el-container class="hl-admin-layout" :class="{ 'hl-menu-open': mobileMenuOpen }">
    <button
      class="hl-sidebar-overlay"
      type="button"
      aria-label="关闭侧边栏"
      @click="mobileMenuOpen = false"
    ></button>

    <el-aside class="hl-sidebar" width="264px">
      <div class="hl-logo">
        <div class="hl-logo-main">
          <span class="hl-logo-icon">
            <el-icon><Connection /></el-icon>
          </span>
          <span>HeartLink</span>
        </div>
        <span class="hl-logo-badge">Admin</span>
      </div>

      <div class="hl-menu">
        <el-menu
          :default-active="route.path"
          background-color="transparent"
          text-color="var(--hl-text-secondary)"
          active-text-color="var(--hl-primary-ink)"
          router
          @select="handleSelect"
        >
          <el-menu-item v-for="item in menuItems" :key="item.index" :index="item.index">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu>
      </div>
    </el-aside>

    <el-container>
      <el-header class="hl-header">
        <div class="hl-header-left">
          <button class="hl-menu-toggle" type="button" aria-label="打开菜单" @click="toggleMobileMenu">
            <el-icon><Menu /></el-icon>
          </button>
          <div class="hl-header-title">
            <span class="hl-title-main">{{ currentTitle }}</span>
            <span class="hl-title-sub">HeartLink 电商运营中心</span>
          </div>
        </div>

        <el-dropdown @command="handleCommand">
          <span class="hl-user-info">
            <el-avatar size="small">{{ adminName.charAt(0) }}</el-avatar>
            <span class="hl-user-name">{{ adminName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="hl-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminInfo, logout } from '../api'

const router = useRouter()
const route = useRoute()

const adminName = ref('管理员')
const mobileMenuOpen = ref(false)
const isMobileViewport = ref(false)

const menuItems = [
  { index: '/dashboard', label: '数据看板', icon: 'Odometer' },
  { index: '/product', label: '商品管理', icon: 'Goods' },
  { index: '/category', label: '分类管理', icon: 'Menu' },
  { index: '/supplier', label: '供应商管理', icon: 'Collection' },
  { index: '/settlement', label: '经营结算', icon: 'DataAnalysis' },
  { index: '/order', label: '订单管理', icon: 'Document' },
  { index: '/logistics', label: '物流管理', icon: 'Van' },
  { index: '/refund', label: '退款管理', icon: 'RefreshLeft' },
  { index: '/user/children', label: '子女管理', icon: 'User' },
  { index: '/user/parents', label: '长辈管理', icon: 'UserFilled' },
  { index: '/member-points', label: '积分管理', icon: 'Coin' },
  { index: '/coupon', label: '优惠券管理', icon: 'Ticket' },
  { index: '/review', label: '评价管理', icon: 'ChatDotSquare' },
]

const currentTitle = computed(() => {
  if (route.meta?.title) {
    return route.meta.title
  }
  const matched = menuItems.find((item) => route.path.startsWith(item.index))
  return matched ? matched.label : '管理后台'
})

const syncAdminNameFromCache = () => {
  try {
    const raw = localStorage.getItem('adminUser')
    if (!raw) return
    const adminUser = JSON.parse(raw)
    adminName.value = adminUser?.nickname || adminUser?.phone || '管理员'
  } catch {
    adminName.value = '管理员'
  }
}

const syncViewport = () => {
  isMobileViewport.value = window.innerWidth <= 768
  if (!isMobileViewport.value) {
    mobileMenuOpen.value = false
  }
}

const toggleMobileMenu = () => {
  if (!isMobileViewport.value) return
  mobileMenuOpen.value = !mobileMenuOpen.value
}

const handleSelect = () => {
  if (isMobileViewport.value) {
    mobileMenuOpen.value = false
  }
}

const handleCommand = async (cmd) => {
  if (cmd !== 'logout') return

  await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
  try {
    await logout()
  } catch {
    // ignore backend logout error, continue local cleanup
  }

  localStorage.removeItem('token')
  localStorage.removeItem('adminUser')
  mobileMenuOpen.value = false
  router.push('/login')
  ElMessage.success('已退出登录')
}

onMounted(async () => {
  syncViewport()
  syncAdminNameFromCache()
  window.addEventListener('resize', syncViewport)

  try {
    const res = await getAdminInfo()
    const adminUser = res.data || null
    adminName.value = adminUser?.nickname || adminUser?.phone || '管理员'
    if (adminUser) {
      localStorage.setItem('adminUser', JSON.stringify(adminUser))
    }
  } catch {
    syncAdminNameFromCache()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
})
</script>

<style scoped>
/* Styles moved to layout.scss */
</style>
