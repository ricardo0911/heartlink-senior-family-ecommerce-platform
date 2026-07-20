<template>
  <div class="hl-settlement-page">
    <section class="hl-page-header">
      <div>
        <h1 class="hl-page-title">经营结算</h1>
        <p class="hl-page-subtitle">
          统一查看第三方抽成、自营销售与毛利、退款冲回，以及订单级结算明细。
        </p>
      </div>
      <el-space wrap>
        <el-tag type="info" effect="plain">{{ rangeSummary }}</el-tag>
        <el-button type="primary" :icon="RefreshRight" :loading="loading" @click="loadAll">
          刷新数据
        </el-button>
      </el-space>
    </section>

    <el-card shadow="hover" class="hl-search-card">
      <el-form :inline="true" class="hl-form hl-filter-form">
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            unlink-panels
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="货品类型">
          <el-select v-model="filters.sourceType" clearable placeholder="全部类型" style="width: 148px">
            <el-option label="全部" value="" />
            <el-option label="自营" value="SELF_OPERATED" />
            <el-option label="第三方" value="THIRD_PARTY" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-select
            v-model="filters.supplierId"
            clearable
            :disabled="filters.sourceType === 'SELF_OPERATED'"
            :placeholder="filters.sourceType === 'SELF_OPERATED' ? '自营无需供应商' : '全部供应商'"
            style="width: 180px"
          >
            <el-option v-for="item in suppliers" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" clearable placeholder="订单号 / 商品名称" style="width: 220px" />
        </el-form-item>
        <el-form-item label="仅退款冲回">
          <el-switch v-model="filters.refundOnly" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16" class="hl-stat-row">
      <el-col v-for="item in overviewCards" :key="item.key" :xs="24" :sm="12" :lg="8" :xl="4">
        <el-card shadow="hover" class="hl-stat-card-wrap">
          <div class="hl-stat-card">
            <div class="hl-stat-icon" :class="item.iconClass">
              <el-icon :size="22">
                <component :is="item.icon" />
              </el-icon>
            </div>
            <div class="hl-stat-info">
              <div class="hl-stat-value">{{ item.value }}</div>
              <div class="hl-stat-label">{{ item.label }}</div>
              <div class="hl-stat-sub">{{ item.subtext }}</div>
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
                <div class="hl-card-subtitle">按周期查看净经营收益、第三方抽成、自营毛利与退款冲回</div>
              </div>
              <el-radio-group v-model="groupBy" @change="handleGroupByChange">
                <el-radio-button value="day">按天</el-radio-button>
                <el-radio-button value="month">按月</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <v-chart :option="trendOption" autoresize style="height: 340px" />
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card shadow="hover" class="hl-bento-card">
          <template #header>
            <div class="hl-card-header-main">
              <div>
                <div class="hl-card-title">数据提醒</div>
                <div class="hl-card-subtitle">优先处理缺成本、缺税率和退款冲回异常</div>
              </div>
            </div>
          </template>

          <div v-if="warningItems.length > 0" class="hl-warning-list">
            <el-alert
              v-for="item in warningItems"
              :key="item.key"
              :title="item.title"
              :description="item.description"
              :type="item.type"
              :closable="false"
              show-icon
            />
          </div>
          <el-empty v-else description="当前区间暂无结算提醒" :image-size="72" />

          <div class="hl-side-meta">
            <div class="hl-side-meta-row">
              <span>订单数</span>
              <strong>{{ formatNumber(overview.orderCount) }}</strong>
            </div>
            <div class="hl-side-meta-row">
              <span>净经营收益</span>
              <strong>¥{{ formatAmount(overview.netBusinessIncome) }}</strong>
            </div>
            <div class="hl-side-meta-row">
              <span>退款冲回</span>
              <strong>¥{{ formatAmount(overview.refundReversalAmount) }}</strong>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" class="hl-table-card">
      <template #header>
        <div class="hl-card-header-main">
          <div>
            <div class="hl-card-title">订单级明细</div>
            <div class="hl-card-subtitle">按订单完成时间与退款完成时间分别计入口径，历史单据支持快照回填标记</div>
          </div>
        </div>
      </template>

      <el-table :data="orderRows" v-loading="loading" stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="170" />
        <el-table-column label="商品 / 来源" min-width="220">
          <template #default="{ row }">
            <div class="hl-order-main">
              <div class="hl-order-product">{{ row.productName || '-' }}</div>
              <div class="hl-order-sub">
                <el-tag size="small" effect="light" :type="row.sourceType === 'SELF_OPERATED' ? 'success' : 'warning'">
                  {{ row.sourceTypeLabel || '-' }}
                </el-tag>
                <span>{{ row.supplierName || (row.sourceType === 'SELF_OPERATED' ? '平台自营' : '-') }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="结算参数" min-width="220">
          <template #default="{ row }">
            <div class="hl-order-main">
              <div v-if="row.sourceType === 'SELF_OPERATED'">
                成本 {{ row.purchasePrice != null ? `¥${formatAmount(row.purchasePrice)}` : '缺失' }}
              </div>
              <div v-else>
                税率 {{ formatTaxRate(row.taxRate) }}
              </div>
              <div class="hl-order-sub">
                <span v-if="row.sourceType === 'SELF_OPERATED'">自营按销售额/毛利统计</span>
                <span v-else>{{ taxSourceLabel(row.taxSource) }}</span>
                <el-tag v-if="row.historicalBackfill" size="small" type="info" effect="plain">历史回填</el-tag>
                <el-tag v-if="row.missingCostData || row.missingTaxRateData" size="small" type="danger" effect="plain">
                  {{ row.missingCostData ? '缺成本' : '缺税率' }}
                </el-tag>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="订单金额" width="120">
          <template #default="{ row }">¥{{ formatAmount(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="正向收益" width="130">
          <template #default="{ row }">¥{{ formatAmount(row.positiveIncome) }}</template>
        </el-table-column>
        <el-table-column label="退款冲回" width="130">
          <template #default="{ row }">
            <span :class="{ 'hl-negative': Number(row.reversalIncome) < 0 }">
              ¥{{ formatAmount(row.reversalIncome) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="净经营收益" width="140">
          <template #default="{ row }">
            <strong :class="{ 'hl-negative': Number(row.netIncome) < 0 }">¥{{ formatAmount(row.netIncome) }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="销售额" width="120">
          <template #default="{ row }">¥{{ formatAmount(row.netSales) }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="160">
          <template #default="{ row }">
            <div class="hl-order-main">
              <div>{{ statusLabel(row.status) }}</div>
              <div class="hl-order-sub">退款：{{ refundLabel(row.refundStatus) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="完成 / 退款时间" min-width="180">
          <template #default="{ row }">
            <div class="hl-order-main">
              <div>完成：{{ formatDateTime(row.completedAt) }}</div>
              <div class="hl-order-sub">退款：{{ formatDateTime(row.refundAt) }}</div>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && orderRows.length === 0"
        class="hl-empty"
        description="当前筛选条件下暂无结算明细"
      />

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        @size-change="loadOrders"
        @current-change="loadOrders"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { use } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { Coin, DataAnalysis, Discount, Money, RefreshRight, Warning } from '@element-plus/icons-vue'
import { getSettlementOrders, getSettlementOverview, getSettlementTrend, getSupplierList } from '../../api'

use([CanvasRenderer, LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent])

const loading = ref(false)
const suppliers = ref([])
const overview = ref({})
const trendData = ref([])
const orderRows = ref([])
const groupBy = ref('day')

const today = new Date()
const monthStart = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-01`
const monthEnd = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`

const dateRange = ref([monthStart, monthEnd])
const filters = reactive({
  sourceType: '',
  supplierId: null,
  keyword: '',
  refundOnly: false
})
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

watch(() => filters.sourceType, (value) => {
  if (value === 'SELF_OPERATED') {
    filters.supplierId = null
  }
})

const buildBaseParams = () => {
  const [startDate, endDate] = dateRange.value || []
  return {
    startDate: startDate || undefined,
    endDate: endDate || undefined,
    sourceType: filters.sourceType || undefined,
    supplierId: filters.sourceType === 'SELF_OPERATED' ? undefined : (filters.supplierId || undefined)
  }
}

const rangeSummary = computed(() => {
  const [startDate, endDate] = dateRange.value || []
  return startDate && endDate ? `${startDate} 至 ${endDate}` : '全部时间'
})

const overviewCards = computed(() => [
  {
    key: 'netBusinessIncome',
    label: '净经营收益',
    value: `¥${formatAmount(overview.value.netBusinessIncome)}`,
    subtext: '第三方抽成 + 自营毛利 - 退款冲回',
    icon: DataAnalysis,
    iconClass: 'hl-stat-blue'
  },
  {
    key: 'thirdPartyIncome',
    label: '第三方抽成',
    value: `¥${formatAmount(overview.value.thirdPartyIncome)}`,
    subtext: '订单实付金额 × 税率',
    icon: Discount,
    iconClass: 'hl-stat-orange'
  },
  {
    key: 'selfOperatedSales',
    label: '自营销售额',
    value: `¥${formatAmount(overview.value.selfOperatedSales)}`,
    subtext: '完成订单正向销售额',
    icon: Money,
    iconClass: 'hl-stat-green'
  },
  {
    key: 'selfOperatedGrossProfit',
    label: '自营毛利',
    value: `¥${formatAmount(overview.value.selfOperatedGrossProfit)}`,
    subtext: '订单实付金额 - 采购成本',
    icon: Coin,
    iconClass: 'hl-stat-blue'
  },
  {
    key: 'refundReversalAmount',
    label: '退款冲回',
    value: `¥${formatAmount(overview.value.refundReversalAmount)}`,
    subtext: '按退款完成时间记负向冲回',
    icon: RefreshRight,
    iconClass: 'hl-stat-red'
  },
  {
    key: 'warningCount',
    label: '数据缺口',
    value: formatNumber((overview.value.missingCostOrderCount || 0) + (overview.value.missingTaxRateOrderCount || 0)),
    subtext: `缺成本 ${formatNumber(overview.value.missingCostOrderCount)} / 缺税率 ${formatNumber(overview.value.missingTaxRateOrderCount)}`,
    icon: Warning,
    iconClass: 'hl-stat-red'
  }
])

const warningItems = computed(() => {
  const items = []
  if ((overview.value.missingCostOrderCount || 0) > 0) {
    items.push({
      key: 'missing-cost',
      type: 'warning',
      title: `有 ${formatNumber(overview.value.missingCostOrderCount)} 笔自营订单缺采购成本`,
      description: '这些订单会计入销售额，但不会纳入毛利累计，建议尽快补齐采购价快照。'
    })
  }
  if ((overview.value.missingTaxRateOrderCount || 0) > 0) {
    items.push({
      key: 'missing-tax',
      type: 'error',
      title: `有 ${formatNumber(overview.value.missingTaxRateOrderCount)} 笔第三方订单缺税率`,
      description: '这些订单无法正确计算抽成收益，需补齐供应商默认税率或商品自定义税率。'
    })
  }
  if ((overview.value.refundReversalAmount || 0) > 0) {
    items.push({
      key: 'refund',
      type: 'info',
      title: `当前区间退款冲回 ¥${formatAmount(overview.value.refundReversalAmount)}`,
      description: '跨月退款不会改写历史月，而是在退款完成当月做负向冲回。'
    })
  }
  return items
})

const trendOption = computed(() => ({
  color: ['#0A84FF', '#FF9F0A', '#30D158', '#FF453A'],
  tooltip: { trigger: 'axis' },
  legend: { bottom: 0 },
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
    data: trendData.value.map((item) => item.date)
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
      areaStyle: { opacity: 0.1 },
      data: trendData.value.map((item) => Number(item.netBusinessIncome || 0))
    },
    {
      name: '第三方抽成',
      type: 'line',
      smooth: true,
      data: trendData.value.map((item) => Number(item.thirdPartyIncome || 0))
    },
    {
      name: '自营毛利',
      type: 'line',
      smooth: true,
      data: trendData.value.map((item) => Number(item.selfOperatedGrossProfit || 0))
    },
    {
      name: '退款冲回',
      type: 'bar',
      barMaxWidth: 24,
      data: trendData.value.map((item) => Number(item.refundReversalAmount || 0))
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

const formatTaxRate = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const rate = Number(value)
  return Number.isFinite(rate) ? `${rate.toFixed(2)}%` : '-'
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

const statusLabel = (value) => ({
  PENDING_PAY: '待付款',
  PAID: '已付款',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}[value] || value || '-')

const refundLabel = (value) => ({
  REQUESTED: '待处理',
  APPROVED: '已同意',
  REJECTED: '已拒绝',
  COMPLETED: '已完成'
}[value] || value || '-')

const taxSourceLabel = (value) => ({
  PRODUCT_CUSTOM: '商品单独覆盖',
  SUPPLIER_DEFAULT: '供应商默认',
  SELF_OPERATED: '平台自营'
}[value] || value || '-')

const loadOverview = async () => {
  try {
    const res = await getSettlementOverview(buildBaseParams())
    overview.value = res.data || {}
  } catch {
    overview.value = {}
  }
}

const loadTrend = async () => {
  try {
    const res = await getSettlementTrend({
      ...buildBaseParams(),
      groupBy: groupBy.value
    })
    trendData.value = res.data || []
  } catch {
    trendData.value = []
  }
}

const loadOrders = async () => {
  try {
    const res = await getSettlementOrders({
      ...buildBaseParams(),
      page: pagination.page,
      size: pagination.size,
      keyword: filters.keyword || undefined,
      refundOnly: filters.refundOnly || undefined
    })
    orderRows.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch {
    orderRows.value = []
    pagination.total = 0
  }
}

const loadAll = async () => {
  loading.value = true
  try {
    await Promise.allSettled([loadOverview(), loadTrend(), loadOrders()])
  } finally {
    loading.value = false
  }
}

const handleGroupByChange = async () => {
  await loadTrend()
}

const handleSearch = () => {
  pagination.page = 1
  loadAll()
}

const handleReset = () => {
  dateRange.value = [monthStart, monthEnd]
  filters.sourceType = ''
  filters.supplierId = null
  filters.keyword = ''
  filters.refundOnly = false
  groupBy.value = 'day'
  pagination.page = 1
  pagination.size = 10
  loadAll()
}

onMounted(async () => {
  try {
    const supplierRes = await getSupplierList()
    suppliers.value = supplierRes.data || []
  } catch {
    suppliers.value = []
  }
  await loadAll()
})
</script>

<style scoped>
.hl-filter-form {
  align-items: flex-end;
}

.hl-warning-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hl-side-meta {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid var(--hl-border-light, #ebeef5);
}

.hl-side-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--hl-text-secondary, #606266);
}

.hl-order-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.hl-order-product {
  color: var(--hl-text-primary, #303133);
  font-weight: 600;
}

.hl-order-sub {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--hl-text-secondary, #909399);
  font-size: 12px;
}

.hl-negative {
  color: #ff453a;
}

@media screen and (max-width: 768px) {
  .hl-filter-form :deep(.el-form-item) {
    width: 100%;
    margin-right: 0;
  }

  .hl-filter-form :deep(.el-date-editor),
  .hl-filter-form :deep(.el-select),
  .hl-filter-form :deep(.el-input) {
    width: 100% !important;
  }
}
</style>
