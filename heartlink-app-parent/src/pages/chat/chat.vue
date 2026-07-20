<template>
  <view class="container">
    <view class="header">
      <text class="title">{{ chatTitle }}</text>
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
        <text class="empty-text">暂无消息，和家人打个招呼吧</text>
      </view>

      <view
        class="message-row"
        v-for="(msg, index) in messages"
        :key="msg.id || msg.localId || index"
        :class="{ mine: msg.isMine }"
      >
        <view class="bubble" :class="{ 'mine-bubble': msg.isMine }">
          <view v-if="isVoiceMessage(msg)" class="voice-message" @click="playVoiceMessage(msg)">
            <text class="voice-icon">{{ isPlayingVoiceMessage(msg) ? '■' : '▶' }}</text>
            <text class="voice-label">{{ isPlayingVoiceMessage(msg) ? '播放中...' : getVoiceLabel(msg) }}</text>
          </view>
          <text v-else class="bubble-text">{{ msg.content }}</text>
          <text class="bubble-time">{{ formatTime(msg.createTime) }}</text>
        </view>
      </view>
      <view id="msg-bottom" class="message-bottom"></view>
    </scroll-view>

    <view class="voice-status" v-if="voiceStatus">
      <text class="voice-status-text">{{ voiceStatus }}</text>
    </view>

    <view class="input-bar">
      <!-- #ifdef MP-WEIXIN -->
      <view
        class="voice-btn voice-btn-wide"
        :class="{ recording: isVoiceRecording, sending: voiceSending }"
        @click="toggleVoiceInput"
      >
        <text class="voice-btn-text">{{ voiceSending ? '发送中' : (isVoiceRecording ? '结束并发送' : '点击说话') }}</text>
      </view>
      <!-- #endif -->
      <text class="voice-only-tip">点一次开始录音，再点一次直接发送语音</text>
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
      chatTitle: '消息',
      messages: [],
      scrollToId: '',
      loading: false,
      currentUserId: '',
      initialized: false,
      isVoiceRecording: false,
      ignoreNextVoiceStop: false,
      voiceStatus: '',
      voiceSending: false,
      recorderManager: null,
      audioPlayer: null,
      playingVoiceId: '',
      socketTask: null,
      socketConnected: false,
      socketConnecting: false,
      socketClosing: false,
      socketSessionId: 0,
      realtimeDisabled: false,
      manualClose: false,
      reconnectTimer: null,
      pollTimer: null
    }
  },
  onLoad(options) {
    this.familyBindId = String(options.familyBindId || '')
    this.targetUserId = String(options.receiverId || options.childId || '')
    this.chatTitle = decodeURIComponent(options.relation || options.nickname || '消息')
    const userInfo = uni.getStorageSync('userInfo') || {}
    this.currentUserId = String(userInfo.id || '')
    this.initConversation()
  },
  onShow() {
    if (!this.initialized) return
    this.manualClose = false
    if (this.shouldSkipRealtimeSocket()) {
      this.realtimeDisabled = true
      this.startFallbackPolling()
      return
    }
    if (this.realtimeDisabled) {
      this.startFallbackPolling()
      return
    }
    if (!this.socketConnected && !this.socketConnecting) {
      this.connectSocket()
      this.startFallbackPolling()
    }
  },
  onHide() {
    this.stopVoiceInput({ resetStatus: true, silent: true, discard: true })
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
    this.stopVoiceInput({ resetStatus: true, silent: true, discard: true })
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
      this.startFallbackPolling()
      if (this.shouldSkipRealtimeSocket()) {
        this.realtimeDisabled = true
      }
      if (!this.realtimeDisabled) {
        this.connectSocket()
      }
      this.initialized = true
    },
    async ensureConversationTarget() {
      try {
        const res = await familyApi.getMyChildren()
        const children = Array.isArray(res.data) ? res.data : []
        if (!children.length) return

        if (!this.familyBindId) {
          const first = children[0]
          this.familyBindId = String(first.bind?.id || '')
          this.targetUserId = String(first.child?.id || '')
          this.chatTitle = first.child?.nickname || first.bind?.relation || this.chatTitle
          return
        }

        const matched = children.find((item) => String(item.bind?.id) === String(this.familyBindId))
        if (matched) {
          this.targetUserId = this.targetUserId || String(matched.child?.id || '')
          this.chatTitle = matched.child?.nickname || matched.bind?.relation || this.chatTitle
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
    getRecorderManager() {
      if (this.recorderManager) return this.recorderManager
      if (typeof uni.getRecorderManager !== 'function') return null

      const manager = uni.getRecorderManager()
      manager.onStart(() => {
        this.isVoiceRecording = true
        this.voiceStatus = '正在录音，再点一次发送'
      })
      manager.onStop((res) => {
        this.handleVoiceRecordStop(res)
      })
      manager.onError((err) => {
        console.error('voice record error', err)
        this.isVoiceRecording = false
        this.voiceSending = false
        this.ignoreNextVoiceStop = false
        this.voiceStatus = ''
        uni.showToast({ title: '录音失败，请重试', icon: 'none' })
      })
      this.recorderManager = manager
      return manager
    },
    ensureRecordPermission() {
      return new Promise((resolve) => {
        if (typeof uni.authorize !== 'function') {
          resolve(true)
          return
        }
        uni.authorize({
          scope: 'scope.record',
          success: () => resolve(true),
          fail: () => {
            uni.showModal({
              title: '需要麦克风权限',
              content: '开启麦克风后，才能发送语音消息',
              confirmText: '去开启',
              cancelText: '取消',
              success: (res) => {
                if (!res.confirm) {
                  resolve(false)
                  return
                }
                uni.openSetting({
                  success: (settingRes) => resolve(!!settingRes?.authSetting?.['scope.record']),
                  fail: () => resolve(false)
                })
              },
              fail: () => resolve(false)
            })
          }
        })
      })
    },
    async toggleVoiceInput() {
      if (this.voiceSending) return
      if (this.isVoiceRecording) {
        this.stopVoiceInput()
        return
      }

      const manager = this.getRecorderManager()
      if (!manager) {
        uni.showToast({ title: '当前环境不支持录音', icon: 'none' })
        return
      }

      const granted = await this.ensureRecordPermission()
      if (!granted) return

      this.stopAudioPlayback()
      this.ignoreNextVoiceStop = false
      this.voiceStatus = '正在准备录音...'
      try {
        manager.start({
          duration: 60000,
          sampleRate: 16000,
          numberOfChannels: 1,
          encodeBitRate: 96000,
          format: 'mp3'
        })
      } catch (e) {
        console.error(e)
        this.isVoiceRecording = false
        this.voiceSending = false
        this.ignoreNextVoiceStop = false
        this.voiceStatus = ''
        uni.showToast({ title: '无法开始录音，请重试', icon: 'none' })
      }
    },
    stopVoiceInput(options = {}) {
      if (!this.isVoiceRecording || !this.recorderManager) {
        if (options.resetStatus) {
          this.voiceStatus = ''
        }
        return
      }

      if (options.discard) {
        this.ignoreNextVoiceStop = true
      }
      if (!options.silent) {
        this.voiceStatus = options.discard ? '' : '正在处理语音...'
      }
      try {
        this.recorderManager.stop()
      } catch (e) {
        console.error(e)
        this.isVoiceRecording = false
        this.voiceSending = false
        this.ignoreNextVoiceStop = false
        if (options.resetStatus || !options.silent) {
          this.voiceStatus = ''
        }
      }
    },
    async handleVoiceRecordStop(res) {
      this.isVoiceRecording = false
      const tempFilePath = String(res?.tempFilePath || '').trim()
      const durationMs = Number(res?.duration || 0)

      if (this.ignoreNextVoiceStop) {
        this.ignoreNextVoiceStop = false
        this.voiceStatus = ''
        return
      }

      if (!tempFilePath) {
        this.voiceStatus = ''
        uni.showToast({ title: '没有录到语音，请重试', icon: 'none' })
        return
      }
      this.voiceStatus = '正在发送语音...'
      await this.sendVoiceMessage(tempFilePath, durationMs)
    },
    async sendVoiceMessage(filePath, durationMs = 0) {
      if (!this.familyBindId) return
      if (!this.targetUserId) {
        await this.ensureConversationTarget()
      }
      if (!this.targetUserId) {
        this.voiceStatus = ''
        uni.showToast({ title: '未找到接收方', icon: 'none' })
        return
      }

      const seconds = Math.max(1, Math.round(durationMs / 1000) || 1)
      const content = `语音消息 ${seconds}秒`
      const localMessage = this.normalizeMessage({
        localId: `voice-${Date.now()}`,
        familyBindId: Number(this.familyBindId),
        senderId: Number(this.currentUserId),
        receiverId: Number(this.targetUserId),
        content,
        type: 'VOICE',
        voiceUrl: filePath,
        createdAt: new Date().toISOString()
      })
      this.messages = this.sortMessages(this.dedupMessages([...this.messages, localMessage]))
      this.scrollToBottom()
      this.voiceSending = true

      try {
        const uploadRes = await messageApi.uploadVoice(filePath)
        const voiceUrl = uploadRes?.data?.relativeUrl || uploadRes?.data?.url || ''
        if (!voiceUrl) {
          throw new Error('voice url missing')
        }
        const res = await messageApi.send({
          familyBindId: Number(this.familyBindId),
          receiverId: Number(this.targetUserId),
          content,
          type: 'VOICE',
          voiceUrl
        })
        this.messages = this.messages.filter((m) => m.localId !== localMessage.localId)
        if (res?.data) {
          this.appendMessage(res.data)
        }
        this.voiceStatus = ''
      } catch (e) {
        console.error(e)
        this.messages = this.messages.filter((m) => m.localId !== localMessage.localId)
        this.voiceStatus = ''
        uni.showToast({ title: '语音发送失败，请重试', icon: 'none' })
      } finally {
        this.voiceSending = false
      }
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
    shouldSkipRealtimeSocket() {
      if (this.realtimeDisabled) return true

      // #ifndef MP-WEIXIN
      return false
      // #endif

      // #ifdef MP-WEIXIN
      const wsUrl = String(WS_BASE_URL || '').toLowerCase()
      return /:\/\/(127\.0\.0\.1|localhost|\[::1\]|::1)(:|\/|$)/.test(wsUrl)
      // #endif
    },
    scrollToBottom() {
      this.$nextTick(() => {
        this.scrollToId = ''
        this.$nextTick(() => {
          this.scrollToId = 'msg-bottom'
        })
      })
    },
    connectSocket() {
      const token = uni.getStorageSync('token')
      if (this.shouldSkipRealtimeSocket()) {
        this.realtimeDisabled = true
        this.disconnectSocket()
        this.startFallbackPolling()
        return
      }
      if (this.realtimeDisabled) {
        this.disconnectSocket()
        this.startFallbackPolling()
        return
      }
      if (!token) return
      if (this.socketTask || this.socketConnecting || this.socketConnected) return
      if (this.socketClosing) {
        this.startFallbackPolling()
        return
      }

      this.manualClose = false
      this.socketConnecting = true
      this.socketClosing = false
      const sessionId = Date.now()
      this.socketSessionId = sessionId
      let socketTask = null

      try {
        const separator = String(WS_BASE_URL).includes('?') ? '&' : '?'
        const wsUrl = `${WS_BASE_URL}${separator}token=${encodeURIComponent(token)}`
        socketTask = uni.connectSocket({
          url: wsUrl,
          header: {
            Authorization: token
          },
          protocols: [token],
          complete: () => {}
        })
      } catch (e) {
        this.socketConnecting = false
        this.disableRealtime(e)
        return
      }

      if (!socketTask) {
        this.socketConnecting = false
        this.disableRealtime()
        return
      }

      this.socketTask = socketTask
      const isStaleSocket = () => this.socketSessionId !== sessionId || this.socketTask !== socketTask

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
        this.socketConnecting = false
        if (this.handleSocketAuthFailure(err)) return
        this.socketConnected = false
        this.socketTask = null
        this.socketClosing = false
        this.disableRealtime(err)
      }

      const onClose = (evt) => {
        if (isStaleSocket()) return
        this.socketConnecting = false
        if (this.handleSocketAuthFailure(evt)) return
        const isManualClose = this.manualClose || this.socketClosing
        this.socketConnected = false
        this.socketTask = null
        this.socketClosing = false
        if (!isManualClose && !this.realtimeDisabled) {
          this.scheduleReconnect()
        }
      }

      if (socketTask && typeof socketTask.onOpen === 'function') {
        socketTask.onOpen(onOpen)
        socketTask.onMessage(onMessage)
        socketTask.onError(onError)
        socketTask.onClose(onClose)
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
      this.socketConnecting = false
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

      const finishClose = () => {
        this.socketClosing = false
      }

      try {
        if (typeof socket.close === 'function') {
          socket.close({
            code: 1000,
            reason: 'page hide',
            fail: () => {},
            complete: finishClose
          })
          return
        }
        if (typeof uni.closeSocket === 'function') {
          uni.closeSocket({
            code: 1000,
            reason: 'page hide',
            fail: () => {},
            complete: finishClose
          })
          return
        }
      } catch (e) {
        console.error(e)
      }

      finishClose()
    },
    scheduleReconnect(delay = 3000) {
      if (this.manualClose || this.realtimeDisabled || this.reconnectTimer) return
      if (!uni.getStorageSync('token')) return
      this.startFallbackPolling()
      this.reconnectTimer = setTimeout(() => {
        this.reconnectTimer = null
        this.connectSocket()
      }, delay)
    },
    disableRealtime(err) {
      const errMsg = String((err && err.errMsg) || '').toLowerCase()
      const shouldWarn = errMsg
        && !errMsg.includes('not found')
        && !errMsg.includes('route')
        && !errMsg.includes('未完成的操作')
        && !errMsg.includes('not finished')
        && !errMsg.includes('in progress')
      this.realtimeDisabled = true
      this.socketTask = null
      this.socketConnected = false
      this.socketConnecting = false
      this.socketClosing = false
      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer)
        this.reconnectTimer = null
      }
      if (shouldWarn) {
        console.warn('chat realtime unavailable, fallback to polling', err)
      }
      this.startFallbackPolling()
    },
    handleSocketAuthFailure(event) {
      const code = Number(event && event.code)
      const rawReason = event && (event.reason || event.errMsg || '')
      const reason = String(rawReason).toLowerCase()
      const isAuthFailure = code === 1008 || reason.includes('invalid token') || reason.includes('401')
      if (!isAuthFailure) return false

      this.manualClose = true
      this.socketConnected = false
      this.socketConnecting = false
      this.socketClosing = false
      this.socketTask = null
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
    formatTime(time) {
      if (!time) return ''
      const d = new Date(time)
      const h = String(d.getHours()).padStart(2, '0')
      const m = String(d.getMinutes()).padStart(2, '0')
      return `${h}:${m}`
    }
  }
}
</script>

<style scoped>
page {
  background-color: #f5f5f5;
  height: 100%;
}

.container {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.header {
  padding: 28rpx 24rpx 18rpx;
  background: #fff;
  border-bottom: 1rpx solid #f0f0f0;
}

.title {
  display: block;
  font-size: 36rpx;
  color: #222;
  font-weight: 600;
}

.subtitle {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #52c41a;
}

.subtitle.warn {
  color: #faad14;
}

.message-list {
  flex: 1;
  padding: 22rpx;
}

.message-bottom {
  height: 8rpx;
}

.empty-wrap {
  padding-top: 220rpx;
  text-align: center;
}

.empty-text {
  font-size: 28rpx;
  color: #999;
}

.message-row {
  display: flex;
  margin-bottom: 20rpx;
}

.message-row.mine {
  justify-content: flex-end;
}

.bubble {
  max-width: 72%;
  padding: 18rpx 22rpx;
  border-radius: 18rpx;
  background: #fff;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.06);
}

.mine-bubble {
  background: #ff6b6b;
}

.bubble-text {
  color: #333;
  font-size: 30rpx;
  line-height: 1.5;
  word-break: break-all;
}

.mine-bubble .bubble-text {
  color: #fff;
}

.voice-message {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.voice-icon {
  font-size: 28rpx;
  color: #ff6b6b;
}

.voice-label {
  font-size: 28rpx;
  color: #333;
  line-height: 1.5;
}

.mine-bubble .voice-icon,
.mine-bubble .voice-label {
  color: #fff;
}

.bubble-time {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #999;
}

.mine-bubble .bubble-time {
  color: rgba(255, 255, 255, 0.7);
  text-align: right;
}

.voice-status {
  padding: 12rpx 24rpx 0;
  background: #fff;
}

.voice-status-text {
  display: inline-block;
  font-size: 24rpx;
  color: #ff6b6b;
  background: #fff1f0;
  border-radius: 16rpx;
  padding: 8rpx 18rpx;
}

.input-bar {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12rpx;
  padding: 16rpx 20rpx calc(20rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #eee;
}

.voice-btn {
  flex-shrink: 0;
  width: 104rpx;
  height: 76rpx;
  border-radius: 38rpx;
  background: #fff2f0;
  border: 2rpx solid #ffd6d1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.voice-btn-wide {
  width: 100%;
  height: 88rpx;
  border-radius: 44rpx;
}

.voice-btn.recording,
.voice-btn.sending {
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e8e 100%);
  border-color: transparent;
  box-shadow: 0 8rpx 18rpx rgba(255, 107, 107, 0.22);
}

.voice-btn-text {
  font-size: 30rpx;
  font-weight: 600;
  color: #ff6b6b;
}

.voice-btn.recording .voice-btn-text,
.voice-btn.sending .voice-btn-text {
  color: #fff;
}

.voice-only-tip {
  text-align: center;
  font-size: 24rpx;
  color: #999;
  line-height: 1.5;
}
</style>
