<template>
  <view class="page">
    <view class="hero">
      <view class="hero-glow hero-glow-left"></view>
      <view class="hero-glow hero-glow-right"></view>
      <view class="hero-badge">长辈端注册</view>
      <view class="hero-icon">HL</view>
      <text class="hero-title">给自己建一个长辈账号</text>
      <text class="hero-subtitle">填好常用手机号和密码后，就可以独立登录查看家人为您准备的内容</text>
      <view class="hero-points">
        <text class="hero-point">默认长辈身份</text>
        <text class="hero-point">可直接登录</text>
        <text class="hero-point">大字易看</text>
      </view>
    </view>

    <view class="sheet">
      <view class="sheet-head">
        <text class="sheet-title">注册长辈账号</text>
        <text class="sheet-subtitle">输入怎么称呼您、手机号和密码，注册成功后回到登录页</text>
      </view>

      <view class="field-card">
        <text class="field-title">怎么称呼您</text>
        <input
          class="field-input"
          v-model="nickname"
          placeholder="如：王奶奶、张爷爷"
          maxlength="20"
        />
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
        <text class="field-title">设置密码</text>
        <input
          class="field-input"
          type="password"
          v-model="password"
          placeholder="请输入新密码"
        />
      </view>

      <view class="field-card">
        <text class="field-title">再输入一次</text>
        <input
          class="field-input"
          type="password"
          v-model="confirmPassword"
          placeholder="请再输入一次密码"
        />
      </view>

      <button class="primary-btn" :loading="loading" @click="register">完成注册</button>
      <button class="secondary-btn" @click="goLogin">已有账号，回去登录</button>

      <view class="helper-card">
        <text class="helper-title">注册说明</text>
        <text class="helper-text">这个页面只为长辈端创建账号，系统会自动按长辈身份注册。</text>
      </view>
    </view>

    <view class="footer-note">注册成功后就能用同一个手机号登录</view>
  </view>
</template>

<script>
import { authApi } from '../../api/index.js'

const PHONE_PATTERN = /^1[3-9]\d{9}$/

export default {
  data() {
    return {
      nickname: '',
      phone: '',
      password: '',
      confirmPassword: '',
      loading: false
    }
  },
  methods: {
    async register() {
      if (!this.nickname.trim()) {
        uni.showToast({ title: '请输入怎么称呼您', icon: 'none' })
        return
      }
      if (!PHONE_PATTERN.test(this.phone)) {
        uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
        return
      }
      if (!this.password) {
        uni.showToast({ title: '请设置登录密码', icon: 'none' })
        return
      }
      if (this.password.length < 6) {
        uni.showToast({ title: '密码至少 6 位', icon: 'none' })
        return
      }
      if (this.password !== this.confirmPassword) {
        uni.showToast({ title: '两次输入的密码不一致', icon: 'none' })
        return
      }

      this.loading = true
      uni.showLoading({ title: '注册中...' })

      try {
        await authApi.register({
          phone: this.phone,
          password: this.password,
          nickname: this.nickname.trim(),
          role: 'PARENT'
        })

        uni.showToast({ title: '注册成功', icon: 'success' })
        setTimeout(() => {
          uni.redirectTo({ url: `/pages/login/login?phone=${encodeURIComponent(this.phone)}` })
        }, 800)
      } catch (e) {
        if (!e || (!e.code && !e.errMsg && !e.statusCode)) {
          uni.showToast({ title: e?.message || '注册失败', icon: 'none' })
        }
      } finally {
        uni.hideLoading()
        this.loading = false
      }
    },
    goLogin() {
      uni.redirectTo({ url: '/pages/login/login' })
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
  font-size: 50rpx;
  font-weight: 700;
  color: #ffffff;
}

.hero-subtitle {
  margin-top: 14rpx;
  font-size: 30rpx;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.88);
  max-width: 620rpx;
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
  font-size: 34rpx;
  color: #16312c;
  font-weight: 600;
}

.primary-btn,
.secondary-btn {
  width: 100%;
  height: 104rpx;
  line-height: 104rpx;
  margin-top: 28rpx;
  border: none;
  border-radius: 999rpx;
  font-size: 34rpx;
  font-weight: 700;
}

.primary-btn {
  background: linear-gradient(135deg, #11897d 0%, #25a895 100%);
  color: #ffffff;
  box-shadow: 0 14rpx 26rpx rgba(17, 137, 125, 0.22);
}

.secondary-btn {
  margin-top: 18rpx;
  background: #edf7f5;
  color: #157d71;
}

.helper-card {
  margin-top: 24rpx;
  padding: 20rpx 22rpx;
  border-radius: 22rpx;
  background: #fff7e8;
}

.helper-title {
  display: block;
  font-size: 28rpx;
  color: #996b1f;
  font-weight: 700;
}

.helper-text {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #7c5310;
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
