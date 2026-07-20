<template>
  <view class="container">
    <view class="header">
      <text class="title">{{ isEdit ? '编辑地址' : '新增地址' }}</text>
    </view>

    <view class="form">
      <view class="form-item">
        <text class="form-label">收货人</text>
        <input class="form-input" v-model="form.receiverName" placeholder="请输入收货人姓名" />
      </view>
      <view class="form-item">
        <text class="form-label">联系电话</text>
        <input class="form-input" v-model="form.receiverPhone" placeholder="请输入手机号" type="number" maxlength="11" />
      </view>
      <view class="form-item">
        <text class="form-label">省份</text>
        <input class="form-input" v-model="form.province" placeholder="请输入省份" />
      </view>
      <view class="form-item">
        <text class="form-label">城市</text>
        <input class="form-input" v-model="form.city" placeholder="请输入城市" />
      </view>
      <view class="form-item">
        <text class="form-label">区/县</text>
        <input class="form-input" v-model="form.district" placeholder="请输入区/县" />
      </view>
      <view class="form-item">
        <text class="form-label">详细地址</text>
        <input class="form-input" v-model="form.detailAddress" placeholder="请输入详细地址" />
      </view>
      <view class="form-item switch-item">
        <text class="form-label">设为默认地址</text>
        <switch :checked="form.isDefault" @change="form.isDefault = $event.detail.value" color="#0369a1" />
      </view>
    </view>

    <button class="btn-save" @click="saveAddress">保存地址</button>
  </view>
</template>

<script>
import { addressApi } from '../../api/index.js'

export default {
  data() {
    return {
      isEdit: false,
      addressId: null,
      form: {
        receiverName: '',
        receiverPhone: '',
        province: '',
        city: '',
        district: '',
        detailAddress: '',
        isDefault: false
      }
    }
  },
  onLoad(options) {
    if (options.id) {
      this.isEdit = true
      this.addressId = options.id
      this.loadAddress()
    }
  },
  methods: {
    normalizeIsDefault(value) {
      return value === true || value === 1 || value === '1' || String(value).toLowerCase() === 'true'
    },
    async loadAddress() {
      try {
        const res = await addressApi.list()
        const list = res.data || []
        const addr = list.find(a => String(a.id) === String(this.addressId))
        if (addr) {
          this.form = {
            receiverName: addr.receiverName || '',
            receiverPhone: addr.receiverPhone || '',
            province: addr.province || '',
            city: addr.city || '',
            district: addr.district || '',
            detailAddress: addr.detailAddress || '',
            isDefault: this.normalizeIsDefault(addr.isDefault)
          }
        }
      } catch (e) {
        console.error(e)
      }
    },
    async saveAddress() {
      if (!this.form.receiverName) {
        uni.showToast({ title: '请输入收货人', icon: 'none' })
        return
      }
      if (!this.form.receiverPhone) {
        uni.showToast({ title: '请输入联系电话', icon: 'none' })
        return
      }
      if (!this.form.detailAddress) {
        uni.showToast({ title: '请输入详细地址', icon: 'none' })
        return
      }
      try {
        const data = {
          ...this.form,
          isDefault: this.form.isDefault ? 1 : 0
        }
        if (this.addressId) {
          data.id = this.addressId
        }
        await addressApi.save(data)
        uni.showToast({ title: '保存成功', icon: 'success' })
        setTimeout(() => {
          uni.navigateBack()
        }, 1000)
      } catch (e) {
        console.error(e)
      }
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

.form {
  background: #fff;
  margin: 20rpx;
  border-radius: 20rpx;
  overflow: hidden;
  border: 1rpx solid #dbeafe;
  box-shadow: 0 8rpx 22rpx rgba(12, 74, 110, 0.08);
}

.form-item {
  display: flex;
  align-items: center;
  padding: 28rpx 30rpx;
  border-bottom: 1rpx solid #f5f5f5;
}

.form-item:last-child {
  border-bottom: none;
}

.form-label {
  font-size: 28rpx;
  color: #0f172a;
  width: 160rpx;
  flex-shrink: 0;
}

.form-input {
  flex: 1;
  font-size: 28rpx;
  color: #0f172a;
}

.switch-item {
  justify-content: space-between;
}

.btn-save {
  margin: 40rpx 20rpx;
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
