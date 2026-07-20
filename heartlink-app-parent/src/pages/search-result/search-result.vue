<template>
  <view class="container">
    <view class="summary-card">
      <text class="summary-title">识图结果</text>
      <text class="summary-desc" v-if="keywords.length > 0">
        识别关键词：{{ keywords.join('、') }}
      </text>
      <text class="summary-desc" v-else>
        暂未识别出明确关键词
      </text>
      <text class="summary-extra" v-if="description">{{ description }}</text>
    </view>

    <view class="help-card" @click="notifyChildHelp">
      <text class="help-title">没找到想要的商品？</text>
      <text class="help-subtitle">点我通知子女帮您查找</text>
    </view>

    <view class="product-list" v-if="products.length > 0">
      <view class="product-card" v-for="item in products" :key="item.id" @click="goDetail(item)">
        <image class="product-image" :src="getImageUrl(item)" mode="aspectFill"></image>
        <view class="product-info">
          <text class="product-name">{{ item.name }}</text>
          <text class="product-price">楼{{ item.price }}</text>
        </view>
      </view>
    </view>

    <view class="empty-box" v-else>
      <text class="empty-text">暂未匹配到商品，可以通知子女帮您找找</text>
    </view>
  </view>
</template>

<script>
import { familyApi, messageApi, toAbsoluteMediaUrl } from '../../api/index.js'

export default {
  data() {
    return {
      keywords: [],
      products: [],
      description: '',
      sendingHelp: false
    }
  },
  onShow() {
    this.loadSearchResult()
  },
  methods: {
    loadSearchResult() {
      const cached = uni.getStorageSync('imageSearchResult') || {}
      this.keywords = Array.isArray(cached.keywords) ? cached.keywords.filter(Boolean) : []
      this.products = Array.isArray(cached.products) ? cached.products : []
      this.description = cached.description || ''
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
    goDetail(item) {
      if (!item?.id) return
      uni.navigateTo({ url: `/pages/detail/detail?id=${item.id}` })
    },
    async notifyChildHelp() {
      if (this.sendingHelp) return
      this.sendingHelp = true
      try {
        const childRes = await familyApi.getMyChildren()
        const children = childRes.data || []
        if (!children.length) {
          uni.showToast({ title: '您还没有绑定子女', icon: 'none' })
          return
        }

        const target = children[0]
        const familyBindId = target?.bind?.id
        const receiverId = target?.child?.id
        if (!familyBindId || !receiverId) {
          uni.showToast({ title: '绑定信息不完整', icon: 'none' })
          return
        }

        const keywordText = this.keywords.length ? this.keywords.join('、') : '拍照识别商品'
        const content = `我想找这个商品，麻烦帮我看看：${keywordText}`

        await messageApi.send({
          familyBindId,
          receiverId,
          content,
          type: 'TEXT'
        })

        uni.showToast({ title: '已通知子女帮您查找', icon: 'success' })
      } catch (e) {
        console.error(e)
        uni.showToast({ title: '通知失败，请稍后重试', icon: 'none' })
      } finally {
        this.sendingHelp = false
      }
    }
  }
}
</script>

<style>
page {
  background: #f5f5f5;
}

.container {
  padding: 24rpx;
}

.summary-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 28rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
}

.summary-title {
  display: block;
  font-size: 36rpx;
  color: #333;
  font-weight: 600;
  margin-bottom: 12rpx;
}

.summary-desc {
  display: block;
  font-size: 30rpx;
  color: #666;
  line-height: 1.6;
}

.summary-extra {
  display: block;
  font-size: 28rpx;
  color: #999;
  margin-top: 10rpx;
}

.help-card {
  margin-top: 20rpx;
  background: linear-gradient(135deg, #fff2f2 0%, #ffe5e5 100%);
  border: 2rpx solid #ffd6d6;
  border-radius: 20rpx;
  padding: 24rpx;
}

.help-title {
  display: block;
  font-size: 32rpx;
  color: #e53935;
  font-weight: 600;
}

.help-subtitle {
  display: block;
  margin-top: 8rpx;
  font-size: 28rpx;
  color: #d06060;
}

.product-list {
  margin-top: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.product-card {
  background: #fff;
  border-radius: 20rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
}

.product-image {
  width: 100%;
  height: 320rpx;
  background: #eee;
}

.product-info {
  padding: 20rpx 24rpx 24rpx;
}

.product-name {
  display: block;
  font-size: 32rpx;
  color: #333;
  line-height: 1.4;
}

.product-price {
  display: block;
  margin-top: 10rpx;
  font-size: 34rpx;
  color: #ff6b6b;
  font-weight: 700;
}

.empty-box {
  margin-top: 40rpx;
  text-align: center;
  padding: 40rpx 20rpx;
}

.empty-text {
  font-size: 30rpx;
  color: #999;
}
</style>
