<template>
  <view class="container">
    <view class="user-card">
      <view class="avatar-wrap">
        <image class="avatar" :src="userInfo.avatar || '/static/default-avatar.png'" mode="aspectFill"></image>
      </view>
      <view class="info">
        <text class="nickname">{{ normalizeDisplayText(userInfo.nickname, '家人') }}</text>
        <text class="phone">{{ maskPhone(userInfo.phone) }}</text>
      </view>
      <view class="identity-tag">长辈端</view>
    </view>

    <view class="bind-card">
      <view class="bind-header">
        <text class="bind-title">亲情绑定</text>
        <text class="bind-subtitle">输入亲情码或扫码绑定子女</text>
      </view>
      <view class="bind-input-row">
        <input
          class="bind-input"
          :value="bindCodeInput"
          type="text"
          maxlength="6"
          placeholder="请输入6位亲情码"
          placeholder-style="color: #b5b5b5;"
          @input="onBindCodeInput"
        />
        <button class="scan-btn" @click="scanBindCode">扫码</button>
      </view>
      <button class="bind-btn" @click="confirmBind(bindCodeInput)" :disabled="binding">
        {{ binding ? '绑定中...' : '确认绑定' }}
      </button>

      <view class="children-list" v-if="childrenList.length > 0">
        <view class="child-item" v-for="item in childrenList" :key="item.bind.id">
          <image class="child-avatar" :src="item.child.avatar || '/static/default-avatar.png'" mode="aspectFill"></image>
          <view class="child-info">
            <text class="child-name">{{ normalizeDisplayText(item.child.nickname, '子女') }}</text>
            <text class="child-relation">称呼：{{ normalizeDisplayText(item.bind.relation, '家人') }}</text>
          </view>
          <text class="child-status">已绑定</text>
        </view>
      </view>
      <view class="bind-empty" v-else>
        <text class="bind-empty-text">还没有绑定子女，输入亲情码后点击确认绑定</text>
      </view>
    </view>

    <view class="assist-card">
      <text class="assist-title">由子女协助处理</text>
      <text class="assist-text">购物订单、售后退款、优惠券、收货地址、SOS 联系人和各类流程处理由子女端统一管理，长辈端保留聊天、健康查看和一键求助。</text>
    </view>

    <view class="menu-list">
      <view class="menu-item" @click="goChat">
        <view class="menu-left">
          <view class="menu-icon">聊</view>
          <text class="menu-text">亲情聊天</text>
        </view>
        <text class="menu-arrow">›</text>
      </view>

      <view class="menu-item" @click="goHealth">
        <view class="menu-left">
          <view class="menu-icon">康</view>
          <text class="menu-text">健康档案</text>
        </view>
        <text class="menu-arrow">›</text>
      </view>

      <view class="menu-item" @click="goLogin">
        <view class="menu-left">
          <view class="menu-icon">退</view>
          <text class="menu-text">退出登录</text>
        </view>
        <text class="menu-arrow">›</text>
      </view>
    </view>
  </view>
</template>

<script>
import { familyApi } from '../../api/index.js'

export default {
  data() {
    return {
      userInfo: {},
      bindCodeInput: '',
      binding: false,
      childrenList: []
    }
  },
  onShow() {
    this.loadUserInfoLocal()
    this.loadChildrenList()
  },
  methods: {
    loadUserInfoLocal() {
      this.userInfo = uni.getStorageSync('userInfo') || {}
    },
    maskPhone(phone) {
      const text = String(phone || '')
      const normalized = text.replace(/\s/g, '')
      if (!/^\d{11}$/.test(normalized)) {
        return '138****8001'
      }
      return normalized.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
    },
    normalizeDisplayText(value, fallback = '') {
      const text = String(value || '').trim()
      if (!text) return fallback
      if (/[�]|[閳閺鐎娴缁闂鍞鍝濞鏉鎴妫╃偛绺鹃柅鑳发]/.test(text)) return fallback || text
      return text
    },
    normalizeDigits(raw) {
      const text = String(raw || '')
      const halfWidth = text.replace(/[０-９]/g, (ch) =>
        String.fromCharCode(ch.charCodeAt(0) - 65248)
      )
      return halfWidth.replace(/\D/g, '').slice(0, 6)
    },
    onBindCodeInput(e) {
      this.bindCodeInput = this.normalizeDigits(e?.detail?.value)
    },
    extractBindCode(raw) {
      const text = String(raw || '').trim()
      if (!text) return ''
      const normalizedText = text.replace(/[０-９]/g, (ch) =>
        String.fromCharCode(ch.charCodeAt(0) - 65248)
      )
      const fromQuery = normalizedText.match(/[?&]bindCode=(\d{6})/i)
      if (fromQuery) return fromQuery[1]
      const fromPrefix = normalizedText.match(/HEARTLINK_BIND[:=](\d{6})/i)
      if (fromPrefix) return fromPrefix[1]
      const digits = this.normalizeDigits(normalizedText)
      return digits.length === 6 ? digits : ''
    },
    scanBindCode() {
      uni.scanCode({
        onlyFromCamera: false,
        scanType: ['qrCode'],
        success: async (res) => {
          const code = this.extractBindCode(res.result)
          if (!code) {
            uni.showToast({ title: '未识别到有效亲情码', icon: 'none' })
            return
          }
          this.bindCodeInput = code
          await this.confirmBind(code)
        },
        fail: (err) => {
          if (err && err.errMsg && String(err.errMsg).includes('cancel')) {
            return
          }
          uni.showToast({ title: '扫码失败，请重试', icon: 'none' })
        }
      })
    },
    async confirmBind(bindCodeInput = '') {
      const token = String(uni.getStorageSync('token') || '')
      if (!token) {
        uni.showToast({ title: '请先登录后再绑定', icon: 'none' })
        setTimeout(() => {
          uni.reLaunch({ url: '/pages/login/login' })
        }, 300)
        return
      }
      const candidate = typeof this.bindCodeInput === 'string' && this.bindCodeInput
        ? this.bindCodeInput
        : (typeof bindCodeInput === 'string' ? bindCodeInput : '')
      const bindCode = this.extractBindCode(candidate)
      if (!bindCode) {
        uni.showToast({ title: '请输入正确的6位亲情码', icon: 'none' })
        return
      }
      if (this.binding) return
      this.binding = true
      uni.showLoading({ title: '绑定中...' })
      try {
        await familyApi.confirmBind(bindCode)
        this.bindCodeInput = ''
        uni.hideLoading()
        uni.showToast({ title: '绑定成功', icon: 'success' })
        this.loadChildrenList()
      } catch (e) {
        uni.hideLoading()
        const message = String(e?.message || '')
        if (message.includes('已经绑定过')) {
          this.loadChildrenList()
          uni.showToast({ title: '该子女已绑定，无需重复绑定', icon: 'none' })
          return
        }
        uni.showToast({ title: e?.message || '绑定失败', icon: 'none' })
      } finally {
        this.binding = false
      }
    },
    async loadChildrenList() {
      try {
        const res = await familyApi.getMyChildren()
        this.childrenList = res.data || []
      } catch (e) {
        this.childrenList = []
      }
    },
    goHealth() {
      uni.navigateTo({ url: '/pages/health/health' })
    },
    goChat() {
      uni.switchTab({ url: '/pages/chat/chat' })
    },
    goLogin() {
      uni.removeStorageSync('token');
      uni.reLaunch({ url: '/pages/login/login' });
    }
  }
}
</script>

<style>
page {
  background: linear-gradient(180deg, #dbeafe 0%, #eaf3ff 52%, #f2f8ff 100%);
  min-height: 100vh;
  color: #102a43;
  font-family: 'SF\ Pro\ Text',\ 'SF\ Pro\ Display',\ 'PingFang\ SC',\ 'Segoe\ UI',\ 'Microsoft\ YaHei',\ sans-serif;
}

.container {
  padding: 24rpx;
}

.user-card {
  background: #eaf3ff;
  border: 1rpx solid #bfd8ff;
  border-radius: 28rpx;
  padding: 28rpx;
  display: flex;
  align-items: center;
  margin-bottom: 20rpx;
  box-shadow: 0 10rpx 24rpx rgba(37, 99, 235, 0.12);
  animation: fade-up 320ms ease-out both;
}

.avatar-wrap {
  width: 132rpx;
  height: 132rpx;
  border-radius: 50%;
  background: linear-gradient(140deg, #93c5fd 0%, #60a5fa 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar {
  width: 124rpx;
  height: 124rpx;
  border-radius: 50%;
  border: 4rpx solid #ffffff;
}

.info {
  margin-left: 22rpx;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.nickname {
  font-size: 42rpx;
  font-weight: 700;
  color: #102a43;
}

.phone {
  font-size: 28rpx;
  color: #3a5a7a;
  margin-top: 8rpx;
}

.identity-tag {
  background: #dbeafe;
  color: #1d4ed8;
  border-radius: 999rpx;
  padding: 10rpx 20rpx;
  font-size: 24rpx;
  font-weight: 600;
}

.bind-card {
  background: #f5f9ff;
  border: 1rpx solid #bfd8ff;
  border-radius: 26rpx;
  padding: 30rpx;
  margin-bottom: 30rpx;
  box-shadow: 0 10rpx 24rpx rgba(37, 99, 235, 0.1);
  animation: fade-up 440ms ease-out both;
}

.bind-header {
  margin-bottom: 20rpx;
}

.bind-title {
  display: block;
  font-size: 36rpx;
  color: #102a43;
  font-weight: 700;
}

.bind-subtitle {
  display: block;
  font-size: 24rpx;
  color: #4b647c;
  margin-top: 8rpx;
}

.bind-input-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.bind-input {
  flex: 1;
  height: 88rpx;
  background: #f8fafc;
  border: 1rpx solid #bfd8ff;
  border-radius: 16rpx;
  padding: 0 24rpx;
  font-size: 32rpx;
  box-sizing: border-box;
  color: #102a43;
}

.scan-btn {
  width: 148rpx;
  height: 88rpx;
  border: none;
  border-radius: 16rpx;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 28rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.bind-btn {
  margin-top: 18rpx;
  width: 100%;
  height: 88rpx;
  border: none;
  border-radius: 16rpx;
  background: linear-gradient(140deg, #1d4ed8 0%, #2563eb 100%);
  color: #fff;
  font-size: 32rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 10rpx 22rpx rgba(3, 105, 161, 0.2);
}

.bind-btn[disabled] {
  opacity: 0.7;
}

.children-list {
  margin-top: 22rpx;
}

.child-item {
  display: flex;
  align-items: center;
  padding: 18rpx 0 20rpx;
  border-bottom: 1rpx solid #d7e7fb;
}

.child-item:last-child {
  border-bottom: none;
}

.child-avatar {
  width: 74rpx;
  height: 74rpx;
  border-radius: 50%;
  border: 2rpx solid #bfdbfe;
}

.child-info {
  flex: 1;
  margin-left: 16rpx;
}

.child-name {
  display: block;
  font-size: 30rpx;
  color: #102a43;
  font-weight: 600;
}

.child-relation {
  display: block;
  font-size: 24rpx;
  color: #4b647c;
  margin-top: 6rpx;
}

.child-status {
  font-size: 24rpx;
  color: #15803d;
  font-weight: 600;
}

.bind-empty {
  margin-top: 20rpx;
  background: #eef5ff;
  border: 1rpx dashed #bfd8ff;
  border-radius: 14rpx;
  padding: 20rpx;
}

.bind-empty-text {
  font-size: 24rpx;
  color: #4b647c;
}

.assist-card {
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border: 1rpx solid #bfd8ff;
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 10rpx 24rpx rgba(37, 99, 235, 0.08);
}

.assist-title {
  display: block;
  color: #1e3a8a;
  font-size: 30rpx;
  font-weight: 700;
}

.assist-text {
  display: block;
  margin-top: 10rpx;
  color: #355070;
  font-size: 24rpx;
  line-height: 1.7;
}

.menu-list {
  background: #f7fbff;
  border: 1rpx solid #bfd8ff;
  border-radius: 26rpx;
  overflow: hidden;
  box-shadow: 0 10rpx 24rpx rgba(37, 99, 235, 0.1);
  animation: fade-up 500ms ease-out both;
}

.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 26rpx 24rpx;
  border-bottom: 1rpx solid #d7e7fb;
  min-height: 96rpx;
  box-sizing: border-box;
  cursor: pointer;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-item:active {
  background: #eaf3ff;
}

.menu-left {
  display: flex;
  align-items: center;
}

.menu-icon {
  width: 54rpx;
  height: 54rpx;
  border-radius: 14rpx;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 24rpx;
  font-weight: 700;
  margin-right: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.menu-text {
  font-size: 31rpx;
  color: #102a43;
  font-weight: 600;
}

.menu-arrow {
  font-size: 34rpx;
  color: #6b85a0;
}

button::after {
  border: none;
}

@keyframes fade-up {
  from {
    opacity: 0;
    transform: translateY(10rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .user-card,
  .bind-card,
  .menu-list {
    animation: none;
  }
}
</style>



