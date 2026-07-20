<template>
  <view v-if="visible" class="logistics-mask" @tap="close">
    <view class="logistics-panel" @tap.stop>
      <view class="panel-header">
        <text class="panel-title">{{ title }}</text>
        <text class="panel-close" @click="close">关闭</text>
      </view>

      <view v-if="loading" class="loading-box">
        <text class="loading-text">正在同步最新物流...</text>
      </view>

      <view v-else class="panel-body">
        <view class="summary-card">
          <text class="summary-company">{{ summary.expressCompanyName || '暂未识别快递公司' }}</text>
          <text class="summary-tracking">运单号：{{ summary.trackingNo || '--' }}</text>
          <view class="status-row">
            <text class="status-label">当前状态</text>
            <text class="status-value">{{ summary.logisticsStatusText || '待物流更新' }}</text>
          </view>
          <text class="summary-last">{{ summary.logisticsLastTrace || '已记录运单，等待快递公司更新轨迹。' }}</text>
          <text v-if="summary.logisticsUpdatedAt" class="summary-updated">
            最近更新：{{ summary.logisticsUpdatedAt }}
          </text>
        </view>

        <view v-if="summary.message" class="message-card">
          <text class="message-text">{{ summary.message }}</text>
        </view>

        <view class="trace-head">
          <text class="trace-title">物流轨迹</text>
          <text class="trace-subtitle">{{ traceList.length ? `${traceList.length} 条记录` : '暂无轨迹' }}</text>
        </view>

        <scroll-view scroll-y class="trace-scroll">
          <view v-if="traceList.length">
            <view
              v-for="(item, index) in traceList"
              :key="`${item.acceptTime || index}-${item.acceptStation || index}`"
              class="trace-item"
            >
              <view class="trace-axis">
                <view class="trace-dot" :class="{ active: index === 0 }"></view>
                <view v-if="index !== traceList.length - 1" class="trace-line"></view>
              </view>
              <view class="trace-content">
                <text class="trace-station">{{ item.acceptStation || '物流状态更新中' }}</text>
                <text class="trace-time">{{ item.acceptTime || '--' }}</text>
                <text v-if="item.location" class="trace-location">{{ item.location }}</text>
              </view>
            </view>
          </view>
          <view v-else class="trace-empty">
            <text class="trace-empty-text">暂未返回物流轨迹，可稍后再试。</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    loading: {
      type: Boolean,
      default: false
    },
    logistics: {
      type: Object,
      default: () => ({})
    },
    title: {
      type: String,
      default: '物流详情'
    }
  },
  computed: {
    summary() {
      return this.logistics || {}
    },
    traceList() {
      return Array.isArray(this.summary.traces) ? this.summary.traces : []
    }
  },
  methods: {
    close() {
      this.$emit('close')
    }
  }
}
</script>

<style>
.logistics-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(15, 23, 42, 0.48);
  display: flex;
  align-items: flex-end;
}

.logistics-panel {
  width: 100%;
  max-height: 82vh;
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 28rpx 20rpx;
  border-bottom: 1rpx solid #edf2f7;
}

.panel-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #1f2937;
}

.panel-close {
  font-size: 24rpx;
  color: #64748b;
}

.loading-box {
  padding: 80rpx 32rpx;
  text-align: center;
}

.loading-text {
  font-size: 28rpx;
  color: #64748b;
}

.panel-body {
  padding: 24rpx 24rpx 30rpx;
}

.summary-card,
.message-card {
  border-radius: 22rpx;
  padding: 22rpx 24rpx;
}

.summary-card {
  background: linear-gradient(135deg, #eff6ff 0%, #f8fbff 100%);
}

.message-card {
  margin-top: 16rpx;
  background: #fff7ed;
}

.summary-company,
.summary-tracking,
.summary-last,
.summary-updated,
.message-text,
.trace-title,
.trace-subtitle,
.trace-station,
.trace-time,
.trace-location,
.trace-empty-text,
.status-label,
.status-value {
  display: block;
}

.summary-company {
  font-size: 30rpx;
  font-weight: 700;
  color: #1d4ed8;
}

.summary-tracking,
.summary-last,
.summary-updated,
.message-text {
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #5b6778;
}

.status-row {
  margin-top: 14rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.status-label {
  font-size: 24rpx;
  color: #64748b;
}

.status-value {
  font-size: 26rpx;
  font-weight: 700;
  color: #2563eb;
}

.trace-head {
  margin-top: 22rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.trace-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #1e293b;
}

.trace-subtitle {
  font-size: 22rpx;
  color: #94a3b8;
}

.trace-scroll {
  max-height: 620rpx;
  margin-top: 16rpx;
}

.trace-item {
  display: flex;
  gap: 18rpx;
}

.trace-axis {
  width: 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.trace-dot {
  width: 18rpx;
  height: 18rpx;
  border-radius: 50%;
  background: #cbd5e1;
}

.trace-dot.active {
  background: #2563eb;
  box-shadow: 0 0 0 8rpx rgba(37, 99, 235, 0.12);
}

.trace-line {
  flex: 1;
  width: 2rpx;
  margin-top: 8rpx;
  background: #dbeafe;
}

.trace-content {
  flex: 1;
  padding-bottom: 24rpx;
}

.trace-station {
  font-size: 26rpx;
  line-height: 1.6;
  color: #1f2937;
}

.trace-time,
.trace-location {
  margin-top: 8rpx;
  font-size: 22rpx;
  line-height: 1.5;
  color: #64748b;
}

.trace-empty {
  padding: 48rpx 0 64rpx;
  text-align: center;
}

.trace-empty-text {
  font-size: 24rpx;
  color: #94a3b8;
}
</style>
