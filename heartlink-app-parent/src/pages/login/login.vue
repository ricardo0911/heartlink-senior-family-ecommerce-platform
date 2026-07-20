<template>
  <view class="page">
    <view class="hero">
      <view class="hero-glow hero-glow-left"></view>
      <view class="hero-glow hero-glow-right"></view>
      <view class="hero-badge">适老化登录</view>
      <view class="hero-icon">HL</view>
      <text class="hero-title">连心选长辈端</text>
      <text class="hero-subtitle">大字更清楚，操作更简单，登录后就能查看家人为您准备的内容</text>
      <view class="hero-points">
        <text class="hero-point">大字高对比</text>
        <text class="hero-point">一键求助</text>
        <text class="hero-point">亲情聊天</text>
      </view>
    </view>

    <view class="sheet">
      <view class="sheet-head">
        <text class="sheet-title">欢迎回家</text>
        <text class="sheet-subtitle">请输入手机号和密码，登录后继续使用</text>
      </view>

      <view class="field-card">
        <text class="field-title">手机号</text>
        <input
          class="field-input"
          type="number"
          v-model="phone"
          placeholder="请输入 11 位手机号"
          maxlength="11"
        />
      </view>

      <view class="field-card">
        <text class="field-title">登录密码</text>
        <input
          class="field-input"
          type="password"
          v-model="password"
          placeholder="请输入密码"
        />
      </view>

      <button class="primary-btn" :loading="loading" @click="login">登录长辈端</button>

      <view class="secondary-actions">
        <button class="secondary-btn" @click="goRegister">注册新账号</button>
        <button class="secondary-btn secondary-btn-light" @click="goForgotPassword">忘记密码</button>
      </view>

      <view class="entry-note">子女协助处理也可以</view>

      <view class="demo-card">
        <text class="demo-title">体验账号</text>
        <text class="demo-value">13800138002 / 123456</text>
      </view>
    </view>

    <view class="footer-note">适老化设计 · 更大的字号 · 更少的干扰项</view>
  </view>
</template>

<script>
import { authApi } from '../../api/index.js'

const PHONE_PATTERN = /^1[3-9]\d{9}$/

export default {
  data() {
    return {
      phone: '',
      password: '',
      loading: false
    }
  },
  onLoad(query) {
    if (query && query.phone) {
      this.phone = String(query.phone)
    }
  },
  methods: {
    async login() {
      if (this.loading) {
        return
      }
      if (!PHONE_PATTERN.test(this.phone)) {
        uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
        return
      }
      if (!this.password) {
        uni.showToast({ title: '请输入密码', icon: 'none' })
        return
      }

      this.loading = true
      uni.showLoading({ title: '登录中...' })

      try {
        const res = await authApi.login({
          phone: this.phone,
          password: this.password,
          role: 'PARENT'
        })

        const token = String(res?.data?.token || '')
        if (!token) {
          throw new Error('登录失败，请稍后再试')
        }

        uni.setStorageSync('token', token)
        uni.setStorageSync('userInfo', res.data.user)
        uni.showToast({ title: '登录成功', icon: 'success' })

        setTimeout(() => {
          uni.switchTab({ url: '/pages/index/index' })
        }, 800)
      } catch (e) {
        if (!e || (!e.code && !e.errMsg && !e.statusCode)) {
          uni.showToast({ title: e?.message || '登录失败', icon: 'none' })
        }
      } finally {
        uni.hideLoading()
        this.loading = false
      }
    },
    goRegister() {
      uni.navigateTo({ url: '/pages/register/register' })
    },
    goForgotPassword() {
      uni.navigateTo({ url: '/pages/forgot-password/forgot-password' })
    }
  }
}
</script>

<style>
page {
  min-height: 100vh;
  background: linear-gradient(180deg, #0e8c7f 0%, #3ab6a7 46%, #f6fbfa 46%, #f6fbfa 100%);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.page {
  min-height: 100vh;
  padding: 48rpx 28rpx 36rpx;
  box-sizing: border-box;
}

.hero {
  position: relative;
  padding: 40rpx 16rpx 100rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  overflow: hidden;
}

.hero-glow {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
}

.hero-glow-left {
  width: 260rpx;
  height: 260rpx;
  top: -60rpx;
  left: -40rpx;
}

.hero-glow-right {
  width: 300rpx;
  height: 300rpx;
  top: 40rpx;
  right: -120rpx;
}

.hero-badge {
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  font-size: 24rpx;
}

.hero-icon {
  width: 128rpx;
  height: 128rpx;
  line-height: 128rpx;
  margin-top: 22rpx;
  border-radius: 36rpx;
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  font-size: 48rpx;
  font-weight: 700;
  box-shadow: 0 14rpx 28rpx rgba(10, 89, 80, 0.16);
}

.hero-title {
  margin-top: 20rpx;
  font-size: 54rpx;
  font-weight: 700;
  color: #ffffff;
}

.hero-subtitle {
  margin-top: 14rpx;
  font-size: 30rpx;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.88);
  max-width: 600rpx;
}

.hero-points {
  margin-top: 26rpx;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 14rpx;
}

.hero-point {
  padding: 14rpx 22rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.14);
  color: #ffffff;
  font-size: 26rpx;
}

.sheet {
  position: relative;
  margin-top: -42rpx;
  background: #ffffff;
  border-radius: 38rpx;
  padding: 38rpx 28rpx 32rpx;
  box-shadow: 0 18rpx 44rpx rgba(6, 95, 70, 0.12);
}

.sheet-head {
  text-align: center;
}

.sheet-title {
  font-size: 46rpx;
  font-weight: 700;
  color: #15302b;
}

.sheet-subtitle {
  display: block;
  margin-top: 12rpx;
  font-size: 28rpx;
  line-height: 1.7;
  color: #6c807b;
}

.field-card {
  margin-top: 24rpx;
  padding: 22rpx 22rpx 18rpx;
  background: #f5fbfa;
  border: 2rpx solid #d8ede9;
  border-radius: 24rpx;
}

.field-title {
  display: block;
  font-size: 28rpx;
  color: #5f7771;
}

.field-input {
  width: 100%;
  height: 68rpx;
  margin-top: 12rpx;
  font-size: 36rpx;
  color: #16312c;
  font-weight: 600;
}

.primary-btn {
  width: 100%;
  height: 104rpx;
  line-height: 104rpx;
  margin-top: 32rpx;
  border: none;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #11897d 0%, #25a895 100%);
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 700;
  box-shadow: 0 14rpx 26rpx rgba(17, 137, 125, 0.22);
}

.secondary-actions {
  margin-top: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.secondary-btn {
  width: 100%;
  height: 94rpx;
  line-height: 94rpx;
  border: none;
  border-radius: 999rpx;
  background: #e7f5f3;
  color: #147b70;
  font-size: 30rpx;
  font-weight: 700;
}

.secondary-btn-light {
  background: #f4f7fb;
  color: #4d6470;
}

.entry-note {
  margin-top: 24rpx;
  text-align: center;
  font-size: 28rpx;
  color: #6c807b;
}

.demo-card {
  margin-top: 24rpx;
  padding: 18rpx 20rpx;
  border-radius: 22rpx;
  background: #fff7e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.demo-title {
  font-size: 26rpx;
  color: #996b1f;
}

.demo-value {
  font-size: 28rpx;
  color: #7c5310;
  font-weight: 700;
}

.footer-note {
  margin-top: 24rpx;
  text-align: center;
  font-size: 24rpx;
  color: #6e8480;
}

button::after {
  border: none;
}
</style>
