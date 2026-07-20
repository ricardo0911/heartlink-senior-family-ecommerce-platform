<template>
  <view class="page">
    <view class="top-banner">
      <text class="banner-title">共 {{ childCount + parentCount }} 条，按来源快速筛选</text>
      <text class="banner-subtitle">子女端看订单，长辈端看长辈推送和喜欢的商品。</text>
    </view>

    <view class="tabs">
      <view
        v-for="tab in tabs"
        :key="tab.value"
        class="tab"
        :class="{ active: activeTab === tab.value }"
        @click="changeMainTab(tab.value)"
      >
        {{ getTabLabel(tab.value) }}
      </view>
    </view>

    <view v-if="activeTab === 'child'" class="child-status-tabs">
      <view
        v-for="tab in childStatusTabs"
        :key="tab.value"
        class="child-status-tab"
        :class="{ active: activeChildStatus === tab.value }"
        @click="activeChildStatus = tab.value"
      >
        {{ getChildStatusLabel(tab.value) }}
      </view>
    </view>

    <view v-if="displayRecords.length" class="record-list">
      <view
        v-for="record in displayRecords"
        :key="`${record.type}-${record.id}`"
        class="record-card"
      >
        <view class="card-head">
          <view class="head-main">
            <text class="product-name">{{ getRecordTitle(record) }}</text>
            <text class="sub-line">{{ getRecordSubLine(record) }}</text>
            <text class="order-no">{{ getRecordMetaLine(record) }}</text>
          </view>

          <view class="head-side">
            <text class="source-tag" :class="record.type === 'child' ? 'is-child' : 'is-parent'">
              {{ record.type === 'child' ? '子女端' : '长辈端' }}
            </text>
            <text class="status-text" :class="getRecordStatusClass(record)">
              {{ getRecordStatusText(record) }}
            </text>
          </view>
        </view>

        <view class="card-divider"></view>

        <view class="product-row">
          <image class="product-image" :src="getRecordImage(record)" mode="aspectFill" />

          <view class="product-main">
            <text class="info-text">{{ getPrimaryInfo(record) }}</text>
            <text class="info-text">{{ getSecondaryInfo(record) }}</text>
            <text v-if="getVoiceText(record)" class="voice-text">推荐语：{{ getVoiceText(record) }}</text>
          </view>
        </view>

        <view class="card-footer">
          <view class="amount-block">
            <text class="amount-label">{{ record.type === 'child' ? '实付' : '参考价' }}</text>
            <text class="amount-value">¥{{ getRecordAmount(record) }}</text>
          </view>

          <view class="action-row">
            <button class="action-btn outline" @click="goRecordDetail(record)">
              查看详情
            </button>

            <button
              v-if="record.type === 'child' && canViewLogistics(record.raw)"
              class="action-btn outline"
              @click="openLogistics(record.raw)"
            >
              查看物流
            </button>

            <button
              v-if="record.type === 'child' && canRequestRefund(record.raw)"
              class="action-btn outline warn"
              @click="goToRefund(record.raw.id)"
            >
              {{ getRefundActionText(record.raw) }}
            </button>

            <button
              v-if="record.type === 'child' && record.raw.status === 'PENDING_PAY'"
              class="action-btn primary"
              @click="goToPayment(record.raw.id)"
            >
              继续支付
            </button>

            <button
              v-if="record.type === 'parent' && record.raw.shelf?.reaction === 'LIKE'"
              class="action-btn primary"
              :disabled="creatingShelfId === record.raw.shelf.id"
              @click="createOrder(record.raw)"
            >
              {{ creatingShelfId === record.raw.shelf.id ? '下单中...' : '立即下单' }}
            </button>
          </view>
        </view>
      </view>
    </view>

    <view v-else class="empty">
      <view class="empty-icon">单</view>
      <text class="empty-title">当前标签下没有内容</text>
      <text class="empty-text">切换到其他标签看看，或者先去商城给长辈选商品。</text>
      <button class="empty-btn" @click="goProducts">去商城</button>
    </view>

    <order-logistics-popup
      :visible="logisticsPopup.visible"
      :loading="logisticsPopup.loading"
      :logistics="logisticsPopup.data"
      @close="closeLogisticsPopup"
    />
  </view>
</template>

<script>
import { familyApi, orderApi, shelfApi } from '../../api/index.js'
import OrderLogisticsPopup from '../../components/OrderLogisticsPopup.vue'
import { resolveProductImageUrl } from '../../utils/product-image.js'

const buildPaymentUrl = (orderId) => `/pages/payment/payment?orderId=${orderId}`
const buildRefundUrl = (orderId) => `/pages/refund/refund?orderId=${orderId}`
const buildOrderDetailUrl = (orderId) => `/pages/order-detail/order-detail?orderId=${orderId}`
const ORDER_RECEIVE_CONFIRMED_EVENT = 'HL_ORDER_RECEIVE_CONFIRMED'

const safeTime = (value) => {
  if (!value) return 0
  const date = new Date(String(value).replace(/-/g, '/').replace('T', ' '))
  return Number.isNaN(date.getTime()) ? 0 : date.getTime()
}

export default {
  components: {
    OrderLogisticsPopup
  },
  data() {
    return {
      parentId: null,
      activeTab: 'child',
      activeChildStatus: 'pending',
      creatingShelfId: null,
      tabs: [
        { label: '子女端', value: 'child' },
        { label: '长辈端', value: 'parent' }
      ],
      childStatusTabs: [
        { label: '未支付', value: 'pending' },
        { label: '已支付', value: 'paid' },
        { label: '正在配送', value: 'shipping' },
        { label: '已退款', value: 'refunded' },
        { label: '待评价', value: 'review' }
      ],
      childOrders: [],
      parentRecords: [],
      logisticsPopup: {
        visible: false,
        loading: false,
        data: null
      }
    }
  },
  computed: {
    childCount() {
      return this.childOrders.length
    },
    parentCount() {
      return this.parentRecords.length
    },
    childStatusCountMap() {
      return {
        pending: this.childOrders.filter((order) => this.matchChildStatus(order, 'pending')).length,
        paid: this.childOrders.filter((order) => this.matchChildStatus(order, 'paid')).length,
        shipping: this.childOrders.filter((order) => this.matchChildStatus(order, 'shipping')).length,
        refunded: this.childOrders.filter((order) => this.matchChildStatus(order, 'refunded')).length,
        review: this.childOrders.filter((order) => this.matchChildStatus(order, 'review')).length
      }
    },
    displayRecords() {
      const childRecords = this.childOrders
        .filter((order) => this.activeTab !== 'child' || this.matchChildStatus(order, this.activeChildStatus))
        .map((order) => ({
          type: 'child',
          id: order.id,
          sortTime: order.createdAt,
          raw: order
        }))

      const parentRecords = this.parentRecords.map((item) => ({
        type: 'parent',
        id: item?.shelf?.id,
        sortTime: item?.shelf?.reactedAt || item?.shelf?.createdAt,
        raw: item
      }))

      const list = this.activeTab === 'child' ? childRecords : parentRecords
      return list.sort((a, b) => safeTime(b.sortTime) - safeTime(a.sortTime))
    }
  },
  onLoad() {
    this._orderReceiveConfirmedHandler = (payload) => {
      if (!this.applyConfirmedChildOrder(payload)) {
        this.loadPageData()
      }
    }
    uni.$on(ORDER_RECEIVE_CONFIRMED_EVENT, this._orderReceiveConfirmedHandler)
  },
  onShow() {
    this.loadPageData()
  },
  onUnload() {
    if (this._orderReceiveConfirmedHandler) {
      uni.$off(ORDER_RECEIVE_CONFIRMED_EVENT, this._orderReceiveConfirmedHandler)
      this._orderReceiveConfirmedHandler = null
    }
  },
  methods: {
    async loadPageData() {
      try {
        const familyRes = await familyApi.getMyParents()
        const parents = Array.isArray(familyRes?.data) ? familyRes.data : []
        this.parentId = parents[0]?.parent?.id || null

        const orderTask = orderApi.getChildOrders({ page: 1, size: 100 })
        const shelfTask = this.parentId
          ? shelfApi.getChildList({ parentId: this.parentId, page: 1, size: 100 })
          : Promise.resolve({ data: { records: [] } })

        const [orderRes, shelfRes] = await Promise.all([orderTask, shelfTask])
        this.childOrders = Array.isArray(orderRes?.data?.records) ? orderRes.data.records : []
        this.parentRecords = Array.isArray(shelfRes?.data?.records) ? shelfRes.data.records : []
        this.ensureChildStatus()
      } catch (e) {
        console.error(e)
        this.childOrders = []
        this.parentRecords = []
      }
    },
    changeMainTab(value) {
      this.activeTab = value
      if (value === 'child') {
        this.ensureChildStatus()
      }
    },
    buildConfirmedOrder(order, payload = {}) {
      const confirmedAt = payload?.completedAt
        || payload?.logisticsUpdatedAt
        || order?.completedAt
        || order?.logisticsUpdatedAt
        || new Date().toISOString()
      return {
        ...(order || {}),
        ...(payload || {}),
        id: payload?.id || order?.id,
        status: 'COMPLETED',
        logisticsStatus: 'SIGNED',
        logisticsStatusText: payload?.logisticsStatusText || '\u5df2\u7b7e\u6536',
        completedAt: confirmedAt,
        logisticsUpdatedAt: payload?.logisticsUpdatedAt || confirmedAt
      }
    },
    applyConfirmedChildOrder(payload) {
      if (!payload?.id) {
        return false
      }
      const index = this.childOrders.findIndex((item) => String(item?.id || '') === String(payload.id))
      if (index < 0) {
        return false
      }
      const nextOrders = this.childOrders.slice()
      nextOrders.splice(index, 1, this.buildConfirmedOrder(this.childOrders[index], payload))
      this.childOrders = nextOrders
      this.ensureChildStatus()
      return true
    },
    ensureChildStatus() {
      const firstAvailable = this.childStatusTabs.find((tab) => this.childStatusCountMap[tab.value] > 0)
      this.activeChildStatus = firstAvailable ? firstAvailable.value : 'pending'
    },
    getTabLabel(value) {
      if (value === 'child') {
        return `子女端 ${this.childCount}`
      }
      return `长辈端 ${this.parentCount}`
    },
    getChildStatusLabel(value) {
      const current = this.childStatusTabs.find((tab) => tab.value === value)
      const count = this.childStatusCountMap[value] || 0
      return `${current?.label || value} ${count}`
    },
    isRefundBucket(order) {
      return ['REQUESTED', 'APPROVED', 'COMPLETED'].includes(order?.refundStatus)
    },
    matchChildStatus(order, status) {
      if (!order) return false
      if (status === 'pending') {
        return order.status === 'PENDING_PAY'
      }
      if (status === 'paid') {
        return order.status === 'PAID' && !this.isRefundBucket(order)
      }
      if (status === 'shipping') {
        return order.status === 'SHIPPED' && !this.isRefundBucket(order)
      }
      if (status === 'refunded') {
        return this.isRefundBucket(order)
      }
      if (status === 'review') {
        return order.status === 'COMPLETED' && !this.isRefundBucket(order)
      }
      return false
    },
    getRecordTitle(record) {
      if (record.type === 'child') {
        return record.raw.productName || '商品订单'
      }
      return record.raw.product?.name || '长辈推送商品'
    },
    getRecordSubLine(record) {
      if (record.type === 'child') {
        return record.raw.receiverName
          ? `收货人：${record.raw.receiverName}`
          : '当前订单正在等待处理'
      }
      const reaction = record.raw.shelf?.reaction
      if (reaction === 'LIKE') return '长辈已喜欢，可直接下单'
      if (reaction === 'DISLIKE') return '长辈暂不考虑这个商品'
      return '长辈还没查看，先保留在心选里'
    },
    getRecordMetaLine(record) {
      if (record.type === 'child') {
        return `订单号：${record.raw.orderNo || '--'}`
      }
      return `记录时间：${this.formatTime(record.raw.shelf?.reactedAt || record.raw.shelf?.createdAt)}`
    },
    getRecordStatusText(record) {
      if (record.type === 'parent') {
        const reaction = record.raw.shelf?.reaction
        if (reaction === 'LIKE') return '已喜欢'
        if (reaction === 'DISLIKE') return '暂不考虑'
        return '待查看'
      }

      const order = record.raw
      if (order.refundStatus === 'REQUESTED') return '退款中'
      if (order.refundStatus === 'APPROVED' || order.refundStatus === 'COMPLETED') return '已退款'

      const map = {
        PENDING_PAY: '未支付',
        PAID: '已支付',
        SHIPPED: '正在配送',
        COMPLETED: '待评价',
        CANCELLED: '已取消'
      }
      return map[order.status] || '处理中'
    },
    getRecordStatusClass(record) {
      if (record.type === 'parent') {
        const reaction = record.raw.shelf?.reaction
        if (reaction === 'LIKE') return 'is-liked'
        if (reaction === 'DISLIKE') return 'is-muted'
        return 'is-pending'
      }

      const order = record.raw
      if (order.refundStatus === 'REQUESTED') return 'is-refunding'
      if (order.refundStatus === 'APPROVED' || order.refundStatus === 'COMPLETED') return 'is-refunded'
      if (order.status === 'PENDING_PAY') return 'is-pending'
      if (order.status === 'PAID') return 'is-paid'
      if (order.status === 'SHIPPED') return 'is-shipping'
      if (order.status === 'COMPLETED') return 'is-review'
      return 'is-muted'
    },
    getRecordImage(record) {
      if (record.type === 'child') {
        return resolveProductImageUrl(record.raw.productImage, { productName: record.raw.productName })
      }
      const image = record.raw.product?.images && record.raw.product.images[0]
      return resolveProductImageUrl(image, { productName: record.raw.product?.name })
    },
    getPrimaryInfo(record) {
      if (record.type === 'child') {
        return `下单时间：${this.formatTime(record.raw.createdAt)}`
      }
      return `商品价格：¥${this.formatMoney(record.raw.product?.price)}`
    },
    getSecondaryInfo(record) {
      if (record.type === 'child') {
        if (this.canViewLogistics(record.raw)) {
          return `物流单号：${record.raw.trackingNo}`
        }
        if (record.raw.receiverAddress) {
          return `收货地址：${record.raw.receiverAddress}`
        }
        return `购买数量：${record.raw.quantity || 1}`
      }
      return `商品编号：${record.raw.product?.id || '--'}`
    },
    getVoiceText(record) {
      if (record.type !== 'parent') return ''
      return String(record.raw.shelf?.voiceMessage || '').trim()
    },
    getRecordAmount(record) {
      if (record.type === 'child') {
        return this.formatMoney(record.raw.totalAmount)
      }
      return this.formatMoney(record.raw.product?.price)
    },
    goRecordDetail(record) {
      if (record.type === 'child') {
        this.showOrderDetail(record.raw)
        return
      }

      const productId = record.raw.product?.id || record.raw.shelf?.productId
      if (!productId) {
        uni.showToast({ title: '商品信息缺失', icon: 'none' })
        return
      }
      let url = `/pages/product-detail/product-detail?id=${productId}`
      if (this.parentId) {
        url += `&parentId=${this.parentId}`
      }
      uni.navigateTo({ url })
    },
    goToPayment(orderId) {
      uni.navigateTo({ url: buildPaymentUrl(orderId) })
    },
    goToRefund(orderId) {
      uni.navigateTo({ url: buildRefundUrl(orderId) })
    },
    async createOrder(item) {
      const shelfId = item?.shelf?.id
      if (!shelfId || this.creatingShelfId) return
      this.creatingShelfId = shelfId
      try {
        const res = await orderApi.create({
          shelfId,
          quantity: 1,
          generateGreeting: true
        })
        const orderId = res?.data?.id
        if (!orderId) {
          throw new Error('Missing order id')
        }
        uni.navigateTo({ url: buildPaymentUrl(orderId) })
      } catch (e) {
        console.error(e)
        uni.showToast({ title: '下单失败', icon: 'none' })
      } finally {
        this.creatingShelfId = null
      }
    },
    showOrderDetail(order) {
      if (!order?.id) {
        uni.showToast({ title: '订单信息缺失', icon: 'none' })
        return
      }
      uni.navigateTo({ url: buildOrderDetailUrl(order.id) })
    },
    canRequestRefund(order) {
      return (order.status === 'PAID' || order.status === 'SHIPPED' || order.status === 'COMPLETED')
        && (!order.refundStatus || order.refundStatus === 'NONE')
    },
    getCreditScore(order) {
      const score = Number(order?.childCreditScore)
      return Number.isFinite(score) ? score : 100
    },
    isHighTrustRefund(order) {
      return this.getCreditScore(order) > 90
    },
    isNegotiatedRefund(order) {
      return order?.status === 'COMPLETED' || order?.logisticsStatus === 'SIGNED'
    },
    getRefundActionText(order) {
      if (this.isHighTrustRefund(order)) {
        return '直接退款'
      }
      return this.isNegotiatedRefund(order) ? '协商退款' : '申请退款'
    },
    canViewLogistics(order) {
      return !!String(order?.trackingNo || '').trim()
    },
    openLogistics(order) {
      if (!this.canViewLogistics(order)) {
        uni.showToast({ title: '暂未录入物流单号', icon: 'none' })
        return
      }
      this.logisticsPopup.visible = true
      this.logisticsPopup.loading = true
      this.logisticsPopup.data = {
        expressCompanyName: order.expressCompanyName,
        trackingNo: order.trackingNo,
        logisticsStatusText: order.logisticsStatusText,
        logisticsLastTrace: order.logisticsLastTrace,
        logisticsUpdatedAt: this.formatTime(order.logisticsUpdatedAt),
        traces: []
      }
      orderApi.getLogistics(order.id)
        .then((res) => {
          this.logisticsPopup.data = this.normalizeLogistics(res?.data, order)
        })
        .catch((e) => {
          console.error(e)
          uni.showToast({ title: '物流查询失败', icon: 'none' })
          this.logisticsPopup.data = this.normalizeLogistics(null, order, '物流服务暂时不可用，请稍后再试。')
        })
        .finally(() => {
          this.logisticsPopup.loading = false
        })
    },
    closeLogisticsPopup() {
      this.logisticsPopup.visible = false
    },
    normalizeLogistics(data, order, fallbackMessage = '') {
      const payload = data || {}
      return {
        expressCompanyName: payload.expressCompanyName || order.expressCompanyName || '',
        trackingNo: payload.trackingNo || order.trackingNo || '',
        logisticsStatusText: payload.logisticsStatusText || order.logisticsStatusText || '待物流更新',
        logisticsLastTrace: payload.logisticsLastTrace || order.logisticsLastTrace || '已记录运单，等待快递公司更新轨迹。',
        logisticsUpdatedAt: this.formatTime(payload.logisticsUpdatedAt || order.logisticsUpdatedAt),
        traces: Array.isArray(payload.traces) ? payload.traces : [],
        message: payload.message || fallbackMessage
      }
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
    },
    goProducts() {
      uni.switchTab({ url: '/pages/products/products' })
    }
  }
}
</script>

<style>
page {
  background: linear-gradient(180deg, #f0f6ff 0%, #edf4ff 42%, #f7fbff 100%);
  min-height: 100vh;
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.page {
  min-height: 100vh;
  padding-bottom: 36rpx;
}

.top-banner {
  margin: 0 18rpx;
  padding: 28rpx 24rpx 32rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  box-shadow: 0 16rpx 32rpx rgba(37, 99, 235, 0.16);
}

.banner-title,
.banner-subtitle {
  display: block;
  color: #fff;
}

.banner-title {
  font-size: 30rpx;
  font-weight: 700;
}

.banner-subtitle {
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.88);
}

.tabs {
  display: flex;
  gap: 12rpx;
  padding: 18rpx 18rpx 0;
  overflow-x: auto;
  white-space: nowrap;
}

.tab {
  flex-shrink: 0;
  min-width: 156rpx;
  padding: 16rpx 24rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.96);
  color: #64748b;
  font-size: 26rpx;
  font-weight: 600;
  text-align: center;
  box-shadow: 0 8rpx 18rpx rgba(15, 23, 42, 0.04);
}

.tab.active {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: #fff;
}

.child-status-tabs {
  display: flex;
  gap: 12rpx;
  padding: 16rpx 18rpx 0;
  overflow-x: auto;
  white-space: nowrap;
}

.child-status-tab {
  flex-shrink: 0;
  min-width: 150rpx;
  padding: 14rpx 22rpx;
  border-radius: 999rpx;
  background: #ffffff;
  color: #64748b;
  font-size: 24rpx;
  font-weight: 600;
  text-align: center;
  box-shadow: 0 8rpx 18rpx rgba(15, 23, 42, 0.04);
}

.child-status-tab.active {
  background: #dbeafe;
  color: #1d4ed8;
}

.record-list {
  padding: 18rpx 18rpx 0;
}

.record-card {
  background: rgba(255, 255, 255, 0.98);
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 18rpx;
  box-shadow: 0 10rpx 24rpx rgba(15, 23, 42, 0.06);
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18rpx;
}

.head-main {
  flex: 1;
  min-width: 0;
}

.product-name {
  display: block;
  font-size: 34rpx;
  line-height: 1.35;
  font-weight: 700;
  color: #1e293b;
}

.sub-line,
.order-no {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #7c8798;
}

.head-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12rpx;
  flex-shrink: 0;
}

.source-tag,
.status-text {
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 700;
}

.source-tag.is-child {
  background: #eff6ff;
  color: #2563eb;
}

.source-tag.is-parent {
  background: #fff7ed;
  color: #ea580c;
}

.status-text.is-pending {
  background: #eff6ff;
  color: #2563eb;
}

.status-text.is-paid {
  background: #ecfeff;
  color: #0891b2;
}

.status-text.is-shipping {
  background: #ede9fe;
  color: #7c3aed;
}

.status-text.is-refunding {
  background: #fff1f2;
  color: #e11d48;
}

.status-text.is-refunded {
  background: #ffe4e6;
  color: #e11d48;
}

.status-text.is-review {
  background: #fef3c7;
  color: #d97706;
}

.status-text.is-liked {
  background: #f0fdf4;
  color: #16a34a;
}

.status-text.is-muted {
  background: #f8fafc;
  color: #64748b;
}

.card-divider {
  height: 1rpx;
  margin: 22rpx 0;
  background: #e5edf8;
}

.product-row {
  display: flex;
  gap: 16rpx;
}

.product-image {
  width: 140rpx;
  height: 140rpx;
  border-radius: 20rpx;
  background: #f8fafc;
  flex-shrink: 0;
}

.product-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.info-text,
.voice-text {
  font-size: 24rpx;
  line-height: 1.6;
  color: #5b6778;
}

.voice-text {
  color: #2563eb;
}

.card-footer {
  margin-top: 22rpx;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18rpx;
}

.amount-block {
  display: flex;
  align-items: baseline;
  gap: 10rpx;
}

.amount-label {
  font-size: 26rpx;
  color: #7c8798;
}

.amount-value {
  font-size: 42rpx;
  font-weight: 700;
  color: #2563eb;
}

.action-row {
  display: flex;
  justify-content: flex-end;
  gap: 12rpx;
  flex-wrap: wrap;
}

.action-btn {
  margin: 0;
  min-width: 150rpx;
  height: 66rpx;
  line-height: 66rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  border: 1rpx solid transparent;
  font-size: 24rpx;
  font-weight: 700;
}

.action-btn::after {
  border: none;
}

.action-btn.outline {
  background: #f8fafc;
  color: #475569;
  border-color: #dbeafe;
}

.action-btn.outline.warn {
  background: #fff1f2;
  color: #e11d48;
  border-color: #fecdd3;
}

.action-btn.primary {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: #fff;
  box-shadow: 0 10rpx 20rpx rgba(37, 99, 235, 0.16);
}

.empty {
  min-height: 58vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 40rpx;
}

.empty-icon {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #60a5fa 0%, #2563eb 100%);
  color: #fff;
  font-size: 54rpx;
  font-weight: 700;
}

.empty-title {
  margin-top: 20rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: #1e293b;
}

.empty-text {
  margin-top: 14rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #7c8798;
  text-align: center;
}

.empty-btn {
  margin-top: 28rpx;
  min-width: 220rpx;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 999rpx;
  border: none;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
}

.empty-btn::after {
  border: none;
}
</style>
