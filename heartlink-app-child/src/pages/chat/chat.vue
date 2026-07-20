<template>
  <view class="container">
    <view class="header">
      <text class="title">{{ relationName }}</text>
      <text class="subtitle" v-if="socketConnected">实时连接中</text>
      <text class="subtitle warn" v-else>离线同步中</text>
    </view>

    <scroll-view
      class="message-list"
      scroll-y
      :scroll-into-view="scrollToId"
      scroll-with-animation
    >
      <view class="empty-wrap" v-if="!loading && messages.length === 0">
        <text class="empty-text">暂无消息，发一句问候吧</text>
      </view>

      <view class="message-wrapper" v-for="(msg, index) in messages" :key="msg.id || msg.localId || index">
        <view class="time-divider" v-if="showTimeDivider(index)">
          <text class="time-text">{{ formatTime(msg.createTime) }}</text>
        </view>

        <view class="message-item sent" v-if="msg.isMine">
          <view class="bubble bubble-right">
            <view v-if="isVoiceMessage(msg)" class="voice-message" @click="playVoiceMessage(msg)">
              <text class="voice-icon">{{ isPlayingVoiceMessage(msg) ? '■' : '▶' }}</text>
              <text class="voice-label">{{ isPlayingVoiceMessage(msg) ? '播放中...' : getVoiceLabel(msg) }}</text>
            </view>
            <text v-else class="bubble-text">{{ msg.content }}</text>
          </view>
          <view class="avatar-wrap">
            <image class="msg-avatar" src="/static/default-avatar.png" mode="aspectFill"></image>
          </view>
        </view>

        <view class="message-item received" v-else>
          <view class="avatar-wrap">
            <image class="msg-avatar" :src="parentAvatar" mode="aspectFill"></image>
          </view>
          <view class="bubble bubble-left">
            <view v-if="isVoiceMessage(msg)" class="voice-message" @click="playVoiceMessage(msg)">
              <text class="voice-icon">{{ isPlayingVoiceMessage(msg) ? '■' : '▶' }}</text>
              <text class="voice-label">{{ isPlayingVoiceMessage(msg) ? '播放中...' : getVoiceLabel(msg) }}</text>
            </view>
            <text v-else class="bubble-text">{{ msg.content }}</text>
          </view>
        </view>
      </view>
      <view id="msg-bottom" style="height: 8rpx;"></view>
    </scroll-view>

    <view class="input-bar">
      <input
        class="msg-input"
        v-model="inputText"
        placeholder="输入消息..."
        confirm-type="send"
        @confirm="sendMessage"
      />
      <view class="send-btn" :class="{ active: !!inputText.trim() }" @click="sendMessage">
        <text class="send-text">发送</text>
      </view>
    </view>
  </view>
</template>

<script>
import { appConfig, familyApi, messageApi, toAbsoluteMediaUrl } from '../../api/index.js'

const WS_BASE_URL = appConfig.wsChatUrl

export default {
  data() {
    return {
      familyBindId: '',
      targetUserId: '',
      relationName: '消息',
      parentAvatar: '/static/default-avatar.png',
      messages: [],
      inputText: '',
      scrollToId: '',
      loading: false,
      currentUserId: '',
      initialized: false,
      audioPlayer: null,
      playingVoiceId: '',
      socketTask: null,
      socketConnected: false,
      socketConnecting: false,
      socketClosing: false,
      socketSessionId: 0,
      manualClose: false,
      reconnectTimer: null,
      pollTimer: null
    }
  },
  onLoad(options) {
    this.familyBindId = String(options.familyBindId || '')
    this.targetUserId = String(options.parentId || '')
    this.relationName = decodeURIComponent(options.relation || '消息')
    if (options.avatar) {
      this.parentAvatar = decodeURIComponent(options.avatar)
    }
    const userInfo = uni.getStorageSync('userInfo') || {}
    this.currentUserId = String(userInfo.id || '')
    this.initConversation()
  },
  onShow() {
    if (this.initialized && !this.socketConnected && !this.socketConnecting) {
      this.connectSocket()
      this.startFallbackPolling()
    }
  },
  onHide() {
    this.stopAudioPlayback()
    this.manualClose = true
    this.stopFallbackPolling()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.disconnectSocket()
  },
  onUnload() {
    this.stopAudioPlayback()
    this.manualClose = true
    this.stopFallbackPolling()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.disconnectSocket()
  },
  methods: {
    async initConversation() {
      await this.ensureConversationTarget()
      if (!this.familyBindId) {
        uni.showToast({ title: '未找到可用会话', icon: 'none' })
        return
      }
      await this.loadMessages()
      await this.markReadSafe()
      this.connectSocket()
      this.startFallbackPolling()
      this.initialized = true
    },
    async ensureConversationTarget() {
      try {
        const res = await familyApi.getMyParents()
        const parents = Array.isArray(res.data) ? res.data : []
        if (!parents.length) return

        if (!this.familyBindId) {
          const first = parents[0]
          this.familyBindId = String(first.bind?.id || '')
          this.targetUserId = String(first.parent?.id || '')
          this.relationName = first.bind?.relation || first.parent?.nickname || this.relationName
          this.parentAvatar = first.parent?.avatar || this.parentAvatar
          return
        }

        const matched = parents.find((item) => String(item.bind?.id) === String(this.familyBindId))
        if (matched) {
          this.targetUserId = this.targetUserId || String(matched.parent?.id || '')
          this.relationName = matched.bind?.relation || matched.parent?.nickname || this.relationName
          this.parentAvatar = matched.parent?.avatar || this.parentAvatar
        }
      } catch (e) {
        console.error(e)
      }
    },
    normalizeMessage(raw) {
      const createTime = raw.createTime || raw.createdAt || new Date().toISOString()
      const senderId = String(raw.senderId || '')
      return {
        ...raw,
        createTime,
        isMine: senderId && senderId === String(this.currentUserId)
      }
    },
    sortMessages(list) {
      return [...list].sort((a, b) => {
        const ta = new Date(a.createTime).getTime() || 0
        const tb = new Date(b.createTime).getTime() || 0
        return ta - tb
      })
    },
    dedupMessages(list) {
      const seen = new Set()
      return list.filter((item) => {
        const key = item.id ? `id-${item.id}` : `local-${item.localId || ''}-${item.createTime || ''}`
        if (seen.has(key)) return false
        seen.add(key)
        return true
      })
    },
    async loadMessages() {
      if (!this.familyBindId) return
      this.loading = true
      try {
        const res = await messageApi.list({
          familyBindId: Number(this.familyBindId),
          page: 1,
          size: 100
        })
        const pageData = res.data || {}
        const records = Array.isArray(pageData.records)
          ? pageData.records
          : (Array.isArray(pageData) ? pageData : [])
        this.messages = this.sortMessages(this.dedupMessages(records.map((item) => this.normalizeMessage(item))))
        this.scrollToBottom()
      } catch (e) {
        console.error(e)
      } finally {
        this.loading = false
      }
    },
    async markReadSafe() {
      if (!this.familyBindId) return
      try {
        await messageApi.markRead(Number(this.familyBindId))
      } catch (e) {
        console.error(e)
      }
    },
    appendMessage(raw) {
      if (!raw) return
      const normalized = this.normalizeMessage(raw)
      if (String(normalized.familyBindId || '') !== String(this.familyBindId)) return
      this.messages = this.sortMessages(this.dedupMessages([...this.messages, normalized]))
      this.scrollToBottom()
    },
    async sendMessage() {
      const content = this.inputText.trim()
      if (!content || !this.familyBindId) return
      if (!this.targetUserId) {
        await this.ensureConversationTarget()
      }
      if (!this.targetUserId) {
        uni.showToast({ title: '未找到接收方', icon: 'none' })
        return
      }

      this.inputText = ''
      const localMessage = this.normalizeMessage({
        localId: `local-${Date.now()}`,
        familyBindId: Number(this.familyBindId),
        senderId: Number(this.currentUserId),
        receiverId: Number(this.targetUserId),
        content,
        type: 'TEXT',
        createdAt: new Date().toISOString()
      })
      this.messages = this.sortMessages(this.dedupMessages([...this.messages, localMessage]))
      this.scrollToBottom()

      try {
        const res = await messageApi.send({
          familyBindId: Number(this.familyBindId),
          receiverId: Number(this.targetUserId),
          content,
          type: 'TEXT'
        })
        if (res?.data) {
          this.messages = this.messages.filter((m) => m.localId !== localMessage.localId)
          this.appendMessage(res.data)
        }
      } catch (e) {
        console.error(e)
        this.messages = this.messages.filter((m) => m.localId !== localMessage.localId)
        uni.showToast({ title: '发送失败，请重试', icon: 'none' })
      }
    },
    isVoiceMessage(message) {
      return String(message?.type || '').toUpperCase() === 'VOICE' && !!this.resolveVoiceUrl(message)
    },
    getVoiceLabel(message) {
      return String(message?.content || '').trim() || '语音消息'
    },
    resolveVoiceUrl(message) {
      const raw = String(message?.voiceUrl || '').trim()
      if (!raw) return ''
      if (/^(https?:|wxfile:|file:|blob:)/i.test(raw)) return raw
      return toAbsoluteMediaUrl(raw)
    },
    isPlayingVoiceMessage(message) {
      const id = String(message?.id || message?.localId || '')
      return id && id === String(this.playingVoiceId || '')
    },
    stopAudioPlayback() {
      if (!this.audioPlayer) {
        this.playingVoiceId = ''
        return
      }
      try {
        this.audioPlayer.stop()
      } catch (e) {}
      try {
        this.audioPlayer.destroy()
      } catch (e) {}
      this.audioPlayer = null
      this.playingVoiceId = ''
    },
    playVoiceMessage(message) {
      const source = this.resolveVoiceUrl(message)
      if (!source) {
        uni.showToast({ title: '语音文件不存在', icon: 'none' })
        return
      }

      const currentId = String(message?.id || message?.localId || '')
      if (this.playingVoiceId === currentId) {
        this.stopAudioPlayback()
        return
      }

      this.stopAudioPlayback()
      const audio = uni.createInnerAudioContext()
      audio.autoplay = true
      audio.src = source
      audio.onEnded(() => {
        if (this.audioPlayer === audio) {
          this.stopAudioPlayback()
        }
      })
      audio.onStop(() => {
        if (this.audioPlayer === audio) {
          this.stopAudioPlayback()
        }
      })
      audio.onError((err) => {
        console.error('voice playback error', err)
        if (this.audioPlayer === audio) {
          this.stopAudioPlayback()
        }
        uni.showToast({ title: '语音播放失败', icon: 'none' })
      })
      this.audioPlayer = audio
      this.playingVoiceId = currentId
    },
    connectSocket() {
      const token = uni.getStorageSync('token')
      if (!token || this.socketTask || this.socketConnecting || this.socketConnected) return
      if (this.socketClosing) {
        this.scheduleReconnect(1200)
        return
      }

      this.manualClose = false
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
        this.scheduleReconnect()
        return
      }
      if (!socket) {
        this.socketConnecting = false
        this.scheduleReconnect()
        return
      }
      this.socketTask = socket
      const isStaleSocket = () => this.socketSessionId !== sessionId || this.socketTask !== socket

      const onOpen = () => {
        if (isStaleSocket()) return
        this.socketConnecting = false
        this.socketClosing = false
        this.socketConnected = true
        this.stopFallbackPolling()
      }

      const onMessage = (res) => {
        if (isStaleSocket()) return
        this.handleSocketMessage(res.data)
      }

      const onError = (err) => {
        if (isStaleSocket()) return
        if (this.handleSocketAuthFailure(err)) return
        this.socketConnecting = false
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
          this.scheduleReconnect(8000)
          return
        }
        console.error('ws error', err)
        this.scheduleReconnect()
      }

      const onClose = (evt) => {
        if (isStaleSocket()) return
        if (this.handleSocketAuthFailure(evt)) return
        this.socketConnecting = false
        this.socketConnected = false
        this.socketTask = null
        this.socketClosing = false
        if (!this.manualClose) {
          this.scheduleReconnect()
        }
      }

      if (socket && typeof socket.onOpen === 'function') {
        socket.onOpen(onOpen)
        socket.onMessage(onMessage)
        socket.onError(onError)
        socket.onClose(onClose)
        return
      }

      if (typeof uni.offSocketOpen === 'function') uni.offSocketOpen()
      if (typeof uni.offSocketMessage === 'function') uni.offSocketMessage()
      if (typeof uni.offSocketError === 'function') uni.offSocketError()
      if (typeof uni.offSocketClose === 'function') uni.offSocketClose()
      if (typeof uni.onSocketOpen === 'function') uni.onSocketOpen(onOpen)
      if (typeof uni.onSocketMessage === 'function') uni.onSocketMessage(onMessage)
      if (typeof uni.onSocketError === 'function') uni.onSocketError(onError)
      if (typeof uni.onSocketClose === 'function') uni.onSocketClose(onClose)
    },
    disconnectSocket() {
      if (typeof uni.offSocketOpen === 'function') uni.offSocketOpen()
      if (typeof uni.offSocketMessage === 'function') uni.offSocketMessage()
      if (typeof uni.offSocketError === 'function') uni.offSocketError()
      if (typeof uni.offSocketClose === 'function') uni.offSocketClose()
      const socket = this.socketTask
      this.socketTask = null
      this.socketConnecting = false
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
    scheduleReconnect(delay = 3000) {
      if (this.manualClose || this.reconnectTimer) return
      if (this.socketTask || this.socketConnecting || this.socketConnected) return
      if (!uni.getStorageSync('token')) return
      this.startFallbackPolling()
      this.reconnectTimer = setTimeout(() => {
        this.reconnectTimer = null
        this.connectSocket()
      }, delay)
    },
    handleSocketAuthFailure(event) {
      const code = Number(event && event.code)
      const rawReason = event && (event.reason || event.errMsg || '')
      const reason = String(rawReason).toLowerCase()
      const isAuthFailure = code === 1008 || reason.includes('invalid token') || reason.includes('401')
      if (!isAuthFailure) return false

      this.manualClose = true
      this.socketConnecting = false
      this.socketConnected = false
      this.socketTask = null
      this.socketClosing = false
      this.socketSessionId += 1
      this.stopFallbackPolling()
      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer)
        this.reconnectTimer = null
      }

      uni.removeStorageSync('token')
      uni.removeStorageSync('userInfo')
      uni.showToast({ title: '登录状态已失效，请重新登录', icon: 'none' })
      setTimeout(() => {
        uni.reLaunch({ url: '/pages/login/login' })
      }, 300)
      return true
    },
    handleSocketMessage(raw) {
      if (!raw) return
      let payload = raw
      if (typeof raw === 'string') {
        try {
          payload = JSON.parse(raw)
        } catch (e) {
          return
        }
      }
      if (payload.type === 'CHAT_MESSAGE' && payload.data) {
        this.appendMessage(payload.data)
        if (String(payload.data.receiverId || '') === String(this.currentUserId)) {
          this.markReadSafe()
        }
      }
    },
    startFallbackPolling() {
      if (this.pollTimer) return
      this.pollTimer = setInterval(() => {
        if (!this.socketConnected) {
          this.loadMessages()
        }
      }, 5000)
    },
    stopFallbackPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },
    scrollToBottom() {
      this.$nextTick(() => {
        this.scrollToId = ''
        this.$nextTick(() => {
          this.scrollToId = 'msg-bottom'
        })
      })
    },
    showTimeDivider(index) {
      if (index === 0) return true
      const current = new Date(this.messages[index].createTime).getTime() || 0
      const prev = new Date(this.messages[index - 1].createTime).getTime() || 0
      return current - prev > 5 * 60 * 1000
    },
    formatTime(time) {
      if (!time) return ''
      const d = new Date(time)
      const now = new Date()
      const isToday = d.toDateString() === now.toDateString()
      const h = String(d.getHours()).padStart(2, '0')
      const m = String(d.getMinutes()).padStart(2, '0')
      if (isToday) return `${h}:${m}`
      return `${d.getMonth() + 1}/${d.getDate()} ${h}:${m}`
    }
  }
}
</script>

<style>
page {
  background: linear-gradient(180deg, #f0f9ff 0%, #e9f5ff 45%, #f7fbff 100%);
  min-height: 100vh;
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.container {
  min-height: 100vh;
  background: transparent;
  display: flex;
  flex-direction: column;
}

.header {
  background: linear-gradient(135deg, #0369a1 0%, #0ea5e9 50%, #38bdf8 100%);
  padding: 56rpx 28rpx 24rpx;
}

.title {
  display: block;
  font-size: 38rpx;
  font-weight: 600;
  color: #fff;
}

.subtitle {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.9);
}

.subtitle.warn {
  color: #fef3c7;
}

.message-list {
  flex: 1;
  padding: 20rpx 22rpx;
  height: calc(100vh - 280rpx);
}

.empty-wrap {
  padding-top: 220rpx;
  text-align: center;
}

.empty-text {
  font-size: 28rpx;
  color: #475569;
}

.message-wrapper {
  margin-bottom: 14rpx;
}

.time-divider {
  display: flex;
  justify-content: center;
  margin: 18rpx 0;
}

.time-text {
  font-size: 22rpx;
  color: #475569;
  background: rgba(0, 0, 0, 0.05);
  padding: 6rpx 18rpx;
  border-radius: 14rpx;
}

.message-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: 8rpx;
}

.message-item.sent {
  justify-content: flex-end;
}

.avatar-wrap {
  flex-shrink: 0;
}

.msg-avatar {
  width: 68rpx;
  height: 68rpx;
  border-radius: 50%;
}

.bubble {
  max-width: 62%;
  padding: 18rpx 24rpx;
  border-radius: 20rpx;
  margin: 0 14rpx;
}

.bubble-left {
  background: #fff;
  border-top-left-radius: 8rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.05);
}

.bubble-right {
  background: linear-gradient(135deg, #0369a1 0%, #0ea5e9 100%);
  border-top-right-radius: 8rpx;
}

.bubble-text {
  font-size: 28rpx;
  line-height: 1.6;
  color: #0f172a;
  word-break: break-all;
}

.bubble-right .bubble-text {
  color: #fff;
}

.voice-message {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.voice-icon {
  font-size: 28rpx;
  color: #0369a1;
}

.voice-label {
  font-size: 28rpx;
  line-height: 1.6;
  color: #0f172a;
}

.bubble-right .voice-icon,
.bubble-right .voice-label {
  color: #fff;
}

.input-bar {
  display: flex;
  align-items: center;
  padding: 16rpx 22rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #eee;
}

.msg-input {
  flex: 1;
  height: 74rpx;
  background: #f5f5f5;
  border-radius: 37rpx;
  padding: 0 26rpx;
  font-size: 28rpx;
  color: #0f172a;
}

.send-btn {
  margin-left: 14rpx;
  background: #ddd;
  border-radius: 37rpx;
  padding: 0 34rpx;
  height: 74rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.send-btn.active {
  background: linear-gradient(135deg, #0369a1 0%, #0ea5e9 100%);
}

.send-text {
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}
</style>
