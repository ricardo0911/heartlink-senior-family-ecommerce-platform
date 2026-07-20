<template>
  <view class="page">
    <view class="hero-card">
      <view class="hero-top">
        <view>
          <text class="hero-eyebrow">健康异常预警中心</text>
          <text class="hero-title">{{ summary.displayName || '家人' }}</text>
        </view>
        <view class="risk-chip" :class="getRiskClass(summary.riskLevel)">
          {{ getRiskText(summary.riskLevel) }}
        </view>
      </view>

      <text class="hero-summary">
        {{ loading ? '正在分析当前照护风险，请稍候...' : (summary.summary || '当前暂无明显异常预警，建议继续保持提醒和档案更新。') }}
      </text>

      <view class="stats-row" v-if="summary.stats">
        <view class="stat-pill high">高优 {{ summary.stats.high || 0 }}</view>
        <view class="stat-pill medium">中优 {{ summary.stats.medium || 0 }}</view>
        <view class="stat-pill low">低优 {{ summary.stats.low || 0 }}</view>
      </view>
    </view>

    <view class="alert-list" v-if="!loading && summary.alerts && summary.alerts.length > 0">
      <view class="alert-card" v-for="(item, index) in summary.alerts" :key="`${item.title}-${index}`">
        <view class="alert-card-top">
          <text class="level-badge" :class="getLevelClass(item.level)">{{ getLevelText(item.level) }}</text>
          <text class="alert-index">{{ index + 1 }}</text>
        </view>
        <text class="alert-title">{{ item.title }}</text>
        <text class="alert-desc">{{ item.description }}</text>
        <view class="alert-footer">
          <text class="alert-hint">建议尽快跟进</text>
          <view class="action-btn" @click.stop="handleAlertAction(item)">
            {{ item.actionLabel || '去处理' }}
          </view>
        </view>
      </view>
    </view>

    <view class="empty-card" v-else-if="!loading">
      <text class="empty-icon">✓</text>
      <text class="empty-title">当前没有明显异常预警</text>
      <text class="empty-desc">可以继续关注周报、提醒和商品反馈，保持照护节奏。</text>
      <view class="empty-btn" @click="goReport">查看健康周报</view>
    </view>
  </view>
</template>

<script>
import { profileApi } from '../../api/index.js'

export default {
  data() {
    return {
      parentId: '',
      loading: false,
      summary: {
        displayName: '',
        riskLevel: 'STABLE',
        stats: {
          high: 0,
          medium: 0,
          low: 0
        },
        alerts: [],
        summary: ''
      }
    }
  },
  onLoad(options) {
    this.parentId = options.parentId || ''
    this.loadAlerts()
  },
  methods: {
    async loadAlerts() {
      if (!this.parentId) {
        uni.showToast({ title: '缺少长辈信息', icon: 'none' })
        return
      }
      this.loading = true
      try {
        const res = await profileApi.getHealthAlerts(this.parentId)
        this.summary = {
          displayName: res?.data?.displayName || '家人',
          riskLevel: res?.data?.riskLevel || 'STABLE',
          stats: res?.data?.stats || { high: 0, medium: 0, low: 0 },
          alerts: Array.isArray(res?.data?.alerts)
            ? res.data.alerts.map((item) => ({
                ...item,
                actionType: item?.actionType === 'REMINDER' ? 'REPORT' : item?.actionType,
                actionLabel: item?.actionType === 'REMINDER' ? '查看周报' : item?.actionLabel
              }))
            : [],
          summary: res?.data?.summary || ''
        }
      } catch (e) {
        console.warn('health alerts unavailable', e)
        this.summary = {
          displayName: '家人',
          riskLevel: 'STABLE',
          stats: { high: 0, medium: 0, low: 0 },
          alerts: [],
          summary: '健康预警分析暂时不可用，可先查看周报与健康档案。'
        }
      } finally {
        this.loading = false
      }
    },
    getRiskText(level) {
      const map = {
        HIGH: '高风险',
        MEDIUM: '中风险',
        LOW: '低风险',
        STABLE: '平稳'
      }
      return map[level] || '平稳'
    },
    getRiskClass(level) {
      const map = {
        HIGH: 'is-high',
        MEDIUM: 'is-medium',
        LOW: 'is-low',
        STABLE: 'is-stable'
      }
      return map[level] || 'is-stable'
    },
    getLevelText(level) {
      const map = {
        HIGH: '高优先级',
        MEDIUM: '中优先级',
        LOW: '低优先级'
      }
      return map[level] || '待跟进'
    },
    getLevelClass(level) {
      const map = {
        HIGH: 'is-high',
        MEDIUM: 'is-medium',
        LOW: 'is-low'
      }
      return map[level] || 'is-low'
    },
    handleAlertAction(item) {
      const actionType = item?.actionType
      if (actionType === 'PROFILE') {
        uni.navigateTo({ url: `/pages/profile/profile?parentId=${this.parentId}` })
        return
      }
      if (actionType === 'REPORT') {
        this.goReport()
        return
      }
      if (actionType === 'ORDER') {
        uni.navigateTo({ url: '/pages/orders/orders' })
        return
      }
      if (actionType === 'SHELF') {
        uni.switchTab({ url: '/pages/shelf/shelf' })
        return
      }
      this.goReport()
    },
    goReport() {
      uni.navigateTo({ url: `/pages/report/report?parentId=${this.parentId}` })
    }
  }
}
</script>

<style>
page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eef5ff 0%, #f7fbff 48%, #ffffff 100%);
}

.page {
  min-height: 100vh;
  padding: 24rpx;
  box-sizing: border-box;
}

.hero-card {
  background: linear-gradient(135deg, #0f4fbf 0%, #2563eb 52%, #38bdf8 100%);
  border-radius: 32rpx;
  padding: 30rpx;
  color: #ffffff;
  box-shadow: 0 18rpx 42rpx rgba(37, 99, 235, 0.24);
}

.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24rpx;
}

.hero-eyebrow {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.76);
  letter-spacing: 2rpx;
}

.hero-title {
  display: block;
  margin-top: 12rpx;
  font-size: 44rpx;
  font-weight: 700;
}

.risk-chip {
  min-width: 132rpx;
  text-align: center;
  padding: 14rpx 18rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.16);
}

.risk-chip.is-high {
  background: rgba(239, 68, 68, 0.2);
}

.risk-chip.is-medium {
  background: rgba(245, 158, 11, 0.2);
}

.risk-chip.is-low,
.risk-chip.is-stable {
  background: rgba(15, 118, 110, 0.2);
}

.hero-summary {
  display: block;
  margin-top: 24rpx;
  font-size: 28rpx;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.92);
}

.stats-row {
  display: flex;
  gap: 16rpx;
  flex-wrap: wrap;
  margin-top: 24rpx;
}

.stat-pill {
  padding: 12rpx 18rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 600;
}

.stat-pill.high {
  background: rgba(239, 68, 68, 0.18);
}

.stat-pill.medium {
  background: rgba(245, 158, 11, 0.18);
}

.stat-pill.low {
  background: rgba(255, 255, 255, 0.16);
}

.alert-list {
  margin-top: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.alert-card {
  background: #ffffff;
  border-radius: 28rpx;
  padding: 28rpx;
  border: 1rpx solid #dbeafe;
  box-shadow: 0 10rpx 26rpx rgba(12, 74, 110, 0.08);
}

.alert-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.level-badge {
  padding: 10rpx 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 700;
}

.level-badge.is-high {
  background: #fee2e2;
  color: #b91c1c;
}

.level-badge.is-medium {
  background: #fef3c7;
  color: #b45309;
}

.level-badge.is-low {
  background: #dbeafe;
  color: #1d4ed8;
}

.alert-index {
  font-size: 24rpx;
  color: #94a3b8;
}

.alert-title {
  display: block;
  margin-top: 18rpx;
  font-size: 34rpx;
  font-weight: 700;
  color: #0f172a;
}

.alert-desc {
  display: block;
  margin-top: 14rpx;
  font-size: 28rpx;
  line-height: 1.7;
  color: #475569;
}

.alert-footer {
  margin-top: 24rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.alert-hint {
  font-size: 24rpx;
  color: #64748b;
}

.action-btn,
.empty-btn {
  min-width: 188rpx;
  height: 78rpx;
  line-height: 78rpx;
  text-align: center;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #0a84ff 0%, #2563eb 100%);
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 700;
}

.empty-card {
  margin-top: 24rpx;
  background: #ffffff;
  border-radius: 28rpx;
  padding: 56rpx 32rpx;
  text-align: center;
  border: 1rpx solid #dbeafe;
  box-shadow: 0 10rpx 26rpx rgba(12, 74, 110, 0.06);
}

.empty-icon {
  font-size: 72rpx;
  color: #16a34a;
}

.empty-title {
  display: block;
  margin-top: 20rpx;
  font-size: 34rpx;
  font-weight: 700;
  color: #0f172a;
}

.empty-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 28rpx;
  line-height: 1.7;
  color: #64748b;
}

.empty-btn {
  margin: 26rpx auto 0;
}
</style>
