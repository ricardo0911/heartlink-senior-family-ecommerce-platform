<template>
  <view class="page">
    <view class="hero">
      <view class="hero-glow hero-glow-left"></view>
      <view class="hero-glow hero-glow-right"></view>
      <view class="hero-badge">密码重置</view>
      <view class="hero-icon">HL</view>
      <text class="hero-title">重新设置登录密码</text>
      <text class="hero-subtitle">用手机号重设长辈端登录密码，设置完成后可以直接回去登录</text>
      <view class="hero-points">
        <text class="hero-point">新密码立即生效</text>
        <text class="hero-point">固定长辈身份</text>
        <text class="hero-point">大字易看</text>
      </view>
    </view>

    <view class="sheet">
      <view class="sheet-head">
        <text class="sheet-title">忘记密码</text>
        <text class="sheet-subtitle">输入注册时的手机号和新密码，保存成功后跳回登录页</text>
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
        <text class="field-title">新密码</text>
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
          placeholder="请再输入一次新密码"
        />
      </view>

      <button class="primary-btn" :loading="loading" @click="resetPassword">保存新密码</button>
      <button class="secondary-btn" @click="goLogin">返回登录</button>

      <view class="helper-card">
        <text class="helper-title">提示</text>
        <text class="helper-text">当前页面重置的是长辈端登录密码，保存成功后新密码会立即生效。</text>
      </view>
    </view>

    <view class="footer-note">如果您已经想起来密码，也可以直接回去登录</view>
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
      confirmPassword: '',
      loading: false
    }
  },
  methods: {
    async resetPassword() {
      if (!PHONE_PATTERN.test(this.phone)) {
        uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
        return
      }
      if (!this.password) {
        uni.showToast({ title: '请输入新密码', icon: 'none' })
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
      uni.showLoading({ title: '保存中...' })

      try {
        await authApi.resetPassword({
          phone: this.phone,
          password: this.password,
          role: 'PARENT'
        })

        uni.showToast({ title: '密码已重置', icon: 'success' })
        setTimeout(() => {
          uni.redirectTo({ url: `/pages/login/login?phone=${encodeURIComponent(this.phone)}` })
        }, 800)
      } catch (e) {
        if (!e || (!e.code && !e.errMsg && !e.statusCode)) {
          uni.showToast({ title: e?.message || '重置失败', icon: 'none' })
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
