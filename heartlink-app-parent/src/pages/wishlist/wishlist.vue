<template>
  <view class="container">
    <view class="hero-card">
      <text class="hero-title">我的心愿单</text>
      <text class="hero-subtitle">把喜欢的商品先收好，也可以直接请子女帮忙付款</text>
    </view>

    <view v-if="loading" class="loading-wrap">
      <text class="loading-text">正在加载心愿单...</text>
    </view>

    <view v-else-if="wishlist.length > 0" class="wish-list">
      <view class="wish-card" v-for="item in wishlist" :key="item.shelfId || item.id">
        <image class="wish-image" :src="getProductImage(item)" mode="aspectFill" />

        <view class="wish-main">
          <text class="wish-name">{{ item.name || '心愿商品' }}</text>
          <text class="wish-price">¥{{ formatMoney(item.price) }}</text>

          <view class="wish-actions">
            <view class="action-btn primary" @click="requestChildPay(item)">
              <text class="action-text primary-text">请子女付款</text>
            </view>
            <view class="action-btn" @click="goDetail(item)">
              <text class="action-text">查看详情</text>
            </view>
            <view class="action-btn danger" @click="removeItem(item)">
              <text class="action-text danger-text">移除</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view v-else class="empty-card">
      <text class="empty-title">心愿单还是空的</text>
      <text class="empty-hint">遇到喜欢的商品先点收藏，子女也能更快看到您的需求</text>
      <view class="empty-btn" @click="goHome">
        <text class="empty-btn-text">去首页看看</text>
      </view>
    </view>
  </view>
</template>

<script>
import { paymentRequestApi, shelfApi, toAbsoluteMediaUrl } from '../../api/index.js'

export default {
  data() {
    return {
      wishlist: [],
      loading: false
    }
  },
  onShow() {
    const token = uni.getStorageSync('token')
    if (!token) {
      uni.reLaunch({ url: '/pages/login/login' })
      return
    }
    this.loadWishlist()
  },
  methods: {
    async loadWishlist() {
      this.loading = true
      try {
        const res = await shelfApi.getMyLiked()
        const list = Array.isArray(res.data) ? res.data : []
        this.wishlist = list
          .filter((item) => item && item.shelf && item.product)
          .map((item) => ({
            ...item.product,
            shelfId: item.shelf.id
          }))
      } catch (e) {
        console.error(e)
        this.wishlist = []
      } finally {
        this.loading = false
      }
    },
    getProductImage(item) {
      const image = Array.isArray(item?.images) ? item.images[0] : ''
      const raw = String(image || '').trim()
      if (!raw) return '/static/products/oatmeal.png'
      if (/^https?:\/\//i.test(raw) || /^data:/i.test(raw)) return raw
      if (raw.startsWith('/static/')) return raw
      return toAbsoluteMediaUrl(raw) || '/static/products/oatmeal.png'
    },
    formatMoney(value) {
      const amount = Number(value)
      return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
    },
    async requestChildPay(item) {
      if (!item?.id) return
      uni.showModal({
        title: '请子女付款',
        content: `将把“${item.name || '这件商品'}”发送给子女代付，是否继续？`,
        confirmText: '立即发送',
        success: async (res) => {
          if (!res.confirm) return
          try {
            uni.showLoading({ title: '发送中...' })
            await paymentRequestApi.createByProduct(item.id, `我想要这件商品，麻烦帮我付款：${item.name || '心愿商品'}`)
            uni.showToast({ title: '代付请求已发送', icon: 'success' })
          } catch (e) {
            console.error(e)
            uni.showToast({ title: e?.message || '发送失败，请稍后重试', icon: 'none' })
          } finally {
            uni.hideLoading()
          }
        }
      })
    },
    removeItem(item) {
      uni.showModal({
        title: '移出心愿单',
        content: '确认把这件商品从心愿单中移除吗？',
        confirmText: '确认移除',
        success: async (res) => {
          if (!res.confirm) return
          try {
            if (item?.shelfId) {
              await shelfApi.react(item.shelfId, 'DISLIKE')
            }
            this.wishlist = this.wishlist.filter((wish) => wish.shelfId !== item.shelfId)
            uni.showToast({ title: '已移除', icon: 'none' })
          } catch (e) {
            console.error(e)
            uni.showToast({ title: '移除失败，请稍后重试', icon: 'none' })
          }
        }
      })
    },
    goDetail(item) {
      if (!item?.id) return
      uni.navigateTo({ url: `/pages/detail/detail?id=${item.id}` })
    },
    goHome() {
      uni.switchTab({ url: '/pages/index/index' })
    }
  }
}
</script>

<style>
page {
  background: linear-gradient(180deg, #dbeafe 0%, #eff6ff 100%);
  min-height: 100vh;
  color: #102a43;
}

.container {
  padding: 24rpx;
}

.hero-card {
  background: linear-gradient(135deg, #1d4ed8 0%, #2563eb 55%, #0ea5e9 100%);
  border-radius: 28rpx;
  padding: 30rpx;
  box-shadow: 0 16rpx 36rpx rgba(37, 99, 235, 0.2);
}

.hero-title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #ffffff;
}

.hero-subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.85);
}

.loading-wrap {
  padding: 120rpx 0;
  text-align: center;
}

.loading-text {
  color: #4b647c;
  font-size: 28rpx;
}

.wish-list {
  margin-top: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.wish-card {
  background: rgba(255, 255, 255, 0.98);
  border: 1rpx solid #d8e7ff;
  border-radius: 26rpx;
  padding: 22rpx;
  display: flex;
  gap: 20rpx;
  box-shadow: 0 12rpx 30rpx rgba(59, 130, 246, 0.12);
}

.wish-image {
  width: 200rpx;
  height: 200rpx;
  border-radius: 22rpx;
  background: #dbeafe;
  flex-shrink: 0;
}

.wish-main {
  flex: 1;
  min-width: 0;
}

.wish-name {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  line-height: 1.45;
  color: #102a43;
}

.wish-price {
  display: block;
  margin-top: 16rpx;
  font-size: 40rpx;
  font-weight: 700;
  color: #1d4ed8;
}

.wish-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 22rpx;
}

.action-btn {
  min-width: 168rpx;
  height: 72rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: #eef4ff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-btn.primary {
  background: linear-gradient(135deg, #1d4ed8 0%, #0ea5e9 100%);
}

.action-btn.danger {
  background: #fff1f2;
}

.action-text {
  font-size: 24rpx;
  font-weight: 600;
  color: #355070;
}

.primary-text {
  color: #ffffff;
}

.danger-text {
  color: #e11d48;
}

.empty-card {
  margin-top: 28rpx;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 28rpx;
  padding: 56rpx 30rpx;
  text-align: center;
  box-shadow: 0 12rpx 28rpx rgba(59, 130, 246, 0.12);
}

.empty-title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: #102a43;
}

.empty-hint {
  display: block;
  margin-top: 14rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #4b647c;
}

.empty-btn {
  margin: 30rpx auto 0;
  width: 240rpx;
  height: 82rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #1d4ed8 0%, #0ea5e9 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-btn-text {
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
}
</style>
