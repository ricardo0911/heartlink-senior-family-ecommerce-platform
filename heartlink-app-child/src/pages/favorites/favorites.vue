<template>
  <view class="container">
    <view class="hero-card">
      <text class="hero-eyebrow">我的收藏</text>
      <text class="hero-title">{{ getHeroTitle() }}</text>
      <text class="hero-subtitle">已收录 {{ favorites.length }} 件商品，可随时查看和下单</text>
    </view>

    <view v-if="favorites.length" class="favorite-list">
      <view v-for="item in favorites" :key="item.favoriteKey" class="favorite-card">
        <image
          class="product-image"
          :src="getProductImage(item)"
          mode="aspectFill"
        />

        <view class="card-main">
          <view class="card-head">
            <text class="product-name">{{ item.product?.name || '收藏商品' }}</text>
            <text class="source-badge" :class="getSourceClass(item)">{{ getSourceLabel(item) }}</text>
          </view>

          <text v-if="item.product?.description" class="product-desc">{{ item.product.description }}</text>
          <text class="favorite-time">收藏时间：{{ formatTime(item.favoritedAt) }}</text>

          <view class="card-bottom">
            <text class="product-price">￥{{ formatMoney(item.product?.price) }}</text>
            <view class="action-row">
              <button class="btn btn-ghost" @click="goDetail(item)">商品详情</button>
              <button
                class="btn btn-primary"
                :loading="creatingKey === item.favoriteKey"
                :disabled="creatingKey === item.favoriteKey"
                @click="createOrder(item)"
              >
                立即下单
              </button>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view v-else class="empty">
      <view class="empty-icon">藏</view>
      <text class="empty-title">还没有收藏商品</text>
      <text class="empty-text">商品详情页点一下收藏，这里就会自动沉淀你的心选清单。</text>
      <button class="empty-btn" @click="goProducts">去商品页看看</button>
    </view>
  </view>
</template>

<script>
import { familyApi, orderApi, shelfApi } from '../../api/index.js'
import { resolveProductImageUrl } from '../../utils/product-image.js'
import { getLocalFavorites } from '../../utils/local-favorites.js'

const FAVORITE_SOURCE = {
  LOCAL: 'LOCAL',
  FAMILY: 'FAMILY'
}

export default {
  data() {
    return {
      parentId: null,
      parentName: '家人',
      favorites: [],
      creatingKey: ''
    }
  },
  onLoad(options = {}) {
    this.parentId = options?.parentId ? Number(options.parentId) : null
  },
  onShow() {
    this.loadPageData()
  },
  methods: {
    async loadPageData() {
      const localFavorites = this.buildLocalFavorites()
      const mergedFavorites = [...localFavorites]

      try {
        const res = await familyApi.getMyParents()
        const parents = Array.isArray(res?.data) ? res.data : []
        if (parents.length > 0) {
          const current = parents.find((item) => Number(item.parent?.id) === Number(this.parentId)) || parents[0]
          this.parentId = current?.parent?.id || this.parentId
          this.parentName = current?.bind?.relation || current?.parent?.nickname || '家人'

          if (this.parentId) {
            try {
              const listRes = await shelfApi.getChildList({
                parentId: this.parentId,
                reaction: 'LIKE',
                page: 1,
                size: 50
              })
              const records = Array.isArray(listRes?.data?.records) ? listRes.data.records : []
              mergedFavorites.push(...this.buildFamilyFavorites(records))
            } catch (e) {
              console.error(e)
            }
          }
        } else {
          this.parentName = '自己'
        }
      } catch (e) {
        console.error(e)
        this.parentName = '自己'
      }

      this.favorites = mergedFavorites.sort((left, right) => {
        return String(right.favoritedAt || '').localeCompare(String(left.favoritedAt || ''))
      })
    },
    buildLocalFavorites() {
      return getLocalFavorites().map((item) => ({
        favoriteKey: `local-${item.id}`,
        favoriteType: FAVORITE_SOURCE.LOCAL,
        favoritedAt: item.favoritedAt,
        parentId: this.parentId,
        shelf: null,
        product: {
          ...item,
          images: this.normalizeListField(item.images)
        }
      }))
    },
    buildFamilyFavorites(records) {
      return (Array.isArray(records) ? records : []).map((item, index) => ({
        favoriteKey: `family-${item?.shelf?.id || item?.product?.id || index}`,
        favoriteType: FAVORITE_SOURCE.FAMILY,
        favoritedAt: item?.shelf?.reactedAt || item?.shelf?.createdAt || '',
        parentId: this.parentId,
        shelf: item?.shelf || null,
        product: {
          ...(item?.product || {}),
          images: this.normalizeListField(item?.product?.images)
        }
      }))
    },
    normalizeListField(value) {
      if (Array.isArray(value)) return value.filter(Boolean)
      if (typeof value === 'string' && value.trim()) {
        try {
          const parsed = JSON.parse(value)
          return Array.isArray(parsed) ? parsed.filter(Boolean) : [value]
        } catch (e) {
          return [value]
        }
      }
      return []
    },
    getHeroTitle() {
      return this.parentId ? `${this.parentName}和我的心选清单` : '我的心选清单'
    },
    getSourceLabel(item) {
      return item.favoriteType === FAVORITE_SOURCE.FAMILY ? '长辈喜欢' : '我的收藏'
    },
    getSourceClass(item) {
      return item.favoriteType === FAVORITE_SOURCE.FAMILY ? 'source-family' : 'source-local'
    },
    getProductImage(item) {
      const image = this.normalizeListField(item?.product?.images)[0]
      return resolveProductImageUrl(image, { productName: item?.product?.name || '' })
    },
    async createOrder(item) {
      if (!item || this.creatingKey === item.favoriteKey) return

      if (item.favoriteType === FAVORITE_SOURCE.FAMILY && item?.shelf?.id) {
        uni.navigateTo({
          url: `/pages/orders/orders?action=create&shelfId=${item.shelf.id}`
        })
        return
      }

      const productId = Number(item?.product?.id || item?.shelf?.productId || 0)
      if (!productId) {
        uni.showToast({ title: '商品信息缺失', icon: 'none' })
        return
      }

      const parentId = Number(item?.parentId || this.parentId || 0)
      if (!parentId) {
        this.promptBindParent('下单')
        return
      }

      this.creatingKey = item.favoriteKey
      try {
        const res = await orderApi.create({
          productId,
          parentId,
          quantity: 1,
          generateGreeting: true
        })
        const orderId = res?.data?.id
        if (!orderId) {
          throw new Error('Missing order id')
        }
        uni.navigateTo({ url: `/pages/payment/payment?orderId=${orderId}` })
      } catch (e) {
        console.error(e)
        uni.showToast({ title: '下单失败', icon: 'none' })
      } finally {
        this.creatingKey = ''
      }
    },
    goDetail(item) {
      const productId = Number(item?.product?.id || item?.shelf?.productId || 0)
      if (!productId) {
        uni.showToast({ title: '商品信息缺失', icon: 'none' })
        return
      }

      let url = `/pages/product-detail/product-detail?id=${productId}`
      const parentId = Number(item?.parentId || this.parentId || 0)
      if (parentId) {
        url += `&parentId=${parentId}`
      }
      uni.navigateTo({ url })
    },
    promptBindParent(actionText = '下单') {
      uni.showModal({
        title: '未检测到已绑定长辈',
        content: `请先绑定长辈，再${actionText}`,
        confirmText: '去绑定',
        cancelText: '取消',
        success: (res) => {
          if (res.confirm) {
            uni.navigateTo({ url: '/pages/bind/bind' })
          }
        }
      })
    },
    goProducts() {
      uni.switchTab({ url: '/pages/products/products' })
    },
    formatMoney(value) {
      const num = Number(value)
      return Number.isFinite(num) ? num.toFixed(2) : '0.00'
    },
    formatTime(value) {
      if (!value) return '--'
      const text = String(value).replace('T', ' ')
      const date = new Date(text.replace(/-/g, '/'))
      if (Number.isNaN(date.getTime())) return text.slice(0, 16)
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      const hour = String(date.getHours()).padStart(2, '0')
      const minute = String(date.getMinutes()).padStart(2, '0')
      return `${month}/${day} ${hour}:${minute}`
    }
  }
}
</script>

<style>
page {
  min-height: 100vh;
  background: linear-gradient(180deg, #fff3f0 0%, #fff7f5 48%, #ffffff 100%);
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.container {
  min-height: 100vh;
  padding: 24rpx;
  padding-bottom: 40rpx;
}

.hero-card {
  padding: 32rpx 30rpx;
  border-radius: 30rpx;
  background: linear-gradient(135deg, #fb7185 0%, #f97316 52%, #fdba74 100%);
  color: #fff;
  box-shadow: 0 18rpx 36rpx rgba(249, 115, 22, 0.18);
}

.hero-eyebrow {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.82);
}

.hero-title {
  display: block;
  margin-top: 12rpx;
  font-size: 40rpx;
  font-weight: 700;
}

.hero-subtitle {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.9);
}

.favorite-list {
  margin-top: 22rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.favorite-card {
  display: flex;
  gap: 18rpx;
  padding: 22rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.98);
  border: 1rpx solid #ffe0d6;
  box-shadow: 0 12rpx 28rpx rgba(120, 53, 15, 0.08);
}

.product-image {
  width: 180rpx;
  height: 180rpx;
  border-radius: 22rpx;
  background: #fff1eb;
  flex-shrink: 0;
}

.card-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12rpx;
}

.product-name {
  flex: 1;
  font-size: 30rpx;
  line-height: 1.45;
  font-weight: 700;
  color: #341d14;
}

.source-badge {
  flex-shrink: 0;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 700;
}

.source-local {
  background: #ffe4e6;
  color: #e11d48;
}

.source-family {
  background: #ffedd5;
  color: #c2410c;
}

.product-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #7c675c;
}

.favorite-time {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #9a8478;
}

.card-bottom {
  margin-top: auto;
  padding-top: 18rpx;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16rpx;
}

.product-price {
  font-size: 40rpx;
  font-weight: 700;
  color: #ef4444;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.btn {
  margin: 0;
  min-width: 148rpx;
  height: 64rpx;
  line-height: 64rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  padding: 0 20rpx;
  border: none;
}

.btn::after {
  border: none;
}

.btn-ghost {
  background: #fff7f3;
  color: #9a3412;
  border: 1rpx solid #fed7aa;
}

.btn-primary {
  background: linear-gradient(135deg, #fb7185 0%, #f97316 100%);
  color: #fff;
  box-shadow: 0 10rpx 22rpx rgba(249, 115, 22, 0.16);
}

.empty {
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.empty-icon {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fb7185 0%, #f97316 100%);
  color: #fff;
  font-size: 54rpx;
  font-weight: 700;
}

.empty-title {
  margin-top: 22rpx;
  font-size: 34rpx;
  font-weight: 700;
  color: #341d14;
}

.empty-text {
  margin-top: 14rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #8b756a;
  text-align: center;
}

.empty-btn {
  margin-top: 26rpx;
  min-width: 240rpx;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 999rpx;
  border: none;
  background: linear-gradient(135deg, #fb7185 0%, #f97316 100%);
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  box-shadow: 0 12rpx 24rpx rgba(249, 115, 22, 0.16);
}

.empty-btn::after {
  border: none;
}
</style>
