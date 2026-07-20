<template>
  <div class="hl-refund-list">
    <div class="hl-page-header">
      <div>
        <h1 class="hl-page-title">退款管理</h1>
        <p class="hl-page-subtitle">集中处理退款申请、跟踪审批结果，并快速跳转到订单详情核验信息。</p>
      </div>
    </div>

    <el-card shadow="hover" class="hl-search-card">
      <div class="hl-refund-tabs">
        <el-radio-group v-model="queryParams.refundStatus" @change="handleSearch">
          <el-radio-button value="">全部退款</el-radio-button>
          <el-radio-button value="REQUESTED">待处理</el-radio-button>
          <el-radio-button value="APPROVED">已同意</el-radio-button>
          <el-radio-button value="REJECTED">已拒绝</el-radio-button>
          <el-radio-button value="COMPLETED">已完成</el-radio-button>
        </el-radio-group>
      </div>

      <el-form inline class="hl-form hl-refund-form">
        <el-form-item label="订单号">
          <el-input v-model="queryParams.orderNo" placeholder="输入订单号搜索" clearable />
        </el-form-item>
        <el-form-item label="订单状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="待付款" value="PENDING_PAY" />
            <el-option label="已付款" value="PAID" />
            <el-option label="已发货" value="SHIPPED" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" class="hl-btn" @click="handleSearch">搜索</el-button>
          <el-button class="hl-btn" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" class="hl-table">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column label="商品信息" min-width="240">
          <template #default="{ row }">
            <div class="hl-refund-product">
              <div class="hl-image hl-image-sm">
                <el-image :src="resolveProductImage(row.productImage)" fit="cover">
                  <template #error>
                    <div class="hl-image-placeholder">无图</div>
                  </template>
                </el-image>
              </div>
              <div class="hl-refund-product-copy">
                <div class="hl-refund-product-name">{{ row.productName || '-' }}</div>
                <div class="hl-refund-product-meta">
                  金额 <strong>¥{{ formatAmount(row.totalAmount) }}</strong>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="收货信息" min-width="180">
          <template #default="{ row }">
            <div class="hl-refund-receiver">
              <div>{{ row.receiverName || '-' }}</div>
              <div class="hl-refund-phone">{{ row.receiverPhone || '-' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="退款原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.refundReason || '未填写退款原因' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="信用风险" min-width="220">
          <template #default="{ row }">
            <div class="hl-refund-risk">
              <el-tag :type="getRiskTagType(row.refundCreditRiskLevel)" effect="light">
                {{ riskLabel(row) }}
              </el-tag>
              <div class="hl-refund-risk-note">{{ riskText(row) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="退款状态" width="120">
          <template #default="{ row }">
            <el-tag :class="getRefundClass(row.refundStatus)" size="default" effect="light">
              {{ refundLabel(row.refundStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="订单状态" width="120">
          <template #default="{ row }">
            <el-tag :class="getStatusClass(row.status)" size="default" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近更新时间" width="180">
          <template #default="{ row }">
            {{ formatDatetime(row.refundAt || row.updatedAt || row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="router.push(`/order/${row.id}`)">详情</el-button>
            <el-button
              v-if="row.refundStatus === 'REQUESTED'"
              link
              type="success"
              :icon="CircleCheck"
              @click="handleRefundApprove(row)"
            >
              同意
            </el-button>
            <el-button
              v-if="row.refundStatus === 'REQUESTED'"
              link
              type="danger"
              :icon="CircleClose"
              @click="handleRefundReject(row)"
            >
              拒绝
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && tableData.length === 0"
        class="hl-empty"
        description="当前筛选条件下暂无退款记录"
      />

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
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose, Search, View } from '@element-plus/icons-vue'
import { approveRefund, getRefundPage, rejectRefund } from '../../api'

const router = useRouter()

const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const queryParams = ref({
  page: 1,
  size: 10,
  refundStatus: '',
  status: '',
  orderNo: ''
})

const STATIC_PRODUCT_IMAGES = new Set([
  '1.png', '2.png', '3.png', '4.png',
  'band.png', 'blood-pressure.png', 'cane.png', 'foot-bath.png',
  'kettle.png', 'kneepad.png', 'oatmeal.png', 'protein.png',
  'smartphone.png', 'sweater.png'
])

const DEFAULT_IMAGE = '/static/products/oatmeal.png'

function resolveProductImage(url) {
  const raw = String(url || '').trim()
  if (!raw) return DEFAULT_IMAGE
  if (/^data:/i.test(raw)) return raw
  if (/^https?:\/\//i.test(raw)) return raw
  if (raw.startsWith('/upload/')) return raw
  if (raw.startsWith('upload/')) return `/${raw}`

  if (raw.startsWith('/static/products/')) {
    const fileName = raw.split('/').pop().toLowerCase()
    return STATIC_PRODUCT_IMAGES.has(fileName) ? raw : DEFAULT_IMAGE
  }
  if (raw.startsWith('static/products/')) {
    const normalized = `/${raw}`
    const fileName = normalized.split('/').pop().toLowerCase()
    return STATIC_PRODUCT_IMAGES.has(fileName) ? normalized : DEFAULT_IMAGE
  }

  if (raw.startsWith('/')) return raw
  return `/${raw}`
}

function statusLabel(status) {
  const map = {
    PENDING_PAY: '待付款',
    PAID: '已付款',
    SHIPPED: '已发货',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }
  return map[status] || status || '-'
}

function getStatusClass(status) {
  const map = {
    PENDING_PAY: 'hl-tag-pending',
    PAID: 'hl-tag-paid',
    SHIPPED: 'hl-tag-shipped',
    COMPLETED: 'hl-tag-completed',
    CANCELLED: 'hl-tag-cancelled'
  }
  return map[status] || ''
}

function refundLabel(status) {
  const map = {
    REQUESTED: '待处理',
    APPROVED: '已同意',
    REJECTED: '已拒绝',
    COMPLETED: '已完成'
  }
  return map[status] || status || '-'
}

function getRefundClass(status) {
  const map = {
    REQUESTED: 'hl-tag-refund-requested',
    APPROVED: 'hl-tag-refund-approved',
    REJECTED: 'hl-tag-refund-rejected',
    COMPLETED: 'hl-tag-refund-completed'
  }
  return map[status] || 'hl-tag-refund-requested'
}

function formatCreditScore(value) {
  const score = Number(value)
  return Number.isFinite(score) ? score : 100
}

function getRiskTagType(level) {
  if (level === 'LOW_CREDIT') return 'danger'
  if (level === 'HIGH_TRUST') return 'success'
  return 'warning'
}

function riskLabel(row) {
  const score = formatCreditScore(row.refundCreditScore ?? row.childCreditScore)
  if (row.refundCreditRiskLevel === 'LOW_CREDIT') return `低信用 ${score} 分`
  if (row.refundCreditRiskLevel === 'HIGH_TRUST') return `高信用 ${score} 分`
  return `普通信用 ${score} 分`
}

function riskText(row) {
  if (row.refundStatus === 'APPROVED' && formatCreditScore(row.refundCreditScore) > 90) {
    return '高信用退款已自动同意。'
  }
  return row.refundCreditRiskText || '-'
}

function formatDatetime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function formatAmount(value) {
  if (value === null || value === undefined || value === '') return '0.00'
  const amount = Number(value)
  return Number.isFinite(amount) ? amount.toFixed(2) : value
}

async function loadData() {
  loading.value = true
  try {
    const params = {
      ...queryParams.value
    }
    if (!params.refundStatus) {
      delete params.refundStatus
    }
    if (!params.status) {
      delete params.status
    }
    if (!params.orderNo) {
      delete params.orderNo
    }
    const res = await getRefundPage(params)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.value.page = 1
  loadData()
}

function resetSearch() {
  queryParams.value = {
    page: 1,
    size: 10,
    refundStatus: '',
    status: '',
    orderNo: ''
  }
  loadData()
}

async function handleRefundApprove(row) {
  await ElMessageBox.confirm(`确认同意订单 ${row.orderNo} 的退款申请吗？`, '提示', { type: 'warning' })
  await approveRefund(row.id)
  ElMessage.success('已同意退款')
  loadData()
}

async function handleRefundReject(row) {
  await ElMessageBox.confirm(`确认拒绝订单 ${row.orderNo} 的退款申请吗？`, '提示', { type: 'warning' })
  await rejectRefund(row.id)
  ElMessage.success('已拒绝退款')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.hl-refund-tabs {
  margin-bottom: 16px;
}

.hl-refund-form {
  align-items: flex-end;
}

.hl-refund-product {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hl-refund-product-copy {
  min-width: 0;
}

.hl-refund-product-name {
  color: var(--hl-text-primary);
  font-size: var(--hl-text-base);
  font-weight: 600;
}

.hl-refund-product-meta {
  margin-top: 4px;
  color: var(--hl-text-secondary);
  font-size: var(--hl-text-sm);
}

.hl-refund-receiver {
  color: var(--hl-text-regular);
}

.hl-refund-phone {
  margin-top: 4px;
  color: var(--hl-text-secondary);
  font-size: var(--hl-text-sm);
}

.hl-refund-risk {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.hl-refund-risk-note {
  color: var(--hl-text-secondary);
  line-height: 1.5;
}

.hl-image-placeholder {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  background: var(--hl-bg-tertiary);
  color: var(--hl-text-placeholder);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

@media screen and (max-width: 768px) {
  .hl-refund-tabs :deep(.el-radio-group) {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .hl-refund-tabs :deep(.el-radio-button),
  .hl-refund-tabs :deep(.el-radio-button__inner) {
    width: 100%;
  }
}
</style>
