<template>
  <div class="hl-dashboard">
    <section class="hl-page-header">
      <div>
        <h1 class="hl-page-title">经营总览</h1>
        <p class="hl-page-subtitle">统一查看经营收益、库存、退款与代付处理情况，便于快速定位异常。</p>
      </div>
      <el-space>
        <el-tag type="info" effect="plain">{{ currentDateLabel }}</el-tag>
        <el-button type="primary" :icon="RefreshRight" :loading="loading" @click="loadDashboard">
          刷新数据
        </el-button>
      </el-space>
    </section>

    <el-alert
      class="hl-summary-tip"
      title="经营收益按已完成口径统计"
      description="已付款、已发货但未完成的订单不会计入净经营收益，相关金额会显示在待结算卡片中。"
      type="info"
      :closable="false"
      show-icon
    />

    <el-row :gutter="16" class="hl-stat-row">
      <el-col v-for="item in statCards" :key="item.key" :xs="24" :sm="12" :lg="8" :xl="6">
        <el-card shadow="hover" class="hl-stat-card-wrap">
          <div class="hl-stat-card">
            <div class="hl-stat-icon" :class="item.iconClass">
              <el-icon :size="24">
                <component :is="item.icon" />
              </el-icon>
            </div>
            <div class="hl-stat-info">
              <div class="hl-stat-value">{{ item.value }}</div>
              <div class="hl-stat-label">{{ item.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="16">
        <el-card shadow="hover" class="hl-bento-card">
          <template #header>
            <div class="hl-card-header-main">
              <div>
                <div class="hl-card-title">经营趋势</div>
                <div class="hl-card-subtitle">按天查看净收益、第三方抽成、自营毛利与退款冲回变化</div>
              </div>
              <el-radio-group v-model="trendDays" @change="loadSalesTrend">
                <el-radio-button :value="7">近 7 天</el-radio-button>
                <el-radio-button :value="30">近 30 天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <v-chart :option="salesTrendOption" autoresize style="height: 320px" />
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card shadow="hover" class="hl-bento-card">
          <template #header>
            <div class="hl-card-header-main">
              <div>
                <div class="hl-card-title">运营提醒</div>
                <div class="hl-card-subtitle">优先处理退款、代付和库存不足事项</div>
              </div>
            </div>
          </template>

          <div v-if="alertItems.length > 0" class="hl-stock-list">
            <el-alert
              v-for="item in alertItems"
              :key="item.key"
              :title="item.title"
              :description="item.description"
              :type="item.type"
              :closable="false"
              show-icon
            />
          </div>
          <el-empty v-else description="暂无待处理提醒" :image-size="72" />

          <div class="hl-card-subtitle hl-inventory-title">库存预警</div>
          <div v-if="inventoryAlerts.length > 0" class="hl-stock-list">
            <div v-for="item in inventoryAlerts" :key="item.id" class="hl-stock-item">
              <div class="hl-stock-info">
                <div class="hl-stock-name">{{ item.name }}</div>
                <div class="hl-stock-meta">销量 {{ formatNumber(item.sales) }} · {{ item.suggestion }}</div>
              </div>
              <div class="hl-stock-side">
                <div class="hl-stock-value" :class="`is-${String(item.level || '').toLowerCase()}`">
                  库存 {{ formatNumber(item.stock) }}
                </div>
                <el-tag :type="item.level === 'CRITICAL' ? 'danger' : 'warning'" effect="light">
                  {{ item.levelLabel || (item.level === 'CRITICAL' ? '告急' : '预警') }}
                </el-tag>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无库存预警" :image-size="72" />

          <div class="hl-card-subtitle hl-inventory-title">最新订单</div>
          <div v-if="recentOrders.length > 0">
            <div v-for="item in recentOrders" :key="item.id" class="hl-order-item">
              <div class="hl-order-info">
                <div class="hl-order-no">{{ item.orderNo || `#${item.id}` }}</div>
                <div class="hl-order-product">{{ item.productName || '商品订单' }}</div>
              </div>
              <div class="hl-order-meta">
                <span class="hl-order-amount">¥{{ formatAmount(item.totalAmount) }}</span>
                <el-tag :type="orderStatusTypeMap[item.status] || 'info'" effect="light">
                  {{ orderStatusLabelMap[item.status] || item.status || '-' }}
                </el-tag>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无订单数据" :image-size="72" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import {
  Checked,
  Coin,
  Discount,
  Money,
  RefreshRight,
  RefreshLeft,
  Warning
} from '@element-plus/icons-vue'
import {
  getDashboardStats,
  getInventoryAlerts,
  getRecentOrders,
  getSalesTrend
} from '../../api'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent])

const loading = ref(false)
const trendDays = ref(7)
const stats = ref({})
const inventoryAlerts = ref([])
const recentOrders = ref([])
const salesTrendData = ref([])

const orderStatusLabelMap = {
  PENDING_PAY: '待付款',
  PAID: '已付款',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const orderStatusTypeMap = {
  PENDING_PAY: 'warning',
  PAID: 'primary',
  SHIPPED: 'info',
  COMPLETED: 'success',
  CANCELLED: 'danger'
}

const currentDateLabel = computed(() => {
  return new Date().toLocaleString('zh-CN', { hour12: false })
})

const statCards = computed(() => [
  {
    key: 'netBusinessIncome',
    label: '净经营收益',
    value: `¥${formatAmount(stats.value.netBusinessIncome)}`,
    icon: Coin,
    iconClass: 'hl-stat-blue'
  },
  {
    key: 'thirdPartyIncome',
    label: '第三方抽成',
    value: `¥${formatAmount(stats.value.thirdPartyIncome)}`,
    icon: Discount,
    iconClass: 'hl-stat-orange'
  },
  {
    key: 'selfOperatedSales',
    label: '自营销售额',
    value: `¥${formatAmount(stats.value.selfOperatedSales)}`,
    icon: Money,
    iconClass: 'hl-stat-green'
  },
  {
    key: 'selfOperatedGrossProfit',
    label: '自营毛利',
    value: `¥${formatAmount(stats.value.selfOperatedGrossProfit)}`,
    icon: Checked,
    iconClass: 'hl-stat-blue'
  },
  {
    key: 'refundReversalAmount',
    label: '退款冲回',
    value: `¥${formatAmount(stats.value.refundReversalAmount)}`,
    icon: RefreshLeft,
    iconClass: 'hl-stat-red'
  },
  {
    key: 'pendingSettlementOrderCount',
    label: '待结算订单',
    value: formatNumber(stats.value.pendingSettlementOrderCount),
    icon: Checked,
    iconClass: 'hl-stat-blue'
  },
  {
    key: 'pendingSettlementAmount',
    label: '待结算金额',
    value: `¥${formatAmount(stats.value.pendingSettlementAmount)}`,
    icon: Money,
    iconClass: 'hl-stat-orange'
  },
  {
    key: 'pendingRefundCount',
    label: '待处理退款',
    value: formatNumber(stats.value.pendingRefundCount),
    icon: RefreshLeft,
    iconClass: 'hl-stat-orange'
  },
  {
    key: 'pendingPaymentRequestCount',
    label: '待处理代付',
    value: formatNumber(stats.value.pendingPaymentRequestCount),
    icon: Warning,
    iconClass: 'hl-stat-blue'
  },
  {
    key: 'lowStockCount',
    label: '库存预警',
    value: formatNumber(stats.value.lowStockCount),
    icon: Warning,
    iconClass: 'hl-stat-red'
  }
])

const alertItems = computed(() => {
  const items = []

  if ((stats.value.pendingSettlementOrderCount || 0) > 0) {
    items.push({
      key: 'pending-settlement',
      type: 'info',
      title: `有 ${formatNumber(stats.value.pendingSettlementOrderCount)} 笔订单待结算`,
      description: `这些订单已经购买，但要等签收完成后才会计入经营收益，当前待结算金额 ¥${formatAmount(stats.value.pendingSettlementAmount)}。`
    })
  }

  if ((stats.value.pendingRefundCount || 0) > 0) {
    items.push({
      key: 'refund',
      type: 'warning',
      title: `有 ${formatNumber(stats.value.pendingRefundCount)} 笔退款待处理`,
      description: '建议优先核对订单状态并及时处理退款请求，避免售后链路堆积。'
    })
  }

  if ((stats.value.pendingPaymentRequestCount || 0) > 0) {
    items.push({
      key: 'payment-request',
      type: 'info',
      title: `有 ${formatNumber(stats.value.pendingPaymentRequestCount)} 条代付申请待处理`,
      description: '长辈端已发起代付，建议尽快确认处理，减少照护链路阻塞。'
    })
  }

  if ((stats.value.lowStockCount || 0) > 0) {
    items.push({
      key: 'inventory',
      type: 'error',
      title: `${formatNumber(stats.value.lowStockCount)} 个商品库存偏低`,
      description: '库存不足会直接影响下单转化，建议优先安排补货或下架处理。'
    })
  }

  if ((stats.value.missingCostOrderCount || 0) > 0) {
    items.push({
      key: 'missing-cost',
      type: 'warning',
      title: `${formatNumber(stats.value.missingCostOrderCount)} 笔自营订单缺采购成本`,
      description: '这些订单只会计入销售额，不会计入毛利，建议补齐采购价快照。'
    })
  }

  if ((stats.value.missingTaxRateOrderCount || 0) > 0) {
    items.push({
      key: 'missing-tax',
      type: 'error',
      title: `${formatNumber(stats.value.missingTaxRateOrderCount)} 笔第三方订单缺税率`,
      description: '缺税率会导致抽成收益缺口，建议补齐供应商默认税率或商品覆盖税率。'
    })
  }

  if ((stats.value.activeReminderCount || 0) > 0) {
    items.push({
      key: 'reminder',
      type: 'success',
      title: `${formatNumber(stats.value.activeReminderCount)} 个健康提醒正在运行`,
      description: '提醒服务处于活跃状态，可持续关注是否存在代付或复购转化机会。'
    })
  }

  return items
})

const salesTrendOption = computed(() => ({
  color: ['#0A84FF', '#FF9F0A', '#30D158', '#FF453A'],
  tooltip: {
    trigger: 'axis'
  },
  legend: {
    bottom: 0
  },
  grid: {
    left: 24,
    right: 24,
    top: 24,
    bottom: 44,
    containLabel: true
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: salesTrendData.value.map((item) => item.date)
  },
  yAxis: {
    type: 'value',
    name: '金额'
  },
  series: [
    {
      name: '净经营收益',
      type: 'line',
      smooth: true,
      areaStyle: {
        opacity: 0.12
      },
      data: salesTrendData.value.map((item) => Number(item.netBusinessIncome || 0))
    },
    {
      name: '第三方抽成',
      type: 'line',
      smooth: true,
      data: salesTrendData.value.map((item) => Number(item.thirdPartyIncome || 0))
    },
    {
      name: '自营毛利',
      type: 'line',
      smooth: true,
      data: salesTrendData.value.map((item) => Number(item.selfOperatedGrossProfit || 0))
    },
    {
      name: '退款冲回',
      type: 'line',
      smooth: true,
      data: salesTrendData.value.map((item) => Number(item.refundReversalAmount || 0))
    }
  ]
}))

const formatNumber = (value) => {
  const num = Number(value || 0)
  return Number.isFinite(num) ? num.toLocaleString('zh-CN') : '0'
}

const formatAmount = (value) => {
  const num = Number(value || 0)
  return Number.isFinite(num) ? num.toFixed(2) : '0.00'
}

const loadSalesTrend = async () => {
  try {
    const res = await getSalesTrend(trendDays.value)
    salesTrendData.value = res.data || []
  } catch {
    salesTrendData.value = []
  }
}

const loadDashboard = async () => {
  loading.value = true
  try {
    const [statsRes, recentOrdersRes, inventoryRes] = await Promise.allSettled([
      getDashboardStats(),
      getRecentOrders(),
      getInventoryAlerts()
    ])

    stats.value = statsRes.status === 'fulfilled' ? (statsRes.value.data || {}) : {}
    recentOrders.value = recentOrdersRes.status === 'fulfilled' ? (recentOrdersRes.value.data || []) : []
    inventoryAlerts.value = inventoryRes.status === 'fulfilled' ? (inventoryRes.value.data || []) : []
    await loadSalesTrend()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDashboard()
})
</script>

<style scoped>
.hl-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.hl-summary-tip {
  margin-bottom: 16px;
}
</style>
