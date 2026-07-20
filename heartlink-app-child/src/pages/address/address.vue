<template>
  <view class="container">
    <view class="header">
      <text class="title">地址管理</text>
    </view>

    <!-- 地址列表 -->
    <view class="address-list" v-if="addressList.length > 0">
      <view class="address-card" v-for="item in addressList" :key="item.id">
        <view class="address-main" @click="goEdit(item.id)">
          <view class="address-top">
            <text class="receiver-name">{{ item.receiverName }}</text>
            <text class="receiver-phone">{{ item.receiverPhone }}</text>
            <view class="default-tag" v-if="item.isDefault">默认</view>
          </view>
          <text class="address-detail">{{ item.province }}{{ item.city }}{{ item.district }}{{ item.detailAddress }}</text>
        </view>
        <view class="address-actions">
          <view class="action-btn" @click="setDefault(item.id)" v-if="!item.isDefault">
            <text class="action-text">设为默认</text>
          </view>
          <view class="action-btn" @click="goEdit(item.id)">
            <text class="action-text">编辑</text>
          </view>
          <view class="action-btn delete" @click="deleteAddress(item.id)">
            <text class="action-text delete-text">删除</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 空状态 -->
    <view class="empty" v-else>
      <view class="empty-icon-wrap"><text class="empty-icon-text">址</text></view>
      <text class="empty-text">暂无收货地址</text>
    </view>

    <!-- 添加地址按钮 -->
    <view class="bottom-bar">
      <button class="btn-add" @click="goEdit()">+ 新增收货地址</button>
    </view>
  </view>
</template>

<script>
import { addressApi } from '../../api/index.js'

export default {
  data() {
    return {
      addressList: []
    }
  },
  onShow() {
    this.loadAddresses()
  },
  methods: {
    async loadAddresses() {
      try {
        const res = await addressApi.list()
        this.addressList = res.data || []
      } catch (e) {
        console.error(e)
      }
    },
    goEdit(id) {
      let url = '/pages/address/edit'
      if (id) {
        url += `?id=${id}`
      }
      uni.navigateTo({ url })
    },
    async setDefault(id) {
      try {
        await addressApi.setDefault(id)
        uni.showToast({ title: '设置成功', icon: 'success' })
        this.loadAddresses()
      } catch (e) {
        console.error(e)
      }
    },
    deleteAddress(id) {
      uni.showModal({
        title: '提示',
        content: '确定删除该地址吗？',
        success: async (res) => {
          if (res.confirm) {
            try {
              await addressApi.delete(id)
              uni.showToast({ title: '删除成功', icon: 'success' })
              this.loadAddresses()
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
page {
  background: linear-gradient(180deg, #f0f9ff 0%, #e9f5ff 45%, #f7fbff 100%);
  min-height: 100vh;
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.container {
  min-height: 100vh;
  background: transparent;
  padding-bottom: 160rpx;
  animation: fadeUp 0.4s ease both;
}

@keyframes fadeUp {
  from { opacity: 0; transform: translateY(24rpx); }
  to   { opacity: 1; transform: translateY(0); }
}

@media (prefers-reduced-motion: reduce) {
  .container { animation: none; }
}

.header {
  background: linear-gradient(135deg, #0369a1 0%, #0ea5e9 50%, #38bdf8 100%);
  padding: 60rpx 30rpx;
}

.title {
  font-size: 40rpx;
  font-weight: 600;
  color: #fff;
}

.address-list {
  padding: 20rpx;
}

.address-card {
  background: #fff;
  border-radius: 20rpx;
  margin-bottom: 20rpx;
  overflow: hidden;
  border: 1rpx solid #dbeafe;
  box-shadow: 0 8rpx 22rpx rgba(12, 74, 110, 0.08);
}

.address-main {
  padding: 28rpx;
}

.address-top {
  display: flex;
  align-items: center;
  margin-bottom: 12rpx;
}

.receiver-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #0f172a;
  margin-right: 20rpx;
}

.receiver-phone {
  font-size: 28rpx;
  color: #475569;
}

.default-tag {
  background: linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%);
  color: #fff;
  font-size: 20rpx;
  padding: 4rpx 14rpx;
  border-radius: 16rpx;
  margin-left: 16rpx;
}

.address-detail {
  font-size: 26rpx;
  color: #64748b;
  line-height: 1.5;
}

.address-actions {
  display: flex;
  justify-content: flex-end;
  padding: 16rpx 28rpx;
  border-top: 1rpx solid #f5f5f5;
  gap: 24rpx;
}

.action-btn {
  padding: 8rpx 0;
}

.action-text {
  font-size: 26rpx;
  color: #475569;
}

.delete-text {
  color: #0369a1;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 100rpx 0;
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
  font-size: 28rpx;
  color: #64748b;
  margin-top: 20rpx;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20rpx 30rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.05);
}

.btn-add {
  background: linear-gradient(140deg, #0369a1 0%, #0ea5e9 100%);
  box-shadow: 0 10rpx 22rpx rgba(3, 105, 161, 0.2);
  color: #fff;
  border: none;
  border-radius: 50rpx;
  height: 90rpx;
  line-height: 90rpx;
  font-size: 30rpx;
  font-weight: 600;
}
</style>
