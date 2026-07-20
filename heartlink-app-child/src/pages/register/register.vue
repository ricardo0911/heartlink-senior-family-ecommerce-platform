<template>
  <view class="page">
    <view class="panel">
      <view class="panel-head">
        <text class="panel-title">注册账号</text>
        <text class="panel-subtitle">创建子女端账号后，就可以绑定长辈并开始使用</text>
      </view>

      <view class="field-card">
        <text class="field-label">手机号</text>
        <input class="field-input" type="number" v-model="phone" placeholder="请输入 11 位手机号" maxlength="11" />
      </view>

      <view class="field-card">
        <text class="field-label">昵称</text>
        <input class="field-input" type="text" v-model="nickname" placeholder="请输入昵称" />
      </view>

      <view class="field-card">
        <text class="field-label">设置密码</text>
        <input class="field-input" type="password" v-model="password" placeholder="请输入密码" />
      </view>

      <view class="field-card">
        <text class="field-label">确认密码</text>
        <input class="field-input" type="password" v-model="confirmPassword" placeholder="请再次输入密码" />
      </view>

      <button class="primary-btn" :loading="loading" @click="submit">完成注册</button>

      <view class="bottom-link" @click="backToLogin">返回登录</view>
    </view>
  </view>
</template>

<script>
import { authApi } from '../../api/index.js'

export default {
  data() {
    return {
      phone: '',
      nickname: '',
      password: '',
      confirmPassword: '',
      loading: false
    }
  },
  methods: {
    async submit() {
      if (!this.phone || this.phone.length !== 11) {
        uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
        return
      }
      if (!this.nickname.trim()) {
        uni.showToast({ title: '请输入昵称', icon: 'none' })
        return
      }
      if (!this.password) {
        uni.showToast({ title: '请设置密码', icon: 'none' })
        return
      }
      if (this.password !== this.confirmPassword) {
        uni.showToast({ title: '两次输入的密码不一致', icon: 'none' })
        return
      }
      this.loading = true
      try {
        await authApi.register({
          phone: this.phone,
          password: this.password,
          nickname: this.nickname,
          role: 'CHILD'
        })
        uni.showToast({ title: '注册成功，请登录', icon: 'success' })
        setTimeout(() => {
          uni.navigateBack()
        }, 900)
      } catch (e) {
        console.error(e)
        uni.showToast({
          title: e?.message || '注册失败，请重试',
          icon: 'none'
        })
      } finally {
        this.loading = false
      }
    },
    backToLogin() {
      uni.navigateBack()
    }
  }
}
</script>

<style>
page {
  min-height: 100vh;
  background: linear-gradient(180deg, #edf4ff 0%, #f7faff 100%);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.page {
  min-height: 100vh;
  padding: 28rpx 24rpx;
  box-sizing: border-box;
}

.panel {
  background: #ffffff;
  border-radius: 32rpx;
  padding: 34rpx 28rpx;
  box-shadow: 0 18rpx 40rpx rgba(30, 64, 175, 0.08);
}

.panel-head {
  text-align: center;
}

.panel-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #13243b;
}

.panel-subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  color: #6e7d91;
  line-height: 1.7;
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
}

.bottom-link {
  margin-top: 24rpx;
  text-align: center;
  font-size: 26rpx;
  color: #2d6df6;
}

button::after {
  border: none;
}
</style>
