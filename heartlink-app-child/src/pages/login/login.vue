<template>
  <view class="page">
    <view class="hero">
      <view class="hero-orb hero-orb-left"></view>
      <view class="hero-orb hero-orb-right"></view>
      <view class="brand-mark">
        <view class="brand-heart left"></view>
        <view class="brand-heart right"></view>
      </view>
      <text class="brand-title">连心选</text>
      <text class="brand-subtitle">子女守护父母，把照护、选购和亲情沟通放到一个入口</text>
      <view class="hero-tags">
        <text class="hero-tag">照护提醒</text>
        <text class="hero-tag">智能选购</text>
        <text class="hero-tag">亲情互动</text>
      </view>
    </view>

    <view class="sheet">
      <view class="sheet-header">
        <text class="sheet-title">欢迎回来</text>
        <text class="sheet-subtitle">登录后继续照护家人</text>
      </view>

      <view class="field-card">
        <text class="field-label">手机号</text>
        <input class="field-input" type="number" v-model="phone" placeholder="请输入 11 位手机号" maxlength="11" />
      </view>

      <view class="field-card">
        <text class="field-label">登录密码</text>
        <input class="field-input" type="password" v-model="password" placeholder="请输入密码" />
      </view>

      <button class="primary-btn" @click="submit" :loading="loading">登录子女端</button>

      <view class="entry-row">
        <text class="entry-link" @click="goRegister">注册账号</text>
        <text class="entry-dot">·</text>
        <text class="entry-link" @click="goForgotPassword">忘记密码</text>
      </view>

      <view class="demo-tip">
        <text class="demo-label">体验账号</text>
        <text class="demo-value">13800138001 / 123456</text>
      </view>
    </view>
  </view>
</template>

<script>
import { authApi } from '../../api/index.js'

export default {
  data() {
    return {
      phone: '',
      password: '',
      loading: false
    }
  },
  methods: {
    async submit() {
      if (!this.phone || this.phone.length !== 11) {
        uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
        return
      }
      if (!this.password) {
        uni.showToast({ title: '请输入密码', icon: 'none' })
        return
      }
      this.loading = true
      try {
        const res = await authApi.login({ phone: this.phone, password: this.password, role: 'CHILD' })
        uni.setStorageSync('token', res.data.token)
        uni.setStorageSync('userInfo', res.data.user)
        uni.showToast({ title: '登录成功', icon: 'success' })
        setTimeout(() => { uni.switchTab({ url: '/pages/index/index' }) }, 1000)
      } catch (e) {
        console.error(e)
        uni.showToast({
          title: e?.message || '登录失败，请重试',
          icon: 'none'
        })
      } finally {
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
  background: linear-gradient(180deg, #2a63d8 0%, #4d8df5 48%, #f4f7fb 48%, #f4f7fb 100%);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.page {
  min-height: 100vh;
  padding: 56rpx 32rpx 40rpx;
  box-sizing: border-box;
}

.hero {
  position: relative;
  padding: 56rpx 18rpx 110rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  overflow: hidden;
}

.hero-orb {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  filter: blur(4rpx);
}

.hero-orb-left {
  width: 240rpx;
  height: 240rpx;
  top: -40rpx;
  left: -40rpx;
}

.hero-orb-right {
  width: 320rpx;
  height: 320rpx;
  right: -120rpx;
  top: 60rpx;
}

.brand-mark {
  position: relative;
  width: 128rpx;
  height: 116rpx;
  margin-bottom: 22rpx;
}

.brand-heart {
  position: absolute;
  width: 64rpx;
  height: 100rpx;
  background: #ffffff;
  border-radius: 64rpx 64rpx 0 0;
  top: 0;
}

.brand-heart.left {
  left: 18rpx;
  transform: rotate(-45deg);
  transform-origin: 100% 100%;
}

.brand-heart.right {
  right: 18rpx;
  transform: rotate(45deg);
  transform-origin: 0 100%;
}

.brand-title {
  font-size: 56rpx;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 2rpx;
}

.brand-subtitle {
  margin-top: 16rpx;
  font-size: 28rpx;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.82);
  max-width: 580rpx;
}

.hero-tags {
  margin-top: 28rpx;
  display: flex;
  gap: 14rpx;
  flex-wrap: wrap;
  justify-content: center;
}

.hero-tag {
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.14);
  border: 1rpx solid rgba(255, 255, 255, 0.18);
  font-size: 24rpx;
  color: #ffffff;
}

.sheet {
  position: relative;
  margin-top: -44rpx;
  background: #ffffff;
  border-radius: 36rpx;
  padding: 38rpx 30rpx 34rpx;
  box-shadow: 0 18rpx 44rpx rgba(30, 64, 175, 0.14);
}

.sheet-header {
  text-align: center;
}

.sheet-title {
  font-size: 42rpx;
  font-weight: 700;
  color: #10213a;
}

.sheet-subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #6b7a90;
}

.field-card {
  margin-top: 22rpx;
  padding: 22rpx 22rpx 18rpx;
  border-radius: 24rpx;
  background: #f8fbff;
  border: 2rpx solid #e5eefb;
}

.field-label {
  display: block;
  font-size: 24rpx;
  color: #7a8799;
}

.field-input {
  width: 100%;
  height: 60rpx;
  margin-top: 12rpx;
  font-size: 32rpx;
  color: #15253c;
  font-weight: 500;
}

.primary-btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  margin-top: 30rpx;
  border: none;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #2d6df6 0%, #4d8df5 100%);
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 700;
  box-shadow: 0 14rpx 28rpx rgba(45, 109, 246, 0.24);
}

.entry-row {
  margin-top: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18rpx;
}

.entry-link {
  font-size: 26rpx;
  color: #2d6df6;
}

.entry-dot {
  color: #9fb0c7;
  font-size: 24rpx;
}

.demo-tip {
  margin-top: 30rpx;
  padding: 18rpx 20rpx;
  border-radius: 22rpx;
  background: #fff8eb;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.demo-label {
  font-size: 24rpx;
  color: #a16207;
}

.demo-value {
  font-size: 26rpx;
  color: #7c4a0a;
  font-weight: 600;
}

button::after {
  border: none;
}
</style>
