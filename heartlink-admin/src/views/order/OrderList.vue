<template>
  <div class="hl-order-list">
    <el-card shadow="hover" class="hl-search-card">
      <el-form inline class="hl-form">
        <el-form-item label="订单号">
          <el-input v-model="queryParams.orderNo" placeholder="输入订单号" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="待付款" value="PENDING_PAY" />
            <el-option label="已付款" value="PAID" />
            <el-option label="已发货" value="SHIPPED" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch" class="hl-btn">搜索</el-button>
          <el-button @click="resetSearch" class="hl-btn">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" class="hl-table">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column label="商品" min-width="240">
          <template #default="{ row }">
            <div class="product-cell">
              <div class="hl-image hl-image-sm">
                <el-image :src="resolveProductImage(row.productImage)" fit="cover">
                  <template #error>
                    <div class="hl-image-placeholder">无图</div>
                  </template>
                </el-image>
              </div>
              <div class="product-text">
                <div class="product-name">{{ row.productName }}</div>
                <div class="product-meta">规格: {{ row.productSpec || '默认规格' }} / 数量: {{ row.quantity || 1 }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="150">
          <template #default="{ row }">
            <div class="order-source-cell">
              <el-tag :type="getOrderSourceTagType(row)" effect="light">
                {{ row.orderSourceText || '子女下单' }}
              </el-tag>
              <el-tag
                v-if="row.paymentRequestStatus"
                size="small"
                :type="getPaymentRequestTagType(row.paymentRequestStatus)"
                effect="plain"
              >
                {{ paymentRequestLabel(row.paymentRequestStatus) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="家庭信息" min-width="180">
          <template #default="{ row }">
            <div class="family-cell">
              <div>长辈：{{ row.parentNickname || formatUserFallback(row.parentId) }}</div>
              <div>子女：{{ row.childNickname || formatUserFallback(row.childId) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            <span class="amount-text">¥{{ row.totalAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="receiverName" label="收货人" width="120" />
        <el-table-column label="物流信息" min-width="220">
          <template #default="{ row }">
            <div v-if="row.trackingNo" class="logistics-brief">
              <div>{{ row.expressCompanyName || row.expressCompanyCode || '已发货' }}</div>
              <div class="logistics-meta">{{ row.trackingNo }}</div>
              <div class="logistics-meta">{{ row.logisticsStatusText || '待物流更新' }}</div>
            </div>
            <div v-else class="logistics-brief">
              <div>{{ getLogisticsSummary(row).title }}</div>
              <div class="logistics-meta">{{ getLogisticsSummary(row).detail }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="订单状态" width="120">
          <template #default="{ row }">
            <el-tag :class="getStatusClass(row)" size="default" effect="light">
              {{ getDisplayStatus(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="退款状态" width="120">
          <template #default="{ row }">
            <el-tag
              v-if="row.refundStatus && row.refundStatus !== 'NONE'"
              :class="getRefundClass(row.refundStatus)"
              size="default"
              effect="light"
            >
              {{ refundLabel(row.refundStatus) }}
            </el-tag>
            <span v-else class="placeholder-text">-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDatetime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="default" link type="primary" :icon="View" @click="router.push(`/order/${row.id}`)">详情</el-button>
            <el-button size="default" link type="success" v-if="row.status === 'PAID'" :icon="Box" @click="openShipDialog(row)">发货</el-button>
            <el-button size="default" link type="warning" v-if="row.refundStatus === 'REQUESTED'" @click="handleRefundApprove(row)">同意</el-button>
            <el-button size="default" link type="danger" v-if="row.refundStatus === 'REQUESTED'" @click="handleRefundReject(row)">拒绝</el-button>
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

    <el-dialog v-model="shipDialog.visible" title="确认发货" width="560px" destroy-on-close>
      <el-form ref="shipFormRef" :model="shipForm" :rules="shipRules" label-width="110px">
        <el-form-item label="常用快递">
          <el-select
            v-model="shipForm.presetCode"
            placeholder="选择常用快递"
            filterable
            clearable
            @change="handlePresetChange"
          >
            <el-option
              v-for="item in LOGISTICS_COMPANIES"
              :key="item.code"
              :label="`${item.name} (${item.code})`"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="快递公司名称" prop="expressCompanyName">
          <el-input v-model.trim="shipForm.expressCompanyName" placeholder="例如：圆通速递" />
        </el-form-item>
        <el-form-item label="快递公司编码" prop="expressCompanyCode">
          <el-input v-model.trim="shipForm.expressCompanyCode" placeholder="例如：YTO" />
        </el-form-item>
        <el-form-item label="运单号" prop="trackingNo">
          <el-input v-model.trim="shipForm.trackingNo" placeholder="请输入物流运单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="shipDialog.loading" @click="submitShip">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Box, Search, View } from '@element-plus/icons-vue'
import { approveRefund, getOrderPage, rejectRefund, shipOrder } from '../../api'
import { LOGISTICS_COMPANIES, findLogisticsCompany } from '../../constants/logistics'

const router = useRouter()

const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const shipFormRef = ref()

const queryParams = reactive({
  page: 1,
  size: 10,
  orderNo: '',
  status: ''
})

const shipDialog = reactive({
  visible: false,
  loading: false,
  orderId: null
})

const shipForm = reactive({
  presetCode: '',
  expressCompanyName: '',
  expressCompanyCode: '',
  trackingNo: ''
})

const shipRules = {
  expressCompanyName: [{ required: true, message: '请输入快递公司名称', trigger: 'blur' }],
  expressCompanyCode: [{ required: true, message: '请输入快递公司编码', trigger: 'blur' }],
  trackingNo: [{ required: true, message: '请输入运单号', trigger: 'blur' }]
}

const statusMap = {
  PENDING_PAY: '待付款',
  PAID: '已付款',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const refundMap = {
  REQUESTED: '申请中',
  APPROVED: '已同意',
  REJECTED: '已拒绝',
  COMPLETED: '已完成'
}

const paymentRequestMap = {
  PENDING: '待代付',
  PAID: '已代付',
  REJECTED: '已拒绝',
  EXPIRED: '已过期'
}

const STATIC_PRODUCT_IMAGES = new Set([
  '1.png', '2.png', '3.png', '4.png',
  'band.png', 'blood-pressure.png', 'cane.png', 'foot-bath.png',
  'kettle.png', 'kneepad.png', 'oatmeal.png', 'protein.png',
  'smartphone.png', 'sweater.png'
])

const DEFAULT_IMAGE = '/static/products/oatmeal.png'

const resolveProductImage = (url) => {
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
  return raw.startsWith('/') ? raw : `/${raw}`
}

const statusLabel = (status) => statusMap[status] || status || '-'
const refundLabel = (status) => refundMap[status] || status || '-'
const paymentRequestLabel = (status) => paymentRequestMap[status] || status || '-'

const formatUserFallback = (userId) => (userId == null ? '-' : `ID ${userId}`)

const getDisplayStatus = (row) => {
  if (row?.status === 'SHIPPED' && row?.logisticsStatus === 'DELIVERED') {
    return '待签收'
  }
  return statusLabel(row?.status)
}

const getStatusClass = (row) => {
  if (row?.status === 'SHIPPED' && row?.logisticsStatus === 'DELIVERED') {
    return 'hl-tag-paid'
  }
  return ({
    PENDING_PAY: 'hl-tag-pending',
    PAID: 'hl-tag-paid',
    SHIPPED: 'hl-tag-shipped',
    COMPLETED: 'hl-tag-completed',
    CANCELLED: 'hl-tag-cancelled'
  }[row?.status] || '')
}

const getRefundClass = (status) => ({
  REQUESTED: 'hl-tag-refund-requested',
  APPROVED: 'hl-tag-refund-approved',
  REJECTED: 'hl-tag-refund-rejected',
  COMPLETED: 'hl-tag-refund-completed'
}[status] || '')

const getOrderSourceTagType = (row) => {
  return row?.orderSourceType === 'PARENT_PAYMENT_REQUEST' ? 'warning' : 'success'
}

const getPaymentRequestTagType = (status) => ({
  PENDING: 'warning',
  PAID: 'success',
  REJECTED: 'danger',
  EXPIRED: 'info'
}[status] || 'info')

const formatDatetime = (value) => {
  if (!value) return ''
  const date = new Date(String(value).replace('T', ' ').replace(/-/g, '/'))
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

const getLogisticsSummary = (row) => {
  if (row?.trackingNo) {
    return {
      title: row.expressCompanyName || row.expressCompanyCode || '已发货',
      detail: row.logisticsStatusText || '待物流更新'
    }
  }

  switch (row?.status) {
    case 'PENDING_PAY':
      return {
        title: '待付款',
        detail: '订单尚未支付，暂未进入发货流程'
      }
    case 'PAID':
      return {
        title: '待发货',
        detail: '订单已支付，等待商家录入运单号'
      }
    case 'CANCELLED':
      return {
        title: '已取消',
        detail: '订单已取消，无物流信息'
      }
    case 'COMPLETED':
      return {
        title: '已完成',
        detail: '订单已完成'
      }
    default:
      return {
        title: '未发货',
        detail: '暂未录入物流单号'
      }
  }
}

const resetShipForm = () => {
  shipForm.presetCode = ''
  shipForm.expressCompanyName = ''
  shipForm.expressCompanyCode = ''
  shipForm.trackingNo = ''
}

const handlePresetChange = (code) => {
  const selected = findLogisticsCompany(code)
  if (!selected) return
  shipForm.expressCompanyCode = selected.code
  shipForm.expressCompanyName = selected.name
}

const buildShipPayload = () => ({
  expressCompanyName: shipForm.expressCompanyName.trim(),
  expressCompanyCode: shipForm.expressCompanyCode.trim().toUpperCase(),
  trackingNo: shipForm.trackingNo.trim()
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await getOrderPage({ ...queryParams })
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.page = 1
  loadData()
}

const resetSearch = () => {
  queryParams.page = 1
  queryParams.size = 10
  queryParams.orderNo = ''
  queryParams.status = ''
  loadData()
}

const openShipDialog = (row) => {
  shipDialog.visible = true
  shipDialog.orderId = row.id
  resetShipForm()
  shipForm.trackingNo = row.trackingNo || ''
  shipForm.expressCompanyName = row.expressCompanyName || ''
  shipForm.expressCompanyCode = row.expressCompanyCode || ''
  const company = findLogisticsCompany(row.expressCompanyCode)
  shipForm.presetCode = company?.code || ''
}

const submitShip = async () => {
  const payload = buildShipPayload()
  if (!payload.expressCompanyName || !payload.expressCompanyCode || !payload.trackingNo) {
    ElMessage.warning('请先填写完整的发货信息')
    return
  }
  await shipFormRef.value?.validate()
  shipDialog.loading = true
  try {
    await shipOrder(shipDialog.orderId, payload)
    ElMessage.success('发货成功')
    shipDialog.visible = false
    await loadData()
  } finally {
    shipDialog.loading = false
  }
}

const handleRefundApprove = async (row) => {
  await ElMessageBox.confirm('确认同意退款吗？', '提示', { type: 'warning' })
  await approveRefund(row.id)
  ElMessage.success('已同意退款')
  await loadData()
}

const handleRefundReject = async (row) => {
  await ElMessageBox.confirm('确认拒绝退款吗？', '提示', { type: 'warning' })
  await rejectRefund(row.id)
  ElMessage.success('已拒绝退款')
  await loadData()
}

onMounted(loadData)
</script>

<style scoped>
.product-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.product-text {
  min-width: 0;
}

.product-name {
  font-size: var(--hl-text-base);
  font-weight: 600;
  color: var(--hl-text-regular);
}

.product-meta,
.logistics-meta,
.placeholder-text,
.family-cell {
  margin-top: 4px;
  font-size: 12px;
  color: var(--hl-text-secondary);
}

.logistics-brief,
.family-cell,
.order-source-cell {
  line-height: 1.5;
}

.order-source-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.amount-text {
  font-weight: 600;
  color: var(--hl-danger);
  font-size: var(--hl-text-base);
}

.hl-image-placeholder {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  background: var(--hl-bg-tertiary);
  color: var(--hl-text-placeholder);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}
</style>
