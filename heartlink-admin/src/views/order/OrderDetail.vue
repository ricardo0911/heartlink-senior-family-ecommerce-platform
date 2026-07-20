<template>
  <div class="hl-order-detail" v-loading="loading">
    <el-page-header @back="router.back()" title="返回" content="订单详情" style="margin-bottom: 24px" />

    <template v-if="order">
      <el-card shadow="hover" class="hl-detail-card" style="margin-bottom: 24px">
        <template #header>
          <div class="hl-card-header">
            <span>订单信息</span>
            <div class="actions">
              <el-button
                v-if="canViewLogistics"
                :loading="logisticsLoading"
                @click="loadLogistics(true)"
                class="hl-btn"
              >
                刷新物流
              </el-button>
              <el-button type="success" v-if="order.refundStatus === 'REQUESTED'" @click="handleRefundApprove" class="hl-btn">
                同意退款
              </el-button>
              <el-button type="danger" v-if="order.refundStatus === 'REQUESTED'" @click="handleRefundReject" class="hl-btn">
                拒绝退款
              </el-button>
            </div>
          </div>
        </template>
        <el-alert
          v-if="order.refundStatus && order.refundStatus !== 'NONE'"
          :title="refundCreditAlertTitle"
          :type="refundCreditAlertType"
          show-icon
          :closable="false"
          style="margin-bottom: 16px"
        />
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :class="getStatusClass(order.status)" size="default" effect="light">
              {{ statusLabel(order.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="退款状态">
            <el-tag
              v-if="order.refundStatus && order.refundStatus !== 'NONE'"
              :class="getRefundClass(order.refundStatus)"
              size="default"
              effect="light"
            >
              {{ refundLabel(order.refundStatus) }}
            </el-tag>
            <span v-else style="color: var(--hl-text-placeholder)">无</span>
          </el-descriptions-item>
          <el-descriptions-item label="退款信用分">
            <el-tag :type="creditRiskTagType" effect="light">{{ formatCreditScore(order.refundCreditScore ?? order.childCreditScore) }} 分</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="信用风险">
            <el-tag :type="creditRiskTagType" effect="light">{{ creditRiskLabel }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDatetime(order.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatDatetime(order.paidAt) }}</el-descriptions-item>
          <el-descriptions-item label="发货时间">{{ formatDatetime(order.shippedAt) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatDatetime(order.completedAt) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card v-if="showShipForm" shadow="hover" class="hl-detail-card" style="margin-bottom: 24px">
        <template #header><span>发货录入</span></template>
        <el-form ref="shipFormRef" :model="shipForm" :rules="shipRules" label-width="110px">
          <div class="ship-grid">
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
          </div>
          <el-button type="primary" :loading="shipping" @click="handleShip">确认发货并同步物流</el-button>
        </el-form>
      </el-card>

      <el-card shadow="hover" class="hl-detail-card" style="margin-bottom: 24px">
        <template #header><span>商品信息</span></template>
        <div class="hl-product-snapshot">
          <el-image :src="resolveProductImage(order.productImage)" fit="cover" class="hl-order-image">
            <template #error>
              <div class="hl-image-placeholder">无图</div>
            </template>
          </el-image>
          <div class="hl-snapshot-info">
            <div class="hl-snapshot-name">{{ order.productName || '-' }}</div>
            <div class="hl-snapshot-specs">
              规格: {{ order.productSpec || '默认规格' }} | 数量: {{ order.quantity || 0 }}
            </div>
            <div style="margin-top: 8px">
              <span style="color: var(--hl-text-placeholder); text-decoration: line-through">
                单价: ¥{{ order.price || 0 }}
              </span>
              <span style="color: var(--hl-danger); font-size: 20px; font-weight: 700; margin-left: 16px">
                总金额: ¥{{ order.totalAmount || 0 }}
              </span>
            </div>
          </div>
        </div>
      </el-card>

      <el-card shadow="hover" class="hl-detail-card" style="margin-bottom: 24px">
        <template #header><span>收货信息</span></template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="收货人">{{ order.receiverName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ order.receiverPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="2">{{ order.receiverAddress || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card v-if="canViewLogistics" shadow="hover" class="hl-detail-card" style="margin-bottom: 24px">
        <template #header>
          <div class="hl-card-header">
            <span>物流信息</span>
            <el-button text type="primary" :loading="logisticsLoading" @click="loadLogistics(true)">重新查询</el-button>
          </div>
        </template>

        <el-alert
          v-if="logistics?.message"
          :title="logistics.message"
          :type="logistics?.queryEnabled ? 'info' : 'warning'"
          show-icon
          :closable="false"
          style="margin-bottom: 16px"
        />

        <el-descriptions :column="2" border>
          <el-descriptions-item label="快递公司">{{ logisticsView.expressCompanyName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="快递编码">{{ logisticsView.expressCompanyCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="运单号">{{ logisticsView.trackingNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="物流服务商">{{ logisticsView.logisticsProvider || '-' }}</el-descriptions-item>
          <el-descriptions-item label="物流状态">{{ logisticsView.logisticsStatusText || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最近更新时间">{{ formatDatetime(logisticsView.logisticsUpdatedAt) }}</el-descriptions-item>
          <el-descriptions-item label="最新轨迹" :span="2">{{ logisticsView.logisticsLastTrace || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="trace-section">
          <div class="trace-title">物流轨迹</div>
          <el-timeline v-if="logisticsView.traces?.length">
            <el-timeline-item
              v-for="(item, index) in logisticsView.traces"
              :key="`${item.acceptTime || index}-${item.acceptStation || index}`"
              :timestamp="item.acceptTime || ''"
              :type="index === 0 ? 'primary' : ''"
            >
              {{ item.acceptStation || '物流状态更新中' }}
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂未返回物流轨迹" :image-size="84" />
        </div>
      </el-card>

      <el-card shadow="hover" class="hl-detail-card">
        <template #header><span>其他信息</span></template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="情感贺卡">
            <span v-if="order.greetingCard" style="color: var(--hl-accent); font-style: italic">
              "{{ order.greetingCard }}"
            </span>
            <span v-else style="color: var(--hl-text-placeholder)">无</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="order.refundStatus && order.refundStatus !== 'NONE'" label="退款原因">
            <span style="color: var(--hl-danger)">{{ order.refundReason || '未填写' }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="order.refundStatus && order.refundStatus !== 'NONE'" label="风险提示">
            <span>{{ order.refundCreditRiskText || '无' }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approveRefund, getOrder, getOrderLogistics, rejectRefund, shipOrder } from '../../api'
import { LOGISTICS_COMPANIES, findLogisticsCompany } from '../../constants/logistics'

const route = useRoute()
const router = useRouter()
const LOGISTICS_POLL_INTERVAL = 5000

const order = ref(null)
const logistics = ref(null)
const loading = ref(false)
const shipping = ref(false)
const logisticsLoading = ref(false)
const shipFormRef = ref()
let logisticsPollTimer = null

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

const STATIC_PRODUCT_IMAGES = new Set([
  '1.png', '2.png', '3.png', '4.png',
  'band.png', 'blood-pressure.png', 'cane.png', 'foot-bath.png',
  'kettle.png', 'kneepad.png', 'oatmeal.png', 'protein.png',
  'smartphone.png', 'sweater.png'
])

const DEFAULT_IMAGE = '/static/products/oatmeal.png'

const showShipForm = computed(() => order.value?.status === 'PAID')
const canViewLogistics = computed(() => Boolean(order.value?.trackingNo))
const logisticsView = computed(() => logistics.value || {
  expressCompanyName: order.value?.expressCompanyName,
  expressCompanyCode: order.value?.expressCompanyCode,
  trackingNo: order.value?.trackingNo,
  logisticsProvider: order.value?.logisticsProvider,
  logisticsStatusText: order.value?.logisticsStatusText,
  logisticsLastTrace: order.value?.logisticsLastTrace,
  logisticsUpdatedAt: order.value?.logisticsUpdatedAt,
  traces: []
})
const refundCreditScore = computed(() => {
  const score = Number(order.value?.refundCreditScore ?? order.value?.childCreditScore)
  return Number.isFinite(score) ? score : 100
})
const creditRiskLabel = computed(() => {
  if (order.value?.refundCreditRiskLevel === 'LOW_CREDIT') return '低信用用户'
  if (order.value?.refundCreditRiskLevel === 'HIGH_TRUST') return '高信用用户'
  return '普通信用用户'
})
const creditRiskTagType = computed(() => {
  if (order.value?.refundCreditRiskLevel === 'LOW_CREDIT') return 'danger'
  if (order.value?.refundCreditRiskLevel === 'HIGH_TRUST') return 'success'
  return 'warning'
})
const refundCreditAlertType = computed(() => {
  if (order.value?.refundCreditRiskLevel === 'LOW_CREDIT') return 'warning'
  if (order.value?.refundStatus === 'APPROVED' && refundCreditScore.value > 90) return 'success'
  return 'info'
})
const refundCreditAlertTitle = computed(() => {
  if (order.value?.refundStatus === 'APPROVED' && refundCreditScore.value > 90) {
    return `高信用用户 ${refundCreditScore.value} 分，系统已自动同意退款`
  }
  if (order.value?.refundCreditRiskLevel === 'LOW_CREDIT') {
    return `低信用用户 ${refundCreditScore.value} 分，请重点核验本次退款`
  }
  return order.value?.refundCreditRiskText || `当前退款信用分 ${refundCreditScore.value} 分`
})

const statusLabel = (value) => statusMap[value] || value || '-'
const refundLabel = (value) => refundMap[value] || value || '-'

const getStatusClass = (value) => ({
  PENDING_PAY: 'hl-tag-pending',
  PAID: 'hl-tag-paid',
  SHIPPED: 'hl-tag-shipped',
  COMPLETED: 'hl-tag-completed',
  CANCELLED: 'hl-tag-cancelled'
}[value] || '')

const getRefundClass = (value) => ({
  REQUESTED: 'hl-tag-refund-requested',
  APPROVED: 'hl-tag-refund-approved',
  REJECTED: 'hl-tag-refund-rejected',
  COMPLETED: 'hl-tag-refund-completed'
}[value] || '')

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

const formatDatetime = (value) => {
  if (!value) return '-'
  const date = new Date(String(value).replace('T', ' ').replace(/-/g, '/'))
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

const formatCreditScore = (value) => {
  const score = Number(value)
  return Number.isFinite(score) ? score : 100
}

const resetShipForm = () => {
  shipForm.presetCode = ''
  shipForm.expressCompanyName = ''
  shipForm.expressCompanyCode = ''
  shipForm.trackingNo = ''
}

const syncShipForm = () => {
  resetShipForm()
  if (!order.value) return
  shipForm.trackingNo = order.value.trackingNo || ''
  shipForm.expressCompanyName = order.value.expressCompanyName || ''
  shipForm.expressCompanyCode = order.value.expressCompanyCode || ''
  if (order.value.expressCompanyCode && findLogisticsCompany(order.value.expressCompanyCode)) {
    shipForm.presetCode = order.value.expressCompanyCode.toUpperCase()
  }
}

const handlePresetChange = (code) => {
  const selected = findLogisticsCompany(code)
  if (!selected) return
  shipForm.expressCompanyCode = selected.code
  shipForm.expressCompanyName = selected.name
}

const buildShipPayload = () => ({
  expressCompanyCode: shipForm.expressCompanyCode.trim().toUpperCase(),
  expressCompanyName: shipForm.expressCompanyName.trim(),
  trackingNo: shipForm.trackingNo.trim()
})

const loadOrder = async ({ refreshLogistics = false, silent = false } = {}) => {
  if (!silent) {
    loading.value = true
  }
  try {
    const res = await getOrder(route.params.id)
    order.value = res.data || null
    syncShipForm()
    if (order.value?.trackingNo) {
      await loadLogistics(refreshLogistics, { silent })
    } else {
      logistics.value = null
    }
  } finally {
    if (!silent) {
      loading.value = false
    }
  }
}

const loadLogistics = async (refresh = true, { silent = false } = {}) => {
  if (!order.value?.id || !order.value?.trackingNo) {
    logistics.value = null
    return
  }
  if (!silent) {
    logisticsLoading.value = true
  }
  try {
    const res = await getOrderLogistics(order.value.id, refresh)
    logistics.value = res.data || null
  } finally {
    if (!silent) {
      logisticsLoading.value = false
    }
  }
}

const shouldPollLogistics = () => {
  if (!order.value?.id || !order.value?.trackingNo) {
    return false
  }
  if (shipping.value || loading.value || logisticsLoading.value) {
    return false
  }
  const logisticsStatus = logistics.value?.logisticsStatus || order.value?.logisticsStatus
  return order.value?.status === 'SHIPPED' && logisticsStatus !== 'SIGNED' && logisticsStatus !== 'DELIVERED'
}

const stopLogisticsPolling = () => {
  if (logisticsPollTimer) {
    clearInterval(logisticsPollTimer)
    logisticsPollTimer = null
  }
}

const startLogisticsPolling = () => {
  stopLogisticsPolling()
  logisticsPollTimer = setInterval(() => {
    if (!shouldPollLogistics()) {
      return
    }
    loadOrder({ refreshLogistics: true, silent: true })
  }, LOGISTICS_POLL_INTERVAL)
}

const handleShip = async () => {
  const payload = buildShipPayload()
  if (!payload.expressCompanyName || !payload.expressCompanyCode || !payload.trackingNo) {
    ElMessage.warning('请先填写完整的发货录入信息')
    return
  }
  await shipFormRef.value?.validate()
  await ElMessageBox.confirm(
    `确认使用 ${payload.expressCompanyName}（${payload.expressCompanyCode}）发货？`,
    '发货确认',
    { type: 'warning' }
  )
  shipping.value = true
  try {
    await shipOrder(order.value.id, payload)
    ElMessage.success('发货成功')
    await loadOrder({ refreshLogistics: true })
  } finally {
    shipping.value = false
  }
}

const handleRefundApprove = async () => {
  await ElMessageBox.confirm('确认同意退款？', '提示')
  await approveRefund(order.value.id)
  ElMessage.success('已同意退款')
  await loadOrder()
}

const handleRefundReject = async () => {
  await ElMessageBox.confirm('确认拒绝退款？', '提示')
  await rejectRefund(order.value.id)
  ElMessage.success('已拒绝退款')
  await loadOrder()
}

onMounted(async () => {
  await loadOrder()
  startLogisticsPolling()
})

onBeforeUnmount(() => {
  stopLogisticsPolling()
})
</script>

<style scoped>
.hl-order-detail {
  max-width: 1080px;
}

.hl-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.hl-order-detail .actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.ship-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  margin-bottom: 12px;
}

.hl-product-snapshot {
  display: flex;
  align-items: center;
  gap: 16px;
}

.hl-order-image {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
}

.hl-snapshot-info {
  min-width: 0;
}

.hl-snapshot-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--hl-text-regular);
  margin-bottom: 4px;
}

.hl-snapshot-specs {
  color: var(--hl-text-secondary);
  font-size: 13px;
}

.hl-image-placeholder {
  width: 80px;
  height: 80px;
  background: var(--hl-bg-tertiary);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--hl-text-placeholder);
}

.trace-section {
  margin-top: 20px;
}

.trace-title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--hl-text-regular);
}

@media (max-width: 900px) {
  .ship-grid {
    grid-template-columns: 1fr;
  }
}
</style>
