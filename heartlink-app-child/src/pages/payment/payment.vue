<template>
  <view class="cashier-page">
    <view class="cashier-top">
      <text class="cashier-top-title">微信支付</text>
      <text class="cashier-top-subtitle">安全支付环境</text>
    </view>

    <view v-if="loading" class="loading-wrap">
      <view class="loading-card">
        <text class="loading-title">正在准备支付信息</text>
        <text class="loading-text">请稍候，正在同步订单、优惠与支付信息。</text>
      </view>
    </view>

    <view v-else-if="order" class="cashier-content">
      <view class="merchant-card">
        <view class="merchant-avatar">HL</view>
        <text class="merchant-name">连心选官方收款</text>
        <text class="merchant-desc">{{ requestId ? '长辈代付订单' : '商品订单支付' }}</text>
        <view class="amount-row">
          <text class="amount-unit">¥</text>
          <text class="amount-value">{{ formatMoney(finalAmount) }}</text>
        </view>
        <text class="amount-note">{{ order.productName }}</text>
        <text class="status-text">{{ statusText }}</text>
      </view>

      <view class="sheet-card">
        <view class="sheet-row">
          <text class="sheet-label">商品说明</text>
          <text class="sheet-value sheet-value-strong">{{ order.productName }}</text>
        </view>
        <view class="sheet-row">
          <text class="sheet-label">订单号</text>
          <text class="sheet-value">{{ order.orderNo }}</text>
        </view>
        <view class="sheet-row">
          <text class="sheet-label">支付方式</text>
          <view class="pay-method">
            <view class="wechat-dot"></view>
            <text class="sheet-value sheet-value-strong">微信零钱（模拟）</text>
          </view>
        </view>
        <view class="sheet-row interactive" @tap="toggleDiscountDetail">
          <text class="sheet-label">优惠</text>
          <view class="sheet-action">
            <text class="sheet-value discount-value">
              {{ totalDiscount > 0 ? `已减 ¥${formatMoney(totalDiscount)}` : '暂无可用优惠' }}
            </text>
            <text class="sheet-arrow">{{ showDiscountDetail ? '收起' : '展开' }}</text>
          </view>
        </view>
      </view>

      <view v-if="showDiscountDetail" class="sheet-card discount-card">
        <view class="discount-line">
          <text class="discount-label">商品金额</text>
          <text class="discount-money">¥{{ formatMoney(orderAmount) }}</text>
        </view>
        <view class="discount-line">
          <text class="discount-label">会员优惠</text>
          <text class="discount-money minus">-¥{{ formatMoney(memberSaved) }}</text>
        </view>
        <view class="discount-line coupon-line">
          <text class="discount-label">优惠券</text>
          <text class="discount-money minus">-¥{{ formatMoney(couponDiscount) }}</text>
        </view>
        <view class="coupon-pills">
          <view
            class="coupon-pill"
            :class="{ active: !selectedCouponId }"
            @tap.stop="toggleCoupon(null)"
          >
            不使用
          </view>
          <view
            v-for="coupon in coupons"
            :key="coupon.id"
            class="coupon-pill"
            :class="{ active: selectedCouponId === coupon.id }"
            @tap.stop="toggleCoupon(coupon.id)"
          >
            {{ coupon.name || formatCouponValue(coupon) }}
          </view>
        </view>

        <view class="discount-line points-line">
          <text class="discount-label">积分抵扣</text>
          <text class="discount-money minus">-¥{{ formatMoney(pointsDiscount) }}</text>
        </view>
        <view class="points-summary">
          <text class="points-text">
            可用 {{ memberInfo.points || 0 }} 分，当前使用 {{ usedPoints }} 分
          </text>
          <view class="points-actions">
            <view
              class="points-chip"
              :class="{ disabled: !maxUsablePoints }"
              @tap.stop="useAllPoints"
            >
              全部使用
            </view>
            <view
              class="points-chip secondary"
              :class="{ active: usedPoints === 0 }"
              @tap.stop="clearPoints"
            >
              不使用
            </view>
          </view>
        </view>

        <view class="discount-line total-line">
          <text class="discount-label total-label">应付金额</text>
          <text class="discount-money total-money">¥{{ formatMoney(finalAmount) }}</text>
        </view>
      </view>

      <view v-if="requestMessage" class="sheet-card message-card">
        <text class="message-title">长辈留言</text>
        <text class="message-content">{{ requestMessage }}</text>
      </view>

      <view class="submit-card">
        <view class="submit-price-block">
          <text class="submit-price-label">需支付</text>
          <text class="submit-price-value">¥{{ formatMoney(finalAmount) }}</text>
        </view>
        <view
          class="submit-btn"
          :class="{ disabled: submitDisabled }"
          hover-class="submit-btn-hover"
          @tap.stop="handlePayTap"
        >
          {{ paying ? '支付处理中...' : (submitDisabled ? payButtonText : '立即支付') }}
        </view>
      </view>
    </view>

    <view v-else class="loading-wrap">
      <view class="loading-card">
        <text class="loading-title">未找到订单</text>
        <text class="loading-text">请返回上一步重新发起支付。</text>
      </view>
    </view>

    <view v-if="showPasswordSheet" class="password-mask" @tap="closePasswordSheet">
      <view class="password-sheet" @tap.stop>
        <view class="password-head">
          <text class="password-close" @tap.stop="closePasswordSheet">取消</text>
          <text class="password-title">请输入支付密码</text>
          <text class="password-close ghost">占位</text>
        </view>

        <view class="password-amount-block">
          <text class="password-amount-label">付款金额</text>
          <view class="password-amount-row">
            <text class="password-amount-unit">¥</text>
            <text class="password-amount-value">{{ formatMoney(finalAmount) }}</text>
          </view>
          <text class="password-merchant">{{ order.productName }}</text>
        </view>

        <view class="password-cells">
          <view
            v-for="cellIndex in 6"
            :key="cellIndex"
            class="password-cell"
          >
            <view v-if="cellIndex <= passwordValue.length" class="password-dot"></view>
          </view>
        </view>

        <text class="password-hint">{{ passwordHint }}</text>

        <view class="keyboard-grid">
          <view
            v-for="key in keyboardKeys"
            :key="key"
            class="keyboard-key"
            :class="{
              muted: key === 'blank',
              action: key === 'delete'
            }"
            hover-class="keyboard-key-hover"
            @tap.stop="handleKeyboardTap(key)"
          >
            <text v-if="key === 'delete'" class="keyboard-text action-text">删除</text>
            <text v-else-if="key === 'blank'" class="keyboard-text muted-text"></text>
            <text v-else class="keyboard-text">{{ key }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import {
  couponApi,
  memberApi,
  orderApi,
  paymentRequestApi,
  productApi
} from '../../api/index.js'
import { resolveProductImageUrl } from '../../utils/product-image.js'

export default {
  data() {
    return {
      orderId: null,
      requestId: null,
      requestMessage: '',
      loading: true,
      paying: false,
      order: null,
      product: null,
      memberInfo: {
        points: 0,
        levelName: '普通会员',
        discount: '无折扣'
      },
      coupons: [],
      selectedCouponId: null,
      selectedPoints: 0,
      orderAmount: 0,
      memberAmount: 0,
      memberSaved: 0,
      showDiscountDetail: false,
      showPasswordSheet: false,
      passwordValue: ''
    }
  },
  computed: {
    selectedCoupon() {
      return this.coupons.find((item) => item.id === this.selectedCouponId) || null
    },
    couponDiscount() {
      return this.calculateCouponDiscount(this.selectedCoupon, this.memberAmount)
    },
    maxUsablePoints() {
      const availablePoints = Number(this.memberInfo.points || 0)
      const maxByAmount = Math.floor(Math.max(0, this.memberAmount - this.couponDiscount) * 100)
      return Math.max(0, Math.min(availablePoints, maxByAmount))
    },
    usedPoints() {
      const parsed = Number(this.selectedPoints || 0)
      if (!Number.isFinite(parsed) || parsed <= 0) return 0
      return Math.min(Math.floor(parsed), this.maxUsablePoints)
    },
    pointsDiscount() {
      return this.roundMoney(this.usedPoints / 100)
    },
    totalDiscount() {
      return this.roundMoney(this.memberSaved + this.couponDiscount + this.pointsDiscount)
    },
    finalAmount() {
      return this.roundMoney(Math.max(0, this.memberAmount - this.couponDiscount - this.pointsDiscount))
    },
    submitDisabled() {
      return !this.order || this.loading || this.paying || this.order.status !== 'PENDING_PAY'
    },
    submitBlockReason() {
      if (this.loading) return '订单信息加载中'
      if (this.paying) return '支付正在处理中'
      if (!this.order) return '订单信息加载失败'
      if (this.order.status === 'PAID') return '该订单已支付'
      if (this.order.status === 'CANCELLED') return '该订单已取消'
      if (this.order.status === 'COMPLETED') return '该订单已完成'
      if (this.order.status === 'SHIPPED') return '该订单已发货'
      return '当前订单暂不可支付'
    },
    payButtonText() {
      return this.submitDisabled ? this.submitBlockReason : '立即支付'
    },
    statusText() {
      if (!this.order) return ''
      const map = {
        PENDING_PAY: '待支付订单',
        PAID: '已支付',
        SHIPPED: '已发货',
        COMPLETED: '已完成',
        CANCELLED: '已取消'
      }
      return map[this.order.status] || this.order.status
    },
    passwordHint() {
      if (this.paying) return '正在验证支付密码...'
      if (this.passwordValue.length === 0) return '请输入 6 位支付密码'
      return `已输入 ${this.passwordValue.length} / 6 位`
    },
    keyboardKeys() {
      return ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'blank', '0', 'delete']
    }
  },
  onLoad(options) {
    this.orderId = Number(options?.orderId || 0)
    this.requestId = options?.requestId ? Number(options.requestId) : null
    this.requestMessage = options?.message ? decodeURIComponent(options.message) : ''
    this.initialize()
  },
  methods: {
    async initialize() {
      if (!this.orderId) {
        uni.showToast({ title: '订单不存在', icon: 'none' })
        return
      }

      this.loading = true
      try {
        await this.loadOrder()
        await Promise.all([this.loadMemberInfo(), this.loadProduct()])
        await this.loadMemberPricing()
        await this.loadCoupons()
      } catch (e) {
        console.error(e)
        uni.showToast({ title: this.getErrorMessage(e, '订单加载失败'), icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    async loadOrder() {
      const res = await orderApi.getOrder(this.orderId)
      this.order = res.data || null
    },
    async loadMemberInfo() {
      const res = await memberApi.getInfo()
      this.memberInfo = {
        points: Number(res.data?.points || 0),
        levelName: res.data?.levelName || '普通会员',
        discount: res.data?.discount || '无折扣'
      }
    },
    async loadProduct() {
      if (!this.order?.productId) return
      try {
        const res = await productApi.getProduct(this.order.productId)
        this.product = res.data || null
      } catch (e) {
        console.error(e)
      }
    },
    async loadMemberPricing() {
      const original = this.toMoneyNumber(this.order?.totalAmount || 0)
      this.orderAmount = original
      const res = await memberApi.calculateDiscount(original)
      this.memberAmount = this.toMoneyNumber(res.data?.discountPrice || original)
      this.memberSaved = this.toMoneyNumber(res.data?.savedAmount || 0)
      this.syncSelectedPoints()
    },
    async loadCoupons() {
      const params = { amount: this.memberAmount }
      if (this.product?.categoryId) {
        params.categoryId = this.product.categoryId
      }
      const res = await couponApi.usable(params)
      this.coupons = res.data || []
      if (this.selectedCouponId && !this.coupons.some((item) => item.id === this.selectedCouponId)) {
        this.selectedCouponId = null
      }
      this.syncSelectedPoints()
    },
    syncSelectedPoints() {
      this.selectedPoints = this.usedPoints
    },
    toggleDiscountDetail() {
      this.showDiscountDetail = !this.showDiscountDetail
    },
    toggleCoupon(couponId) {
      this.selectedCouponId = this.selectedCouponId === couponId ? null : couponId
      this.syncSelectedPoints()
    },
    useAllPoints() {
      if (!this.maxUsablePoints) return
      this.selectedPoints = this.maxUsablePoints
    },
    clearPoints() {
      this.selectedPoints = 0
    },
    calculateCouponDiscount(coupon, amount) {
      if (!coupon) return 0
      const usableAmount = this.toMoneyNumber(amount)
      if (coupon.type === 'PERCENT') {
        let payRate = this.toMoneyNumber(coupon.value)
        payRate = payRate > 10 ? payRate / 100 : payRate / 10
        payRate = Math.max(0, Math.min(1, payRate))
        return this.roundMoney(usableAmount - usableAmount * payRate)
      }
      return this.roundMoney(Math.min(usableAmount, this.toMoneyNumber(coupon.value)))
    },
    handlePayTap() {
      uni.hideKeyboard()
      if (this.submitDisabled) {
        uni.showToast({ title: this.submitBlockReason, icon: 'none' })
        return
      }
      this.openPasswordSheet()
    },
    openPasswordSheet() {
      this.passwordValue = ''
      this.showPasswordSheet = true
    },
    closePasswordSheet() {
      if (this.paying) return
      this.showPasswordSheet = false
      this.passwordValue = ''
    },
    handleKeyboardTap(key) {
      if (this.paying || !this.showPasswordSheet) return
      if (key === 'blank') return
      if (key === 'delete') {
        this.passwordValue = this.passwordValue.slice(0, -1)
        return
      }
      if (this.passwordValue.length >= 6) return
      this.passwordValue += String(key)
      if (this.passwordValue.length === 6) {
        setTimeout(() => {
          this.submitPayment()
        }, 120)
      }
    },
    async submitPayment() {
      if (this.submitDisabled || this.passwordValue.length < 6) return

      const payload = {}
      if (this.selectedCouponId) {
        payload.userCouponId = this.selectedCouponId
      }
      if (this.usedPoints > 0) {
        payload.pointsToUse = this.usedPoints
      }

      try {
        this.paying = true
        uni.showLoading({ title: '支付处理中...' })
        await this.simulateWechatPay()
        const res = this.requestId
          ? await paymentRequestApi.pay(this.requestId, payload)
          : await orderApi.pay(this.orderId, payload)
        if (res?.data?.status) {
          this.order = {
            ...(this.order || {}),
            ...res.data
          }
        }
        this.showPasswordSheet = false
        this.passwordValue = ''
        uni.hideLoading()
        uni.showToast({ title: '支付成功', icon: 'success' })
        setTimeout(() => {
          this.redirectAfterPay()
        }, 500)
      } catch (e) {
        uni.hideLoading()
        console.error(e)
        this.passwordValue = ''
        uni.showToast({ title: this.getErrorMessage(e, '支付失败'), icon: 'none' })
      } finally {
        this.paying = false
      }
    },
    simulateWechatPay() {
      return new Promise((resolve) => {
        setTimeout(resolve, 900)
      })
    },
    redirectAfterPay() {
      const url = this.requestId
        ? '/pages/payment-requests/payment-requests'
        : '/pages/orders/orders'
      uni.redirectTo({ url })
    },
    formatCouponValue(coupon) {
      if (!coupon) return ''
      return coupon.type === 'PERCENT'
        ? `${coupon.value}折`
        : `减${this.formatMoney(coupon.value)}元`
    },
    getImageUrl(url, productName = '') {
      return resolveProductImageUrl(url, { productName })
    },
    formatMoney(value) {
      return this.toMoneyNumber(value).toFixed(2)
    },
    roundMoney(value) {
      return Math.floor(this.toMoneyNumber(value) * 100) / 100
    },
    toMoneyNumber(value) {
      const num = Number(value)
      return Number.isFinite(num) ? num : 0
    },
    getErrorMessage(error, fallback = '操作失败') {
      if (error?.message && typeof error.message === 'string') return error.message
      if (error?.data?.message && typeof error.data.message === 'string') return error.data.message
      return fallback
    }
  }
}
</script>

<style>
page {
  min-height: 100vh;
  background: #f2f2f7;
  font-family: 'PingFang SC', 'Noto Sans SC', 'Microsoft YaHei', sans-serif;
}

.cashier-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #ededed 0%, #f7f7f7 220rpx, #f2f2f7 100%);
}

.cashier-top {
  padding: 34rpx 32rpx 24rpx;
  text-align: center;
}

.cashier-top-title {
  display: block;
  font-size: 32rpx;
  color: #111827;
  font-weight: 600;
}

.cashier-top-subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #8c8c8c;
}

.loading-wrap {
  padding: 60rpx 24rpx;
}

.loading-card {
  border-radius: 28rpx;
  background: #fff;
  padding: 40rpx 32rpx;
  box-shadow: 0 16rpx 36rpx rgba(15, 23, 42, 0.05);
}

.loading-title {
  display: block;
  font-size: 32rpx;
  color: #111827;
  font-weight: 600;
}

.loading-text {
  display: block;
  margin-top: 14rpx;
  font-size: 26rpx;
  color: #8c8c8c;
  line-height: 1.6;
}

.cashier-content {
  padding: 8rpx 24rpx 34rpx;
}

.merchant-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  border-radius: 32rpx;
  background: #fff;
  padding: 42rpx 32rpx 36rpx;
  box-shadow: 0 18rpx 40rpx rgba(15, 23, 42, 0.06);
}

.merchant-avatar {
  width: 92rpx;
  height: 92rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #09bb07 0%, #1aad19 100%);
  color: #fff;
  font-size: 34rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.merchant-name {
  display: block;
  margin-top: 18rpx;
  color: #111827;
  font-size: 32rpx;
  font-weight: 600;
}

.merchant-desc {
  display: block;
  margin-top: 10rpx;
  color: #8c8c8c;
  font-size: 24rpx;
}

.amount-row {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  margin-top: 28rpx;
}

.amount-unit {
  margin-right: 10rpx;
  font-size: 42rpx;
  color: #111827;
  line-height: 1;
}

.amount-value {
  font-size: 80rpx;
  color: #111827;
  font-weight: 700;
  line-height: 0.92;
}

.amount-note {
  display: block;
  margin-top: 18rpx;
  font-size: 26rpx;
  color: #4b5563;
  text-align: center;
  line-height: 1.6;
}

.status-text {
  display: inline-flex;
  margin-top: 18rpx;
  padding: 10rpx 22rpx;
  border-radius: 999rpx;
  background: #edf8ee;
  color: #18a058;
  font-size: 22rpx;
  font-weight: 600;
}

.sheet-card,
.submit-card {
  margin-top: 20rpx;
  border-radius: 28rpx;
  background: #fff;
  padding: 0 28rpx;
  box-shadow: 0 16rpx 36rpx rgba(15, 23, 42, 0.05);
}

.sheet-row,
.discount-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 98rpx;
  border-bottom: 1rpx solid #f2f2f2;
}

.sheet-row:last-child,
.discount-line:last-child {
  border-bottom: none;
}

.sheet-row.interactive:active,
.coupon-pill:active,
.points-chip:active,
.submit-btn:active,
.keyboard-key:active {
  opacity: 0.85;
}

.sheet-label,
.discount-label {
  font-size: 28rpx;
  color: #111827;
}

.sheet-value,
.discount-money {
  font-size: 26rpx;
  color: #6b7280;
  text-align: right;
}

.sheet-value-strong {
  color: #111827;
  font-weight: 500;
}

.sheet-action,
.pay-method {
  display: flex;
  align-items: center;
}

.discount-value {
  color: #18a058;
  font-weight: 500;
}

.sheet-arrow {
  margin-left: 14rpx;
  font-size: 22rpx;
  color: #999;
}

.wechat-dot {
  width: 18rpx;
  height: 18rpx;
  margin-right: 12rpx;
  border-radius: 50%;
  background: #09bb07;
}

.discount-card {
  padding-top: 6rpx;
  padding-bottom: 18rpx;
}

.minus {
  color: #18a058;
}

.coupon-line,
.points-line {
  border-bottom: none;
  min-height: 84rpx;
}

.coupon-pills,
.points-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.coupon-pills {
  padding-bottom: 14rpx;
}

.coupon-pill,
.points-chip {
  min-height: 62rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: #f5f5f5;
  color: #4b5563;
  font-size: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.coupon-pill.active,
.points-chip.active {
  background: rgba(9, 187, 7, 0.12);
  color: #18a058;
  font-weight: 600;
}

.points-summary {
  padding-bottom: 12rpx;
}

.points-text {
  display: block;
  margin-bottom: 14rpx;
  font-size: 24rpx;
  color: #8c8c8c;
}

.points-chip.secondary {
  background: #f7f7f7;
}

.points-chip.disabled {
  opacity: 0.45;
}

.total-line {
  margin-top: 6rpx;
  padding-top: 10rpx;
  border-top: 1rpx solid #f2f2f2;
}

.total-label,
.total-money {
  color: #111827;
  font-weight: 700;
}

.total-money {
  font-size: 34rpx;
}

.message-card {
  padding-top: 24rpx;
  padding-bottom: 24rpx;
}

.message-title {
  display: block;
  font-size: 26rpx;
  color: #8c8c8c;
}

.message-content {
  display: block;
  margin-top: 14rpx;
  font-size: 28rpx;
  color: #111827;
  line-height: 1.7;
}

.submit-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding-top: 24rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}

.submit-price-block {
  display: flex;
  flex-direction: column;
}

.submit-price-label {
  font-size: 22rpx;
  color: #8c8c8c;
}

.submit-price-value {
  margin-top: 8rpx;
  font-size: 42rpx;
  color: #111827;
  font-weight: 700;
}

.submit-btn {
  min-width: 260rpx;
  height: 88rpx;
  border-radius: 22rpx;
  background: linear-gradient(135deg, #09bb07 0%, #1aad19 100%);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 16rpx 28rpx rgba(9, 187, 7, 0.22);
}

.submit-btn.disabled {
  background: #cdd6dd;
  box-shadow: none;
}

.submit-btn-hover {
  transform: scale(0.98);
}

.password-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.42);
  display: flex;
  align-items: flex-end;
  z-index: 999;
}

.password-sheet {
  width: 100%;
  border-radius: 32rpx 32rpx 0 0;
  background: #f7f7f7;
  overflow: hidden;
}

.password-head {
  height: 96rpx;
  padding: 0 28rpx;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1rpx solid #efefef;
}

.password-close {
  width: 96rpx;
  font-size: 28rpx;
  color: #576b95;
}

.password-close.ghost {
  color: transparent;
}

.password-title {
  font-size: 30rpx;
  color: #111827;
  font-weight: 600;
}

.password-amount-block {
  padding: 34rpx 32rpx 28rpx;
  background: #fff;
  text-align: center;
}

.password-amount-label {
  display: block;
  font-size: 24rpx;
  color: #8c8c8c;
}

.password-amount-row {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  margin-top: 18rpx;
}

.password-amount-unit {
  margin-right: 8rpx;
  font-size: 40rpx;
  color: #111827;
}

.password-amount-value {
  font-size: 68rpx;
  color: #111827;
  font-weight: 700;
  line-height: 1;
}

.password-merchant {
  display: block;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #6b7280;
}

.password-cells {
  display: flex;
  margin: 24rpx 24rpx 0;
  border: 1rpx solid #d6d6d6;
  border-radius: 18rpx;
  overflow: hidden;
  background: #fff;
}

.password-cell {
  flex: 1;
  height: 86rpx;
  border-right: 1rpx solid #d6d6d6;
  display: flex;
  align-items: center;
  justify-content: center;
}

.password-cell:last-child {
  border-right: none;
}

.password-dot {
  width: 18rpx;
  height: 18rpx;
  border-radius: 50%;
  background: #111827;
}

.password-hint {
  display: block;
  margin: 18rpx 24rpx 20rpx;
  text-align: center;
  font-size: 24rpx;
  color: #8c8c8c;
}

.keyboard-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rpx;
  background: #d8d8d8;
}

.keyboard-key {
  height: 104rpx;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.keyboard-key.muted {
  background: #ececec;
}

.keyboard-key.action {
  background: #ececec;
}

.keyboard-key-hover {
  opacity: 0.8;
}

.keyboard-text {
  font-size: 40rpx;
  color: #111827;
  font-weight: 500;
}

.keyboard-text.action-text {
  font-size: 28rpx;
}

.keyboard-text.muted-text {
  color: transparent;
}
</style>
