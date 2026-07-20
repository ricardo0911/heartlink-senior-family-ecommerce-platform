<template>
  <view class="container">
    <!-- 已登录状态 -->
    <view class="user-card is-link" v-if="isLoggedIn" @click="goAccountProfile">
      <view class="avatar-wrap">
        <image class="avatar" :src="userInfo?.avatar || '/static/default-avatar.png'" mode="aspectFill"></image>
      </view>
      <view class="user-info">
        <text class="nickname">{{ normalizeDisplayText(userInfo?.nickname, '推荐官') }}</text>
      </view>
      <view class="card-actions">
        <view class="role identity-tag">子女版</view>
        <text class="edit-entry">编辑资料 ></text>
      </view>
    </view>

    <!-- 未登录状态 -->
    <view class="user-card login-card" v-else @click="goLogin">
      <view class="avatar-wrap">
        <image class="avatar" src="/static/default-avatar.png" mode="aspectFill"></image>
      </view>
      <view class="user-info">
        <text class="nickname">点击登录</text>
      </view>
      <view class="role identity-tag">子女版</view>
      <text class="login-arrow">›</text>
    </view>

    <!-- 会员积分卡 -->
    <view class="member-card" v-if="isLoggedIn" @click="goMemberPage">
      <view class="member-left">
        <view class="member-level">
          <view class="level-icon">{{ ['Lv0','Lv1','Lv2','Lv3'][memberInfo.level] || 'Lv0' }}</view>
          <view class="level-texts">
            <text class="level-name">{{ normalizeDisplayText(memberInfo.levelName, '银卡会员') }}</text>
            <text class="level-hint">每次购物都可累计积分</text>
          </view>
        </view>
        <view class="member-points">
          <text class="points-label">会员积分</text>
          <text class="points-value">{{ memberInfo.points || 0 }}</text>
        </view>
      </view>
      <view class="member-right">
        <view class="discount-tag" v-if="memberInfo.level > 0">
          <text class="discount-text">{{ normalizeDisplayText(memberInfo.discount, '95折') }}</text>
        </view>
        <button
          class="check-in-btn"
          :disabled="!!memberInfo.checkedInToday"
          @click.stop="doCheckIn"
        >
          {{ memberInfo.checkedInToday ? '今日已签到' : '每日签到' }}
        </button>
      </view>
    </view>

    <view class="menu-list">
      <view class="menu-item" @click="goOrders">
        <view class="menu-left">
          <view class="menu-icon">📦</view>
          <text class="menu-text">我的订单</text>
        </view>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goFavorites">
        <view class="menu-left">
          <view class="menu-icon">❤</view>
          <text class="menu-text">我的收藏</text>
        </view>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item highlight" @click="goPaymentRequests">
        <view class="menu-left">
          <view class="menu-icon">💳</view>
          <text class="menu-text">代付请求</text>
        </view>
        <view class="menu-right-row">
          <view class="badge" v-if="pendingPayCount > 0">
            <text class="badge-text">{{ pendingPayCount }}</text>
          </view>
          <text class="menu-arrow">›</text>
        </view>
      </view>
      <view class="menu-item" @click="goBind">
        <view class="menu-left">
          <view class="menu-icon">👪</view>
          <text class="menu-text">绑定长辈</text>
        </view>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goProfile">
        <view class="menu-left">
          <view class="menu-icon">📋</view>
          <text class="menu-text">健康档案</text>
        </view>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goAddress">
        <view class="menu-left">
          <view class="menu-icon">📍</view>
          <text class="menu-text">地址管理</text>
        </view>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goCoupon">
        <view class="menu-left">
          <view class="menu-icon">🎫</view>
          <text class="menu-text">优惠券</text>
        </view>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goChat" v-if="parentList.length > 0">
        <view class="menu-left">
          <view class="menu-icon">💬</view>
          <text class="menu-text">消息</text>
        </view>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 只有登录后才显示退出登录按钮 -->
    <button class="btn-logout" v-if="isLoggedIn" @click="logout">退出登录</button>
  </view>
</template>

<script>
import { authApi, familyApi, paymentRequestApi, memberApi, messageApi } from '../../api/index.js'

export default {
  data() {
    return {
      userInfo: null,
      parentId: null,
      parentList: [],
      isLoggedIn: false,
      pendingPayCount: 0,
      memberInfo: {
        points: 0,
        level: 0,
        levelName: '银卡会员',
        discount: '95折',
        nextLevelPoints: 1000,
        nextLevelName: '银卡会员',
        checkedInToday: false
      }
    }
  },
  onShow() {
    this.checkLoginStatus()
  },
  methods: {
    async checkLoginStatus() {
      const token = uni.getStorageSync('token')
      if (token) {
        this.isLoggedIn = true
        this.loadUserInfo()
        this.loadParent()
        this.loadPendingPayCount()
        this.loadMemberInfo()
      } else {
        this.isLoggedIn = false
        this.userInfo = null
        this.parentId = null
        this.pendingPayCount = 0
      }
    },
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
    getLevelIcon(level) {
      const icons = ['🥉', '🥈', '🥇', '🏆']
      return icons[level] || icons[0]
    },
    normalizeDisplayText(value, fallback = '') {
      const text = String(value || '').trim()
      if (!text) return fallback
      if (/[�]|[閳閺鐎娴缁闂鍞鍝濞鏉鎴妫╃偛绺鹃柅鑳发]/.test(text)) return fallback || text
      return text
    },
    async doCheckIn() {
      if (this.memberInfo.checkedInToday) {
        uni.showToast({ title: '今日已签到', icon: 'none' })
        return
      }
      try {
        const res = await memberApi.checkIn()
        if (res.code === 200) {
          uni.showToast({
            title: res.message || '签到成功，积分 +10',
            icon: 'success'
          })
          await this.loadMemberInfo()
        }
      } catch (e) {
        console.error(e)
        if (String((e && e.message) || '').includes('已经签到')) {
          await this.loadMemberInfo()
        }
      }
    },
    goMemberPage() {
      if (!this.isLoggedIn) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      uni.navigateTo({ url: '/pages/member/member' })
    },
    goAccountProfile() {
      if (!this.isLoggedIn) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      uni.navigateTo({ url: '/pages/account/account' })
    },
    async loadUserInfo() {
      try {
        const res = await authApi.getCurrentUser()
        if (res.code === 200 && res.data) {
          this.userInfo = res.data
        } else {
          // Token 无效，清除登录状态
          this.isLoggedIn = false
          uni.removeStorageSync('token')
        }
      } catch (e) {
        console.error(e)
        this.isLoggedIn = false
      }
    },
    async loadParent() {
      try {
        const res = await familyApi.getMyParents()
        if (res.code === 200 && res.data && res.data.length > 0) {
          this.parentList = res.data
          this.parentId = res.data[0].parent.id
        }
      } catch (e) {
        console.error(e)
      }
    },
    async loadPendingPayCount() {
      try {
        const res = await paymentRequestApi.getPendingCount()
        if (res.code === 200 && res.data) {
          this.pendingPayCount = res.data.count || 0
        }
      } catch (e) {
        console.error(e)
        this.pendingPayCount = 0
      }
    },
    goLogin() {
      uni.navigateTo({ url: '/pages/login/login' })
    },
    goOrders() {
      if (!this.isLoggedIn) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      uni.navigateTo({ url: '/pages/orders/orders' })
    },
    goFavorites() {
      if (!this.isLoggedIn) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      if (this.parentId) {
        uni.navigateTo({ url: `/pages/favorites/favorites?parentId=${this.parentId}` })
      } else {
        uni.navigateTo({ url: '/pages/favorites/favorites' })
      }
    },
    goPaymentRequests() {
      if (!this.isLoggedIn) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      uni.navigateTo({ url: '/pages/payment-requests/payment-requests' })
    },
    goBind() {
      if (!this.isLoggedIn) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      uni.navigateTo({ url: '/pages/bind/bind' })
    },
    goProfile() {
      if (!this.isLoggedIn) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      if (this.parentId) {
        uni.navigateTo({ url: `/pages/profile/profile?parentId=${this.parentId}` })
      } else {
        uni.showToast({ title: '请先绑定长辈', icon: 'none' })
      }
    },
    goAddress() {
      if (!this.isLoggedIn) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      uni.navigateTo({ url: '/pages/address/address' })
    },
    goCoupon() {
      if (!this.isLoggedIn) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      uni.navigateTo({ url: '/pages/coupon/coupon' })
    },
    goChat() {
      if (!this.isLoggedIn) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      if (this.parentList.length > 0) {
        const first = this.parentList[0]
        const avatar = encodeURIComponent(first.parent.avatar || '/static/default-avatar.png')
        uni.navigateTo({
          url: `/pages/chat/chat?familyBindId=${first.bind.id}&parentId=${first.parent.id}&relation=${first.bind.relation}&avatar=${avatar}`
        })
      }
    },
    logout() {
      uni.showModal({
        title: '确认退出',
        content: '退出后将返回登录页，是否继续？',
        success: (res) => {
          if (res.confirm) {
            uni.removeStorageSync('token')
            uni.removeStorageSync('userInfo')
            this.isLoggedIn = false
            this.userInfo = null
            uni.showToast({ title: '已退出登录', icon: 'success' })
          }
        }
      })
    }
  }
}
</script>

<style>
page {
  background: linear-gradient(180deg, #dbeafe 0%, #eaf3ff 52%, #f2f8ff 100%);
  min-height: 100vh;
  color: #102a43;
  font-family: 'SF\ Pro\ Text',\ 'SF\ Pro\ Display',\ 'PingFang\ SC',\ 'Segoe\ UI',\ 'Microsoft\ YaHei',\ sans-serif;
}

.container {
  padding: 24rpx;
  padding-bottom: 120rpx;
}

.user-card {
  background: #eaf3ff;
  border: 1rpx solid #bfd8ff;
  border-radius: 28rpx;
  padding: 28rpx;
  display: flex;
  align-items: center;
  margin-bottom: 20rpx;
  box-shadow: 0 8rpx 22rpx rgba(12, 74, 110, 0.08);
  animation: fade-up 320ms ease-out both;
}

.user-card.is-link:active {
  background: #dfeeff;
}

.avatar-wrap {
  width: 132rpx;
  height: 132rpx;
  border-radius: 50%;
  background: linear-gradient(140deg, #93c5fd 0%, var(--hl-primary-3) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar {
  width: 124rpx;
  height: 124rpx;
  border-radius: 50%;
  border: 4rpx solid #ffffff;
}

.user-info {
  margin-left: 22rpx;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.nickname {
  font-size: 42rpx;
  font-weight: 700;
  color: #102a43;
}

.card-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10rpx;
}

.role {
  background: #dbeafe;
  color: #1d4ed8;
  border-radius: 999rpx;
  padding: 10rpx 20rpx;
  font-size: 24rpx;
  font-weight: 600;
}

.identity-tag {
  background: #dbeafe;
  color: #1d4ed8;
  border-radius: 999rpx;
  padding: 10rpx 20rpx;
  font-size: 24rpx;
  font-weight: 600;
}

.edit-entry {
  font-size: 24rpx;
  color: #5f7fa3;
  font-weight: 600;
}

.login-card {
  cursor: pointer;
}

.login-arrow {
  font-size: 40rpx;
  color: #6b85a0;
  margin-left: 8rpx;
}

.member-card {
  background: linear-gradient(140deg, #f97316 0%, #ea580c 58%, #c2410c 100%);
  border-radius: 28rpx;
  padding: 30rpx;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20rpx;
  box-shadow: 0 16rpx 36rpx rgba(194, 65, 12, 0.26);
  cursor: pointer;
  animation: fade-up 380ms ease-out both;
}

.member-card:active {
  transform: translateY(2rpx);
}

.member-left {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.member-level {
  display: flex;
  align-items: flex-start;
}

.level-icon {
  width: 74rpx;
  height: 74rpx;
  border-radius: 20rpx;
  background: #ffedd5;
  color: #9a3412;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.level-texts {
  margin-left: 14rpx;
  display: flex;
  flex-direction: column;
}

.level-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
}

.level-hint {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #ffedd5;
}

.member-points {
  display: flex;
  flex-direction: column;
  margin-top: 20rpx;
}

.points-label {
  font-size: 24rpx;
  color: #ffedd5;
}

.points-value {
  font-size: 52rpx;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.2;
}

.member-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 14rpx;
  margin-left: 14rpx;
}

.discount-tag {
  background: #fed7aa;
  padding: 8rpx 18rpx;
  border-radius: 20rpx;
}

.discount-text {
  font-size: 24rpx;
  color: #9a3412;
  font-weight: 600;
}

.check-in-btn {
  min-width: 176rpx;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 16rpx;
  border: none;
  background: #ffffff;
  color: #c2410c;
  font-size: 28rpx;
  font-weight: 600;
  padding: 0 24rpx;
  box-shadow: 0 8rpx 18rpx rgba(15, 23, 42, 0.12);
}

.check-in-btn[disabled] {
  background: rgba(255, 255, 255, 0.72);
  color: rgba(194, 65, 12, 0.72);
  box-shadow: none;
}

.menu-list {
  background: #f7fbff;
  border: 1rpx solid #bfd8ff;
  border-radius: 26rpx;
  overflow: hidden;
  box-shadow: 0 8rpx 22rpx rgba(12, 74, 110, 0.08);
  margin-bottom: 20rpx;
  animation: fade-up 440ms ease-out both;
}

.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 26rpx 24rpx;
  border-bottom: 1rpx solid #d7e7fb;
  min-height: 96rpx;
  box-sizing: border-box;
  cursor: pointer;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-item:active {
  background: #eaf3ff;
}

.menu-item.highlight {
  background: linear-gradient(135deg, #eaf3ff 0%, #dbeafe 100%);
}

.menu-left {
  display: flex;
  align-items: center;
}

.menu-icon {
  width: 54rpx;
  height: 54rpx;
  border-radius: 14rpx;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 24rpx;
  font-weight: 700;
  margin-right: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.menu-text {
  font-size: 31rpx;
  color: #102a43;
  font-weight: 600;
}

.menu-arrow {
  font-size: 34rpx;
  color: #6b85a0;
}

.menu-right-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.badge {
  background: linear-gradient(135deg, var(--hl-primary-2) 0%, var(--hl-primary) 100%);
  min-width: 44rpx;
  height: 44rpx;
  border-radius: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 12rpx;
  box-shadow: 0 4rpx 12rpx rgba(3, 105, 161, 0.3);
}

.badge-text {
  font-size: 24rpx;
  color: #fff;
  font-weight: 600;
}

.btn-logout {
  margin: 40rpx 0 60rpx;
  background: linear-gradient(140deg, var(--hl-primary) 0%, var(--hl-primary-2) 100%);
  color: #ffffff;
  border: none;
  border-radius: 50rpx;
  height: 90rpx;
  line-height: 90rpx;
  font-size: 30rpx;
  font-weight: 600;
  box-shadow: 0 10rpx 22rpx rgba(3, 105, 161, 0.2);
}

button::after {
  border: none;
}

@keyframes fade-up {
  from {
    opacity: 0;
    transform: translateY(10rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .user-card,
  .member-card,
  .menu-list {
    animation: none;
  }
}
</style>



