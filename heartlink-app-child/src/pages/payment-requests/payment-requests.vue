<template>
  <view class="container">
    <view class="header">
      <text class="title">代付请求</text>
      <text class="subtitle">长辈正在等您帮忙付款</text>
    </view>

    <!-- 待付款列表 -->
    <view class="request-list" v-if="requests.length > 0">
      <view class="request-card" v-for="req in requests" :key="req.id">
        <view class="request-badge" :class="req.status">
          {{ getStatusText(req.status) }}
        </view>

        <view class="request-content">
          <image class="product-image" :src="getImageUrl(req.productImage, req.productName)" mode="aspectFill"></image>
          <view class="product-info">
            <text class="product-name">{{ req.productName }}</text>
            <text class="amount">¥{{ req.amount }}</text>
          </view>
        </view>

        <view class="message-box" v-if="req.message">
          <view class="message-icon-box">信</view>
          <text class="message-text">{{ req.message }}</text>
        </view>

        <view class="request-time">
          <text>请求时间：{{ formatTime(req.createdAt) }}</text>
        </view>

        <view class="request-actions" v-if="req.status === 'PENDING'">
          <button class="btn-reject" @click="rejectRequest(req)">暂不付款</button>
          <view class="btn-pay" hover-class="btn-pay-hover" @tap.stop="payRequest(req)">微信支付 ¥{{ req.amount }}</view>
        </view>

        <view class="request-result" v-else-if="req.status === 'PAID'">
          <view class="result-icon-box success">付</view>
          <text class="result-text">已付款</text>
        </view>

        <view class="request-result rejected" v-else-if="req.status === 'REJECTED'">
          <view class="result-icon-box reject">拒</view>
          <text class="result-text rejected-text">已拒绝</text>
        </view>
      </view>
    </view>

    <!-- 空状态 -->
    <view class="empty" v-else>
      <view class="empty-icon-box">付</view>
      <text class="empty-text">暂无代付请求</text>
      <text class="empty-hint">长辈需要帮助时会通知您</text>
    </view>
  </view>
</template>

<script>
import { paymentRequestApi } from '../../api/index.js'
import { resolveProductImageUrl } from '../../utils/product-image.js'

const buildPaymentUrl = (req) => {
  const params = [
    `orderId=${req.orderId}`,
    `requestId=${req.id}`,
    `message=${encodeURIComponent(req.message || '')}`
  ]
  return `/pages/payment/payment?${params.join('&')}`
}

export default {
  data() {
    return {
      requests: []
    }
  },
  onLoad() {
    this.loadRequests()
  },
  onShow() {
    this.loadRequests()
  },
  onPullDownRefresh() {
    this.loadRequests().finally(() => {
      uni.stopPullDownRefresh()
    })
  },
  methods: {
    async loadRequests() {
      try {
        const res = await paymentRequestApi.getChildRequests({ page: 1, size: 50 })
        this.requests = res.data?.records || []
      } catch (e) {
        console.error(e)
      }
    },
    getStatusText(status) {
      const map = {
        'PENDING': '待付款',
        'PAID': '已付款',
        'REJECTED': '已拒绝',
        'EXPIRED': '已过期'
      }
      return map[status] || status
    },
    getImageUrl(url, productName = '') {
      return resolveProductImageUrl(url, { productName })
    },
    formatTime(timeStr) {
      if (!timeStr) return ''
      const date = new Date(timeStr)
      return `${date.getMonth() + 1}月${date.getDate()}日 ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`
    },
    payRequest(req) {
      console.log('[payment-requests] payRequest', { requestId: req?.id, orderId: req?.orderId })
      uni.navigateTo({ url: buildPaymentUrl(req) })
    },
    async rejectRequest(req) {
      uni.showModal({
        title: '拒绝付款',
        content: '确定暂不为长辈付款吗？',
        confirmText: '确认拒绝',
        confirmColor: '#999',
        success: async (result) => {
          if (result.confirm) {
            try {
              await paymentRequestApi.reject(req.id)
              uni.showToast({ title: '已拒绝', icon: 'none' })
              this.loadRequests()
            } catch (e) {
              console.error(e)
            }
          }
        }
      })
    }
  }
}
</script>

<style>
@keyframes fadeUp {
  from { opacity: 0; transform: translateY(24rpx); }
  to   { opacity: 1; transform: translateY(0); }
}

page {
  background: linear-gradient(180deg, #f0f9ff 0%, #e9f5ff 45%, #f7fbff 100%);
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  min-height: 100vh;
}

.container {
  min-height: 100vh;
  padding-bottom: 120rpx;
}

.header {
  background: linear-gradient(135deg, #0369a1 0%, #0ea5e9 50%, #38bdf8 100%);
  padding: 60rpx 30rpx 40rpx;
}

.title {
  font-size: 40rpx;
  font-weight: 600;
  color: #fff;
  display: block;
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.subtitle {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 8rpx;
  display: block;
}

.request-list {
  padding: 20rpx;
}

.request-card {
  background: #fff;
  border-radius: 26rpx;
  margin-bottom: 24rpx;
  padding: 24rpx;
  position: relative;
  overflow: hidden;
  border: 1rpx solid #dbeafe;
  box-shadow: 0 8rpx 22rpx rgba(12, 74, 110, 0.08);
  animation: fadeUp 0.35s ease both;
}

.request-badge {
  position: absolute;
  top: 0;
  right: 0;
  padding: 8rpx 24rpx;
  font-size: 22rpx;
  font-weight: 600;
  border-radius: 0 26rpx 0 16rpx;
}

.request-badge.PENDING {
  background: linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%);
  color: #fff;
}

.request-badge.PAID {
  background: #f0fdf4;
  color: #16a34a;
}

.request-badge.REJECTED {
  background: #f8fafc;
  color: #94a3b8;
}

.request-badge.EXPIRED {
  background: #f8fafc;
  color: #94a3b8;
}

.request-content {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
}

.product-image {
  width: 140rpx;
  height: 140rpx;
  border-radius: 16rpx;
  background: #f0f9ff;
}

.product-info {
  flex: 1;
  margin-left: 20rpx;
}

.product-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #0f172a;
  display: block;
  line-height: 1.4;
}

.amount {
  font-size: 40rpx;
  font-weight: 700;
  color: #0369a1;
  margin-top: 12rpx;
  display: block;
}

.message-box {
  display: flex;
  align-items: flex-start;
  background: #eff6ff;
  border-radius: 16rpx;
  padding: 20rpx;
  margin-top: 20rpx;
  border: 1rpx solid #bfdbfe;
}

.message-icon-wrap {
  width: 44rpx;
  height: 44rpx;
  background: linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 14rpx;
  flex-shrink: 0;
}

.message-icon-text {
  font-size: 22rpx;
  color: #fff;
  font-weight: 700;
}

/* New icon box classes */
.message-icon-box {
  width: 44rpx;
  height: 44rpx;
  background: linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%);
  border-radius: 50%;
  color: #fff;
  font-weight: 700;
  font-size: 22rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 14rpx;
  flex-shrink: 0;
}

.result-icon-box {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  color: #fff;
  font-weight: 700;
  font-size: 24rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 12rpx;
}

.result-icon-box.success {
  background: #22c55e;
}

.result-icon-box.reject {
  background: #ef4444;
}

.empty-icon-box {
  width: 120rpx;
  height: 120rpx;
  background: linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%);
  border-radius: 50%;
  color: #fff;
  font-weight: 700;
  font-size: 52rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.message-text {
  flex: 1;
  font-size: 28rpx;
  color: #475569;
  line-height: 1.6;
}

.request-time {
  margin-top: 16rpx;
}

.request-time text {
  font-size: 24rpx;
  color: #64748b;
}

.request-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
}

.btn-reject {
  flex: 1;
  background: #f8fafc;
  color: #475569;
  border: none;
  border-radius: 40rpx;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 28rpx;
}

.btn-pay {
  flex: 2;
  background: linear-gradient(140deg, #0369a1 0%, #0ea5e9 100%);
  color: #fff;
  border-radius: 40rpx;
  height: 80rpx;
  font-size: 28rpx;
  font-weight: 600;
  box-shadow: 0 8rpx 20rpx rgba(3, 105, 161, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-pay-hover {
  transform: scale(0.98);
}

.request-result {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 24rpx;
  padding: 16rpx;
  background: #f0fdf4;
  border-radius: 12rpx;
}

.request-result.rejected {
  background: #f8fafc;
}

.result-icon-wrap {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12rpx;
}

.result-icon-wrap.paid {
  background: linear-gradient(135deg, #16a34a 0%, #22c55e 100%);
}

.result-icon-wrap.rejected-icon {
  background: #e2e8f0;
}

.result-icon-text {
  font-size: 24rpx;
  color: #fff;
  font-weight: 700;
}

.result-text {
  font-size: 28rpx;
  font-weight: 500;
  color: #16a34a;
}

.result-text.rejected-text {
  color: #94a3b8;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 0;
  animation: fadeUp 0.4s ease both;
}

.empty-icon-wrap {
  width: 120rpx;
  height: 120rpx;
  background: linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-icon-text {
  font-size: 52rpx;
  color: #fff;
  font-weight: 700;
}

.empty-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #0f172a;
  margin-top: 24rpx;
}

.empty-hint {
  font-size: 26rpx;
  color: #64748b;
  margin-top: 12rpx;
}

@media (prefers-reduced-motion: reduce) {
  .request-card,
  .empty {
    animation: none;
  }
}
</style>

