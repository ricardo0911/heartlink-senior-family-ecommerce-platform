<template>
  <view class="container">
    <view class="hero-card">
      <view class="hero-top">
        <text class="hero-badge">今日推荐</text>
        <text class="hero-meta">为家人挑选安心好物</text>
      </view>
      <view class="greeting-row">
        <text class="greeting-text">您好，</text>
        <text class="user-name">{{ userInfo.nickname || '家人' }}</text>
      </view>
      <text class="subtitle">更懂长辈需求的精选推荐</text>
    </view>

    <view class="utility-grid">
      <view class="utility-card camera-btn" @click="cameraSearch">
        <view class="utility-icon camera-icon">拍</view>
        <view class="utility-texts">
          <text class="utility-title">拍照搜索</text>
          <text class="utility-desc">拍照识别健康商品与适用建议</text>
        </view>
        <text class="utility-arrow">›</text>
      </view>

      <view class="utility-card" @click="goHealth">
        <view class="utility-icon health-icon">康</view>
        <view class="utility-texts">
          <text class="utility-title">健康档案</text>
          <text class="utility-desc">查看基础健康信息与日常记录</text>
        </view>
        <text class="utility-arrow">›</text>
      </view>

    </view>

    <view class="overview-grid single">
      <view class="overview-card">
        <text class="overview-value">{{ boundChildrenCount }}</text>
        <text class="overview-label">已绑子女</text>
      </view>
    </view>

    <view class="care-note">
      <text class="care-note-title">由子女协助管理</text>
      <text class="care-note-text">订单、退款、优惠券、收货地址、提醒处理和各类流程由子女端处理；长辈端保留商品浏览、聊天、健康查看和一键求助。</text>
    </view>

    <skeleton-cards v-if="loading" :count="2" />

    <view v-else>
      <view class="section-block" v-if="childPushedList.length > 0">
        <view class="section-header">
          <text class="section-title">子女推荐</text>
          <text class="section-subtitle">家人刚为您挑好的商品</text>
        </view>

        <view class="product-list">
          <view
            class="product-card"
            v-for="item in childPushedList"
            :key="item.shelfId || item.id"
            @click="goDetail(item)"
          >
            <image class="product-image" :src="getImageUrl(item)" mode="aspectFill"></image>
            <view class="product-info">
              <view class="source-pill child-source">子女推送</view>
              <text class="product-title">{{ item.name }}</text>
              <view class="product-price-row">
                <text class="product-price-label">参考价</text>
                <text class="product-price">¥{{ item.price }}</text>
              </view>
              <view class="product-bottom">
                <view class="detail-btn" @click.stop="goDetail(item)">查看详情</view>
                <view class="action-btn" @click.stop="toggleLike(item)">
                  <view class="btn-mark">{{ item.isLiked ? '❤️' : '🤍' }}</view>
                  <text class="btn-text">{{ item.isLiked ? '已收藏' : '收藏' }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view class="section-block" v-if="systemRecommendList.length > 0">
        <view class="section-header">
          <text class="section-title">系统推荐</text>
          <text class="section-subtitle">根据日常健康习惯智能推荐</text>
        </view>

        <view class="product-list">
          <view
            class="product-card"
            v-for="item in systemRecommendList"
            :key="item.id"
            @click="goDetail(item)"
          >
            <image class="product-image" :src="getImageUrl(item)" mode="aspectFill"></image>
            <view class="product-info">
              <view class="source-pill system-source">系统推荐</view>
              <text class="product-title">{{ item.name }}</text>
              <view class="product-price-row">
                <text class="product-price-label">参考价</text>
                <text class="product-price">¥{{ item.price }}</text>
              </view>
              <view class="product-bottom">
                <view class="detail-btn full" @click.stop="goDetail(item)">查看详情</view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view class="empty-card" v-if="childPushedList.length === 0 && systemRecommendList.length === 0">
        <text class="empty-title">暂时没有推荐商品</text>
        <text class="empty-hint">保持活跃使用，系统会持续为您更新推荐</text>
      </view>
    </view>

    <view class="sos-btn" @click="confirmSos">
      <text class="sos-text">SOS</text>
    </view>
  </view>
</template>

<script>
import { authApi, familyApi, productApi, shelfApi, sosApi, toAbsoluteMediaUrl } from '../../api/index.js'
import SkeletonCards from '../../components/skeleton.vue'

export default {
  components: {
    SkeletonCards
  },
  data() {
    return {
      userInfo: {},
      childPushedList: [],
      systemRecommendList: [],
      loading: false,
      boundChildrenCount: 0
    }
  },
  async onShow() {
    const token = uni.getStorageSync('token')
    if (!token) {
      uni.reLaunch({ url: '/pages/login/login' })
      return
    }
    const loggedIn = await this.loadUser()
    if (!loggedIn) {
      return
    }
    this.loadRecommend()
    this.loadHubSummary()
  },
  methods: {
    async loadUser() {
      try {
        const res = await authApi.getCurrentUser()
        this.userInfo = res.data || {}
        return true
      } catch (e) {
        if (e && e.code === 401) {
          return false
        }
        console.error(e)
        return false
      }
    },
    getImageUrl(item) {
      if (item && Array.isArray(item.images) && item.images.length > 0 && item.images[0]) {
        const src = String(item.images[0]).trim()
        if (src) {
          // Some backend /s/* assets are unstable in local dev and may return 500.
          if (/^(https?:\/\/[^/]+)?\/?s\//i.test(src)) {
            // fall through
          } else if (src.startsWith('/static/products/')) {
            const fileName = src.split('/').pop().toLowerCase()
            const allowed = new Set([
              '1.png', '2.png', '3.png', '4.png',
              'blood-pressure.png', 'foot-bath.png', 'oatmeal.png', 'smartphone.png', 'sweater.png'
            ])
            if (allowed.has(fileName)) return src
          } else {
            const resolved = toAbsoluteMediaUrl(src)
            if (resolved) return resolved
          }
        }
      }
      return '/static/products/oatmeal.png'
    },
    readFileAsBase64(filePath) {
      return new Promise((resolve, reject) => {
        if (!filePath) {
          reject(new Error('未获取到图片路径'))
          return
        }
        const fs = uni.getFileSystemManager && uni.getFileSystemManager()
        if (!fs || !fs.readFile) {
          reject(new Error('当前环境不支持读取本地文件'))
          return
        }
        fs.readFile({
          filePath,
          encoding: 'base64',
          success: (res) => resolve(res.data),
          fail: (err) => reject(err)
        })
      })
    },
    normalizeSearchResult(payload) {
      if (!payload) return { keywords: [], products: [] }
      if (Array.isArray(payload)) {
        return { keywords: [], products: payload, description: '' }
      }
      if (typeof payload === 'object') {
        return {
          keywords: Array.isArray(payload.keywords) ? payload.keywords : [],
          products: Array.isArray(payload.products) ? payload.products : [],
          description: payload.description || '',
          aiPowered: !!payload.aiPowered
        }
      }
      return { keywords: [], products: [] }
    },
    async loadRecommend() {
      this.loading = true
      try {
        const targetCount = 8
        let unauthorized = false
        let shelfRecords = []

        try {
          const shelfRes = await shelfApi.getMyShelf({ page: 1, size: 50 })
          shelfRecords = (shelfRes.data?.records || [])
            .filter(item => item && item.product && item.shelf)
            .map(item => ({
              ...item.product,
              shelfId: item.shelf.id,
              isLiked: false
            }))
        } catch (e) {
          if (e && e.code === 401) {
            unauthorized = true
          } else {
            console.error(e)
          }
        }

        if (unauthorized) {
          this.childPushedList = []
          this.systemRecommendList = []
          return
        }

        const parentId = this.userInfo?.id
        const res = await productApi.getRecommend(parentId, targetCount)
        const list = Array.isArray(res.data) ? res.data : []
        const fallbackRecords = list
          .filter(item => item && item.id)
          .map(item => ({
            ...item,
            shelfId: null,
            isLiked: false
          }))

        const usedIds = new Set(shelfRecords.map(item => item.id))
        const systemRecommendList = []

        for (const item of fallbackRecords) {
          if (usedIds.has(item.id)) {
            continue
          }
          usedIds.add(item.id)
          systemRecommendList.push(item)
          if (systemRecommendList.length >= targetCount) {
            break
          }
        }

        this.childPushedList = shelfRecords
        this.systemRecommendList = systemRecommendList
      } catch (e) {
        if (!e || e.code !== 401) {
          console.error(e)
        }
        this.childPushedList = []
        this.systemRecommendList = []
      } finally {
        this.loading = false
      }
    },
    async loadHubSummary() {
      try {
        const familyRes = await familyApi.getMyChildren()
        const familyList = Array.isArray(familyRes.data) ? familyRes.data : []
        this.boundChildrenCount = familyList.length
      } catch (e) {
        console.error(e)
        this.boundChildrenCount = 0
      }
    },
    async toggleLike(item) {
      if (!item.shelfId) return
      const reaction = item.isLiked ? 'DISLIKE' : 'LIKE'
      try {
        await shelfApi.react(item.shelfId, reaction)
        item.isLiked = !item.isLiked
        uni.showToast({
          title: item.isLiked ? '已反馈喜欢' : '已反馈不喜欢',
          icon: 'none',
          duration: 2000
        })
        this.childPushedList = this.childPushedList.filter(p => p.shelfId !== item.shelfId)
      } catch (e) {
        console.error(e)
      }
    },
    goDetail(item) {
      if (!item.id) return
      const shelfQuery = item.shelfId ? `&shelfId=${item.shelfId}` : ''
      uni.navigateTo({ url: `/pages/detail/detail?id=${item.id}${shelfQuery}` })
    },
    goHealth() {
      uni.navigateTo({ url: '/pages/health/health' })
    },
    async cameraSearch() {
      try {
        const chooseRes = await new Promise((resolve, reject) => {
          uni.chooseImage({
            count: 1,
            sourceType: ['camera'],
            success: resolve,
            fail: reject
          })
        })

        const tempPath = chooseRes?.tempFilePaths?.[0]
        if (!tempPath) return

        uni.showLoading({ title: '图片识别中...' })
        const imageBase64 = await this.readFileAsBase64(tempPath)
        const res = await productApi.searchByImage(imageBase64)
        const result = this.normalizeSearchResult(res.data)
        uni.setStorageSync('imageSearchResult', result)
        uni.navigateTo({ url: '/pages/search-result/search-result' })
      } catch (e) {
        const errMsg = String((e && e.errMsg) || '').toLowerCase()
        if (errMsg.includes('cancel')) return
        console.error(e)
        uni.showToast({
          title: '识别失败，请重试',
          icon: 'none'
        })
      } finally {
        uni.hideLoading()
      }
    },
    confirmSos() {
      try {
        if (typeof uni.vibrateShort === 'function') {
          uni.vibrateShort()
        }
      } catch (e) {}

      uni.showModal({
        title: '紧急求助',
        content: '将向子女发送 SOS 求助消息，请确认是否继续。',
        confirmText: '确认发送',
        confirmColor: '#FF0000',
        success: async (res) => {
          if (!res.confirm) return
          try {
            uni.showLoading({ title: '发送中...' })
            await sosApi.trigger()
            uni.showToast({ title: '求助消息已发送', icon: 'success' })
          } catch (e) {
            console.error(e)
            uni.showToast({ title: '发送失败，请稍后重试', icon: 'none' })
          } finally {
            uni.hideLoading()
          }
        }
      })
    }
  }
}
</script>

<style>
page {
  background: linear-gradient(180deg, #dbeafe 0%, #e8f2ff 55%, #f1f7ff 100%);
  min-height: 100vh;
  color: #102a43;
  font-family: 'SF\ Pro\ Text',\ 'SF\ Pro\ Display',\ 'PingFang\ SC',\ 'Segoe\ UI',\ 'Microsoft\ YaHei',\ sans-serif;
}

.container {
  padding: 24rpx 24rpx 190rpx;
}

.hero-card {
  background: linear-gradient(140deg, #1d4ed8 0%, #2563eb 55%, #0ea5e9 100%);
  border-radius: 32rpx;
  padding: 34rpx 30rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 16rpx 40rpx rgba(3, 105, 161, 0.24);
  animation: fade-up 340ms ease-out both;
}

.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.hero-badge {
  background: #dbeafe;
  border-radius: 999rpx;
  padding: 10rpx 22rpx;
  color: #1e3a8a;
  font-size: 24rpx;
  font-weight: 600;
}

.hero-meta {
  color: #e0f2fe;
  font-size: 24rpx;
}

.greeting-row {
  display: flex;
  align-items: baseline;
  margin-top: 24rpx;
}

.greeting-text {
  font-size: 40rpx;
  color: #ffffff;
  font-weight: 500;
}

.user-name {
  margin-left: 10rpx;
  font-size: 54rpx;
  line-height: 1.1;
  font-weight: 700;
  color: #ffffff;
}

.subtitle {
  margin-top: 14rpx;
  display: block;
  color: #eff6ff;
  font-size: 30rpx;
  line-height: 1.4;
}

.utility-grid {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
  margin-bottom: 22rpx;
}

.utility-card {
  background: #eaf3ff;
  border: 1rpx solid #bfd8ff;
  border-radius: 24rpx;
  min-height: 108rpx;
  padding: 18rpx 22rpx;
  display: flex;
  align-items: center;
  box-sizing: border-box;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  box-shadow: 0 10rpx 24rpx rgba(37, 99, 235, 0.12);
  animation: fade-up 380ms ease-out both;
}

.utility-card:active {
  transform: translateY(2rpx);
}

.utility-icon {
  width: 60rpx;
  height: 60rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.camera-icon {
  background: #bfdbfe;
  color: #1e3a8a;
}

.health-icon {
  background: #dcfce7;
  color: #166534;
}

.utility-texts {
  margin-left: 16rpx;
  flex: 1;
}

.utility-title {
  display: block;
  color: #102a43;
  font-size: 31rpx;
  font-weight: 700;
}

.utility-desc {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #3a5a7a;
}

.utility-arrow {
  color: #1d4ed8;
  font-size: 36rpx;
  font-weight: 600;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.overview-grid.single {
  grid-template-columns: minmax(0, 1fr);
}

.overview-card {
  background: rgba(255, 255, 255, 0.98);
  border: 1rpx solid #d7e7fb;
  border-radius: 22rpx;
  padding: 22rpx 18rpx;
  text-align: center;
  box-shadow: 0 10rpx 24rpx rgba(37, 99, 235, 0.1);
}

.overview-card:active {
  transform: translateY(2rpx);
}

.overview-value {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
  color: #1d4ed8;
}

.overview-label {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 1.4;
  color: #4b647c;
}

.care-note {
  margin-bottom: 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border: 1rpx solid #bfd8ff;
  box-shadow: 0 10rpx 24rpx rgba(37, 99, 235, 0.08);
}

.care-note-title {
  display: block;
  color: #1e3a8a;
  font-size: 30rpx;
  font-weight: 700;
}

.care-note-text {
  display: block;
  margin-top: 10rpx;
  color: #355070;
  font-size: 24rpx;
  line-height: 1.7;
}

.section-block + .section-block {
  margin-top: 28rpx;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18rpx;
}

.section-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #102a43;
}

.section-subtitle {
  font-size: 24rpx;
  color: #3a5a7a;
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.product-card {
  background: #f7fbff;
  border: 1rpx solid #c8dcff;
  border-radius: 28rpx;
  overflow: hidden;
  box-shadow: 0 10rpx 26rpx rgba(37, 99, 235, 0.12);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  animation: fade-up 460ms ease-out both;
}

.product-card:active {
  transform: translateY(2rpx);
}

.product-image {
  width: 100%;
  height: 320rpx;
  background-color: #dbeafe;
}

.product-info {
  padding: 24rpx;
}

.source-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  margin-bottom: 16rpx;
  font-size: 22rpx;
  font-weight: 700;
}

.source-pill.child-source {
  background: #dbeafe;
  color: #1d4ed8;
}

.source-pill.system-source {
  background: #ecfccb;
  color: #3f6212;
}

.product-title {
  color: #102a43;
  font-size: 34rpx;
  line-height: 1.5;
  font-weight: 700;
}

.product-price-row {
  display: flex;
  align-items: baseline;
  margin-top: 14rpx;
  margin-bottom: 18rpx;
}

.product-price-label {
  font-size: 24rpx;
  color: #3a5a7a;
  margin-right: 10rpx;
}

.product-price {
  font-size: 42rpx;
  line-height: 1;
  font-weight: 700;
  color: #1d4ed8;
}

.product-bottom {
  display: flex;
  gap: 14rpx;
}

.detail-btn,
.action-btn {
  flex: 1;
  min-height: 88rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.detail-btn {
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 28rpx;
  font-weight: 600;
}

.detail-btn.full {
  flex: 1 1 100%;
}

.action-btn {
  background: #dcfce7;
  color: #166534;
  padding: 0 14rpx;
}

.btn-mark {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: #22c55e;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 700;
}

.btn-text {
  margin-left: 10rpx;
  font-size: 26rpx;
  font-weight: 600;
}

.empty-card {
  margin-top: 6rpx;
  background: #eaf3ff;
  border: 1rpx solid #bfd8ff;
  border-radius: 24rpx;
  padding: 36rpx 24rpx;
  text-align: center;
}

.empty-title {
  display: block;
  color: #102a43;
  font-size: 32rpx;
  font-weight: 700;
}

.empty-hint {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #3a5a7a;
}

.sos-btn {
  position: fixed;
  right: 26rpx;
  bottom: 180rpx;
  width: 124rpx;
  height: 124rpx;
  border-radius: 50%;
  background: linear-gradient(140deg, #ef4444 0%, #b91c1c 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 14rpx 28rpx rgba(185, 28, 28, 0.35);
  z-index: 999;
  cursor: pointer;
}

.sos-btn:active {
  transform: scale(0.98);
}

.sos-text {
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
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
  .hero-card,
  .utility-card,
  .product-card {
    animation: none;
  }

  .utility-card,
  .product-card,
  .sos-btn {
    transition: none;
  }
}
</style>



