<template>
  <view class="container">
    <view class="header">
      <text class="title">优惠券</text>
      <text class="subtitle">可直接领取平台券，也可用会员积分兑换指定优惠券</text>
    </view>

    <view class="points-card">
      <view>
        <text class="points-label">当前积分</text>
        <text class="points-value">{{ memberPoints }}</text>
      </view>
      <button class="points-btn" @click="goMember">去赚积分</button>
    </view>

    <view class="tabs">
      <view class="tab" :class="{ active: currentTab === 'available' }" @click="switchTab('available')">
        可领取 / 可兑换
      </view>
      <view class="tab" :class="{ active: currentTab === 'mine' }" @click="switchTab('mine')">
        我的券
      </view>
    </view>

    <view class="coupon-list">
      <view
        v-for="item in currentList"
        :key="`${currentTab}-${item.id || item.couponId}`"
        class="coupon-card"
        :class="{ used: isDimmed(item) }"
      >
        <view class="coupon-left">
          <text class="coupon-value">{{ formatCouponValue(item) }}</text>
          <text class="coupon-condition">满 {{ formatAmount(item.minAmount) }} 可用</text>
        </view>

        <view class="coupon-right">
          <text class="coupon-name">{{ item.name || '专属优惠券' }}</text>
          <text class="coupon-meta">{{ formatCouponType(item.type) }}</text>
          <text class="coupon-expire">有效期至 {{ formatExpire(item) }}</text>

          <template v-if="currentTab === 'available'">
            <text v-if="item.exchangeEnabled === 1" class="exchange-tip">
              {{ Number(item.exchangePoints || 0) }} 积分可兑换
            </text>

            <button
              v-if="item.exchangeEnabled === 1"
              class="btn-action btn-exchange"
              :disabled="processingId === item.id || memberPoints < Number(item.exchangePoints || 0)"
              @click="exchangeCoupon(item)"
            >
              {{ processingId === item.id ? '兑换中...' : '积分兑换' }}
            </button>

            <button
              v-else
              class="btn-action btn-receive"
              :disabled="processingId === item.id"
              @click="receiveCoupon(item.id)"
            >
              {{ processingId === item.id ? '领取中...' : '立即领取' }}
            </button>
          </template>

          <view v-else class="status-box">
            <text class="status-tag" :class="item.status">{{ getStatusText(item.status) }}</text>
          </view>
        </view>
      </view>

      <view v-if="!currentList.length" class="empty">
        <view class="empty-icon">券</view>
        <text class="empty-text">
          {{ currentTab === 'available' ? '暂无可领取或可兑换的优惠券' : '暂无优惠券' }}
        </text>
      </view>
    </view>
  </view>
</template>

<script>
import { couponApi, memberApi } from '../../api/index.js'

export default {
  data() {
    return {
      currentTab: 'available',
      availableList: [],
      myList: [],
      memberPoints: 0,
      refreshing: false,
      processingId: null
    }
  },
  computed: {
    currentList() {
      return this.currentTab === 'available' ? this.availableList : this.myList
    }
  },
  onLoad() {
    this.refreshData()
  },
  onShow() {
    this.refreshData()
  },
  methods: {
    switchTab(tab) {
      if (this.currentTab === tab) return
      this.currentTab = tab
      this.refreshData()
    },
    async refreshData() {
      if (this.refreshing) return
      this.refreshing = true
      try {
        await Promise.all([this.loadMemberInfo(), this.loadAvailable(), this.loadMyCoupons()])
      } finally {
        this.refreshing = false
      }
    },
    async loadMemberInfo() {
      try {
        const res = await memberApi.getInfo()
        this.memberPoints = Number(res?.data?.points || 0)
      } catch (e) {
        console.error(e)
      }
    },
    async loadAvailable() {
      try {
        const res = await couponApi.available()
        this.availableList = Array.isArray(res?.data)
          ? res.data.map((item) => ({
              ...item,
              exchangeEnabled: Number(item.exchangeEnabled || 0),
              exchangePoints: Number(item.exchangePoints || 0)
            }))
          : []
      } catch (e) {
        console.error(e)
      }
    },
    async loadMyCoupons() {
      try {
        const res = await couponApi.my()
        this.myList = Array.isArray(res?.data) ? res.data : []
      } catch (e) {
        console.error(e)
      }
    },
    async receiveCoupon(id) {
      if (this.processingId === id) return
      this.processingId = id
      try {
        await couponApi.receive(id)
        await this.refreshData()
        uni.showToast({ title: '领取成功', icon: 'success' })
      } catch (e) {
        uni.showToast({ title: this.getErrorMessage(e, '领取失败'), icon: 'none' })
      } finally {
        this.processingId = null
      }
    },
    async exchangeCoupon(item) {
      const requiredPoints = Number(item.exchangePoints || 0)
      if (this.memberPoints < requiredPoints) {
        uni.showToast({ title: '积分不足', icon: 'none' })
        return
      }
      if (this.processingId === item.id) return
      this.processingId = item.id
      try {
        await couponApi.exchange(item.id)
        await this.refreshData()
        uni.showToast({ title: `兑换成功，已扣除 ${requiredPoints} 积分`, icon: 'success' })
      } catch (e) {
        uni.showToast({ title: this.getErrorMessage(e, '兑换失败'), icon: 'none' })
      } finally {
        this.processingId = null
      }
    },
    goMember() {
      uni.navigateTo({ url: '/pages/member/member' })
    },
    isDimmed(item) {
      return this.currentTab === 'mine' && item.status !== 'UNUSED'
    },
    formatCouponValue(item) {
      const value = Number(item?.value || 0)
      if (item?.type === 'PERCENT') {
        return `${this.trimZero(value)} 折`
      }
      return `¥${this.formatAmount(value)}`
    },
    formatAmount(value) {
      const amount = Number(value || 0)
      if (!Number.isFinite(amount)) return '0'
      return this.trimZero(amount)
    },
    trimZero(value) {
      const text = Number(value || 0).toFixed(2)
      return text.replace(/\.00$/, '').replace(/(\.\d)0$/, '$1')
    },
    formatCouponType(type) {
      const map = {
        FIXED: '满减券',
        PERCENT: '折扣券'
      }
      return map[type] || '优惠券'
    },
    formatExpire(item) {
      const raw = item?.expireTime || item?.endTime
      if (!raw) return '长期有效'
      const date = new Date(raw)
      if (Number.isNaN(date.getTime())) return raw
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      return `${date.getFullYear()}-${month}-${day}`
    },
    getStatusText(status) {
      const map = {
        UNUSED: '未使用',
        USED: '已使用',
        EXPIRED: '已过期'
      }
      return map[status] || status || '未知状态'
    },
    getErrorMessage(error, fallback = '操作失败') {
      if (typeof error?.message === 'string' && error.message.trim()) return error.message
      if (typeof error?.data?.message === 'string' && error.data.message.trim()) return error.data.message
      return fallback
    }
  }
}
</script>

<style>
page {
  background: linear-gradient(180deg, #f0f9ff 0%, #e9f5ff 45%, #f7fbff 100%);
  min-height: 100vh;
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.container {
  min-height: 100vh;
  padding-bottom: 40rpx;
}

.header {
  background: linear-gradient(135deg, #0369a1 0%, #0ea5e9 50%, #38bdf8 100%);
  padding: 60rpx 30rpx 28rpx;
}

.title {
  color: #fff;
  font-size: 40rpx;
  font-weight: 700;
}

.subtitle {
  display: block;
  margin-top: 12rpx;
  color: rgba(255, 255, 255, 0.88);
  font-size: 24rpx;
  line-height: 1.6;
}

.points-card {
  margin: -12rpx 20rpx 0;
  padding: 24rpx 28rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 14rpx 30rpx rgba(3, 105, 161, 0.12);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.points-label {
  display: block;
  color: #64748b;
  font-size: 24rpx;
}

.points-value {
  display: block;
  margin-top: 10rpx;
  color: #0f172a;
  font-size: 44rpx;
  font-weight: 700;
}

.points-btn {
  margin: 0;
  padding: 0 28rpx;
  height: 64rpx;
  line-height: 64rpx;
  border-radius: 999rpx;
  border: none;
  background: #0369a1;
  color: #fff;
  font-size: 24rpx;
}

.points-btn::after {
  border: none;
}

.tabs {
  display: flex;
  gap: 12rpx;
  padding: 20rpx;
  background: #fff;
  position: sticky;
  top: 0;
  z-index: 10;
  margin-top: 20rpx;
}

.tab {
  flex: 1;
  text-align: center;
  padding: 18rpx 0;
  border-radius: 999rpx;
  background: #f8fafc;
  color: #64748b;
  font-size: 28rpx;
}

.tab.active {
  background: #0369a1;
  color: #fff;
  font-weight: 600;
}

.coupon-list {
  padding: 20rpx;
}

.coupon-card {
  display: flex;
  overflow: hidden;
  background: #fff;
  border-radius: 28rpx;
  border: 1rpx solid #dbeafe;
  box-shadow: 0 12rpx 26rpx rgba(3, 105, 161, 0.08);
  margin-bottom: 20rpx;
}

.coupon-card.used {
  opacity: 0.66;
}

.coupon-left {
  width: 220rpx;
  padding: 28rpx 18rpx;
  background: linear-gradient(135deg, #0369a1 0%, #0ea5e9 55%, #38bdf8 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.coupon-card.used .coupon-left {
  background: linear-gradient(135deg, #94a3b8 0%, #cbd5e1 100%);
}

.coupon-value {
  font-size: 48rpx;
  font-weight: 700;
}

.coupon-condition {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.86);
}

.coupon-right {
  flex: 1;
  padding: 24rpx 26rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.coupon-name {
  color: #0f172a;
  font-size: 30rpx;
  font-weight: 700;
}

.coupon-meta,
.coupon-expire,
.exchange-tip {
  margin-top: 8rpx;
  color: #64748b;
  font-size: 24rpx;
}

.exchange-tip {
  color: #0369a1;
  font-weight: 600;
}

.btn-action {
  margin: 18rpx 0 0;
  width: 200rpx;
  height: 60rpx;
  line-height: 60rpx;
  border-radius: 999rpx;
  border: none;
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
}

.btn-action::after {
  border: none;
}

.btn-receive {
  background: linear-gradient(135deg, #0369a1 0%, #0ea5e9 100%);
}

.btn-exchange {
  background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%);
}

.btn-action[disabled] {
  background: #cbd5e1;
  color: #fff;
}

.status-box {
  margin-top: 18rpx;
}

.status-tag {
  display: inline-flex;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 600;
}

.status-tag.UNUSED {
  background: #dbeafe;
  color: #1d4ed8;
}

.status-tag.USED {
  background: #f1f5f9;
  color: #64748b;
}

.status-tag.EXPIRED {
  background: #fef2f2;
  color: #b91c1c;
}

.empty {
  min-height: 50vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.empty-icon {
  width: 110rpx;
  height: 110rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%);
  color: #fff;
  font-size: 50rpx;
  font-weight: 700;
}

.empty-text {
  margin-top: 18rpx;
  color: #64748b;
  font-size: 28rpx;
}
</style>
