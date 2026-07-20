<template>
  <view class="container">
    <view class="header">
      <text class="title">绑定家人</text>
      <text class="subtitle">生成亲情码，让长辈扫码绑定</text>
    </view>
    
    <!-- 生成二维码区域 -->
    <view class="code-section">
      <view class="code-card">
        <view class="code-box" v-if="bindCode">
          <text class="code-label">亲情码</text>
          <text class="code-number">{{ bindCode }}</text>
          <view class="qr-wrap">
            <canvas canvas-id="bindQrCanvas" id="bindQrCanvas" class="qr-canvas"></canvas>
          </view>
          <text class="qr-tip">长辈端可直接扫码绑定，也可手动输入亲情码</text>
          <text class="code-expire">{{ expireMinutes }}分钟内有效</text>
        </view>
        <view class="code-placeholder" v-else>
          <view class="placeholder-icon-box">绑</view>
          <text class="placeholder-text">点击下方按钮生成亲情码</text>
        </view>
      </view>
      
      <!-- 关系选择 -->
      <view class="relation-section">
        <text class="section-title">选择关系称呼</text>
        <view class="relation-tags">
          <view 
            class="relation-tag" 
            v-for="r in relations" 
            :key="r"
            :class="{ active: relation === r }"
            @click="relation = r"
          >
            {{ r }}
          </view>
        </view>
      </view>
      
      <button class="btn-generate" @click="generateCode">
        {{ bindCode ? '重新生成' : '生成亲情码' }}
      </button>
    </view>
    
    <!-- 使用说明 -->
    <view class="tips-section">
      <text class="tips-title">使用说明</text>
      <view class="tip-item">
        <text class="tip-num">1</text>
        <text class="tip-text">点击生成亲情码</text>
      </view>
      <view class="tip-item">
        <text class="tip-num">2</text>
        <text class="tip-text">让长辈打开「连心选·长辈版」小程序</text>
      </view>
      <view class="tip-item">
        <text class="tip-num">3</text>
        <text class="tip-text">长辈在"我的"页面扫码二维码，或输入亲情码完成绑定</text>
      </view>
    </view>
    
    <!-- 已绑定的长辈 -->
    <view class="bound-section" v-if="boundList.length > 0">
      <text class="section-title">已绑定的家人</text>
      <view class="bound-list">
        <view class="bound-item" v-for="item in boundList" :key="item.bind.id">
          <image class="bound-avatar" :src="item.parent.avatar || '/static/default-avatar.png'" mode="aspectFill"></image>
          <view class="bound-info">
            <text class="bound-name">{{ item.parent.nickname }}</text>
            <text class="bound-relation">{{ item.bind.relation }}</text>
          </view>
          <text class="bound-status">已绑定</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { familyApi } from '../../api/index.js'
import QRCode from '../../utils/qrcode/index.js'
import QRErrorCorrectLevel from '../../utils/qrcode/QRErrorCorrectLevel.js'

export default {
  data() {
    return {
      bindCode: '',
      expireMinutes: 30,
      relation: '妈妈',
      relations: ['妈妈', '爸爸', '奶奶', '爷爷', '姥姥', '姥爷'],
      boundList: []
    }
  },
  onLoad() {
    this.loadBoundList()
  },
  methods: {
    buildQrPayload() {
      return `HEARTLINK_BIND:${this.bindCode}`
    },
    drawBindQrCode() {
      if (!this.bindCode) return

      const qr = new QRCode(0, QRErrorCorrectLevel.M)
      qr.addData(this.buildQrPayload())
      qr.make()

      const moduleCount = qr.getModuleCount()
      const canvasSize = 280
      const padding = 16
      const cellSize = (canvasSize - padding * 2) / moduleCount
      const ctx = uni.createCanvasContext('bindQrCanvas', this)

      ctx.setFillStyle('#FFFFFF')
      ctx.fillRect(0, 0, canvasSize, canvasSize)

      for (let row = 0; row < moduleCount; row++) {
        for (let col = 0; col < moduleCount; col++) {
          ctx.setFillStyle(qr.isDark(row, col) ? '#000000' : '#FFFFFF')
          ctx.fillRect(
            padding + col * cellSize,
            padding + row * cellSize,
            cellSize,
            cellSize
          )
        }
      }
      ctx.draw()
    },
    async generateCode() {
      try {
        const res = await familyApi.generateCode(this.relation)
        this.bindCode = res.data.bindCode
        this.expireMinutes = res.data.expireMinutes
        this.$nextTick(() => {
          this.drawBindQrCode()
        })
        uni.showToast({ title: '生成成功', icon: 'success' })
      } catch (e) {
        console.error(e)
      }
    },
    async loadBoundList() {
      try {
        const res = await familyApi.getMyParents()
        this.boundList = res.data || []
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
}

.header {
  background: linear-gradient(135deg, #0369a1 0%, #0ea5e9 50%, #38bdf8 100%);
  padding: 60rpx 30rpx;
  text-align: center;
}

.title {
  display: block;
  font-size: 40rpx;
  font-weight: 600;
  color: #fff;
}

.subtitle {
  display: block;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 12rpx;
}

.code-section {
  background: #fff;
  margin: -30rpx 30rpx 30rpx;
  border-radius: 24rpx;
  padding: 40rpx;
  box-shadow: 0 8rpx 22rpx rgba(12, 74, 110, 0.08);
  border: 1rpx solid #dbeafe;
}

.code-card {
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border-radius: 20rpx;
  padding: 50rpx;
  text-align: center;
}

.code-box {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.code-label {
  font-size: 26rpx;
  color: #64748b;
}

.code-number {
  font-size: 80rpx;
  font-weight: 700;
  color: #0369a1;
  letter-spacing: 20rpx;
  margin: 20rpx 0;
}

.code-expire {
  font-size: 24rpx;
  color: #64748b;
}

.qr-wrap {
  margin: 10rpx 0 16rpx;
  width: 280rpx;
  height: 280rpx;
  background: #fff;
  border-radius: 12rpx;
  padding: 0;
}

.qr-canvas {
  width: 280rpx;
  height: 280rpx;
}

.qr-tip {
  font-size: 24rpx;
  color: #475569;
  text-align: center;
  margin-bottom: 10rpx;
}

.code-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 0;
}

 .placeholder-icon-box {
  width: 120rpx;
  height: 120rpx;
  background: linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 52rpx;
  font-weight: 700;
}

.placeholder-text {
  font-size: 28rpx;
  color: #64748b;
  margin-top: 20rpx;
}

.relation-section {
  margin-top: 40rpx;
}

.section-title {
  font-size: 28rpx;
  color: #0f172a;
  font-weight: 500;
  display: block;
  margin-bottom: 20rpx;
}

.relation-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.relation-tag {
  padding: 16rpx 32rpx;
  background: #f1f5f9;
  border-radius: 30rpx;
  font-size: 26rpx;
  color: #475569;
}

.relation-tag.active {
  background: linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%);
  color: #fff;
}

.btn-generate {
  background: linear-gradient(140deg, #0369a1 0%, #0ea5e9 100%);
  box-shadow: 0 10rpx 22rpx rgba(3, 105, 161, 0.2);
  color: #fff;
  border: none;
  border-radius: 50rpx;
  height: 100rpx;
  line-height: 100rpx;
  font-size: 32rpx;
  margin-top: 40rpx;
}

.tips-section {
  background: #fff;
  margin: 0 30rpx;
  border-radius: 20rpx;
  padding: 30rpx;
  border: 1rpx solid #dbeafe;
  box-shadow: 0 8rpx 22rpx rgba(12, 74, 110, 0.08);
}

.tips-title {
  font-size: 28rpx;
  color: #0f172a;
  font-weight: 500;
  display: block;
  margin-bottom: 20rpx;
}

.tip-item {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
}

.tip-num {
  width: 40rpx;
  height: 40rpx;
  background: #0369a1;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  margin-right: 20rpx;
}

.tip-text {
  font-size: 26rpx;
  color: #475569;
}

.bound-section {
  background: #fff;
  margin: 30rpx;
  border-radius: 20rpx;
  padding: 30rpx;
  border: 1rpx solid #dbeafe;
  box-shadow: 0 8rpx 22rpx rgba(12, 74, 110, 0.08);
}

.bound-list {
  margin-top: 16rpx;
}

.bound-item {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f0f0;
}

.bound-item:last-child {
  border-bottom: none;
}

.bound-avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
}

.bound-info {
  flex: 1;
  margin-left: 20rpx;
}

.bound-name {
  font-size: 28rpx;
  color: #0f172a;
  display: block;
}

.bound-relation {
  font-size: 24rpx;
  color: #64748b;
}

.bound-status {
  font-size: 24rpx;
  color: #0369a1;
}

/* Animation */
@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(30rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.code-section,
.tips-section,
.bound-section {
  animation: fadeUp 0.6s ease-out both;
}

.tips-section {
  animation-delay: 0.1s;
}

.bound-section {
  animation-delay: 0.2s;
}

@media (prefers-reduced-motion: reduce) {
  .code-section,
  .tips-section,
  .bound-section {
    animation: none;
  }
}
</style>
