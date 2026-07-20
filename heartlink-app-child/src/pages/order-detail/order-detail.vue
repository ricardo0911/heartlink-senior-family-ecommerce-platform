<template>
  <view class="page">
    <view v-if="loading" class="state-card">
      <text class="state-title">正在加载订单详情</text>
      <text class="state-text">请稍候，正在同步订单和物流信息。</text>
    </view>

    <view v-else-if="order" class="content">
      <view class="hero-card">
        <image class="hero-image" :src="productImage" mode="aspectFill" />
        <view class="hero-main">
          <text class="hero-name">{{ order.productName || '商品订单' }}</text>
          <text class="hero-subline">{{ getSubLine(order) }}</text>
          <view class="hero-status-row">
            <text class="status-chip" :class="getStatusClass(order)">{{ getDisplayStatus(order) }}</text>
            <text class="hero-amount">￥{{ formatMoney(order.totalAmount) }}</text>
          </view>
        </view>
      </view>

      <view class="section-card">
        <text class="section-title">订单信息</text>
        <view class="info-row">
          <text class="info-label">订单号</text>
          <text class="info-value">{{ order.orderNo || '--' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">下单时间</text>
          <text class="info-value">{{ formatTime(order.createdAt) }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">商品规格</text>
          <text class="info-value">{{ order.productSpec || '默认规格' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">购买数量</text>
          <text class="info-value">{{ order.quantity || 1 }}</text>
        </view>
        <view class="info-row align-start">
          <text class="info-label">状态说明</text>
          <text class="info-value multiline">{{ getStatusDescription(order) }}</text>
        </view>
      </view>

      <view class="section-card">
        <text class="section-title">收货信息</text>
        <view class="info-row">
          <text class="info-label">收货人</text>
          <text class="info-value">{{ order.receiverName || '待补充' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">联系电话</text>
          <text class="info-value">{{ order.receiverPhone || '待补充' }}</text>
        </view>
        <view class="info-row align-start">
          <text class="info-label">收货地址</text>
          <text class="info-value multiline">{{ order.receiverAddress || '待补充' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">配送方式</text>
          <text class="info-value">{{ getDeliveryText(order) }}</text>
        </view>
      </view>

      <view class="section-card">
        <view class="section-head">
          <text class="section-title">物流信息</text>
          <button
            v-if="canViewLogistics(order)"
            class="inline-btn"
            @click="openLogistics(order)"
          >
            查看物流
          </button>
        </view>
        <view class="info-row">
          <text class="info-label">快递公司</text>
          <text class="info-value">{{ logisticsSummary.company }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">运单号</text>
          <text class="info-value">{{ logisticsSummary.trackingNo }}</text>
        </view>
        <view class="info-row align-start">
          <text class="info-label">物流说明</text>
          <text class="info-value multiline">{{ logisticsSummary.detail }}</text>
        </view>
      </view>

      <view v-if="order.greetingCard" class="note-card">
        <text class="note-title">贺卡内容</text>
        <text class="note-text">{{ order.greetingCard }}</text>
      </view>

      <view class="action-card">
        <button
          v-if="canConfirmReceive(order)"
          class="action-btn primary"
          @click="confirmReceive(order)"
        >
          确认签收
        </button>

        <button
          v-if="canViewLogistics(order)"
          class="action-btn outline"
          @click="openLogistics(order)"
        >
          查看物流
        </button>

        <button
          v-if="canRequestRefund(order)"
          class="action-btn outline"
          @click="goToRefund(order.id)"
        >
          {{ getRefundActionText(order) }}
        </button>

        <button
          v-if="order.status === 'PENDING_PAY'"
          class="action-btn primary"
          @click="goToPayment(order.id)"
        >
          微信支付
        </button>
      </view>
    </view>

    <view v-else class="state-card">
      <text class="state-title">未找到订单</text>
      <text class="state-text">订单不存在，或当前账号没有查看该订单的权限。</text>
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
import { orderApi } from '../../api/index.js'
import OrderLogisticsPopup from '../../components/OrderLogisticsPopup.vue'
import { resolveProductImageUrl } from '../../utils/product-image.js'

const buildPaymentUrl = (orderId) => `/pages/payment/payment?orderId=${orderId}`
const buildRefundUrl = (orderId) => `/pages/refund/refund?orderId=${orderId}`
const ORDER_RECEIVE_CONFIRMED_EVENT = 'HL_ORDER_RECEIVE_CONFIRMED'

export default {
  components: {
    OrderLogisticsPopup
  },
  data() {
    return {
      orderId: null,
      loading: true,
      order: null,
      logisticsPollTimer: null,
      confirmingReceive: false,
      logisticsPopup: {
        visible: false,
        loading: false,
        data: null
      }
    }
  },
  computed: {
    productImage() {
      return resolveProductImageUrl(this.order?.productImage, { productName: this.order?.productName })
    },
    logisticsSummary() {
      return this.getLogisticsSummary(this.order)
    }
  },
  onLoad(options) {
    this._orderReceiveConfirmedHandler = (payload) => {
      if (!this.applyConfirmedOrder(payload) && this.orderId && payload?.id && String(payload.id) === String(this.orderId)) {
        this.loadOrder()
      }
    }
    uni.$on(ORDER_RECEIVE_CONFIRMED_EVENT, this._orderReceiveConfirmedHandler)
    this.orderId = Number(options?.orderId || 0)
    if (!this.orderId) {
      uni.showToast({ title: '订单信息缺失', icon: 'none' })
      this.loading = false
      return
    }
    this.loadOrder()
  },
  onShow() {
    if (this.orderId && !this.loading) {
      this.loadOrder()
    }
  },
  onHide() {
    this.stopLogisticsPolling()
  },
  onUnload() {
    this.stopLogisticsPolling()
    if (this._orderReceiveConfirmedHandler) {
      uni.$off(ORDER_RECEIVE_CONFIRMED_EVENT, this._orderReceiveConfirmedHandler)
      this._orderReceiveConfirmedHandler = null
    }
  },
  onPullDownRefresh() {
    this.loadOrder(true)
  },
  methods: {
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
    applyConfirmedOrder(payload) {
      if (!payload?.id || String(this.order?.id || '') !== String(payload.id)) {
        return false
      }
      const nextOrder = this.buildConfirmedOrder(this.order, payload)
      this.order = nextOrder
      if (this.logisticsPopup.visible) {
        this.logisticsPopup.data = this.normalizeLogistics(nextOrder, nextOrder)
      }
      return true
    },
    async loadOrder(fromRefresh = false) {
      this.loading = true
      try {
        const res = await orderApi.getOrder(this.orderId)
        this.order = res?.data || null
      } catch (e) {
        console.error(e)
        this.order = null
        uni.showToast({ title: '订单加载失败', icon: 'none' })
      } finally {
        this.loading = false
        if (fromRefresh) {
          uni.stopPullDownRefresh()
        }
      }
    },
    goToPayment(orderId) {
      uni.navigateTo({ url: buildPaymentUrl(orderId) })
    },
    goToRefund(orderId) {
      uni.navigateTo({ url: buildRefundUrl(orderId) })
    },
    canConfirmReceive(order) {
      return order?.status === 'SHIPPED' && order?.logisticsStatus === 'DELIVERED'
    },
    canRequestRefund(order) {
      return (order?.status === 'PAID' || order?.status === 'SHIPPED' || order?.status === 'COMPLETED')
        && (!order?.refundStatus || order?.refundStatus === 'NONE')
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
    formatMoney(value) {
      const num = Number(value)
      return Number.isFinite(num) ? num.toFixed(2) : '0.00'
    },
    formatTime(value) {
      if (!value) return '--'
      const text = String(value).replace('T', ' ')
      const date = new Date(text.replace(/-/g, '/'))
      if (Number.isNaN(date.getTime())) {
        return text.slice(0, 16)
      }
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      const hour = String(date.getHours()).padStart(2, '0')
      const minute = String(date.getMinutes()).padStart(2, '0')
      return `${month}/${day} ${hour}:${minute}`
    },
    getSubLine(order) {
      if (order?.receiverName) {
        return `收货人：${order.receiverName}`
      }
      if (order?.productSpec) {
        return order.productSpec
      }
      return '来自连心商城'
    },
    getDeliveryText(order) {
      if (this.canViewLogistics(order)) {
        return `${order.expressCompanyName || '快递配送'} · 送货上门`
      }
      if (order?.status === 'PENDING_PAY') {
        return '待付款，暂未发货'
      }
      if (order?.status === 'PAID') {
        return '商家待发货'
      }
      if (order?.status === 'CANCELLED') {
        return '订单已取消'
      }
      return order?.receiverAddress ? '送货上门' : '待确认'
    },
    getLogisticsSummary(order) {
      if (this.canViewLogistics(order)) {
        return {
          company: order.expressCompanyName || '快递配送',
          trackingNo: order.trackingNo,
          detail: order.logisticsStatusText || '已发货，点击查看物流详情',
          toast: order.logisticsStatusText || '已录入运单号'
        }
      }

      switch (order?.status) {
        case 'PENDING_PAY':
          return {
            company: '待付款',
            trackingNo: '暂无物流单号',
            detail: '订单未支付，暂未进入发货流程',
            toast: '订单待付款，暂无物流信息'
          }
        case 'PAID':
          return {
            company: '待发货',
            trackingNo: '暂无物流单号',
            detail: '订单已支付，等待商家发货',
            toast: '订单已支付，商家尚未录入运单号'
          }
        case 'CANCELLED':
          return {
            company: '已取消',
            trackingNo: '暂无物流单号',
            detail: '订单已取消，无物流信息',
            toast: '订单已取消，无物流信息'
          }
        case 'COMPLETED':
          return {
            company: '已完成',
            trackingNo: '暂无物流单号',
            detail: '订单已完成',
            toast: '订单已完成'
          }
        default:
          return {
            company: '未发货',
            trackingNo: '暂无物流单号',
            detail: '暂未录入物流单号',
            toast: '暂未录入物流单号'
          }
      }
    },
    getDisplayStatus(order) {
      if (order?.status === 'SHIPPED' && order?.logisticsStatus === 'DELIVERED') {
        return '待签收'
      }
      return this.getStatusText(order?.status)
    },
    getStatusText(status) {
      const map = {
        PENDING_PAY: '待支付',
        PAID: '已支付',
        SHIPPED: '已发货',
        COMPLETED: '已完成',
        CANCELLED: '已取消'
      }
      return map[status] || status || '处理中'
    },
    getStatusClass(order) {
      if (order?.status === 'SHIPPED' && order?.logisticsStatus === 'DELIVERED') {
        return 'is-paid'
      }
      const map = {
        PENDING_PAY: 'is-pending',
        PAID: 'is-paid',
        SHIPPED: 'is-shipped',
        COMPLETED: 'is-completed',
        CANCELLED: 'is-cancelled'
      }
      return map[order?.status] || 'is-default'
    },
    getStatusDescription(order) {
      if (order?.status === 'SHIPPED' && order?.logisticsStatus === 'DELIVERED') {
        return '商品已送达收货地址，等待确认签收。'
      }
      const map = {
        PENDING_PAY: '订单已创建，请尽快完成支付。',
        PAID: '订单已支付，平台正在安排发货。',
        SHIPPED: this.canViewLogistics(order)
          ? `商品已发货，当前物流单号 ${order.trackingNo}，可直接查看配送轨迹。`
          : '商品已发货，请留意后续配送进度。',
        COMPLETED: '订单已完成，可查看订单详情。',
        CANCELLED: '订单已取消，如有疑问可联系平台处理。'
      }
      return map[order?.status] || '订单状态已更新，请查看最新信息。'
    },
    canViewLogistics(order) {
      return !!String(order?.trackingNo || '').trim()
    },
    async fetchLogistics(order, refresh = true, fallbackMessage = '物流服务暂时不可用，请稍后再试。') {
      const res = await orderApi.getLogistics(order.id, refresh)
      const nextData = this.normalizeLogistics(res?.data, order, fallbackMessage)
      this.logisticsPopup.data = nextData
      if (this.order && this.order.id === order.id) {
        this.order = {
          ...this.order,
          logisticsStatus: res?.data?.logisticsStatus || this.order.logisticsStatus,
          logisticsStatusText: nextData.logisticsStatusText,
          logisticsLastTrace: nextData.logisticsLastTrace,
          logisticsUpdatedAt: res?.data?.logisticsUpdatedAt || this.order.logisticsUpdatedAt
        }
      }
      return nextData
    },
    startLogisticsPolling(order) {
      this.stopLogisticsPolling()
      if (!order?.id || !this.logisticsPopup.visible) {
        return
      }
      this.logisticsPollTimer = setInterval(() => {
        if (!this.logisticsPopup.visible || !this.canViewLogistics(this.order)) {
          this.stopLogisticsPolling()
          return
        }
        if (this.order?.status === 'COMPLETED' || this.order?.logisticsStatus === 'SIGNED') {
          this.stopLogisticsPolling()
          return
        }
        this.fetchLogistics(this.order, true).catch((e) => {
          console.error(e)
        })
      }, 5000)
    },
    stopLogisticsPolling() {
      if (this.logisticsPollTimer) {
        clearInterval(this.logisticsPollTimer)
        this.logisticsPollTimer = null
      }
    },
    openLogistics(order) {
      if (!this.canViewLogistics(order)) {
        uni.showToast({ title: this.getLogisticsSummary(order).toast, icon: 'none' })
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
      this.fetchLogistics(order)
        .then(() => {
          this.startLogisticsPolling(order)
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
      this.stopLogisticsPolling()
    },
    async confirmReceive(order) {
      if (!this.canConfirmReceive(order) || this.confirmingReceive) {
        return
      }
      this.confirmingReceive = true
      try {
        const res = await orderApi.confirmReceive(order.id)
        const confirmedOrder = this.buildConfirmedOrder(order, res?.data)
        this.applyConfirmedOrder(confirmedOrder)
        uni.$emit(ORDER_RECEIVE_CONFIRMED_EVENT, confirmedOrder)
        uni.showToast({ title: '已签收', icon: 'success' })
        this.closeLogisticsPopup()
        await this.loadOrder()
      } catch (e) {
        console.error(e)
      } finally {
        this.confirmingReceive = false
      }
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
    }
  }
}
</script>

<style>
page {
  background: linear-gradient(180deg, #f7f2ea 0%, #f6efe5 45%, #f8f4ed 100%);
  min-height: 100vh;
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: #2f241b;
}

.page {
  min-height: 100vh;
  padding: 24rpx 24rpx 40rpx;
  box-sizing: border-box;
}

.content {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.state-card,
.hero-card,
.section-card,
.note-card,
.action-card {
  background: #fffdf9;
  border-radius: 26rpx;
  box-shadow: 0 16rpx 34rpx rgba(136, 104, 70, 0.08);
}

.state-card {
  margin-top: 120rpx;
  padding: 48rpx 36rpx;
  text-align: center;
}

.state-title,
.state-text,
.hero-name,
.hero-subline,
.hero-amount,
.section-title,
.info-label,
.info-value,
.note-title,
.note-text {
  display: block;
}

.state-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #2f241b;
}

.state-text {
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #7a6858;
}

.hero-card {
  display: flex;
  gap: 22rpx;
  padding: 24rpx;
  align-items: center;
}

.hero-image {
  width: 180rpx;
  height: 180rpx;
  border-radius: 22rpx;
  background: #f3e8da;
}

.hero-main {
  flex: 1;
  min-width: 0;
}

.hero-name {
  font-size: 32rpx;
  line-height: 1.5;
  font-weight: 700;
  color: #2f241b;
}

.hero-subline {
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #7d6b5b;
}

.hero-status-row {
  margin-top: 18rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.status-chip {
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 700;
  color: #6b7280;
  background: #f3f4f6;
}

.status-chip.is-pending {
  color: #9a6700;
  background: rgba(245, 158, 11, 0.15);
}

.status-chip.is-paid {
  color: #155e75;
  background: rgba(34, 211, 238, 0.15);
}

.status-chip.is-shipped {
  color: #1d4ed8;
  background: rgba(59, 130, 246, 0.15);
}

.status-chip.is-completed {
  color: #065f46;
  background: rgba(16, 185, 129, 0.15);
}

.status-chip.is-cancelled {
  color: #991b1b;
  background: rgba(239, 68, 68, 0.14);
}

.hero-amount {
  font-size: 34rpx;
  font-weight: 800;
  color: #a16207;
}

.section-card,
.note-card,
.action-card {
  padding: 26rpx 24rpx;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.section-title,
.note-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #2f241b;
}

.info-row {
  margin-top: 18rpx;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
}

.info-label {
  width: 136rpx;
  flex-shrink: 0;
  font-size: 24rpx;
  color: #8a7764;
}

.info-value {
  flex: 1;
  min-width: 0;
  text-align: right;
  font-size: 25rpx;
  line-height: 1.7;
  color: #35291f;
}

.info-row.align-start .info-value,
.info-value.multiline {
  text-align: left;
}

.note-card {
  background: #fff9f1;
}

.note-text {
  margin-top: 12rpx;
  font-size: 25rpx;
  line-height: 1.8;
  color: #6f5d4d;
}

.action-card {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.action-btn,
.inline-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1;
  border: none;
}

.action-btn::after,
.inline-btn::after {
  border: none;
}

.action-btn {
  min-width: 200rpx;
  height: 78rpx;
  padding: 0 28rpx;
}

.inline-btn {
  height: 58rpx;
  padding: 0 22rpx;
  background: rgba(59, 130, 246, 0.12);
  color: #2563eb;
}

.action-btn.primary {
  background: linear-gradient(135deg, #d97706 0%, #f59e0b 100%);
  color: #fff;
}

.action-btn.outline {
  background: #fff;
  color: #6b4f34;
  border: 1rpx solid rgba(181, 144, 106, 0.34);
}
</style>
