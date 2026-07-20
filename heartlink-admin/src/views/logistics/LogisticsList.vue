<template>
  <div class="hl-logistics-list">
    <el-card shadow="hover" class="hl-search-card">
      <div class="hl-logistics-toolbar">
        <el-form :inline="true" :model="queryParams" class="hl-form">
          <el-form-item label="订单号">
            <el-input v-model="queryParams.orderNo" placeholder="订单号" clearable />
          </el-form-item>
          <el-form-item label="运单号">
            <el-input v-model="queryParams.trackingNo" placeholder="物流运单号" clearable />
          </el-form-item>
          <el-form-item label="快递公司">
            <el-select
              v-model="queryParams.expressCompanyCode"
              placeholder="全部快递"
              clearable
              filterable
              style="width: 168px"
            >
              <el-option
                v-for="item in LOGISTICS_COMPANIES"
                :key="item.code"
                :label="item.name"
                :value="item.code"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="物流状态">
            <el-select
              v-model="queryParams.logisticsStatus"
              placeholder="全部物流状态"
              clearable
              style="width: 148px"
            >
              <el-option
                v-for="item in logisticsStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="订单状态">
            <el-select
              v-model="queryParams.status"
              placeholder="全部订单状态"
              clearable
              style="width: 148px"
            >
              <el-option
                v-for="item in orderStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" class="hl-btn" @click="handleSearch">搜索</el-button>
            <el-button class="hl-btn" @click="resetSearch">重置</el-button>
          </el-form-item>
        </el-form>

        <div class="hl-logistics-toolbar-actions">
          <el-button :icon="Refresh" @click="loadData">刷新列表</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="hover" class="hl-table">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column label="收货信息" min-width="180">
          <template #default="{ row }">
            <div class="receiver-cell">
              <div class="receiver-name">{{ row.receiverName || '-' }}</div>
              <div class="receiver-meta">{{ row.receiverPhone || '-' }}</div>
              <div class="receiver-meta">{{ row.receiverAddress || '-' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="快递公司" width="150">
          <template #default="{ row }">
            <div class="company-cell">
              <div>{{ resolveCompanyName(row) }}</div>
              <div class="company-code">{{ row.expressCompanyCode || '-' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="trackingNo" label="运单号" min-width="180" />
        <el-table-column label="物流状态" width="140">
          <template #default="{ row }">
            <el-tag :type="resolveLogisticsStatusType(row.logisticsStatus)" effect="light">
              {{ logisticsStatusLabel(row.logisticsStatus, row.logisticsStatusText) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最新轨迹" min-width="300">
          <template #default="{ row }">
            <div class="trace-text">{{ row.logisticsLastTrace || '暂无物流轨迹' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatDatetime(row.logisticsUpdatedAt) }}</template>
        </el-table-column>
        <el-table-column label="订单状态" width="120">
          <template #default="{ row }">
            <el-tag :class="getOrderStatusClass(row.status)" effect="light">
              {{ orderStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发货时间" width="180">
          <template #default="{ row }">{{ formatDatetime(row.shippedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :icon="Refresh"
              :loading="refreshingId === row.id"
              @click="handleRefresh(row)"
            >
              刷新物流
            </el-button>
            <el-button link type="success" :icon="View" @click="router.push(`/order/${row.id}`)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.page"
        v-model:page-size="queryParams.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search, View } from '@element-plus/icons-vue'
import { getOrderLogistics, getOrderPage } from '../../api'
import { LOGISTICS_COMPANIES, findLogisticsCompany } from '../../constants/logistics'

const router = useRouter()
const AUTO_REFRESH_INTERVAL = 5000

const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const refreshingId = ref(null)
let autoRefreshTimer = null

const queryParams = reactive({
  page: 1,
  size: 10,
  orderNo: '',
  trackingNo: '',
  expressCompanyCode: '',
  logisticsStatus: '',
  status: ''
})

const orderStatusMap = {
  PENDING_PAY: '待付款',
  PAID: '已付款',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const logisticsStatusMap = {
  PENDING: '待查询',
  NO_TRACE: '暂无轨迹',
  ACCEPTED: '已揽收',
  IN_TRANSIT: '运输中',
  DELIVERED: '已送达待签收',
  SIGNED: '已签收',
  PROBLEM: '异常件',
  MANUAL: '手动查询',
  UNKNOWN: '物流更新中'
}

const orderStatusOptions = [
  { label: '已发货', value: 'SHIPPED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '已付款', value: 'PAID' }
]

const logisticsStatusOptions = [
  { label: '待查询', value: 'PENDING' },
  { label: '暂无轨迹', value: 'NO_TRACE' },
  { label: '已揽收', value: 'ACCEPTED' },
  { label: '运输中', value: 'IN_TRANSIT' },
  { label: '已送达待签收', value: 'DELIVERED' },
  { label: '已签收', value: 'SIGNED' },
  { label: '异常件', value: 'PROBLEM' },
  { label: '手动查询', value: 'MANUAL' },
  { label: '物流更新中', value: 'UNKNOWN' }
]

const orderStatusLabel = (status) => orderStatusMap[status] || status || '-'

const logisticsStatusLabel = (status, fallbackText) => {
  if (fallbackText) return fallbackText
  return logisticsStatusMap[status] || status || '-'
}

const getOrderStatusClass = (status) => ({
  PENDING_PAY: 'hl-tag-pending',
  PAID: 'hl-tag-paid',
  SHIPPED: 'hl-tag-shipped',
  COMPLETED: 'hl-tag-completed',
  CANCELLED: 'hl-tag-cancelled'
}[status] || '')

const resolveLogisticsStatusType = (status) => ({
  DELIVERED: 'warning',
  SIGNED: 'success',
  IN_TRANSIT: 'primary',
  ACCEPTED: 'info',
  NO_TRACE: 'warning',
  PENDING: 'warning',
  PROBLEM: 'danger',
  MANUAL: 'info',
  UNKNOWN: 'info'
}[status] || 'info')

const resolveCompanyName = (row) => {
  if (row?.expressCompanyName) return row.expressCompanyName
  return findLogisticsCompany(row?.expressCompanyCode)?.name || row?.expressCompanyCode || '-'
}

const formatDatetime = (value) => {
  if (!value) return '-'
  const date = new Date(String(value).replace('T', ' ').replace(/-/g, '/'))
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

const toTimestamp = (value) => {
  if (!value) return 0
  const time = new Date(String(value).replace('T', ' ').replace(/-/g, '/')).getTime()
  return Number.isNaN(time) ? 0 : time
}

const buildQueryString = (params) => {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== '' && value !== null && value !== undefined) {
      search.append(key, String(value))
    }
  })
  return search.toString()
}

const fetchLogisticsPageData = async () => {
  const token = localStorage.getItem('token')
  const query = buildQueryString({ ...queryParams })
  const response = await fetch(`/api/admin/order/logistics/page?${query}`, {
    headers: token ? { Authorization: token } : {}
  })

  let payload = null
  try {
    payload = await response.json()
  } catch {
    payload = null
  }

  if (!response.ok || payload?.code !== 200) {
    throw new Error(payload?.message || `HTTP ${response.status}`)
  }
  return payload
}

const compareOrders = (left, right) => {
  const leftTime = toTimestamp(left.logisticsUpdatedAt) || toTimestamp(left.shippedAt) || toTimestamp(left.createdAt)
  const rightTime = toTimestamp(right.logisticsUpdatedAt) || toTimestamp(right.shippedAt) || toTimestamp(right.createdAt)
  return rightTime - leftTime
}

const matchesCompatibilityFilters = (row) => {
  if (!row?.trackingNo) return false
  if (queryParams.orderNo && !String(row.orderNo || '').includes(queryParams.orderNo.trim())) return false
  if (queryParams.trackingNo && !String(row.trackingNo || '').includes(queryParams.trackingNo.trim())) return false
  if (queryParams.status && row.status !== queryParams.status) return false
  if (queryParams.logisticsStatus && row.logisticsStatus !== queryParams.logisticsStatus) return false
  if (queryParams.expressCompanyCode && String(row.expressCompanyCode || '').toUpperCase() !== queryParams.expressCompanyCode) return false
  return true
}

const loadCompatibilityData = async () => {
  const merged = []
  const size = 100
  const maxPages = 10

  for (let page = 1; page <= maxPages; page += 1) {
    const res = await getOrderPage({
      page,
      size,
      status: queryParams.status || undefined
    })
    const records = res.data?.records || res.data?.list || []
    merged.push(...records)

    const pages = res.data?.pages || Math.ceil((res.data?.total || 0) / size) || 0
    if (!records.length || !pages || page >= pages) {
      break
    }
  }

  const filtered = merged
    .filter(matchesCompatibilityFilters)
    .sort(compareOrders)

  total.value = filtered.length
  const start = (queryParams.page - 1) * queryParams.size
  tableData.value = filtered.slice(start, start + queryParams.size)
}

const loadData = async () => {
  loading.value = true
  try {
    try {
      const res = await fetchLogisticsPageData()
      tableData.value = res.data?.records || res.data?.list || []
      total.value = res.data?.total || 0
    } catch {
      await loadCompatibilityData()
    }
  } finally {
    loading.value = false
  }
}

const stopAutoRefresh = () => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

const startAutoRefresh = () => {
  stopAutoRefresh()
  autoRefreshTimer = setInterval(() => {
    if (loading.value || refreshingId.value) {
      return
    }
    loadData()
  }, AUTO_REFRESH_INTERVAL)
}

const handleSearch = () => {
  queryParams.page = 1
  loadData()
}

const resetSearch = () => {
  queryParams.page = 1
  queryParams.size = 10
  queryParams.orderNo = ''
  queryParams.trackingNo = ''
  queryParams.expressCompanyCode = ''
  queryParams.logisticsStatus = ''
  queryParams.status = ''
  loadData()
}

const handleRefresh = async (row) => {
  refreshingId.value = row.id
  try {
    const res = await getOrderLogistics(row.id, true)
    const message = res.data?.message || '物流状态已更新'
    ElMessage({
      type: res.data?.refreshed ? 'success' : 'info',
      message
    })
    await loadData()
  } finally {
    refreshingId.value = null
  }
}

onMounted(async () => {
  await loadData()
  startAutoRefresh()
})

onBeforeUnmount(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.hl-logistics-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.hl-logistics-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.receiver-name,
.company-cell > div:first-child {
  font-size: var(--hl-text-base);
  font-weight: 600;
  color: var(--hl-text-regular);
}

.receiver-meta,
.company-code {
  margin-top: 4px;
  font-size: 12px;
  color: var(--hl-text-secondary);
}

.trace-text {
  color: var(--hl-text-regular);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (max-width: 768px) {
  .hl-logistics-toolbar-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
