<template>
  <view class="container">
    <view class="header">
      <view class="header-bg"></view>
      <swiper
        class="parent-swiper"
        v-if="parentList.length > 0"
        :current="currentSwiperIndex"
        @change="onSwiperChange"
        indicator-dots
        indicator-color="rgba(255,255,255,0.4)"
        indicator-active-color="#ffffff"
        circular
        autoplay
        :interval="5000"
      >
        <swiper-item
          v-for="(item, index) in parentList"
          :key="item.bind.id"
          @click="selectParentByIndex(index)"
        >
          <view class="parent-slide">
            <image class="parent-bg-avatar" :src="item.parent.avatar || '/static/default-avatar.png'" mode="aspectFill"></image>
            <view class="parent-overlay"></view>
            <view class="parent-content">
              <view class="avatar-container">
                <image class="parent-main-avatar" :src="item.parent.avatar || '/static/default-avatar.png'" mode="aspectFill"></image>
                <view class="unread-badge" v-if="unreadCount > 0 && selectedParentId === item.parent.id"
                >{{ unreadCount > 99 ? '99+' : unreadCount }}</view>
              </view>
              <text class="parent-relation">{{ normalizeDisplayText(item.bind.relation || item.parent.nickname, '家人') }}</text>
              <text class="parent-hint">{{ selectedParentId === item.parent.id ? '当前选购对象' : '点击切换' }}</text>
            </view>
          </view>
        </swiper-item>
      </swiper>
      <view class="empty-swiper" v-else @click="goBind">
        <view class="empty-content">
          <text class="empty-icon">💝</text>
          <text class="empty-title">还没有绑定长辈</text>
          <text class="empty-hint">绑定后为他们精选好物</text>
          <view class="empty-btn">立即绑定</view>
        </view>
      </view>
    </view>
    <view class="section section-top">
      <view class="quick-actions">
        <view class="action-item" @click="goProducts">
          <view class="action-icon">🛒</view>
          <text class="action-text">智能选购</text>
        </view>
        <view class="action-item" @click="goProfile">
          <view class="action-icon">📋</view>
          <text class="action-text">健康档案</text>
        </view>
        <view class="action-item" @click="goShelf">
          <view class="action-icon">📦</view>
          <text class="action-text">已推送</text>
        </view>
        <view class="action-item" @click="goOrders">
          <view class="action-icon">📝</view>
          <text class="action-text">订单</text>
        </view>
        <view class="action-item" @click="goChat">
          <view class="action-icon">💬</view>
          <text class="action-text">消息</text>
        </view>
        <view class="action-item" @click="goReport">
          <view class="action-icon">📊</view>
          <text class="action-text">周报</text>
        </view>
      </view>
    </view>
    <view class="section" v-if="selectedParentId">
      <view class="section-header">
        <text class="section-title">健康异常预警</text>
        <text class="section-more" @click="goAlerts">查看全部</text>
      </view>
      <view class="health-alert-card" @click="goAlerts">
        <view class="health-alert-top">
          <view class="health-alert-main">
            <text class="health-alert-label">当前照护风险</text>
            <text class="health-alert-level" :class="getRiskClass(alertSummary && alertSummary.riskLevel)">
              {{ getRiskText(alertSummary && alertSummary.riskLevel) }}
            </text>
          </view>
          <view class="health-alert-count" :class="getRiskClass(alertSummary && alertSummary.riskLevel)">
            {{ (alertSummary && alertSummary.alertCount) || 0 }} 项
          </view>
        </view>
        <text class="health-alert-summary">
          {{ alertsLoading ? '正在分析当前照护风险，请稍候...' : ((alertSummary && alertSummary.summary) || '当前暂无明显异常，建议保持提醒和档案更新。') }}
        </text>
        <view class="health-alert-preview" v-if="alertSummary && alertSummary.alerts && alertSummary.alerts.length > 0">
          <view class="health-alert-preview-item" v-for="(item, index) in alertSummary.alerts.slice(0, 2)" :key="`${item.title}-${index}`">
            <text class="health-alert-dot" :class="getRiskClass(item.level)"></text>
            <text class="health-alert-preview-text">{{ item.title }}</text>
          </view>
        </view>
        <text class="health-alert-empty" v-else-if="!alertsLoading">当前没有需要立即处理的异常，可定期查看周报。</text>
      </view>
    </view>
    <view class="section">
      <view class="section-header">
        <text class="section-title">为 {{ selectedParentName }} 推荐</text>
      </view>
      <view class="product-list">
        <view
          class="product-card"
          v-for="product in recommendList"
          :key="product.id"
          @click="goProductDetail(product.id)"
        >
          <image class="product-image" :src="getImageUrl(product.images && product.images[0], product.name)" mode="aspectFill"></image>
          <view class="product-info">
            <text class="product-name">{{ product.name }}</text>
            <view class="product-tags" v-if="product.healthTags">
              <text class="tag success" v-for="tag in product.healthTags.slice(0,2)" :key="tag"
              >{{ tag }}</text>
            </view>
            <view class="product-bottom">
              <text class="product-price">{{ product.price }}</text>
              <text class="product-sales">{{ product.sales }}人购买</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>

import { appConfig, authApi, familyApi, productApi, messageApi, profileApi } from '../../api/index.js'
import { resolveProductImageUrl } from '../../utils/product-image.js'

const WS_BASE_URL = appConfig.wsChatUrl
const ENABLE_HOME_REALTIME = true

export default {
  data() {

    return {

      userInfo: null,

      parentList: [],

      selectedParentId: null,

      currentSwiperIndex: 0,
      recommendList: [],
      alertSummary: null,
      alertsLoading: false,
      hasBind: false,
      unreadCount: 0,
      socketTask: null,
      socketConnected: false,
      socketConnecting: false,
      socketManualClose: false,
      socketClosing: false,
      socketReconnectTimer: null,
      socketSessionId: 0,
      lastSosMessageId: '',
      sosAudioContext: null,
      realtimeDisabled: !ENABLE_HOME_REALTIME
    }
  },
  computed: {

    selectedParentName() {

      if (this.parentList.length === 0) return '长辈'
      const selected = this.parentList.find(p => p.parent.id === this.selectedParentId)
      if (!selected) return '长辈'
      return this.normalizeDisplayText(selected.bind.relation || selected.parent.nickname, '长辈')
    }

  },

  onShow() {
    this.checkLogin()
  },
  onHide() {
    this.cleanupSosAudio()
    this.disconnectRealtime()
  },
  onUnload() {
    this.cleanupSosAudio()
    this.disconnectRealtime()
  },
  methods: {
    async checkLogin() {
      const token = uni.getStorageSync('token')
      if (!token) {
        uni.redirectTo({ url: '/pages/login/login' })
        return
      }
      const loggedIn = await this.loadUserInfo()
      if (!loggedIn) {
        return
      }
      const parentsLoaded = await this.loadParents()
      if (!parentsLoaded) {
        return
      }
      await Promise.all([this.loadRecommend(), this.loadHealthAlerts()])
      if (!this.realtimeDisabled) {
        this.socketManualClose = false
        this.connectRealtime()
      } else {
        this.disconnectRealtime()
      }
    },
    async loadUserInfo() {

      try {

        const res = await authApi.getCurrentUser()

        this.userInfo = res.data
        return true

      } catch (e) {

        if (e && e.code === 401) {
          return false
        }
        console.error(e)
        return false

      }

    },

    async loadParents() {

      try {

        const res = await familyApi.getMyParents()

        this.parentList = res.data || []

        this.hasBind = this.parentList.length > 0

        if (this.parentList.length > 0 && !this.selectedParentId) {

          this.selectedParentId = this.parentList[0].parent.id

        }

        this.loadUnreadCount()
        return true

      } catch (e) {

        if (e && e.code === 401) {
          return false
        }
        console.error(e)
        return false

      }

    },

    async loadUnreadCount() {

      try {

        const res = await messageApi.unreadCount()

        this.unreadCount = (res.data && res.data.count) || 0

      } catch (e) {

        console.error(e)

      }

    },

    async loadRecommend() {

      try {

        const res = await productApi.getRecommend(this.selectedParentId, 6)

        this.recommendList = res.data || []

      } catch (e) {

        console.error(e)

      }

    },

    async loadHealthAlerts() {
      if (!this.selectedParentId) {
        this.alertSummary = null
        return
      }
      this.alertsLoading = true
      try {
        const res = await profileApi.getHealthAlerts(this.selectedParentId)
        this.alertSummary = res.data || null
      } catch (e) {
        console.warn('health alerts unavailable', e)
        this.alertSummary = this.createAlertFallbackSummary()
      } finally {
        this.alertsLoading = false
      }
    },
    createAlertFallbackSummary() {
      return {
        displayName: this.selectedParentName || '家人',
        riskLevel: 'STABLE',
        riskScore: 0,
        alertCount: 0,
        summary: '健康预警分析暂时不可用，可先查看周报与档案。',
        stats: {
          high: 0,
          medium: 0,
          low: 0
        },
        alerts: []
      }
    },

    selectParent(item) {

      this.selectedParentId = item.parent.id

      this.loadRecommend()
      this.loadHealthAlerts()

    },

    onSwiperChange(e) {

      const index = e.detail.current

      this.currentSwiperIndex = index

      if (this.parentList[index]) {

        this.selectedParentId = this.parentList[index].parent.id

        this.loadRecommend()
        this.loadHealthAlerts()

      }

    },

    selectParentByIndex(index) {

      if (this.parentList[index]) {

        this.selectedParentId = this.parentList[index].parent.id

        this.currentSwiperIndex = index

        this.loadRecommend()
        this.loadHealthAlerts()

      }

    },

    goBind() {

      uni.navigateTo({ url: '/pages/bind/bind' })

    },

    goProfile() {

      if (!this.selectedParentId) {

        uni.showToast({ title: '请先绑定长辈', icon: 'none' })
        return

      }

      uni.navigateTo({ url: `/pages/profile/profile?parentId=${this.selectedParentId}` })

    },

    goProducts() {

      uni.switchTab({ url: '/pages/products/products' })

    },

    goShelf() {

      uni.switchTab({ url: '/pages/shelf/shelf' })

    },

    goOrders() {
      uni.navigateTo({ url: '/pages/orders/orders' })

    },

    goChat() {

      if (!this.selectedParentId || this.parentList.length === 0) {

        uni.showToast({ title: '请先绑定长辈', icon: 'none' })
        return

      }

      const selected = this.parentList.find(p => p.parent.id === this.selectedParentId)
      if (selected) {
        const relation = encodeURIComponent(this.normalizeDisplayText(selected.bind.relation || selected.parent.nickname, '消息'))
        const avatar = encodeURIComponent(selected.parent.avatar || '/static/default-avatar.png')
        uni.navigateTo({
          url: `/pages/chat/chat?familyBindId=${selected.bind.id}&parentId=${selected.parent.id}&relation=${relation}&avatar=${avatar}`
        })
      }
    },
    goReport() {

      if (!this.selectedParentId) {

        uni.showToast({ title: '请先绑定长辈', icon: 'none' })
        return

      }

      uni.navigateTo({ url: `/pages/report/report?parentId=${this.selectedParentId}` })

    },

    goAlerts() {
      if (!this.selectedParentId) {
        uni.showToast({ title: '请先绑定长辈', icon: 'none' })
        return
      }
      uni.navigateTo({ url: `/pages/alerts/alerts?parentId=${this.selectedParentId}` })
    },

    goProductDetail(id) {

      // 只有 selectedParentId 有效时才传参
      let url = `/pages/product-detail/product-detail?id=${id}`

      if (this.selectedParentId) {

        url += `&parentId=${this.selectedParentId}`

      }

      uni.navigateTo({ url })

    },

    normalizeDisplayText(value, fallback = '') {
      const text = String(value || '').trim()
      if (!text) return fallback
      // Common mojibake markers from previous encoding issues
      if (/[�]|[閹閺鐎娴缁闂鍞鍝濞鏉鎴]/.test(text)) return fallback || text
      return text
    },
    getRiskText(level) {
      const map = {
        HIGH: '高风险',
        MEDIUM: '中风险',
        LOW: '低风险',
        STABLE: '平稳'
      }
      return map[level] || '平稳'
    },
    getRiskClass(level) {
      const map = {
        HIGH: 'is-high',
        MEDIUM: 'is-medium',
        LOW: 'is-low',
        STABLE: 'is-stable'
      }
      return map[level] || 'is-stable'
    },
    connectRealtime() {
      const token = uni.getStorageSync('token')
      if (this.realtimeDisabled) {
        this.disconnectRealtime()
        return
      }
      if (!token) return
      if (this.socketTask || this.socketConnecting || this.socketConnected) return
      if (this.socketClosing) {
        this.scheduleRealtimeReconnect(1200)
        return
      }

      this.socketManualClose = false
      this.socketConnecting = true
      this.socketClosing = false
      const sessionId = Date.now()
      this.socketSessionId = sessionId
      let socket = null
      try {
        const separator = String(WS_BASE_URL).includes('?') ? '&' : '?'
        const wsUrl = `${WS_BASE_URL}${separator}token=${encodeURIComponent(token)}`
        socket = uni.connectSocket({
          url: wsUrl,
          header: {
            Authorization: token
          }
        })
      } catch (e) {
        this.socketConnecting = false
        console.error('ws connect error', e)
        this.scheduleRealtimeReconnect()
        return
      }
      if (!socket) {
        this.socketConnecting = false
        this.scheduleRealtimeReconnect()
        return
      }
      this.socketTask = socket
      const isStaleSocket = () => this.socketSessionId !== sessionId || this.socketTask !== socket

      const onOpen = () => {
        if (isStaleSocket()) return
        this.socketConnecting = false
        this.socketClosing = false
        this.socketConnected = true
      }
      const onMessage = (res) => {
        if (isStaleSocket()) return
        this.handleRealtimeMessage(res && res.data)
      }
      const onError = (err) => {
        if (isStaleSocket()) return
        this.socketConnecting = false
        if (this.handleSocketAuthFailure(err)) return
        this.socketConnected = false
        this.socketTask = null
        this.socketClosing = false
        const errMsg = String((err && err.errMsg) || '').toLowerCase()
        if (
          errMsg.includes('未完成')
          || errMsg.includes('not finished')
          || errMsg.includes('in progress')
          || errMsg.includes('同时最多发起')
          || errMsg.includes('最多发起 2 个 socket')
        ) {
          this.scheduleRealtimeReconnect(8000)
          return
        }
        console.error('ws error', err)
        this.realtimeDisabled = true
      }
      const onClose = (evt) => {
        if (isStaleSocket()) return
        this.socketConnecting = false
        if (this.handleSocketAuthFailure(evt)) return
        const isManualClose = this.socketManualClose || this.socketClosing
        this.socketConnected = false
        this.socketTask = null
        this.socketClosing = false
        if (!isManualClose && !this.realtimeDisabled) {
          this.scheduleRealtimeReconnect()
        }
      }

      // Some runtimes return a plain object instead of SocketTask.
      if (socket && typeof socket.onOpen === 'function') {
        socket.onOpen(onOpen)
        socket.onMessage(onMessage)
        socket.onError(onError)
        socket.onClose(onClose)
        return
      }

      // Fallback to global websocket listeners.
      if (typeof uni.offSocketOpen === 'function') uni.offSocketOpen()
      if (typeof uni.offSocketMessage === 'function') uni.offSocketMessage()
      if (typeof uni.offSocketError === 'function') uni.offSocketError()
      if (typeof uni.offSocketClose === 'function') uni.offSocketClose()
      if (typeof uni.onSocketOpen === 'function') uni.onSocketOpen(onOpen)
      if (typeof uni.onSocketMessage === 'function') uni.onSocketMessage(onMessage)
      if (typeof uni.onSocketError === 'function') uni.onSocketError(onError)
      if (typeof uni.onSocketClose === 'function') uni.onSocketClose(onClose)
    },
    disconnectRealtime() {
      this.socketManualClose = true
      this.socketConnecting = false
      if (this.socketReconnectTimer) {
        clearTimeout(this.socketReconnectTimer)
        this.socketReconnectTimer = null
      }
      if (typeof uni.offSocketOpen === 'function') uni.offSocketOpen()
      if (typeof uni.offSocketMessage === 'function') uni.offSocketMessage()
      if (typeof uni.offSocketError === 'function') uni.offSocketError()
      if (typeof uni.offSocketClose === 'function') uni.offSocketClose()
      const socket = this.socketTask
      this.socketTask = null
      this.socketConnected = false
      this.socketSessionId += 1
      this.socketClosing = !!socket
      if (!socket) {
        this.socketClosing = false
        return
      }
      try {
        if (socket && typeof socket.close === 'function') {
          socket.close({
            code: 1000,
            reason: 'page hide',
            fail: () => {},
            complete: () => {
              this.socketClosing = false
            }
          })
        } else if (typeof uni.closeSocket === 'function') {
          uni.closeSocket({
            code: 1000,
            reason: 'page hide',
            fail: () => {},
            complete: () => {
              this.socketClosing = false
            }
          })
        } else {
          this.socketClosing = false
        }
      } catch (e) {
        console.error(e)
        this.socketClosing = false
      }
    },
    scheduleRealtimeReconnect(delay = 3000) {
      if (this.realtimeDisabled) return
      if (this.socketManualClose || this.socketReconnectTimer) return
      if (this.socketTask || this.socketConnecting || this.socketConnected) return
      if (!uni.getStorageSync('token')) return
      this.socketReconnectTimer = setTimeout(() => {
        this.socketReconnectTimer = null
        this.connectRealtime()
      }, delay)
    },
    handleSocketAuthFailure(event) {
      const code = Number(event && event.code)
      const rawReason = event && (event.reason || event.errMsg || '')
      const reason = String(rawReason).toLowerCase()
      const isAuthFailure = code === 1008 || reason.includes('invalid token') || reason.includes('401')
      if (!isAuthFailure) return false

      this.socketManualClose = true
      this.socketConnected = false
      this.socketTask = null
      this.socketClosing = false
      this.socketSessionId += 1
      if (this.socketReconnectTimer) {
        clearTimeout(this.socketReconnectTimer)
        this.socketReconnectTimer = null
      }

      uni.removeStorageSync('token')
      uni.removeStorageSync('userInfo')
      uni.showToast({ title: '登录状态已失效，请重新登录', icon: 'none' })
      setTimeout(() => {
        uni.reLaunch({ url: '/pages/login/login' })
      }, 300)
      return true
    },
    handleRealtimeMessage(raw) {
      if (!raw) return
      let payload = raw
      if (typeof raw === 'string') {
        try {
          payload = JSON.parse(raw)
        } catch (e) {
          return
        }
      }
      const type = payload.type
      if (type === 'SOS_ALERT') {
        this.handleSosAlert(payload.data || {})
      }
      if (type === 'CHAT_MESSAGE') {
        this.loadUnreadCount()
      }
    },
    handleSosAlert(data) {
      const messageId = String(data.messageId || '')
      if (messageId && messageId === this.lastSosMessageId) {
        return
      }
      this.lastSosMessageId = messageId

      const parentName = data.parentName || '家人'
      const content = data.content || `${parentName}发起了紧急求助，请尽快联系。`
      this.playSosVoice(content)
      this.vibrateForSos()
      uni.showModal({
        title: '紧急求助提醒',
        content,
        showCancel: false,
        confirmText: '我知道了'
      })
    },
    playSosVoice(content) {
      const voiceText = String(content || '收到紧急求助，请尽快联系家人。').trim()
      if (!voiceText) return

      try {
        if (typeof __wxConfig !== 'undefined') {
          const pluginConfig = __wxConfig && __wxConfig.plugins
          if (!pluginConfig || !pluginConfig.WechatSI) return
        }
        if (typeof requirePlugin !== 'function') return
        const plugin = requirePlugin('WechatSI')
        if (!plugin || typeof plugin.textToSpeech !== 'function') return

        plugin.textToSpeech({
          lang: 'zh_CN',
          tts: true,
          content: voiceText,
          success: (res) => {
            const filename = res && res.filename
            if (!filename) return

            if (this.sosAudioContext) {
              try {
                this.sosAudioContext.stop()
                this.sosAudioContext.destroy()
              } catch (e) {}
            }

            const audio = uni.createInnerAudioContext()
            audio.autoplay = true
            audio.src = filename
            audio.onEnded(() => {
              audio.destroy()
              if (this.sosAudioContext === audio) {
                this.sosAudioContext = null
              }
            })
            audio.onError(() => {
              audio.destroy()
              if (this.sosAudioContext === audio) {
                this.sosAudioContext = null
              }
            })
            this.sosAudioContext = audio
          },
          fail: () => {}
        })
      } catch (e) {}
    },
    vibrateForSos() {
      try {
        if (typeof uni.vibrateLong === 'function') {
          uni.vibrateLong()
        }
      } catch (e) {}
    },
    cleanupSosAudio() {
      if (!this.sosAudioContext) return
      try {
        this.sosAudioContext.stop()
        this.sosAudioContext.destroy()
      } catch (e) {}
      this.sosAudioContext = null
    },
    getImageUrl(url, productName = '') {
      return resolveProductImageUrl(url, { productName })
    }

  }

}

</script>



<style>
page {
  background: linear-gradient(180deg, #f4f6fa 0%, #eef2f7 45%, #f7f9fc 100%);
  min-height: 100vh;
  color: var(--hl-text-primary);
  font-family: 'SF\ Pro\ Text',\ 'SF\ Pro\ Display',\ 'PingFang\ SC',\ 'Segoe\ UI',\ 'Microsoft\ YaHei',\ sans-serif;
}

.container {
  min-height: 100vh;
  padding-bottom: 140rpx;
}

.header {
  position: relative;
  padding: 0;
}

.header-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 280rpx;
  background: linear-gradient(135deg, var(--hl-primary) 0%, var(--hl-primary-2) 50%, var(--hl-primary-3) 100%);
  z-index: 0;
}

.parent-swiper {
  position: relative;
  width: 100%;
  height: 308rpx;
  padding: 16rpx 22rpx 12rpx;
  box-sizing: border-box;
  z-index: 1;
}

.parent-slide {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  border-radius: 30rpx;
  background: linear-gradient(138deg, #1d4ed8 0%, #2563eb 55%, #0ea5e9 100%);
  border: 1rpx solid #9ec5ff;
  box-shadow: 0 16rpx 36rpx rgba(29, 78, 216, 0.26);
}

.parent-bg-avatar {
  position: absolute;
  right: -24rpx;
  bottom: -36rpx;
  width: 220rpx;
  height: 220rpx;
  border-radius: 50%;
  filter: none;
  transform: none;
  opacity: 0.22;
}

.parent-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(120deg, #1e40af 0%, #2563eb 52%, #0284c7 100%);
}

.parent-content {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 8rpx;
}

.parent-main-avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  border: 4rpx solid #ffffff;
  box-shadow: 0 10rpx 24rpx rgba(15, 23, 42, 0.28);
}

.avatar-container {
  position: relative;
  display: inline-block;
}

.unread-badge {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  background: linear-gradient(135deg, var(--hl-primary-2) 0%, var(--hl-primary) 100%);
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
  min-width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  text-align: center;
  border-radius: 16rpx;
  padding: 0 8rpx;
  border: 2rpx solid #fff;
}

.parent-relation {
  font-size: 36rpx;
  font-weight: 700;
  color: #fff;
  margin-top: 12rpx;
  text-shadow: 0 3rpx 8rpx rgba(15, 23, 42, 0.2);
}

.parent-hint {
  font-size: 22rpx;
  color: #eaf2ff;
  margin-top: 8rpx;
}

.empty-swiper {
  position: relative;
  width: 100%;
  height: 280rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16rpx 24rpx 12rpx;
}

.empty-content {
  width: 100%;
  min-height: 236rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f8fbff;
  border: 1rpx solid #bfd8ff;
  border-radius: 28rpx;
  box-shadow: 0 10rpx 24rpx rgba(37, 99, 235, 0.14);
  padding: 18rpx 20rpx 24rpx;
  box-sizing: border-box;
}

.empty-icon {
  font-size: 84rpx;
}

.empty-title {
  font-size: 38rpx;
  font-weight: 600;
  color: #14344f;
  margin-top: 12rpx;
}

.empty-hint {
  font-size: 27rpx;
  color: #4b647c;
  margin-top: 10rpx;
}

.empty-btn {
  min-width: 236rpx;
  height: 86rpx;
  line-height: 86rpx;
  text-align: center;
  background: linear-gradient(140deg, #1d4ed8 0%, #2563eb 100%);
  color: #ffffff;
  padding: 0 52rpx;
  border-radius: 44rpx;
  font-size: 32rpx;
  font-weight: 600;
  margin-top: 18rpx;
  box-shadow: 0 10rpx 24rpx rgba(29, 78, 216, 0.28);
}

.section {
  padding: 22rpx 24rpx;
}

.section-top {
  margin-top: 10rpx;
  position: relative;
  z-index: 2;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28rpx;
  padding: 0 8rpx;
}

.section-title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--hl-text-primary);
  letter-spacing: 1rpx;
}

.section-more {
  font-size: 28rpx;
  color: var(--hl-primary);
  font-weight: 500;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  background: #fff;
  border-radius: 32rpx;
  padding: 28rpx 8rpx 20rpx;
  box-shadow: 0 8rpx 22rpx rgba(12, 74, 110, 0.08);
  border: 1rpx solid var(--hl-border);
  animation: fade-up 380ms ease-out both;
  row-gap: 20rpx;
}

.action-item {
  flex: 0 0 33.3333%;
  max-width: 33.3333%;
  display: flex;
  flex-direction: column;
  align-items: center;
  transition: transform 0.2s ease;
}

.action-item:active {
  transform: scale(0.92);
}

.action-icon {
  width: 92rpx;
  height: 92rpx;
  background: var(--hl-surface-alt);
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 46rpx;
  color: var(--hl-primary);
  box-shadow: 0 8rpx 20rpx rgba(3, 105, 161, 0.12);
}

.action-text {
  font-size: 28rpx;
  font-weight: 600;
  color: #444;
  margin-top: 14rpx;
  letter-spacing: 1rpx;
  line-height: 1.2;
}

.health-alert-card {
  background: linear-gradient(145deg, #ffffff 0%, #f8fbff 100%);
  border-radius: 30rpx;
  padding: 28rpx;
  border: 1rpx solid #dbeafe;
  box-shadow: 0 10rpx 26rpx rgba(12, 74, 110, 0.08);
}

.health-alert-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20rpx;
}

.health-alert-main {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.health-alert-label {
  font-size: 24rpx;
  color: #64748b;
}

.health-alert-level {
  font-size: 38rpx;
  font-weight: 700;
}

.health-alert-count {
  min-width: 110rpx;
  text-align: center;
  padding: 14rpx 18rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 700;
  background: #dbeafe;
  color: #1d4ed8;
}

.health-alert-level.is-high,
.health-alert-count.is-high,
.health-alert-dot.is-high {
  color: #b91c1c;
}

.health-alert-count.is-high {
  background: #fee2e2;
}

.health-alert-level.is-medium,
.health-alert-count.is-medium,
.health-alert-dot.is-medium {
  color: #b45309;
}

.health-alert-count.is-medium {
  background: #fef3c7;
}

.health-alert-level.is-low,
.health-alert-count.is-low,
.health-alert-dot.is-low,
.health-alert-level.is-stable,
.health-alert-count.is-stable,
.health-alert-dot.is-stable {
  color: #0f766e;
}

.health-alert-count.is-low,
.health-alert-count.is-stable {
  background: #ccfbf1;
}

.health-alert-summary {
  display: block;
  margin-top: 22rpx;
  font-size: 28rpx;
  line-height: 1.7;
  color: #334155;
}

.health-alert-preview {
  margin-top: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.health-alert-preview-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 14rpx 16rpx;
  border-radius: 18rpx;
  background: #f8fafc;
}

.health-alert-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.health-alert-preview-text,
.health-alert-empty {
  font-size: 26rpx;
  color: #475569;
  line-height: 1.6;
}

.health-alert-empty {
  display: block;
  margin-top: 18rpx;
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.product-card {
  width: 100%;
  background: #fff;
  border-radius: 28rpx;
  overflow: hidden;
  box-shadow: 0 10rpx 26rpx rgba(12, 74, 110, 0.08);
  border: 1rpx solid var(--hl-border);
  display: flex;
  flex-direction: row;
  transition: all 0.25s ease;
  animation: fade-up 460ms ease-out both;
}

.product-card:active {
  transform: translateY(2rpx);
  box-shadow: 0 4rpx 16rpx rgba(12, 74, 110, 0.06);
}

.product-image {
  width: 280rpx;
  height: 280rpx;
  flex-shrink: 0;
  background: linear-gradient(135deg, #f8f9fa 0%, #f0f1f3 100%);
}

.product-info {
  flex: 1;
  padding: 28rpx 28rpx 28rpx 24rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.product-name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--hl-text-primary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.tag {
  font-size: 22rpx;
  padding: 8rpx 16rpx;
  border-radius: 8rpx;
  font-weight: 500;
}

.tag.success {
  background: #dcfce7;
  color: #166534;
}

.product-bottom {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-top: auto;
}

.product-price {
  font-size: 44rpx;
  font-weight: 700;
  color: var(--hl-primary);
  font-family: 'DIN', 'Helvetica', sans-serif;
}

.product-price::before {
  content: '¥';
  font-size: 28rpx;
  font-weight: 600;
  margin-right: 4rpx;
}

.product-sales {
  font-size: 24rpx;
  color: #8e8e93;
  font-weight: 400;
  background: #f5f5f7;
  padding: 8rpx 16rpx;
  border-radius: 12rpx;
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
  .quick-actions,
  .product-card {
    animation: none;
  }

  .action-item,
  .product-card {
    transition: none;
  }
}
</style>



