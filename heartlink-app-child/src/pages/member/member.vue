<template>
  <view class="container">
    <view class="hero-card">
      <view class="hero-top">
        <view class="level-badge">{{ levelCode }}</view>
        <view class="hero-text">
          <text class="hero-title">{{ levelName }}</text>
          <text class="hero-subtitle">积分越高，结算优惠越大，还能兑换专属优惠券</text>
        </view>
        <button
          class="check-in-btn"
          :disabled="!!memberInfo.checkedInToday"
          @click="doCheckIn"
        >
          {{ checkInButtonText }}
        </button>
      </view>

      <view class="hero-stats">
        <view class="stat-item">
          <text class="stat-label">当前积分</text>
          <text class="stat-value">{{ memberInfo.points || 0 }}</text>
        </view>
        <view class="stat-item">
          <text class="stat-label">当前折扣</text>
          <text class="stat-value">{{ discountText }}</text>
        </view>
      </view>

      <view class="progress-box">
        <view class="progress-head">
          <text class="progress-title">升级进度</text>
          <text class="progress-meta">距 {{ nextLevelName }} 还差 {{ nextLevelPoints }} 积分</text>
        </view>
        <view class="progress-track">
          <view class="progress-fill" :style="{ width: progressWidth }"></view>
        </view>
      </view>
    </view>

    <view class="panel">
      <view class="panel-head">
        <text class="panel-title">会员权益</text>
      </view>
      <view class="benefit-list">
        <view class="benefit-item" v-for="item in benefitList" :key="item.title">
          <view class="benefit-icon">{{ item.icon }}</view>
          <view class="benefit-body">
            <text class="benefit-title">{{ item.title }}</text>
            <text class="benefit-desc">{{ item.desc }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="panel">
      <view class="panel-head">
        <text class="panel-title">积分规则</text>
      </view>
      <view class="rule-list">
        <text class="rule-item">购物完成后可累计积分，等级越高折扣越优。</text>
        <text class="rule-item">支付页可直接用积分抵扣，100 积分可抵 1 元。</text>
        <text class="rule-item">每日签到可获得 {{ dailyCheckInPoints }} 积分，可持续积累兑换优惠券。</text>
      </view>
    </view>

    <view class="panel">
      <view class="panel-head">
        <text class="panel-title">快捷入口</text>
      </view>
      <view class="quick-grid">
        <view class="quick-item" @click="goCoupon">
          <text class="quick-icon">券</text>
          <text class="quick-name">优惠券</text>
        </view>
        <view class="quick-item" @click="goOrders">
          <text class="quick-icon">单</text>
          <text class="quick-name">我的订单</text>
        </view>
        <view class="quick-item" @click="goPaymentRequests">
          <text class="quick-icon">付</text>
          <text class="quick-name">代付请求</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { memberApi } from '../../api/index.js'

export default {
  data() {
    return {
      memberInfo: {
        points: 0,
        level: 0,
        levelName: '普通会员',
        discount: '无折扣',
        nextLevelPoints: 0,
        nextLevelName: '下一等级',
        dailyCheckInPoints: 10,
        checkedInToday: false
      }
    }
  },
  computed: {
    levelCode() {
      return ['Lv0', 'Lv1', 'Lv2', 'Lv3'][this.memberInfo.level] || 'Lv0'
    },
    levelName() {
      return this.memberInfo.levelName || '普通会员'
    },
    discountText() {
      return this.memberInfo.discount || '无折扣'
    },
    nextLevelName() {
      return this.memberInfo.nextLevelName || '下一等级'
    },
    nextLevelPoints() {
      return Number(this.memberInfo.nextLevelPoints || 0)
    },
    dailyCheckInPoints() {
      return Number(this.memberInfo.dailyCheckInPoints || 10)
    },
    checkInButtonText() {
      return this.memberInfo.checkedInToday
        ? '\u4eca\u65e5\u5df2\u7b7e\u5230'
        : '\u6bcf\u65e5\u7b7e\u5230'
    },
    progressWidth() {
      const currentPoints = Number(this.memberInfo.points || 0)
      const gap = this.nextLevelPoints
      if (gap <= 0) return '100%'
      const total = currentPoints + gap
      if (total <= 0) return '0%'
      const ratio = Math.max(0, Math.min(1, currentPoints / total))
      return `${Math.round(ratio * 100)}%`
    },
    benefitList() {
      return [
        {
          icon: '折',
          title: '会员折扣',
          desc: `当前下单可享 ${this.discountText}`
        },
        {
          icon: '积',
          title: '积分抵扣',
          desc: '支付时可直接抵扣现金'
        },
        {
          icon: '券',
          title: '积分兑换券',
          desc: '指定优惠券可直接用会员积分兑换'
        }
      ]
    }
  },
  onShow() {
    this.loadMemberInfo()
  },
  methods: {
    async loadMemberInfo() {
      try {
        const res = await memberApi.getInfo()
        if (res.code === 200 && res.data) {
          this.memberInfo = {
            ...this.memberInfo,
            ...res.data,
            checkedInToday: !!res.data.checkedInToday
          }
        }
      } catch (e) {
        console.error(e)
        uni.showToast({ title: '会员信息加载失败', icon: 'none' })
      }
    },
    async doCheckIn() {
      if (this.memberInfo.checkedInToday) {
        uni.showToast({ title: '\u4eca\u65e5\u5df2\u7b7e\u5230', icon: 'none' })
        return
      }
      try {
        const res = await memberApi.checkIn()
        if (res.code === 200) {
          uni.showToast({
            title: res.message || `\u7b7e\u5230\u6210\u529f\uff0c\u79ef\u5206 +${this.dailyCheckInPoints}`,
            icon: 'success'
          })
          await this.loadMemberInfo()
        }
      } catch (e) {
        console.error(e)
        if (String((e && e.message) || '').includes('\u5df2\u7ecf\u7b7e\u5230')) {
          await this.loadMemberInfo()
        }
      }
    },
    goCoupon() {
      uni.navigateTo({ url: '/pages/coupon/coupon' })
    },
    goOrders() {
      uni.navigateTo({ url: '/pages/orders/orders' })
    },
    goPaymentRequests() {
      uni.navigateTo({ url: '/pages/payment-requests/payment-requests' })
    }
  }
}
</script>

<style>
page {
  background:
    radial-gradient(circle at top right, rgba(251, 146, 60, 0.2), transparent 26%),
    linear-gradient(180deg, #fff7ed 0%, #fffbf5 48%, #ffffff 100%);
  min-height: 100vh;
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.container {
  min-height: 100vh;
  padding: 24rpx;
  padding-bottom: 48rpx;
}

.hero-card,
.panel {
  border-radius: 30rpx;
  overflow: hidden;
  box-shadow: 0 16rpx 32rpx rgba(194, 65, 12, 0.1);
}

.hero-card {
  padding: 30rpx;
  background: linear-gradient(145deg, #f97316 0%, #ea580c 55%, #9a3412 100%);
  color: #fff;
}

.hero-top {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.level-badge {
  width: 84rpx;
  height: 84rpx;
  border-radius: 24rpx;
  background: rgba(255, 237, 213, 0.96);
  color: #9a3412;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.hero-text {
  flex: 1;
  min-width: 0;
}

.hero-title {
  display: block;
  font-size: 38rpx;
  font-weight: 700;
}

.hero-subtitle {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #ffedd5;
}

.check-in-btn {
  min-width: 170rpx;
  height: 74rpx;
  line-height: 74rpx;
  margin: 0;
  border: none;
  border-radius: 18rpx;
  background: #fff;
  color: #c2410c;
  font-size: 26rpx;
  font-weight: 700;
  box-shadow: 0 10rpx 22rpx rgba(15, 23, 42, 0.14);
}

.check-in-btn[disabled] {
  background: rgba(255, 255, 255, 0.72);
  color: rgba(194, 65, 12, 0.72);
  box-shadow: none;
}

.check-in-btn::after {
  border: none;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
  margin-top: 28rpx;
}

.stat-item {
  padding: 24rpx;
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(6rpx);
}

.stat-label {
  display: block;
  font-size: 24rpx;
  color: #ffedd5;
}

.stat-value {
  display: block;
  margin-top: 12rpx;
  font-size: 42rpx;
  font-weight: 700;
  color: #fff;
}

.progress-box {
  margin-top: 28rpx;
}

.progress-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 14rpx;
}

.progress-title,
.progress-meta {
  font-size: 24rpx;
}

.progress-title {
  color: #fff7ed;
  font-weight: 600;
}

.progress-meta {
  color: #fed7aa;
}

.progress-track {
  width: 100%;
  height: 18rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.22);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #fde68a 0%, #fff7ed 100%);
}

.panel {
  margin-top: 22rpx;
  padding: 26rpx;
  background: rgba(255, 255, 255, 0.96);
  border: 1rpx solid #fed7aa;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #7c2d12;
}

.benefit-list,
.rule-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
  margin-top: 20rpx;
}

.benefit-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 22rpx;
  border-radius: 22rpx;
  background: linear-gradient(135deg, #fff7ed 0%, #fffbeb 100%);
}

.benefit-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  background: linear-gradient(135deg, #fdba74 0%, #f97316 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.benefit-body {
  flex: 1;
}

.benefit-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #9a3412;
}

.benefit-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #7c2d12;
  line-height: 1.5;
}

.rule-item {
  padding: 20rpx 22rpx;
  border-radius: 20rpx;
  background: #fff7ed;
  font-size: 26rpx;
  color: #7c2d12;
  line-height: 1.7;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16rpx;
  margin-top: 20rpx;
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  min-height: 176rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #fff7ed 0%, #fffbeb 100%);
}

.quick-item:active {
  transform: scale(0.98);
}

.quick-icon {
  width: 60rpx;
  height: 60rpx;
  border-radius: 18rpx;
  background: #f97316;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
}

.quick-name {
  font-size: 26rpx;
  color: #7c2d12;
  font-weight: 600;
}
</style>
