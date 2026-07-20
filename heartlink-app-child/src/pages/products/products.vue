<template>
  <view class="container">
    <view class="header">
      <text class="title">商品列表</text>
    </view>

    <view class="category-section">
      <scroll-view scroll-x class="category-scroll">
        <view class="category-tabs">
          <view
            class="category-tab"
            :class="{ active: selectedCategoryId === null }"
            @click="selectCategory(null)"
          >
            <view class="tab-icon-box">全</view>
            <text class="tab-name">全部</text>
          </view>
          <view
            v-for="cat in categories"
            :key="cat.id"
            class="category-tab"
            :class="{ active: selectedCategoryId === cat.id }"
            @click="selectCategory(cat.id)"
          >
            <view class="tab-icon-box">
              <image
                v-if="isImageIcon(cat.icon)"
                class="tab-icon-image"
                :src="cat.icon"
                mode="aspectFill"
              ></image>
              <text v-else>{{ cat.icon || '好物' }}</text>
            </view>
            <text class="tab-name">{{ cat.name }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="search-bar">
      <input class="search-input" placeholder="搜索商品" v-model="keyword" @confirm="search" />
      <button class="search-btn" @click="search">搜索</button>
    </view>

    <view class="product-list">
      <view
        class="product-card"
        v-for="product in productList"
        :key="product.id"
        @click="goDetail(product.id)"
      >
        <image
          class="product-image"
          :src="getImageUrl(product.images && product.images[0], product.name)"
          mode="aspectFill"
        ></image>
        <view class="product-info">
          <text class="product-name">{{ product.name }}</text>
          <view class="tags" v-if="product.healthTags && product.healthTags.length">
            <text class="tag success" v-for="tag in product.healthTags.slice(0, 2)" :key="tag">{{ tag }}</text>
          </view>
          <view class="tags" v-if="product.warningTags && product.warningTags.length">
            <text class="tag warning" v-for="tag in product.warningTags.slice(0, 1)" :key="tag">注意 {{ tag }}</text>
          </view>
          <view class="bottom">
            <text class="price">¥{{ product.price }}</text>
            <view class="action-group">
              <button
                class="btn-order"
                :disabled="creatingProductId === product.id"
                @click.stop="createOrder(product)"
              >
                {{ creatingProductId === product.id ? '下单中...' : '自己下单' }}
              </button>
              <button class="btn-push" @click.stop="pushToShelf(product)">推送给长辈</button>
            </view>
          </view>
        </view>
      </view>
      <view class="empty-tip" v-if="productList.length === 0 && !loading">暂无商品</view>
    </view>

    <view class="loading" v-if="loading">加载中...</view>
    <view class="no-more" v-if="noMore">没有更多了</view>
  </view>
</template>

<script>
import { productApi, shelfApi, familyApi, categoryApi, orderApi, toAbsoluteMediaUrl } from '../../api/index.js'
import { resolveProductImageUrl } from '../../utils/product-image.js'

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
      productList: [],
      categories: [],
      keyword: '',
      page: 1,
      loading: false,
      noMore: false,
      selectedParentId: null,
      parentLoadingTask: null,
      creatingProductId: null,
      selectedCategoryId: null,
      chronicDiseases: []
    }
  },
  onLoad() {
    this.loadCategories()
    this.loadParent()
    this.loadProducts()
  },
  onReachBottom() {
    if (!this.noMore && !this.loading) {
      this.page++
      this.loadProducts()
    }
  },
  methods: {
    async loadCategories() {
      try {
        const res = await categoryApi.getList()
        const source = Array.isArray(res?.data) ? res.data : []
        this.categories = source.map((item) => ({
          icon: this.normalizeCategoryIcon(item.icon || '好物'),
          id: item.id,
          name: item.name || item.categoryName || '分类',
        }))
      } catch (e) {
        console.error(e)
        this.categories = []
      }
    },
    normalizeCategoryIcon(icon) {
      const value = String(icon || '').trim()
      if (!value) return '好物'
      if (isWechatMiniProgram && (/^http:\/\//i.test(value) || /^(\/)?upload\//i.test(value))) {
        return '好物'
      }
      if (!this.isImageIcon(value)) return value
      if (value.startsWith('/static/')) return value
      if (value.startsWith('static/')) return `/${value}`
      const resolved = toAbsoluteMediaUrl(value) || value
      if (isWechatMiniProgram && /^http:\/\//i.test(resolved)) {
        return '好物'
      }
      return resolved
    },
    normalizeProductItem(item = {}) {
      const next = { ...item }
      if (typeof next.images === 'string') {
        try {
          next.images = JSON.parse(next.images)
        } catch (e) {
          next.images = [next.images]
        }
      }
      if (!Array.isArray(next.images)) {
        next.images = next.images ? [next.images] : []
      }
      if (typeof next.healthTags === 'string') {
        try {
          next.healthTags = JSON.parse(next.healthTags)
        } catch (e) {
          next.healthTags = next.healthTags ? [next.healthTags] : []
        }
      }
      if (!Array.isArray(next.healthTags)) {
        next.healthTags = []
      }
      if (typeof next.warningTags === 'string') {
        try {
          next.warningTags = JSON.parse(next.warningTags)
        } catch (e) {
          next.warningTags = next.warningTags ? [next.warningTags] : []
        }
      }
      if (!Array.isArray(next.warningTags)) {
        next.warningTags = []
      }
      return next
    },
    async loadParent() {
      try {
        const res = await familyApi.getMyParents()
        const list = Array.isArray(res?.data) ? res.data : []
        if (list.length > 0) {
          this.selectedParentId = list[0].parent?.id || null
        }
      } catch (e) {
        console.error(e)
      }
    },
    async ensureParentId() {
      if (this.selectedParentId) return this.selectedParentId
      if (this.parentLoadingTask) {
        await this.parentLoadingTask
        return this.selectedParentId
      }

      this.parentLoadingTask = (async () => {
        try {
          const res = await familyApi.getMyParents()
          const list = Array.isArray(res?.data) ? res.data : []
          if (list.length > 0 && !this.selectedParentId) {
            this.selectedParentId = list[0].parent?.id || null
          }
        } catch (e) {
          console.error(e)
        } finally {
          this.parentLoadingTask = null
        }
      })()

      await this.parentLoadingTask
      return this.selectedParentId
    },
    async loadProducts() {
      if (this.loading) return
      this.loading = true
      try {
        const params = {
          page: this.page,
          size: 10,
          keyword: this.keyword
        }
        if (this.selectedCategoryId) {
          params.categoryId = this.selectedCategoryId
        }
        const res = await productApi.getProducts(params)
        const payload = res?.data || {}
        const newList = Array.isArray(payload.records)
          ? payload.records
          : Array.isArray(payload.list)
            ? payload.list
            : []
        const normalizedList = newList.map((item) => this.normalizeProductItem(item))

        if (this.page === 1) {
          this.productList = normalizedList
        } else {
          this.productList = [...this.productList, ...normalizedList]
        }
        this.noMore = newList.length < 10
      } catch (e) {
        console.error(e)
        if (this.page === 1) {
          this.productList = []
        }
      } finally {
        this.loading = false
      }
    },
    isImageIcon(icon) {
      const text = String(icon || '').trim()
      if (!text) return false
      return /^(https?:\/\/|\/upload\/|\/static\/|data:)/i.test(text)
    },
    selectCategory(categoryId) {
      this.selectedCategoryId = categoryId
      this.page = 1
      this.noMore = false
      this.loadProducts()
    },
    search() {
      this.page = 1
      this.noMore = false
      this.loadProducts()
    },
    async goDetail(id) {
      const parentId = this.selectedParentId || await this.ensureParentId()
      let url = `/pages/product-detail/product-detail?id=${id}`
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
    async createOrder(product) {
      if (!product?.id || this.creatingProductId === product.id) return

      const parentId = this.selectedParentId || await this.ensureParentId()
      if (!parentId) {
        this.promptBindParent('下单')
        return
      }

      this.creatingProductId = product.id
      try {
        const res = await orderApi.create({
          productId: product.id,
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
        this.creatingProductId = null
      }
    },
    async pushToShelf(product) {
      const parentId = this.selectedParentId || await this.ensureParentId()
      if (!parentId) {
        this.promptBindParent('推送商品')
        return
      }
      try {
        await shelfApi.push(parentId, product.id, `妈，这个${product.name}不错，你看看喜不喜欢`)
        uni.showToast({ title: '已推送到长辈货架', icon: 'success' })
      } catch (e) {
        console.error(e)
      }
    },
    getImageUrl(url, productName = '') {
      return resolveProductImageUrl(url, { productName })
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
  padding-bottom: 140rpx;
}

.header {
  background: linear-gradient(135deg, #0369a1 0%, #0ea5e9 50%, #38bdf8 100%);
  padding: 50rpx 32rpx 40rpx;
}

.title {
  font-size: 42rpx;
  font-weight: 700;
  color: #fff;
  letter-spacing: 2rpx;
}

.category-section {
  background: #fff;
  padding: 20rpx 0;
}

.category-scroll {
  white-space: nowrap;
}

.category-tabs {
  display: inline-flex;
  padding: 0 20rpx;
  gap: 24rpx;
}

.category-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16rpx 24rpx;
  border-radius: 20rpx;
  background: #f0f9ff;
  min-width: 120rpx;
  animation: fadeUp 0.3s ease both;
}

.category-tab.active {
  background: linear-gradient(135deg, #0369a1 0%, #0ea5e9 100%);
}

.tab-icon-box {
  width: 56rpx;
  height: 56rpx;
  border-radius: 14rpx;
  background: #e0f2fe;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: 700;
  color: #0c4a6e;
  overflow: hidden;
}

.tab-icon-image {
  width: 100%;
  height: 100%;
}

.category-tab.active .tab-icon-box {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
}

.tab-name {
  font-size: 24rpx;
  color: #475569;
  margin-top: 8rpx;
}

.category-tab.active .tab-name {
  color: #fff;
  font-weight: 600;
}

.search-bar {
  display: flex;
  padding: 24rpx;
  background: #fff;
  gap: 16rpx;
  margin-top: 2rpx;
}

.search-input {
  flex: 1;
  background: #f0f9ff;
  border-radius: 48rpx;
  padding: 20rpx 32rpx;
  font-size: 30rpx;
  border: 2rpx solid #dbeafe;
  color: #0f172a;
}

.search-btn {
  background: linear-gradient(140deg, #0369a1 0%, #0ea5e9 100%);
  color: #fff;
  border: none;
  border-radius: 48rpx;
  padding: 0 40rpx;
  font-size: 30rpx;
  font-weight: 600;
  box-shadow: 0 10rpx 22rpx rgba(3, 105, 161, 0.2);
}

.product-list {
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.product-card {
  background: #fff;
  border-radius: 28rpx;
  overflow: hidden;
  display: flex;
  border: 1rpx solid #dbeafe;
  box-shadow: 0 8rpx 22rpx rgba(12, 74, 110, 0.08);
  animation: fadeUp 0.35s ease both;
}

.product-card:active {
  transform: scale(0.985);
}

.product-image {
  width: 280rpx;
  height: 280rpx;
  flex-shrink: 0;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
}

.product-info {
  flex: 1;
  padding: 28rpx;
  display: flex;
  flex-direction: column;
}

.product-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.5;
}

.tags {
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

.tag.warning {
  background: #fef3c7;
  color: #92400e;
}

.bottom {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16rpx;
  margin-top: auto;
  padding-top: 16rpx;
}

.price {
  font-size: 44rpx;
  font-weight: 700;
  color: #0369a1;
  flex-shrink: 0;
}

.action-group {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12rpx;
}

.btn-order,
.btn-push {
  background: linear-gradient(140deg, #0369a1 0%, #0ea5e9 100%);
  color: #fff;
  border: none;
  border-radius: 36rpx;
  padding: 16rpx 32rpx;
  font-size: 26rpx;
  font-weight: 600;
  box-shadow: 0 10rpx 22rpx rgba(3, 105, 161, 0.2);
}

.btn-order {
  background: linear-gradient(140deg, #15803d 0%, #22c55e 100%);
  box-shadow: 0 10rpx 22rpx rgba(21, 128, 61, 0.2);
}

.btn-order[disabled] {
  opacity: 0.7;
}

.empty-tip {
  text-align: center;
  padding: 80rpx 0;
  color: #64748b;
  font-size: 28rpx;
}

.loading,
.no-more {
  text-align: center;
  padding: 40rpx;
  color: #64748b;
  font-size: 28rpx;
}

@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(20rpx);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .product-card,
  .category-tab {
    animation: none;
  }
}
</style>
