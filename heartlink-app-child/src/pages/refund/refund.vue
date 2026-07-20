<template>
  <view class="container">
    <view class="hero">
        <text class="hero-title">{{ pageTitle }}</text>
        <text class="hero-subtitle">{{ pageSubtitle }}</text>
    </view>

    <view v-if="loading" class="loading-box">
      <text class="loading-text">正在加载订单信息...</text>
    </view>

    <view v-else-if="order" class="content">
      <view class="panel">
        <view class="panel-head">
          <text class="panel-title">订单信息</text>
          <text class="status-tag">{{ getStatusText(order.status) }}</text>
        </view>
        <view class="order-card">
          <image
            class="product-image"
            :src="getImageUrl(order.productImage, order.productName)"
            mode="aspectFill"
          />
          <view class="order-info">
            <text class="product-name">{{ order.productName }}</text>
            <text class="product-meta">订单号：{{ order.orderNo }}</text>
            <text class="product-meta">商品金额：¥{{ formatMoney(order.totalAmount) }}</text>
            <text class="product-meta">收货信息：{{ formatReceiver(order) }}</text>
          </view>
        </view>
      </view>

      <view class="panel">
        <view class="panel-head">
          <text class="panel-title">退款状态</text>
        </view>
        <view class="refund-state">
          <text class="refund-badge" :class="refundStatusClass">{{ refundStatusText }}</text>
          <text class="refund-note">{{ refundStatusNote }}</text>
          <text class="credit-note" :class="{ low: isLowCredit, high: isHighTrust }">
            信用分：{{ creditScore }}{{ creditHint }}
          </text>
        </view>

        <view v-if="order.refundReason" class="reason-block submitted-reason">
          <text class="reason-label">已提交原因</text>
          <text class="reason-text">{{ order.refundReason }}</text>
        </view>

        <view v-if="submittedRefundImages.length > 0" class="proof-block">
          <text class="reason-label">凭证图片</text>
          <view class="proof-grid readonly">
            <image
              v-for="(item, index) in submittedRefundImages"
              :key="item + index"
              class="proof-image"
              :src="item"
              mode="aspectFill"
              @click="previewImages(submittedRefundImages, index)"
            />
          </view>
        </view>
      </view>

      <view v-if="canSubmitRefund" class="panel">
        <view class="panel-head">
          <text class="panel-title">{{ reasonRequired ? (isNegotiatedRefund ? '协商退款说明' : '退款原因') : '直接退款说明' }}</text>
          <text class="panel-note">{{ reasonRequired ? '必填' : '选填' }}</text>
        </view>

        <view v-if="reasonRequired" class="preset-list">
          <view
            v-for="item in presetReasons"
            :key="item"
            class="preset-chip"
            :class="{ active: reason === item }"
            @click="selectReason(item)"
          >
            {{ item }}
          </view>
        </view>

        <view class="reason-block">
          <textarea
            v-model="reason"
            class="reason-input"
            maxlength="120"
            :placeholder="reasonPlaceholder"
          />
          <text class="reason-count">{{ reason.length }}/120</text>
        </view>

        <view v-if="needsProofImages" class="proof-block">
          <view class="proof-head">
            <text class="reason-label">上传凭证图片</text>
            <text class="proof-tip">至少 1 张，最多 3 张</text>
          </view>
          <view class="proof-grid">
            <view
              v-for="(item, index) in proofImages"
              :key="item + index"
              class="proof-item"
            >
              <image
                class="proof-image"
                :src="item"
                mode="aspectFill"
                @click="previewImages(proofImages, index)"
              />
              <view class="proof-remove" @click.stop="removeProofImage(index)">×</view>
            </view>

            <view
              v-if="proofImages.length < maxProofImages"
              class="proof-upload"
              @click="chooseProofImages"
            >
              <text class="proof-upload-plus">{{ uploadingImages ? '...' : '+' }}</text>
              <text class="proof-upload-text">{{ uploadingImages ? '上传中' : '上传图片' }}</text>
            </view>
          </view>
        </view>

        <button
          class="submit-btn"
          :disabled="submitDisabled"
          @click="submitRefund"
        >
          {{ submitButtonText }}
        </button>
      </view>
    </view>
  </view>
</template>

<script>
import { orderApi, toAbsoluteMediaUrl } from '../../api/index.js'
import { resolveProductImageUrl } from '../../utils/product-image.js'

const MAX_PROOF_IMAGES = 3

export default {
  data() {
    return {
      orderId: null,
      order: null,
      loading: true,
      submitting: false,
      uploadingImages: false,
      reason: '',
      proofImages: [],
      maxProofImages: MAX_PROOF_IMAGES,
      presetReasons: ['商品不合适', '重复购买', '物流太慢', '地址填错了', '与描述不符', '商品破损']
    }
  },
  computed: {
    creditScore() {
      const score = Number(this.order?.childCreditScore)
      return Number.isFinite(score) ? score : 100
    },
    isHighTrust() {
      return this.creditScore > 90
    },
    isLowCredit() {
      return this.creditScore < 50
    },
    isNegotiatedRefund() {
      return this.order?.status === 'COMPLETED' || this.order?.logisticsStatus === 'SIGNED'
    },
    reasonRequired() {
      return !this.isHighTrust
    },
    needsProofImages() {
      return !this.isHighTrust && this.isNegotiatedRefund
    },
    canSubmitRefund() {
      return ['PAID', 'SHIPPED', 'COMPLETED'].includes(this.order?.status)
        && (!this.order?.refundStatus || this.order?.refundStatus === 'NONE')
    },
    wasAutoApproved() {
      return this.order?.refundStatus === 'APPROVED' && Number(this.order?.refundCreditScore) > 90
    },
    pageTitle() {
      if (this.isHighTrust) return '直接退款'
      return this.isNegotiatedRefund ? '协商退款' : '退款服务'
    },
    pageSubtitle() {
      if (this.isHighTrust) {
        return '当前信用分较高，可直接退款，无需填写原因或上传凭证。'
      }
      if (this.isNegotiatedRefund) {
        return '当前订单已签收，需填写原因并上传问题凭证图片。'
      }
      return '当前信用分未达到直退标准，退款时需要填写原因。'
    },
    creditHint() {
      if (this.isHighTrust) return '，符合直接退款条件'
      if (this.isLowCredit) return '，本次退款会被重点核验'
      return '，本次退款需填写原因'
    },
    reasonPlaceholder() {
      if (!this.reasonRequired) {
        return '当前为直接退款，可选填补充说明'
      }
      return this.isNegotiatedRefund
        ? '请描述问题，例如商品破损、与预期不符、质量问题等'
        : '请填写退款原因，例如商品不合适、重复购买、物流问题等'
    },
    submitDisabled() {
      return this.submitting || this.uploadingImages || (this.reasonRequired && !this.reason.trim())
    },
    submitButtonText() {
      if (this.submitting) return '提交中...'
      if (this.isHighTrust) return '确认直接退款'
      return this.isNegotiatedRefund ? '提交协商退款' : '提交退款申请'
    },
    refundStatusText() {
      const status = this.order?.refundStatus
      if (status === 'APPROVED' && this.wasAutoApproved) return '已直接退款'
      if (status === 'REQUESTED') return this.isNegotiatedRefund ? '协商退款处理中' : '退款处理中'
      if (status === 'APPROVED') return '已同意退款'
      if (status === 'REJECTED') return this.isNegotiatedRefund ? '协商退款被拒绝' : '退款被拒绝'
      return this.isNegotiatedRefund ? '待协商退款' : '未发起退款'
    },
    refundStatusClass() {
      const status = this.order?.refundStatus
      if (status === 'REQUESTED') return 'requested'
      if (status === 'APPROVED') return 'approved'
      if (status === 'REJECTED') return 'rejected'
      return 'none'
    },
    refundStatusNote() {
      const status = this.order?.refundStatus
      if (status === 'REQUESTED') {
        if (this.isLowCredit) {
          return '退款已提交。当前账号信用较低，平台会对本次退款重点核验。'
        }
        return this.isNegotiatedRefund
          ? '凭证图片和原因已提交，请继续和对方协商处理。'
          : '退款申请已提交，等待对方处理。'
      }
      if (status === 'APPROVED' && this.wasAutoApproved) return '系统已按高信用规则自动同意退款。'
      if (status === 'APPROVED') return '退款已通过，后续将按订单流程处理。'
      if (status === 'REJECTED') return '退款被拒绝，可继续沟通后重新处理。'
      if (this.isHighTrust && this.canSubmitRefund) {
        return '当前信用分较高，可直接退款。'
      }
      if (this.canSubmitRefund) {
        if (this.isLowCredit) {
          return this.isNegotiatedRefund
            ? '当前信用较低，退款需填写原因并上传图片，提交后会被重点核验。'
            : '当前信用较低，退款需填写原因，提交后会被重点核验。'
        }
        return this.isNegotiatedRefund
          ? '当前订单已签收，提交时必须上传问题凭证图片。'
          : '当前订单还未签收，可直接提交退款申请。'
      }
      return '当前订单状态暂不支持退款。'
    },
    submittedRefundImages() {
      return this.parseRefundImages(this.order?.refundImages)
    }
  },
  onLoad(options) {
    this.orderId = Number(options?.orderId || 0)
    this.loadOrder()
  },
  methods: {
    async loadOrder() {
      if (!this.orderId) {
        uni.showToast({ title: '订单不存在', icon: 'none' })
        return
      }
      this.loading = true
      try {
        const res = await orderApi.getOrder(this.orderId)
        this.order = res.data || null
        this.reason = this.order?.refundReason || ''
        this.proofImages = this.parseRefundImages(this.order?.refundImages)
      } catch (e) {
        console.error(e)
      } finally {
        this.loading = false
      }
    },
    parseRefundImages(value) {
      if (!value) return []
      let images = []
      if (Array.isArray(value)) {
        images = value
      } else if (typeof value === 'string') {
        try {
          const parsed = JSON.parse(value)
          images = Array.isArray(parsed) ? parsed : []
        } catch (e) {
          images = value.split(',').map((item) => item.trim()).filter(Boolean)
        }
      }
      return images
        .map((item) => {
          const text = String(item || '').trim()
          if (!text) return ''
          return /^https?:\/\//i.test(text) || /^data:/i.test(text) ? text : toAbsoluteMediaUrl(text)
        })
        .filter(Boolean)
    },
    selectReason(value) {
      this.reason = value
    },
    async chooseProofImages() {
      if (this.uploadingImages) return
      const remain = this.maxProofImages - this.proofImages.length
      if (remain <= 0) return

      try {
        const chooseRes = await new Promise((resolve, reject) => {
          uni.chooseImage({
            count: remain,
            sizeType: ['compressed'],
            success: resolve,
            fail: reject
          })
        })

        const files = Array.isArray(chooseRes?.tempFilePaths) ? chooseRes.tempFilePaths : []
        if (!files.length) return

        this.uploadingImages = true
        uni.showLoading({ title: '上传图片中...' })
        const uploaded = []
        for (const filePath of files) {
          const res = await orderApi.uploadRefundImage(filePath)
          const saved = res?.data?.relativeUrl || res?.data?.url || ''
          if (saved) {
            uploaded.push(/^https?:\/\//i.test(saved) ? saved : toAbsoluteMediaUrl(saved))
          }
        }
        this.proofImages = [...this.proofImages, ...uploaded].slice(0, this.maxProofImages)
      } catch (e) {
        const errMsg = String(e?.errMsg || '').toLowerCase()
        if (!errMsg.includes('cancel')) {
          console.error(e)
          uni.showToast({ title: e?.message || '图片上传失败', icon: 'none' })
        }
      } finally {
        this.uploadingImages = false
        uni.hideLoading()
      }
    },
    removeProofImage(index) {
      this.proofImages.splice(index, 1)
    },
    previewImages(images, index = 0) {
      if (!Array.isArray(images) || !images.length) return
      uni.previewImage({
        current: images[index] || images[0],
        urls: images
      })
    },
    async submitRefund() {
      const refundReason = this.reason.trim()
      if ((this.reasonRequired && !refundReason) || !this.canSubmitRefund) return
      if (this.needsProofImages && this.proofImages.length === 0) {
        uni.showToast({ title: '请先上传凭证图片', icon: 'none' })
        return
      }

      try {
        this.submitting = true
        await orderApi.refund(this.orderId, {
          reason: refundReason,
          images: this.proofImages
        })
        uni.showToast({
          title: this.isHighTrust ? '已直接退款' : (this.isNegotiatedRefund ? '协商退款已提交' : '退款申请已提交'),
          icon: 'success'
        })
        await this.loadOrder()
      } catch (e) {
        console.error(e)
      } finally {
        this.submitting = false
      }
    },
    getImageUrl(url, productName = '') {
      return resolveProductImageUrl(url, { productName })
    },
    formatMoney(value) {
      const num = Number(value)
      return Number.isFinite(num) ? num.toFixed(2) : '0.00'
    },
    formatReceiver(order) {
      const values = [order.receiverName, order.receiverPhone, order.receiverAddress].filter(Boolean)
      return values.length ? values.join(' / ') : '未填写'
    },
    getStatusText(status) {
      const map = {
        PENDING_PAY: '待支付',
        PAID: '已支付',
        SHIPPED: '已发货',
        COMPLETED: '已签收',
        CANCELLED: '已取消'
      }
      return map[status] || status
    }
  }
}
</script>

<style>
page {
  background: linear-gradient(180deg, #f7fafc 0%, #eefbf3 100%);
  min-height: 100vh;
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.container {
  min-height: 100vh;
  padding-bottom: 40rpx;
}

.hero {
  padding: 56rpx 28rpx 30rpx;
}

.hero-title {
  display: block;
  color: #052e16;
  font-size: 44rpx;
  font-weight: 800;
}

.hero-subtitle {
  display: block;
  margin-top: 12rpx;
  color: #4b5563;
  font-size: 26rpx;
  line-height: 1.6;
}

.loading-box {
  padding: 120rpx 0;
  text-align: center;
}

.loading-text {
  color: #64748b;
  font-size: 28rpx;
}

.content {
  padding: 0 20rpx 30rpx;
}

.panel {
  background: rgba(255, 255, 255, 0.96);
  border: 1rpx solid #d1fae5;
  border-radius: 30rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 16rpx 34rpx rgba(15, 118, 110, 0.08);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.panel-title {
  color: #0f172a;
  font-size: 30rpx;
  font-weight: 700;
}

.panel-note {
  color: #ef4444;
  font-size: 22rpx;
}

.status-tag,
.refund-badge {
  display: inline-flex;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 700;
}

.status-tag {
  background: #ecfdf5;
  color: #15803d;
}

.order-card {
  display: flex;
  gap: 20rpx;
  margin-top: 20rpx;
}

.product-image {
  width: 148rpx;
  height: 148rpx;
  border-radius: 24rpx;
  background: #f8fafc;
}

.order-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.product-name {
  color: #0f172a;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1.4;
}

.product-meta {
  margin-top: 10rpx;
  color: #64748b;
  font-size: 24rpx;
  line-height: 1.5;
}

.refund-state {
  margin-top: 20rpx;
}

.refund-badge.none {
  background: #f1f5f9;
  color: #475569;
}

.refund-badge.requested {
  background: #fff7ed;
  color: #c2410c;
}

.refund-badge.approved {
  background: #ecfdf5;
  color: #15803d;
}

.refund-badge.rejected {
  background: #fef2f2;
  color: #b91c1c;
}

.refund-note {
  display: block;
  margin-top: 14rpx;
  color: #64748b;
  font-size: 24rpx;
  line-height: 1.6;
}

.credit-note {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #64748b;
}

.credit-note.low {
  color: #b91c1c;
}

.credit-note.high {
  color: #15803d;
}

.preset-list {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
  margin-top: 20rpx;
}

.preset-chip {
  padding: 14rpx 22rpx;
  border-radius: 999rpx;
  background: #f8fafc;
  color: #475569;
  font-size: 24rpx;
}

.preset-chip.active {
  background: #dcfce7;
  color: #15803d;
  font-weight: 700;
}

.reason-block,
.proof-block {
  margin-top: 20rpx;
  padding: 20rpx;
  border-radius: 24rpx;
  background: #f8fafc;
}

.submitted-reason {
  margin-top: 18rpx;
}

.reason-label {
  display: block;
  color: #0f172a;
  font-size: 24rpx;
  font-weight: 700;
}

.reason-text {
  display: block;
  margin-top: 10rpx;
  color: #475569;
  font-size: 26rpx;
  line-height: 1.7;
}

.reason-input {
  width: 100%;
  min-height: 180rpx;
  color: #0f172a;
  font-size: 26rpx;
  line-height: 1.6;
}

.reason-count {
  display: block;
  margin-top: 8rpx;
  text-align: right;
  color: #94a3b8;
  font-size: 22rpx;
}

.proof-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.proof-tip {
  color: #64748b;
  font-size: 22rpx;
}

.proof-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 18rpx;
}

.proof-grid.readonly {
  margin-top: 14rpx;
}

.proof-item,
.proof-upload {
  position: relative;
  width: 180rpx;
  height: 180rpx;
  border-radius: 22rpx;
  overflow: hidden;
  background: #e2e8f0;
}

.proof-image {
  width: 100%;
  height: 100%;
}

.proof-remove {
  position: absolute;
  top: 10rpx;
  right: 10rpx;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: rgba(15, 23, 42, 0.62);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
}

.proof-upload {
  border: 2rpx dashed #86efac;
  background: #f0fdf4;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.proof-upload-plus {
  color: #16a34a;
  font-size: 48rpx;
  font-weight: 700;
}

.proof-upload-text {
  margin-top: 10rpx;
  color: #15803d;
  font-size: 24rpx;
}

.submit-btn {
  margin-top: 24rpx;
  height: 86rpx;
  line-height: 86rpx;
  border-radius: 999rpx;
  border: none;
  background: linear-gradient(135deg, #16a34a 0%, #22c55e 100%);
  color: #fff;
  font-size: 30rpx;
  font-weight: 800;
  box-shadow: 0 14rpx 30rpx rgba(34, 197, 94, 0.24);
}

.submit-btn::after {
  border: none;
}

.submit-btn[disabled] {
  opacity: 0.5;
}
</style>
