<template>
  <div class="hl-login">
    <div class="hl-login-backdrop" aria-hidden="true">
      <span class="hl-login-orb hl-orb-a"></span>
      <span class="hl-login-orb hl-orb-b"></span>
    </div>

    <div class="hl-login-shell hl-enter">
      <aside class="hl-login-showcase">
        <div class="hl-showcase-head">
          <span class="hl-showcase-brand">
            <el-icon><Connection /></el-icon>
            HeartLink Admin
          </span>
          <h1>让运营决策更快、更稳、更清晰</h1>
          <p>
            统一管理商品、订单、用户与评价数据，使用结构化面板快速定位异常并完成处理。
          </p>
        </div>

        <div class="hl-showcase-points">
          <div class="hl-point">实时销售趋势与订单动态并排展示，关键信息一眼可读。</div>
          <div class="hl-point">后台交互遵循低干扰原则，减少视觉噪音，聚焦任务效率。</div>
          <div class="hl-point">移动端支持自适应导航，保证随时可用。</div>
        </div>

        <div class="hl-showcase-foot">HeartLink · 家庭关怀电商平台</div>
      </aside>

      <section class="hl-login-card">
        <h2 class="hl-login-title">欢迎登录</h2>
        <p class="hl-login-subtitle">请输入管理员账号与密码，进入运营控制台。</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          class="hl-login-form"
          @keyup.enter="handleLogin"
        >
          <el-form-item prop="phone">
            <el-input
              v-model="form.phone"
              placeholder="账号"
              autocomplete="username"
              :prefix-icon="User"
              size="large"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              autocomplete="current-password"
              :prefix-icon="Lock"
              show-password
              size="large"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="loading" @click="handleLogin">
              登录后台
            </el-button>
          </el-form-item>
        </el-form>

        <div class="hl-login-hint">
          首次部署可直接输入你的管理员账号和密码，系统会自动初始化首个管理员账户。
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Connection, Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login } from '../../api'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  phone: '',
  password: ''
})

const rules = {
  phone: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!formRef.value) {
    return
  }

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const res = await login(form)
    const token = res.data?.token || res.token
    const user = res.data?.user || res.user || null
    if (!token) {
      throw new Error('登录返回 token 为空')
    }

    localStorage.setItem('token', token)
    if (user) {
      localStorage.setItem('adminUser', JSON.stringify(user))
    }
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch {
    // The axios interceptor already shows backend errors.
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* Styles are defined in pages.scss */
</style>
