<template>
  <view class="container">
    <swiper class="product-swiper" indicator-dots autoplay circular>
      <swiper-item v-for="(img, index) in product.images" :key="`${img}-${index}`">
        <image class="swiper-image" :src="img" mode="aspectFill"></image>
      </swiper-item>
    </swiper>

    <view class="product-info">
      <text class="product-name">{{ product.name || '商品详情' }}</text>
      <view class="price-row">
        <text class="price">￥{{ formatMoney(product.price) }}</text>
        <text class="original-price" v-if="product.originalPrice">￥{{ formatMoney(product.originalPrice) }}</text>
        <text class="sales">已售 {{ product.sales || 0 }}</text>
      </view>
    </view>

    <view class="health-analysis" v-if="healthAnalysis">
      <view class="analysis-header">
        <view class="analysis-icon-box"></view>
        <text class="analysis-title">AI 健康分析</text>
      </view>

      <view class="warnings" v-if="healthAnalysis.warnings && healthAnalysis.warnings.length > 0">
        <view class="warning-item" v-for="(warn, idx) in healthAnalysis.warnings" :key="`warn-${idx}`">
          <text>{{ warn }}</text>
        </view>
      </view>

      <view class="suggestions" v-if="healthAnalysis.suggestions && healthAnalysis.suggestions.length > 0">
        <view class="suggestion-item" v-for="(sug, idx) in healthAnalysis.suggestions" :key="`sug-${idx}`">
          <view class="suggestion-icon-box"></view>
          <text>{{ sug }}</text>
        </view>
      </view>

      <view class="ai-summary" v-if="healthAnalysis.aiAnalysis">
        <view class="ai-summary-icon-box"></view>
        <view class="ai-summary-content">
          <text class="ai-summary-title">智能健康助手建议</text>
          <text class="ai-summary-text">{{ healthAnalysis.aiAnalysis }}</text>
        </view>
      </view>

      <view class="safe-badge" v-if="healthAnalysis.safe">
        <view class="safe-icon-box"></view>
        <text class="safe-text">适合长辈</text>
      </view>
    </view>

    <view class="tags-section" v-if="product.healthTags && product.healthTags.length > 0">
      <text class="section-title">健康标签</text>
      <view class="tags">
        <text class="tag success" v-for="tag in product.healthTags" :key="tag">{{ tag }}</text>
      </view>
    </view>

    <view class="tags-section warning-section" v-if="product.warningTags && product.warningTags.length > 0">
      <text class="section-title">注意事项</text>
      <view class="tags">
        <text class="tag warning" v-for="tag in product.warningTags" :key="tag">注意 {{ tag }}</text>
      </view>
    </view>

    <view class="reviews-section" v-if="reviewLoading || reviewList.length">
      <view class="review-head">
        <text class="section-title">用户评论</text>
        <text class="review-count" v-if="reviewList.length">{{ reviewList.length }} 条</text>
      </view>
      <view class="review-loading" v-if="reviewLoading">评论加载中...</view>
      <view class="review-list" v-else>
        <view class="review-card" v-for="review in reviewList" :key="review.id || review.createdAt">
          <view class="review-user-row">
            <image
              v-if="getReviewAvatar(review)"
              class="review-avatar-image"
              :src="getReviewAvatar(review)"
              mode="aspectFill"
            ></image>
            <view v-else class="review-avatar">{{ getReviewUserInitial(review) }}</view>
            <view class="review-user-body">
              <view class="review-user-top">
                <text class="review-user-name">{{ getReviewUserName(review) }}</text>
                <text class="review-time">{{ formatReviewTime(review.createdAt) }}</text>
              </view>
              <text class="review-stars">{{ formatRatingStars(review.rating) }}</text>
            </view>
          </view>
          <text class="review-text">{{ review.content }}</text>
          <view class="review-images" v-if="review.images && review.images.length">
            <image
              v-for="(img, index) in review.images"
              :key="`${img}-${index}`"
              class="review-image"
              :src="img"
              mode="aspectFill"
            ></image>
          </view>
        </view>
      </view>
    </view>

    <view class="desc-section" v-if="product.description">
      <text class="section-title">商品详情</text>
      <text class="desc-text">{{ product.description }}</text>
    </view>

    <view class="tryon-section" v-if="supportsTryOn()">
      <view class="tryon-header">
        <view class="tryon-icon-box"></view>
        <view class="tryon-copy">
          <text class="tryon-title">AI试穿</text>
          <text class="tryon-desc">上传一张长辈全身照，快速看看这件衣服的大致上身效果。</text>
        </view>
        <button class="btn-tryon" @click="openTryOnPage">去试穿</button>
      </view>
    </view>

    <view class="voice-section" v-if="product.voiceDescription || product.description" @click="playVoice">
      <view class="voice-card">
        <view class="voice-icon-box"></view>
        <text class="voice-text">点击收听语音介绍</text>
      </view>
    </view>

    <view class="bottom-bar safe-area-bottom">
      <view class="action-left">
        <view class="action-item" @click="goHome">
          <view class="action-icon-box action-icon-home"></view>
          <text class="action-label">首页</text>
        </view>
        <view class="action-item" @click="toggleFavorite">
          <view
            class="action-icon-box action-icon-favorite"
            :class="{ active: isFavorite }"
          ></view>
          <text class="action-label" :class="{ active: isFavorite }">
            {{ isFavorite ? '已收藏' : '收藏' }}
          </text>
        </view>
      </view>

      <view class="action-right">
        <button class="btn-order" :disabled="creatingOrder" @click="createOrder">
          {{ creatingOrder ? '下单中...' : '自己下单' }}
        </button>
        <button class="btn-push" @click="pushToShelf">
          <view class="btn-icon-box"></view>
          推送给长辈
        </button>
      </view>
    </view>

    <view class="push-modal" v-if="showPushModal">
      <view class="modal-mask" @click="showPushModal = false"></view>
      <view class="modal-content">
        <text class="modal-title">给长辈留言</text>
        <textarea
          class="message-input"
          v-model="voiceMessage"
          placeholder="妈，这个商品不错，你看看喜不喜欢？"
          maxlength="100"
        ></textarea>
        <button class="btn-confirm" @click="confirmPush">确认推送</button>
      </view>
    </view>
  </view>
</template>

<script>
import {
  productApi,
  shelfApi,
  familyApi,
  reviewApi,
  orderApi,
  toAbsoluteMediaUrl
} from '../../api/index.js'
import { resolveProductImageUrl } from '../../utils/product-image.js'
import { isLocalFavorite, toggleLocalFavorite } from '../../utils/local-favorites.js'

const DEFAULT_PRODUCT_IMAGE = '/static/products/oatmeal.png'
const TRY_ON_PRODUCT_KEY = 'CHILD_TRY_ON_PRODUCT'
const TRY_ON_KEYWORDS = /(毛衣|针织|卫衣|外套|衬衫|上衣|夹克|羽绒|衣服|服饰|保暖|T恤|大衣|马甲|开衫|裤|裙|连衣|连体|dress|skirt|pants|trousers|jeans|sweater|coat|shirt|jacket|hoodie|cardigan|wear)/i
export default {
  data() {
    return {
      productId: null,
      parentId: null,
      parentLoadingTask: null,
      product: {
        images: [DEFAULT_PRODUCT_IMAGE],
        healthTags: [],
        warningTags: []
      },
      healthAnalysis: null,
      reviewList: [],
      reviewLoading: false,
      isFavorite: false,
      creatingOrder: false,
      showPushModal: false,
      voiceMessage: ''
    }
  },
  onLoad(options = {}) {
    this.productId = Number(options.id || 0)
    this.parentId = this.normalizeParentId(options.parentId)
    this.syncFavoriteState()
    this.loadProduct()
    this.loadReviews()
    if (!this.parentId) {
      this.ensureParentId({ reloadProduct: true })
    }
  },
  onShow() {
    this.syncFavoriteState()
  },
  methods: {
    parseMaybeList(value) {
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
    normalizeParentId(value) {
      if (value === null || value === undefined) return null
      const text = String(value).trim()
      if (!text || text === 'null' || text === 'undefined') return null
      const parsed = Number(text)
      return Number.isFinite(parsed) && parsed > 0 ? parsed : null
    },
    normalizeImageUrl(value) {
      const text = String(value || '').trim()
      if (!text) return ''
      if (/^data:/i.test(text) || /^https?:\/\//i.test(text)) return text
      if (text.startsWith('/upload/') || text.startsWith('upload/')) {
        return toAbsoluteMediaUrl(text)
      }
      if (text.startsWith('/static/')) return text
      if (text.startsWith('static/')) return `/${text}`
      return ''
    },
    normalizeProduct(raw = {}) {
      const images = this.parseMaybeList(raw.images)
        .map((item) => resolveProductImageUrl(item, { productName: raw.name }))
        .filter(Boolean)

      return {
        ...raw,
        images: images.length ? images : [DEFAULT_PRODUCT_IMAGE],
        healthTags: this.parseMaybeList(raw.healthTags),
        warningTags: this.parseMaybeList(raw.warningTags)
      }
    },
    normalizeReviewList(records) {
      return (Array.isArray(records) ? records : [])
        .filter((item) => String(item?.content || '').trim())
        .map((item) => ({
          ...item,
          images: this.parseMaybeList(item.images)
            .map((img) => this.normalizeImageUrl(img))
            .filter(Boolean)
        }))
    },
    buildFavoriteProduct() {
      const productId = Number(this.product?.id || this.productId || 0)
      if (!productId) return null
      return {
        id: productId,
        name: this.product?.name || '',
        price: this.product?.price,
        description: this.product?.description || '',
        images: this.product?.images || [],
        healthTags: this.product?.healthTags || [],
        warningTags: this.product?.warningTags || []
      }
    },
    syncFavoriteState() {
      const productId = Number(this.product?.id || this.productId || 0)
      this.isFavorite = productId > 0 ? isLocalFavorite(productId) : false
    },
    async ensureParentId({ reloadProduct = false } = {}) {
      if (this.parentId) return this.parentId
      if (this.parentLoadingTask) {
        await this.parentLoadingTask
        return this.parentId
      }

      this.parentLoadingTask = (async () => {
        try {
          const res = await familyApi.getMyParents()
          const list = Array.isArray(res?.data) ? res.data : []
          const fallbackParentId = Number(list[0]?.parent?.id || 0)
          if (fallbackParentId && !this.parentId) {
            this.parentId = fallbackParentId
            if (reloadProduct && this.productId) {
              await this.loadProduct()
            }
          }
        } catch (e) {
          console.error(e)
        } finally {
          this.parentLoadingTask = null
        }
      })()

      await this.parentLoadingTask
      return this.parentId
    },
    async loadProduct() {
      if (!this.productId) return
      try {
        if (this.parentId) {
          const res = await productApi.getProductWithAnalysis(this.productId, this.parentId)
          this.product = this.normalizeProduct(res?.data?.product || {})
          this.healthAnalysis = res?.data?.healthAnalysis || null
        } else {
          const res = await productApi.getProduct(this.productId)
          this.product = this.normalizeProduct(res?.data || {})
          this.healthAnalysis = null
        }

        const previewReviews = this.normalizeReviewList(this.product.reviewPreview)
        if (previewReviews.length > 0) {
          this.reviewList = previewReviews
        }
        this.syncFavoriteState()
      } catch (e) {
        console.error(e)
      }
    },
    async loadReviews() {
      if (!this.productId) return
      this.reviewLoading = true
      try {
        const res = await reviewApi.productReviews(this.productId, { page: 1, size: 6 })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        const normalized = this.normalizeReviewList(records)
        if (normalized.length > 0) {
          this.reviewList = normalized
        }
      } catch (e) {
        console.error(e)
      } finally {
        this.reviewLoading = false
      }
    },
    getReviewUserName(review) {
      const nickname = String(review?.userNickname || '').trim()
      if (nickname && nickname !== 'null' && nickname !== 'undefined') return nickname
      const userId = Number(review?.userId || 0)
      if (!Number.isFinite(userId) || userId <= 0) return '匿名用户'
      return `用户 ${String(userId).slice(-4).padStart(4, '0')}`
    },
    getReviewAvatar(review) {
      return this.normalizeImageUrl(review?.userAvatar)
    },
    getReviewUserInitial(review) {
      return this.getReviewUserName(review).slice(-1)
    },
    formatReviewTime(value) {
      const text = String(value || '').trim()
      if (!text) return ''
      return text.replace('T', ' ').slice(0, 16)
    },
    formatRatingStars(rating) {
      const score = Math.max(0, Math.min(5, Number(rating) || 5))
      return `${'★'.repeat(score)}${'☆'.repeat(5 - score)}`
    },
    formatMoney(value) {
      const num = Number(value)
      return Number.isFinite(num) ? num.toFixed(2) : '0.00'
    },
    supportsTryOn() {
      const categoryId = Number(this.product?.categoryId || 0)
      if (categoryId === 3) return true

      const sourceText = [
        this.product?.name,
        this.product?.description,
        this.product?.categoryName,
        ...(Array.isArray(this.product?.healthTags) ? this.product.healthTags : []),
        ...(Array.isArray(this.product?.warningTags) ? this.product.warningTags : [])
      ]
        .filter(Boolean)
        .join(' ')

      return TRY_ON_KEYWORDS.test(sourceText)
    },
    openTryOnPage() {
      const garmentImage = Array.isArray(this.product?.images)
        ? this.product.images.find((item) => String(item || '').trim())
        : ''

      if (!garmentImage) {
        uni.showToast({ title: '缺少商品图片', icon: 'none' })
        return
      }

      uni.setStorageSync(TRY_ON_PRODUCT_KEY, {
        id: Number(this.product?.id || this.productId || 0),
        name: this.product?.name || '服饰商品',
        image: garmentImage,
        description: this.product?.description || '',
        categoryId: this.product?.categoryId || null,
        categoryName: this.product?.categoryName || ''
      })

      uni.navigateTo({ url: '/pages/try-on/try-on' })
    },
    playVoice() {
      const sections = [
        this.product?.name ? `商品：${this.product.name}` : '',
        this.product?.description ? `介绍：${this.product.description}` : '',
        this.product?.healthTags?.length ? `健康标签：${this.product.healthTags.join('、')}` : '',
        this.product?.warningTags?.length ? `注意事项：${this.product.warningTags.join('、')}` : '',
        this.product?.price ? `价格：${this.formatMoney(this.product.price)} 元` : ''
      ].filter(Boolean)

      uni.showModal({
        title: '语音介绍',
        content: sections.join('\n'),
        showCancel: false,
        confirmText: '知道了'
      })
    },
    goHome() {
      uni.switchTab({ url: '/pages/index/index' })
    },
    toggleFavorite() {
      const favoriteProduct = this.buildFavoriteProduct()
      if (!favoriteProduct) {
        uni.showToast({ title: '商品信息加载中', icon: 'none' })
        return
      }
      const { favorited } = toggleLocalFavorite(favoriteProduct)
      this.isFavorite = favorited
      uni.showToast({
        title: favorited ? '已加入收藏' : '已取消收藏',
        icon: 'none'
      })
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
    async createOrder() {
      if (!this.productId || this.creatingOrder) return

      const parentId = this.parentId || await this.ensureParentId()
      if (!parentId) {
        this.promptBindParent('下单')
        return
      }

      this.creatingOrder = true
      try {
        const res = await orderApi.create({
          productId: this.productId,
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
        this.creatingOrder = false
      }
    },
    async pushToShelf() {
      const parentId = this.parentId || await this.ensureParentId()
      if (!parentId) {
        this.promptBindParent('推送商品')
        return
      }

      this.voiceMessage = `妈，这个${this.product?.name || '商品'}不错，你看看喜不喜欢？`
      this.showPushModal = true
    },
    async confirmPush() {
      const parentId = this.parentId || await this.ensureParentId()
      if (!parentId) {
        uni.showToast({ title: '请先绑定长辈', icon: 'none' })
        this.showPushModal = false
        return
      }

      try {
        await shelfApi.push(parentId, this.productId, this.voiceMessage)
        uni.showToast({ title: '已推送到长辈货架', icon: 'success' })
        this.showPushModal = false
      } catch (e) {
        console.error(e)
        uni.showToast({ title: '推送失败', icon: 'none' })
      }
    }
  }
}
</script>

<style>
page {
  background: linear-gradient(180deg, #f0f9ff 0%, #e9f5ff 45%, #f7fbff 100%);
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.container {
  min-height: 100vh;
  padding-bottom: 180rpx;
}

.product-swiper {
  width: 100%;
  height: 600rpx;
}

.swiper-image {
  width: 100%;
  height: 100%;
}

.product-info,
.health-analysis,
.tags-section,
.reviews-section,
.desc-section,
.tryon-section,
.voice-section {
  background: #fff;
  margin-top: 20rpx;
  padding: 30rpx;
  border: 1rpx solid #dbeafe;
  box-shadow: 0 8rpx 22rpx rgba(12, 74, 110, 0.08);
}

.product-info {
  margin-top: 0;
  border-top: none;
}

.product-name {
  font-size: 34rpx;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.45;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  margin-top: 20rpx;
}

.price {
  font-size: 48rpx;
  font-weight: 700;
  color: #0369a1;
}

.original-price {
  font-size: 28rpx;
  color: #94a3b8;
  text-decoration: line-through;
}

.sales {
  margin-left: auto;
  font-size: 24rpx;
  color: #64748b;
}

.analysis-header,
.voice-card,
.review-head,
.review-user-row,
.review-user-top {
  display: flex;
}

.analysis-header,
.voice-card {
  align-items: center;
}

.analysis-header {
  margin-bottom: 20rpx;
}

.analysis-icon-box,
.suggestion-icon-box,
.ai-summary-icon-box,
.safe-icon-box,
.voice-icon-box,
.action-icon-box,
.btn-icon-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 0;
}

.analysis-icon-box {
  width: 48rpx;
  height: 48rpx;
  margin-right: 12rpx;
  border-radius: 12rpx;
  background: linear-gradient(135deg, #0369a1 0%, #0ea5e9 100%);
  box-shadow: 0 4rpx 10rpx rgba(3, 105, 161, 0.25);
}

.analysis-icon-box::after {
  content: '\1F4CA';
  font-size: 22rpx;
  line-height: 1;
}

.analysis-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #0f172a;
}

.warnings {
  margin-bottom: 16rpx;
  padding: 20rpx;
  border: 1rpx solid #fed7aa;
  border-radius: 16rpx;
  background: #fff7ed;
}

.warning-item {
  font-size: 28rpx;
  color: #92400e;
  padding: 8rpx 0;
}

.suggestions {
  padding: 20rpx;
  border: 1rpx solid #bfdbfe;
  border-radius: 16rpx;
  background: #eff6ff;
}

.suggestion-item {
  display: flex;
  align-items: flex-start;
  padding: 8rpx 0;
  font-size: 28rpx;
  color: #1e40af;
}

.suggestion-icon-box {
  width: 40rpx;
  height: 40rpx;
  margin-right: 10rpx;
  border-radius: 10rpx;
  background: linear-gradient(135deg, #0ea5e9 0%, #38bdf8 100%);
  box-shadow: 0 4rpx 8rpx rgba(14, 165, 233, 0.25);
}

.suggestion-icon-box::after {
  content: '\1F4A1';
  font-size: 20rpx;
  line-height: 1;
}

.ai-summary {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  margin-top: 20rpx;
  padding: 24rpx;
  border: 1rpx solid #c7d2fe;
  border-radius: 18rpx;
  background: linear-gradient(135deg, #f8fafc 0%, #eef2ff 100%);
}

.ai-summary-icon-box {
  width: 48rpx;
  height: 48rpx;
  border-radius: 14rpx;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  box-shadow: 0 4rpx 10rpx rgba(99, 102, 241, 0.25);
}

.ai-summary-icon-box::after {
  content: '\2728';
  font-size: 22rpx;
  line-height: 1;
}

.ai-summary-content {
  flex: 1;
}

.ai-summary-title {
  display: block;
  margin-bottom: 8rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #3730a3;
}

.ai-summary-text {
  font-size: 27rpx;
  color: #475569;
  line-height: 1.7;
}

.safe-badge {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
  padding: 20rpx;
  border: 1rpx solid #bbf7d0;
  border-radius: 16rpx;
  background: #dcfce7;
}

.safe-icon-box {
  width: 44rpx;
  height: 44rpx;
  margin-right: 12rpx;
  border-radius: 12rpx;
  background: linear-gradient(135deg, #22c55e 0%, #4ade80 100%);
  box-shadow: 0 4rpx 10rpx rgba(34, 197, 94, 0.25);
}

.safe-icon-box::after {
  content: '\2705';
  font-size: 20rpx;
  line-height: 1;
}

.safe-text {
  font-size: 28rpx;
  color: #166534;
  font-weight: 500;
}

.warning-section {
  border-color: #fed7aa;
}

.section-title {
  display: block;
  margin-bottom: 16rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #0f172a;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.tag {
  padding: 8rpx 20rpx;
  border-radius: 20rpx;
  font-size: 24rpx;
}

.tag.success {
  background: #dcfce7;
  color: #166534;
}

.tag.warning {
  background: #fef3c7;
  color: #92400e;
}

.review-head {
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.review-count,
.review-time {
  font-size: 22rpx;
  color: #64748b;
}

.review-loading {
  margin-top: 18rpx;
  font-size: 26rpx;
  color: #64748b;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
  margin-top: 18rpx;
}

.review-card {
  padding: 22rpx;
  border: 1rpx solid #dbeafe;
  border-radius: 20rpx;
  background: linear-gradient(135deg, #f8fbff 0%, #eff6ff 100%);
}

.review-user-row {
  align-items: flex-start;
  gap: 14rpx;
}

.review-user-top {
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.review-avatar,
.review-avatar-image {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  flex-shrink: 0;
}

.review-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%);
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
}

.review-avatar-image {
  background: #e2e8f0;
}

.review-user-body {
  flex: 1;
  min-width: 0;
}

.review-user-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #0f172a;
}

.review-stars {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  letter-spacing: 2rpx;
  color: #f59e0b;
}

.review-text,
.desc-text {
  font-size: 28rpx;
  color: #475569;
  line-height: 1.75;
}

.review-text {
  display: block;
  margin-top: 16rpx;
}

.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.review-image {
  width: 160rpx;
  height: 160rpx;
  border-radius: 16rpx;
  background: #e2e8f0;
}

.tryon-header {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.tryon-icon-box {
  width: 76rpx;
  height: 76rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #ec4899 0%, #f97316 100%);
  box-shadow: 0 8rpx 18rpx rgba(244, 114, 182, 0.25);
  flex-shrink: 0;
  position: relative;
}

.tryon-icon-box::after {
  content: 'AI';
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 700;
  color: #fff;
}

.tryon-copy {
  flex: 1;
  min-width: 0;
}

.tryon-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #7c2d12;
}

.tryon-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #9a3412;
}

.btn-tryon {
  height: 78rpx;
  line-height: 78rpx;
  padding: 0 28rpx;
  border: none;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #f97316 0%, #ec4899 100%);
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
  box-shadow: 0 10rpx 22rpx rgba(249, 115, 22, 0.22);
  flex-shrink: 0;
}

.voice-card {
  gap: 16rpx;
  padding: 24rpx;
  border-radius: 16rpx;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
}

.voice-icon-box {
  width: 44rpx;
  height: 44rpx;
  border-radius: 12rpx;
  background: linear-gradient(135deg, #0369a1 0%, #0ea5e9 100%);
  box-shadow: 0 4rpx 10rpx rgba(3, 105, 161, 0.25);
}

.voice-icon-box::after {
  content: '\1F50A';
  font-size: 20rpx;
  line-height: 1;
}

.voice-text {
  font-size: 28rpx;
  color: #1e40af;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  padding: 20rpx 30rpx;
  border-top: 1rpx solid #dbeafe;
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(12, 74, 110, 0.08);
}

.action-left {
  display: flex;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 20rpx;
}

.action-icon-box {
  width: 44rpx;
  height: 44rpx;
  border-radius: 12rpx;
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  box-shadow: 0 4rpx 8rpx rgba(3, 105, 161, 0.15);
}

.action-icon-home::after {
  content: '\1F3E0';
  font-size: 20rpx;
  line-height: 1;
}

.action-icon-favorite::after {
  content: '\2764';
  font-size: 20rpx;
  line-height: 1;
}

.action-icon-favorite.active {
  background: linear-gradient(135deg, #fb7185 0%, #f97316 100%);
  box-shadow: 0 8rpx 14rpx rgba(244, 114, 182, 0.28);
}

.action-label {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #64748b;
}

.action-label.active {
  color: #e11d48;
  font-weight: 600;
}

.action-right {
  flex: 1;
  display: flex;
  justify-content: flex-end;
  gap: 18rpx;
}

.btn-order,
.btn-push,
.btn-confirm {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50rpx;
  font-size: 30rpx;
}

.btn-order {
  padding: 24rpx 36rpx;
  border: 1rpx solid #bfdbfe;
  background: #eff6ff;
  color: #0369a1;
}

.btn-push,
.btn-confirm {
  border: none;
  background: linear-gradient(140deg, #0369a1 0%, #0ea5e9 100%);
  color: #fff;
  box-shadow: 0 10rpx 22rpx rgba(3, 105, 161, 0.2);
}

.btn-push {
  padding: 24rpx 50rpx;
}

.btn-icon-box {
  width: 40rpx;
  height: 40rpx;
  margin-right: 10rpx;
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.3);
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.1);
}

.btn-icon-box::after {
  content: '\1F4E4';
  font-size: 18rpx;
  line-height: 1;
}

.push-modal {
  position: fixed;
  inset: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-mask {
  position: absolute;
  inset: 0;
  background: rgba(3, 105, 161, 0.35);
}

.modal-content {
  position: relative;
  width: 80%;
  padding: 40rpx;
  border: 1rpx solid #dbeafe;
  border-radius: 32rpx;
  background: #fff;
  box-shadow: 0 20rpx 60rpx rgba(12, 74, 110, 0.18);
}

.modal-title {
  display: block;
  margin-bottom: 30rpx;
  text-align: center;
  font-size: 36rpx;
  font-weight: 600;
  color: #0f172a;
}

.message-input {
  width: 100%;
  height: 200rpx;
  padding: 20rpx;
  box-sizing: border-box;
  border: 1rpx solid #dbeafe;
  border-radius: 16rpx;
  background: #f0f9ff;
  color: #0f172a;
  font-size: 28rpx;
}

.btn-confirm {
  height: 90rpx;
  margin-top: 30rpx;
  line-height: 90rpx;
}
</style>
