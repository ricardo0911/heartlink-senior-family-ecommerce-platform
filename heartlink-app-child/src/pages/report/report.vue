<template>
  <view class="container">
    <view class="header">
      <text class="title">健康周报</text>
    </view>

    <!-- 周报卡片 -->
    <view class="report-card" v-if="report">
      <view class="report-header">
        <view class="report-icon-box">报</view>
        <view class="report-title-wrap">
          <text class="report-title">{{ report.title || '本周健康报告' }}</text>
          <text class="report-period">{{ report.period || '' }}</text>
        </view>
      </view>

      <!-- 活动概要 -->
      <view class="summary-section" v-if="report.summary">
        <text class="summary-title">活动概要</text>
        <text class="summary-content">{{ report.summary }}</text>
      </view>

      <!-- 统计数据 -->
      <view class="stats-grid" v-if="report.stats && report.stats.length > 0">
        <view class="stat-item" v-for="(stat, index) in report.stats" :key="index">
          <text class="stat-value">{{ stat.value }}</text>
          <text class="stat-label">{{ stat.label }}</text>
        </view>
      </view>

      <!-- 健康建议 -->
      <view class="advice-section" v-if="report.advice && report.advice.length > 0">
        <text class="advice-title">健康建议</text>
        <view class="advice-item" v-for="(item, index) in report.advice" :key="index">
          <text class="advice-dot">{{ index + 1 }}</text>
          <text class="advice-text">{{ item }}</text>
        </view>
      </view>

      <!-- 鼓励语 -->
      <view class="encourage-section" v-if="report.encourage">
        <text class="encourage-text">{{ report.encourage }}</text>
      </view>
    </view>

    <!-- 加载中 -->
    <view class="loading" v-else-if="loading">
      <text class="loading-text">正在生成周报...</text>
    </view>

    <!-- 空状态 -->
    <view class="empty" v-else>
      <view class="empty-icon-box">报</view>
      <text class="empty-text">暂无周报数据</text>
    </view>
  </view>
</template>

<script>
import { reportApi } from '../../api/index.js'

export default {
  data() {
    return {
      parentId: '',
      report: null,
      loading: false
    }
  },
  onLoad(options) {
    this.parentId = options.parentId || ''
    this.loadReport()
  },
  methods: {
    async loadReport() {
      if (!this.parentId) {
        uni.showToast({ title: '缺少长辈信息', icon: 'none' })
        return
      }
      this.loading = true
      try {
        const res = await reportApi.weekly(this.parentId)
        this.report = this.normalizeReport(res.data)
      } catch (e) {
        console.error(e)
      } finally {
        this.loading = false
      }
    },
    normalizeReport(payload) {
      if (!payload) return null
      if (typeof payload === 'string') {
        return {
          title: '本周健康周报',
          period: '',
          summary: payload,
          stats: [],
          advice: [],
          encourage: ''
        }
      }
      return {
        title: payload.title || '本周健康周报',
        period: payload.period || '',
        summary: payload.summary || payload.report || '',
        stats: Array.isArray(payload.stats) ? payload.stats : [],
        advice: Array.isArray(payload.advice) ? payload.advice : [],
        encourage: payload.encourage || ''
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
}

.title {
  font-size: 40rpx;
  font-weight: 600;
  color: #fff;
}

.report-card {
  background: #fff;
  margin: 20rpx;
  border-radius: 24rpx;
  overflow: hidden;
  border: 1rpx solid #dbeafe;
  box-shadow: 0 8rpx 22rpx rgba(12, 74, 110, 0.08);
  animation: fadeUp 0.6s ease-out;
}

.report-header {
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  padding: 36rpx 32rpx;
  display: flex;
  align-items: center;
}

.report-icon-box {
  width: 80rpx;
  height: 80rpx;
  background: linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%);
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 36rpx;
  font-weight: 700;
  margin-right: 20rpx;
}

.report-title-wrap {
  display: flex;
  flex-direction: column;
}

.report-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #0f172a;
}

.report-period {
  font-size: 24rpx;
  color: #64748b;
  margin-top: 4rpx;
}

.summary-section {
  padding: 32rpx;
  border-bottom: 1rpx solid #f5f5f5;
}

.summary-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #0f172a;
  display: block;
  margin-bottom: 16rpx;
}

.summary-content {
  font-size: 28rpx;
  color: #475569;
  line-height: 1.8;
}

.stats-grid {
  display: flex;
  flex-wrap: wrap;
  padding: 24rpx 16rpx;
  border-bottom: 1rpx solid #f5f5f5;
}

.stat-item {
  width: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20rpx 0;
}

.stat-value {
  font-size: 44rpx;
  font-weight: 700;
  color: #0369a1;
}

.stat-label {
  font-size: 24rpx;
  color: #64748b;
  margin-top: 8rpx;
}

.advice-section {
  padding: 32rpx;
  border-bottom: 1rpx solid #f5f5f5;
}

.advice-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #0f172a;
  display: block;
  margin-bottom: 20rpx;
}

.advice-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: 16rpx;
}

.advice-dot {
  width: 40rpx;
  height: 40rpx;
  background: linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%);
  color: #fff;
  font-size: 22rpx;
  font-weight: 600;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: 16rpx;
  margin-top: 4rpx;
}

.advice-text {
  font-size: 28rpx;
  color: #475569;
  line-height: 1.6;
  flex: 1;
}

.encourage-section {
  padding: 32rpx;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
}

.encourage-text {
  font-size: 28rpx;
  color: #0c4a6e;
  line-height: 1.6;
  text-align: center;
}

.loading {
  display: flex;
  justify-content: center;
  padding: 100rpx 0;
}

.loading-text {
  font-size: 28rpx;
  color: #64748b;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 100rpx 0;
  animation: fadeUp 0.6s ease-out;
}

.empty-icon-box {
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

.empty-text {
  font-size: 28rpx;
  color: #64748b;
  margin-top: 20rpx;
}

@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(20rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .report-card,
  .empty {
    animation: none;
  }
}
</style>
