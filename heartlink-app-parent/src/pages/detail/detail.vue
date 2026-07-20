<template>
  <view class="container">
    <view class="image-section">
      <image class="product-image" :src="getProductImage()" mode="aspectFill" @error="onImageError"></image>
    </view>

    <view class="info-section">
      <text class="product-name">{{ product.name }}</text>
      <view class="price-row">
        <text class="price">¥{{ product.price }}</text>
        <text class="original-price" v-if="product.originalPrice">¥{{ product.originalPrice }}</text>
      </view>
      <view class="tags" v-if="displayTags.length > 0">
        <text class="tag" v-for="tag in displayTags" :key="tag">{{ tag }}</text>
      </view>
    </view>

    <view class="desc-section">
      <text class="section-title">商品介绍</text>
      <text class="desc-text">{{ product.description || '这款商品适合您日常使用，欢迎了解详情。' }}</text>
      <view class="voice-actions">
        <view class="voice-btn" @click="toggleSpeak">
          <text class="voice-btn-text">{{ ttsLoading ? '语音生成中...' : (isSpeaking ? '停止播报' : '语音播报') }}</text>
        </view>
        <view class="rate-btn" @click="toggleRate">
          <text class="rate-btn-text">语速：{{ ttsRateLabel }}</text>
        </view>
      </view>
    </view>

    <view class="bottom-bar">
      <view class="like-btn" @click="toggleLike">
        <text class="like-icon">{{ isLiked ? '❤' : '♡' }}</text>
        <text class="like-text">{{ isLiked ? '已收藏' : '收藏' }}</text>
      </view>
      <view class="buy-btn" @click="showBuyOptions">
        <text class="buy-text">我想要</text>
      </view>
    </view>

    <view class="buy-popup" v-if="showPopup" @click="showPopup = false">
      <view class="popup-content" @click.stop>
        <view class="popup-header">
          <text class="popup-title">选择购买方式</text>
          <text class="popup-close" @click="showPopup = false">×</text>
        </view>

        <view class="popup-product">
          <image class="popup-image" :src="getProductImage()" mode="aspectFill" @error="onImageError"></image>
          <view class="popup-info">
            <text class="popup-name">{{ product.name }}</text>
            <text class="popup-price">¥{{ product.price }}</text>
          </view>
        </view>

        <view class="popup-options">
          <view class="option-item" @click="requestChildPay">
            <text class="option-icon">👨‍👩‍👧</text>
            <view class="option-info">
              <text class="option-title">请子女付款</text>
              <text class="option-desc">发送请求给子女，请他们帮您付款</text>
            </view>
            <text class="option-arrow">›</text>
          </view>

          <view class="option-item" @click="addToWishlist">
            <text class="option-icon">📝</text>
            <view class="option-info">
              <text class="option-title">加入心愿单</text>
              <text class="option-desc">先收藏起来，稍后再购买</text>
            </view>
            <text class="option-arrow">›</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { productApi, paymentRequestApi, shelfApi, aiApi, toAbsoluteMediaUrl } from '../../api/index.js'

const isWechatMiniProgram = (() => {
  let value = false
  // #ifdef MP-WEIXIN
  value = true
  // #endif
  return value
})()

export default {
  data() {
    return {
      productId: null,
      shelfId: null,
      product: {},
      isLiked: false,
      showPopup: false,
      isSpeaking: false,
      ttsLoading: false,
      ttsRate: 'slow',
      audioCtx: null,
      audioCache: {},
      imageLoadFailed: false
    }
  },
  computed: {
    displayTags() {
      if (Array.isArray(this.product.healthTags) && this.product.healthTags.length > 0) {
        return this.product.healthTags
      }
      if (Array.isArray(this.product.tags) && this.product.tags.length > 0) {
        return this.product.tags
      }
      return []
    },
    ttsRateLabel() {
      return this.ttsRate === 'slow' ? '慢速' : '正常'
    }
  },
  onLoad(options) {
    this.productId = Number(options.id)
    this.shelfId = options.shelfId ? Number(options.shelfId) : null
    this.loadProduct()
    this.loadLikedState()
  },
  onUnload() {
    this.stopSpeak()
    if (this.audioCtx) {
      this.audioCtx.destroy()
      this.audioCtx = null
    }
  },
  methods: {
    resolveImageUrl(value) {
      const raw = String(value || '').trim()
      if (!raw) return ''
      if (/^(https?:\/\/[^/]+)?\/?s\//i.test(raw)) return ''
      if (/^data:/i.test(raw)) return raw
      if (/^https?:\/\//i.test(raw)) {
        const resolved = toAbsoluteMediaUrl(raw) || raw
        // Known unstable buckets in local env.
        if (/\/(s|products)\//i.test(resolved)) return ''
        if (isWechatMiniProgram && /^http:\/\//i.test(resolved)) return ''
        return resolved
      }
      if (raw.startsWith('/static/products/')) {
        const fileName = raw.split('/').pop().toLowerCase()
        const allowed = new Set([
          '1.png', '2.png', '3.png', '4.png',
          'blood-pressure.png', 'foot-bath.png', 'oatmeal.png', 'smartphone.png', 'sweater.png'
        ])
        return allowed.has(fileName) ? raw : ''
      }
      if (raw.startsWith('/upload/') || raw.startsWith('upload/')) {
        return toAbsoluteMediaUrl(raw)
      }
      if (raw.startsWith('/static/')) return raw
      if (raw.startsWith('static/')) return `/${raw}`

      return toAbsoluteMediaUrl(raw)
    },
    getProductImage() {
      if (!this.imageLoadFailed && Array.isArray(this.product.images) && this.product.images.length > 0) {
        const resolved = this.resolveImageUrl(this.product.images[0])
        if (resolved) return resolved
      }
      return this.getImageUrl(this.product.id)
    },
    onImageError() {
      this.imageLoadFailed = true
    },
    getImageUrl(id) {
      return '/static/products/oatmeal.png'
    },
    ensureAudioContext() {
      if (this.audioCtx) return
      this.audioCtx = uni.createInnerAudioContext()
      this.audioCtx.autoplay = false
      this.audioCtx.onEnded(() => {
        this.isSpeaking = false
      })
      this.audioCtx.onStop(() => {
        this.isSpeaking = false
      })
      this.audioCtx.onError(() => {
        this.isSpeaking = false
        uni.showToast({
          title: '语音播放失败',
          icon: 'none'
        })
      })
    },
    buildTtsText() {
      const name = this.product.name || '商品'
      const price = this.product.price
      const tags = this.displayTags.slice(0, 3).join('、')
      const desc = this.product.description || ''
      const tagText = tags ? `标签：${tags}。` : ''
      const priceText = price ? `价格${price}元。` : ''
      return `这款${name}。${priceText}${tagText}${desc}`
    },
    async toggleSpeak() {
      if (this.ttsLoading) return
      if (this.isSpeaking) {
        this.stopSpeak()
        return
      }
      const text = this.buildTtsText()
      if (!text) {
        uni.showToast({
          title: '暂无可播报内容',
          icon: 'none'
        })
        return
      }
      await this.speakText(text)
    },
    stopSpeak() {
      if (this.audioCtx) {
        this.audioCtx.stop()
      }
      this.isSpeaking = false
    },
    toggleRate() {
      if (this.isSpeaking) {
        this.stopSpeak()
      }
      this.ttsRate = this.ttsRate === 'slow' ? 'normal' : 'slow'
      uni.showToast({
        title: this.ttsRate === 'slow' ? '已切换慢速' : '已切换正常语速',
        icon: 'none'
      })
    },
    getPlaybackRate() {
      return this.ttsRate === 'slow' ? 0.75 : 1.0
    },
    applyPlaybackRate() {
      if (!this.audioCtx) return
      const rate = this.getPlaybackRate()
      try {
        this.audioCtx.playbackRate = rate
      } catch (e) {
        console.warn('playbackRate unsupported:', e)
      }
    },
    async speakText(text) {
      this.ttsLoading = true
      try {
        // Plugin first on mp-weixin to avoid backend 503 when external TTS provider is not configured.
        const playedByPlugin = this.tryPlayByPlugin(text)
        if (playedByPlugin) return

        const cacheKey = `${this.productId || 'product'}_${this.ttsRate}`
        let audioSrc = this.audioCache[cacheKey]

        if (!audioSrc) {
          try {
            const ttsRes = await aiApi.tts(text, this.ttsRate)
            audioSrc = await this.toAudioSrc(ttsRes.arrayBuffer)
            this.audioCache[cacheKey] = audioSrc
          } catch (e) {
            console.warn('AI TTS unavailable:', e)
          }
        }

        if (audioSrc) {
          this.ensureAudioContext()
          this.applyPlaybackRate()
          this.audioCtx.src = audioSrc
          this.audioCtx.play()
          this.isSpeaking = true
          return
        }

        this.showVoiceText(text)
      } catch (e) {
        console.error(e)
        this.showVoiceText(text)
      } finally {
        this.ttsLoading = false
      }
    },
    tryPlayByPlugin(voiceText) {
      try {
        if (typeof __wxConfig !== 'undefined') {
          const pluginConfig = __wxConfig && __wxConfig.plugins
          if (!pluginConfig || !pluginConfig.WechatSI) {
            return false
          }
        }
        if (typeof requirePlugin !== 'function') {
          return false
        }
        const plugin = requirePlugin('WechatSI')
        if (!plugin || typeof plugin.textToSpeech !== 'function') {
          return false
        }

        plugin.textToSpeech({
          lang: 'zh_CN',
          tts: true,
          content: voiceText,
          success: (res) => {
            if (!res || !res.filename) {
              this.showVoiceText(voiceText)
              return
            }
            this.ensureAudioContext()
            this.applyPlaybackRate()
            this.audioCtx.src = res.filename
            this.audioCtx.play()
            this.isSpeaking = true
          },
          fail: () => {
            this.showVoiceText(voiceText)
          }
        })
        return true
      } catch (e) {
        console.warn('WechatSI unavailable:', e)
        return false
      }
    },
    showVoiceText(text) {
      uni.showModal({
        title: '语音内容',
        content: text,
        showCancel: false,
        confirmText: '知道了'
      })
    },
    toAudioSrc(arrayBuffer) {
      return new Promise((resolve, reject) => {
        if (!arrayBuffer) {
          reject(new Error('empty audio data'))
          return
        }
        const base64 = uni.arrayBufferToBase64(arrayBuffer)
        const dataUrl = `data:audio/wav;base64,${base64}`

        if (typeof window !== 'undefined') {
          resolve(dataUrl)
          return
        }

        const fs = uni.getFileSystemManager && uni.getFileSystemManager()
        const wechatPath = typeof wx !== 'undefined' && wx.env && wx.env.USER_DATA_PATH

        if (!fs || !wechatPath) {
          resolve(dataUrl)
          return
        }

        const fileName = `tts_${Date.now()}_${Math.floor(Math.random() * 1000)}.wav`
        const filePath = `${wechatPath}/${fileName}`
        fs.writeFile({
          filePath,
          data: base64,
          encoding: 'base64',
          success: () => resolve(filePath),
          fail: () => resolve(dataUrl)
        })
      })
    },
    async loadProduct() {
      try {
        uni.showLoading({ title: '加载中...' })
        const res = await productApi.getProduct(this.productId)
        if (!res.data) {
          throw new Error('商品不存在')
        }

        const product = { ...res.data }
        if (typeof product.images === 'string') {
          try {
            const parsed = JSON.parse(product.images)
            product.images = Array.isArray(parsed) ? parsed : []
          } catch {
            product.images = []
          }
        }

        this.product = product
        this.imageLoadFailed = false
      } catch (e) {
        console.error(e)
        uni.showToast({
          title: '商品加载失败',
          icon: 'none'
        })
      } finally {
        uni.hideLoading()
      }
    },
    async loadLikedState() {
      try {
        const res = await shelfApi.getMyLiked()
        const likedList = Array.isArray(res.data) ? res.data : []
        const matched = likedList.find(item => item.product && Number(item.product.id) === Number(this.productId))
        if (matched && matched.shelf) {
          this.isLiked = true
          if (!this.shelfId) {
            this.shelfId = matched.shelf.id
          }
        }
      } catch (e) {
        console.error(e)
      }
    },
    async toggleLike() {
      if (!this.shelfId) {
        if (this.isLiked) {
          uni.showToast({ title: '收藏记录缺失，请刷新后重试', icon: 'none' })
          return
        }
        try {
          const shelf = await shelfApi.collect(this.productId)
          this.shelfId = shelf?.id || this.shelfId
          this.isLiked = true
          uni.showToast({
            title: '已加入心愿单',
            icon: 'none'
          })
        } catch (e) {
          console.error(e)
          uni.showToast({ title: '收藏失败，请稍后重试', icon: 'none' })
        }
        return
      }
      const reaction = this.isLiked ? 'DISLIKE' : 'LIKE'
      try {
        await shelfApi.react(this.shelfId, reaction)
        this.isLiked = !this.isLiked
        uni.showToast({
          title: this.isLiked ? '已加入心愿单' : '已取消收藏',
          icon: 'none'
        })
      } catch (e) {
        console.error(e)
      }
    },
    showBuyOptions() {
      this.showPopup = true
    },
    async requestChildPay() {
      this.showPopup = false

      uni.showModal({
        title: '请子女帮忙付款',
        content: `发送请求给子女，让他们帮您购买《${this.product.name}》。`,
        confirmText: '发送请求',
        confirmColor: '#FF6B6B',
        success: async (res) => {
          if (res.confirm) {
            try {
              uni.showLoading({ title: '发送中...' })
              await paymentRequestApi.createByProduct(
                this.productId,
                `孩子，帮我买一下《${this.product.name}》吧~`
              )
              uni.showToast({
                title: '已发送给子女',
                icon: 'success',
                duration: 2000
              })
              setTimeout(() => uni.navigateBack(), 1200)
            } catch (e) {
              console.error(e)
              uni.showToast({
                title: e?.message || '发送失败，请稍后重试',
                icon: 'none'
              })
            } finally {
              uni.hideLoading()
            }
          }
        }
      })
    },
    async addToWishlist() {
      this.showPopup = false
      if (this.isLiked) {
        uni.showToast({ title: '已在心愿单中', icon: 'none' })
        return
      }
      await this.toggleLike()
    }
  }
}
</script>

<style>
page {
  background-color: #f5f5f5;
}

.container {
  padding-bottom: 180rpx;
}

.image-section {
  width: 100%;
  height: 600rpx;
  background: #fff;
}

.product-image {
  width: 100%;
  height: 100%;
}

.info-section {
  background: #fff;
  padding: 30rpx;
  margin-top: 20rpx;
}

.product-name {
  font-size: 44rpx;
  font-weight: 600;
  color: #1a1a1a;
  line-height: 1.4;
  display: block;
}

.price-row {
  display: flex;
  align-items: baseline;
  margin-top: 20rpx;
}

.price {
  font-size: 56rpx;
  font-weight: 700;
  color: #FF6B6B;
}

.original-price {
  font-size: 32rpx;
  color: #999;
  text-decoration: line-through;
  margin-left: 20rpx;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 24rpx;
}

.tag {
  background: linear-gradient(135deg, #e8f8e8 0%, #d4f4d4 100%);
  color: #2eb82e;
  font-size: 26rpx;
  padding: 10rpx 20rpx;
  border-radius: 8rpx;
  font-weight: 500;
}

.desc-section {
  background: #fff;
  padding: 30rpx;
  margin-top: 20rpx;
}

.section-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #333;
  display: block;
  margin-bottom: 20rpx;
}

.desc-text {
  font-size: 32rpx;
  color: #666;
  line-height: 1.8;
}

.voice-actions {
  margin-top: 24rpx;
  display: flex;
  gap: 16rpx;
}

.voice-btn {
  flex: 1;
  min-height: 92rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #4caf50 0%, #66bb6a 100%);
}

.voice-btn-text {
  font-size: 30rpx;
  color: #fff;
  font-weight: 600;
}

.rate-btn {
  min-width: 180rpx;
  min-height: 92rpx;
  border-radius: 16rpx;
  background: #fff0f0;
  border: 2rpx solid #ffd7d7;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 20rpx;
}

.rate-btn-text {
  font-size: 28rpx;
  color: #e64c4c;
  font-weight: 600;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  display: flex;
  padding: 20rpx 30rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.08);
}

.like-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 140rpx;
  margin-right: 20rpx;
}

.like-icon {
  font-size: 48rpx;
}

.like-text {
  font-size: 24rpx;
  color: #666;
  margin-top: 4rpx;
}

.buy-btn {
  flex: 1;
  background: linear-gradient(135deg, #FF6B6B 0%, #FF8787 100%);
  border-radius: 50rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(255, 107, 107, 0.3);
}

.buy-text {
  font-size: 36rpx;
  font-weight: 600;
  color: #fff;
}

.buy-popup {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}

.popup-content {
  width: 100%;
  background: #fff;
  border-radius: 40rpx 40rpx 0 0;
  padding: 30rpx;
  padding-bottom: calc(30rpx + env(safe-area-inset-bottom));
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30rpx;
}

.popup-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #333;
}

.popup-close {
  font-size: 48rpx;
  color: #999;
  padding: 10rpx;
}

.popup-product {
  display: flex;
  align-items: center;
  background: #f9f9f9;
  border-radius: 20rpx;
  padding: 20rpx;
  margin-bottom: 30rpx;
}

.popup-image {
  width: 120rpx;
  height: 120rpx;
  border-radius: 12rpx;
}

.popup-info {
  margin-left: 20rpx;
}

.popup-name {
  font-size: 30rpx;
  font-weight: 500;
  color: #333;
  display: block;
}

.popup-price {
  font-size: 36rpx;
  font-weight: 600;
  color: #FF6B6B;
  margin-top: 8rpx;
  display: block;
}

.popup-options {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.option-item {
  display: flex;
  align-items: center;
  background: #fff;
  border: 2rpx solid #f0f0f0;
  border-radius: 20rpx;
  padding: 30rpx;
}

.option-item:active {
  background: #f9f9f9;
}

.option-icon {
  font-size: 48rpx;
  margin-right: 20rpx;
}

.option-info {
  flex: 1;
}

.option-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
  display: block;
}

.option-desc {
  font-size: 26rpx;
  color: #999;
  margin-top: 6rpx;
  display: block;
}

.option-arrow {
  font-size: 40rpx;
  color: #ccc;
}
</style>
