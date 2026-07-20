<template>
  <view class="page">
    <view class="page-header">
      <text class="page-title">我的订单</text>
    </view>

    <view class="tabs">
      <view
        v-for="tab in tabs"
        :key="tab.value"
        class="tab"
        :class="{ active: currentTab === tab.value }"
        @click="changeTab(tab.value)"
      >
        {{ tab.label }}
      </view>
    </view>

    <view v-if="orders.length" class="order-list">
      <view v-for="order in orders" :key="order.id" class="order-card">
        <view class="card-head">
          <view class="head-main">
            <text class="product-name">{{ order.productName || '商品订单' }}</text>
            <text class="sub-line">{{ getSubLine(order) }}</text>
            <text class="order-no">订单号：{{ order.orderNo || '--' }}</text>
          </view>
          <text class="status-text" :class="getStatusClass(order)">{{ getDisplayStatus(order) }}</text>
        </view>

        <view class="card-divider"></view>

        <view class="info-list">
          <view class="info-row">
            <text class="info-label">下单时间</text>
            <text class="info-value">{{ formatTime(order.createdAt) }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">收货方式</text>
            <text class="info-value">{{ getDeliveryText(order) }}</text>
          </view>
          <view class="info-row align-start">
            <text class="info-label">状态说明</text>
            <text class="info-value multiline">{{ getStatusDescription(order) }}</text>
          </view>
        </view>

        <view v-if="order.greetingCard" class="note-box">
          <text class="note-text">贺卡内容：{{ order.greetingCard }}</text>
        </view>

        <view v-if="showRefundTip(order)" class="note-box warm">
          <text class="note-text">{{ getRefundTip(order) }}</text>
        </view>

        <view v-if="canViewLogistics(order)" class="note-box logistics-box">
          <text class="note-text">物流单号：{{ order.expressCompanyName || '快递' }} {{ order.trackingNo }}</text>
          <text class="note-subtext">{{ order.logisticsStatusText || '已发货，点击查看物流详情' }}</text>
        </view>

        <view class="card-footer">
          <view class="amount-block">
            <text class="amount-label">实付</text>
            <text class="amount-value">¥{{ formatMoney(order.totalAmount) }}</text>
          </view>

          <view class="action-row">
            <button
              v-if="canRequestRefund(order)"
              class="action-btn outline warn"
              @click="goToRefund(order.id)"
            >
              {{ getRefundActionText(order) }}
            </button>

            <button
              v-else-if="hasRefund(order) && order.refundStatus !== 'REQUESTED'"
              class="action-btn outline warn"
              @click="goToRefund(order.id)"
            >
              退款详情
            </button>

            <button
              v-if="order.refundStatus === 'REQUESTED'"
              class="action-btn outline"
              @click="rejectRefund(order.id)"
            >
              拒绝退款
            </button>

            <button
              v-if="order.refundStatus === 'REQUESTED'"
              class="action-btn warn"
              @click="approveRefund(order.id)"
            >
              同意退款
            </button>

            <button
              v-if="canConfirmReceive(order)"
              class="action-btn primary"
              @click="confirmReceive(order.id)"
            >
              确认签收
            </button>

            <button
              v-if="order.refundStatus !== 'REQUESTED'"
              class="action-btn outline"
              @click="showOrderDetail(order)"
            >
              订单详情
            </button>

            <button
              v-if="canViewLogistics(order)"
              class="action-btn outline"
              @click="openLogistics(order)"
            >
              查看物流
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
      </view>
    </view>

    <view v-else class="empty">
      <view class="empty-icon">单</view>
      <text class="empty-text">暂无订单</text>
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

const buildPaymentUrl = (orderId) => `/pages/payment/payment?orderId=${orderId}`
const buildRefundUrl = (orderId) => `/pages/refund/refund?orderId=${orderId}`
const buildOrderDetailUrl = (orderId) => `/pages/order-detail/order-detail?orderId=${orderId}`
const ORDER_RECEIVE_CONFIRMED_EVENT = 'HL_ORDER_RECEIVE_CONFIRMED'

export default {
  components: {
    OrderLogisticsPopup
  },
  data() {
    return {
      currentTab: '',
      tabs: [
        { label: '全部', value: '' },
        { label: '待支付', value: 'PENDING_PAY' },
        { label: '已支付', value: 'PAID' },
        { label: '已发货', value: 'SHIPPED' },
        { label: '已完成', value: 'COMPLETED' }
      ],
      orders: [],
      page: 1,
      logisticsPopup: {
        visible: false,
        loading: false,
        data: null
      }
    }
  },
  onLoad(options) {
    this._orderReceiveConfirmedHandler = (payload) => {
      if (!this.applyConfirmedOrder(payload)) {
        this.loadOrders()
      }
    }
    uni.$on(ORDER_RECEIVE_CONFIRMED_EVENT, this._orderReceiveConfirmedHandler)
    if (options?.action === 'create' && options?.shelfId) {
      this.createOrderFromShelf(options.shelfId)
      return
    }
    this.loadOrders()
  },
  onShow() {
    if (!this._creatingOrder) {
      this.loadOrders()
    }
  },
  onUnload() {
    if (this._orderReceiveConfirmedHandler) {
      uni.$off(ORDER_RECEIVE_CONFIRMED_EVENT, this._orderReceiveConfirmedHandler)
      this._orderReceiveConfirmedHandler = null
    }
  },
  methods: {
    async createOrderFromShelf(shelfId) {
      this._creatingOrder = true
      try {
        const res = await orderApi.create({
          shelfId: Number(shelfId),
          quantity: 1,
          generateGreeting: true
        })
        const orderId = res?.data?.id
        if (!orderId) {
          throw new Error('Missing order id')
        }
        uni.redirectTo({ url: buildPaymentUrl(orderId) })
      } catch (e) {
        console.error(e)
        uni.showToast({ title: '订单创建失败', icon: 'none' })
        this.loadOrders()
      } finally {
        this._creatingOrder = false
      }
    },
    async loadOrders() {
      try {
        const params = {
          page: this.page,
          size: 20
        }
        if (this.currentTab) {
          params.status = this.currentTab
        }
        const res = await orderApi.getChildOrders(params)
        this.orders = Array.isArray(res?.data?.records) ? res.data.records : []
      } catch (e) {
        console.error(e)
      }
    },
    changeTab(value) {
      this.currentTab = value
      this.page = 1
      this.loadOrders()
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
    applyConfirmedOrder(payload) {
      if (!payload?.id) {
        return false
      }
      const index = this.orders.findIndex((item) => String(item?.id || '') === String(payload.id))
      if (index < 0) {
        return false
      }
      const nextOrder = this.buildConfirmedOrder(this.orders[index], payload)
      const nextOrders = this.orders.slice()
      if (this.currentTab === 'SHIPPED') {
        nextOrders.splice(index, 1)
      } else {
        nextOrders.splice(index, 1, nextOrder)
      }
      this.orders = nextOrders
      return true
    },
    goToPayment(orderId) {
      uni.navigateTo({ url: buildPaymentUrl(orderId) })
    },
    goToRefund(orderId) {
      uni.navigateTo({ url: buildRefundUrl(orderId) })
    },
    showOrderDetail(order) {
      if (!order?.id) {
        uni.showToast({ title: '订单信息缺失', icon: 'none' })
        return
      }
      uni.navigateTo({ url: buildOrderDetailUrl(order.id) })
    },
    formatMoney(value) {
      const num = Number(value)
      return Number.isFinite(num) ? num.toFixed(2) : '0.00'
    },
    formatReceiver(order) {
      const values = [order.receiverName, order.receiverPhone, order.receiverAddress].filter(Boolean)
      return values.length ? values.join(' / ') : '待补充'
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
      if (order.receiverName) {
        return `收货人：${order.receiverName}`
      }
      if (order.productSpec) {
        return order.productSpec
      }
      return '来自连心商城'
    },
    getDeliveryText(order) {
      if (this.canViewLogistics(order)) {
        return `${order.expressCompanyName || '快递'} · 送货上门`
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
      return order.receiverAddress ? '送货上门' : '待确认'
    },
    getLogisticsSummary(order) {
      if (this.canViewLogistics(order)) {
        return {
          company: order.expressCompanyName || '快递',
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
      if (order.refundStatus === 'APPROVED' || order.refundStatus === 'COMPLETED') return '已退款'
      if (order.refundStatus === 'REQUESTED') return '退款中'
      if (order.refundStatus === 'REJECTED') return '退款驳回'
      if (order?.status === 'SHIPPED' && order?.logisticsStatus === 'DELIVERED') return '待签收'
      return this.getStatusText(order.status)
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
      if (order.refundStatus === 'APPROVED' || order.refundStatus === 'COMPLETED') return 'is-refunded'
      if (order.refundStatus === 'REQUESTED') return 'is-refunding'
      if (order.refundStatus === 'REJECTED') return 'is-rejected'
      if (order?.status === 'SHIPPED' && order?.logisticsStatus === 'DELIVERED') return 'is-paid'
      const map = {
        PENDING_PAY: 'is-pending',
        PAID: 'is-paid',
        SHIPPED: 'is-shipped',
        COMPLETED: 'is-completed',
        CANCELLED: 'is-cancelled'
      }
      return map[order.status] || 'is-default'
    },
    getStatusDescription(order) {
      if (order?.status === 'SHIPPED' && order?.logisticsStatus === 'DELIVERED') {
        return '商品已送达收货地址，等待确认签收。'
      }
      if (order.refundStatus === 'APPROVED' || order.refundStatus === 'COMPLETED') {
        return '退款已完成，金额将按原路退回。'
      }
      if (order.refundStatus === 'REQUESTED') {
        return '退款申请已提交，请尽快处理本次退款。'
      }
      if (order.refundStatus === 'REJECTED') {
        return '退款申请已被拒绝，可继续与对方沟通处理。'
      }

      const map = {
        PENDING_PAY: '订单已创建，请尽快完成支付。',
        PAID: '订单已支付，平台正在安排发货。',
        SHIPPED: this.canViewLogistics(order)
          ? `商品已发货，当前物流单号 ${order.trackingNo}，可直接查看配送轨迹。`
          : '商品已发货，请留意后续配送进度。',
        COMPLETED: '订单已完成，可查看订单详情或售后记录。',
        CANCELLED: '订单已取消，如有疑问可联系平台处理。'
      }
      return map[order.status] || '订单状态已更新，请查看最新信息。'
    },
    canViewLogistics(order) {
      return !!String(order?.trackingNo || '').trim()
    },
    canConfirmReceive(order) {
      return order?.status === 'SHIPPED' && order?.logisticsStatus === 'DELIVERED'
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
    async confirmReceive(orderId) {
      try {
        const currentOrder = this.orders.find((item) => String(item?.id || '') === String(orderId))
        const res = await orderApi.confirmReceive(orderId)
        const confirmedOrder = this.buildConfirmedOrder(currentOrder, res?.data)
        this.applyConfirmedOrder(confirmedOrder)
        uni.$emit(ORDER_RECEIVE_CONFIRMED_EVENT, confirmedOrder)
        uni.showToast({ title: '已签收', icon: 'success' })
        await this.loadOrders()
      } catch (e) {
        console.error(e)
      }
    },
    hasRefund(order) {
      return !!order.refundStatus && order.refundStatus !== 'NONE'
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
    isLowCreditRefund(order) {
      return this.getCreditScore(order) < 50
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
    showRefundTip(order) {
      return this.canRequestRefund(order) || order.refundStatus === 'REQUESTED'
    },
    getRefundTip(order) {
      if (order.refundStatus === 'APPROVED' && Number(order?.refundCreditScore) > 90) {
        return '高信用退款已自动同意，无需再等待人工处理。'
      }
      if (order.refundStatus === 'REQUESTED') {
        if (this.isLowCreditRefund(order)) {
          return '退款已提交。当前账号信用较低，本次退款会被重点核验。'
        }
        return this.isNegotiatedRefund(order)
          ? '协商退款已提交，凭证图片会一并带给对方处理。'
          : '退款申请已提交，请及时处理当前售后请求。'
      }
      if (this.isHighTrustRefund(order)) {
        return '当前信用分较高，可直接退款。'
      }
      if (this.isLowCreditRefund(order)) {
        return this.isNegotiatedRefund(order)
          ? '当前信用较低，签收后退款需填原因、传图片，并会被重点核验。'
          : '当前信用较低，退款需填写原因，提交后会被重点核验。'
      }
      if (this.isNegotiatedRefund(order)) {
        return '订单已签收，如需退款需走协商退款并上传问题图片。'
      }
      return '订单未签收前可直接退款，只需要填写退款原因。'
    },
    async approveRefund(orderId) {
      try {
        await orderApi.approveRefund(orderId)
        uni.showToast({ title: '已同意退款', icon: 'success' })
        this.loadOrders()
      } catch (e) {
        console.error(e)
      }
    },
    async rejectRefund(orderId) {
      try {
        await orderApi.rejectRefund(orderId)
        uni.showToast({ title: '已拒绝退款', icon: 'success' })
        this.loadOrders()
      } catch (e) {
        console.error(e)
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
  padding-bottom: 36rpx;
}

.page-header {
  padding: 44rpx 30rpx 24rpx;
  background: #fff;
}

.page-title {
  display: block;
  text-align: center;
  font-size: 38rpx;
  font-weight: 700;
  color: #2f241b;
}

.tabs {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  padding: 18rpx 20rpx;
  background: #f3ebdf;
  border-top: 1rpx solid rgba(188, 160, 125, 0.14);
  border-bottom: 1rpx solid rgba(188, 160, 125, 0.14);
}

.tab {
  flex: 1;
  text-align: center;
  font-size: 27rpx;
  color: #6f6255;
  padding: 10rpx 0;
  position: relative;
}

.tab.active {
  color: #ff6c2f;
  font-weight: 700;
}

.tab.active::after {
  content: '';
  position: absolute;
  left: 28rpx;
  right: 28rpx;
  bottom: -18rpx;
  height: 4rpx;
  border-radius: 999rpx;
  background: #ff6c2f;
}

.order-list {
  padding: 18rpx 18rpx 0;
}

.order-card {
  background: rgba(255, 255, 255, 0.96);
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 18rpx;
  box-shadow: 0 8rpx 22rpx rgba(105, 76, 43, 0.08);
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18rpx;
}

.head-main {
  min-width: 0;
  flex: 1;
}

.product-name {
  display: block;
  font-size: 34rpx;
  line-height: 1.35;
  font-weight: 700;
  color: #2f241b;
}

.sub-line,
.order-no {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #8a7d70;
}

.status-text {
  flex-shrink: 0;
  font-size: 28rpx;
  font-weight: 700;
}

.status-text.is-pending {
  color: #ff7a28;
}

.status-text.is-paid,
.status-text.is-shipped {
  color: #15b8d4;
}

.status-text.is-completed {
  color: #17a34a;
}

.status-text.is-cancelled,
.status-text.is-rejected {
  color: #8a7d70;
}

.status-text.is-refunding {
  color: #ff7a28;
}

.status-text.is-refunded {
  color: #ff6c2f;
}

.card-divider {
  height: 1rpx;
  margin: 22rpx 0;
  background: #f0e8df;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
}

.info-row.align-start {
  align-items: flex-start;
}

.info-label {
  min-width: 110rpx;
  font-size: 28rpx;
  color: #9a8d80;
}

.info-value {
  flex: 1;
  text-align: right;
  font-size: 28rpx;
  color: #5e5145;
}

.info-value.multiline {
  line-height: 1.6;
}

.note-box {
  margin-top: 18rpx;
  border-radius: 18rpx;
  padding: 18rpx 20rpx;
  background: #f5f7fb;
}

.note-box.warm {
  background: #fff1e9;
}

.note-text {
  display: block;
  font-size: 26rpx;
  line-height: 1.6;
  color: #8a5a38;
}

.note-subtext {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 1.5;
  color: #7c8798;
}

.logistics-box {
  background: #eef6ff;
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
  flex-wrap: wrap;
}

.amount-label {
  font-size: 26rpx;
  color: #8a7d70;
}

.amount-value {
  font-size: 42rpx;
  font-weight: 700;
  color: #ff6c2f;
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14rpx;
  flex-wrap: wrap;
}

.action-btn {
  margin: 0;
  min-width: 156rpx;
  height: 68rpx;
  line-height: 68rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  border: 1rpx solid transparent;
  background: #fff;
  font-size: 26rpx;
  font-weight: 600;
  color: #6f6255;
}

.action-btn::after {
  border: none;
}

.action-btn.outline {
  border-color: #e0d4c7;
  color: #6f6255;
  background: #fff;
}

.action-btn.primary {
  background: linear-gradient(135deg, #ff8a3d 0%, #ff6c2f 100%);
  color: #fff;
  box-shadow: 0 10rpx 18rpx rgba(255, 108, 47, 0.18);
}

.action-btn.warn {
  border-color: #ff8a3d;
  color: #ff6c2f;
}

.empty {
  min-height: 58vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.empty-icon {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #ffb07a 0%, #ff7d3c 100%);
  color: #fff;
  font-size: 54rpx;
  font-weight: 700;
}

.empty-text {
  margin-top: 18rpx;
  font-size: 28rpx;
  color: #8a7d70;
}
</style>
