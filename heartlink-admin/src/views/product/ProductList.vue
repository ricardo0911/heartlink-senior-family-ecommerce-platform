<template>
  <div class="hl-product-list">
    <el-card shadow="hover" class="hl-search-card">
      <el-form :inline="true" :model="query" class="hl-form">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="商品名称" clearable />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.categoryId" placeholder="全部分类" clearable style="width: 160px;">
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="供货类型">
          <el-select
            v-model="query.supplyType"
            placeholder="全部类型"
            clearable
            style="width: 148px;"
            @change="handleSupplyTypeChange"
          >
            <el-option label="自营" value="SELF_OPERATED" />
            <el-option label="第三方" value="THIRD_PARTY" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-select
            v-model="query.supplierId"
            :disabled="query.supplyType === 'SELF_OPERATED'"
            :placeholder="query.supplyType === 'SELF_OPERATED' ? '自供应无需选择' : '全部供应商'"
            clearable
            style="width: 180px;"
          >
            <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name" :value="supplier.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px;">
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" class="hl-btn" @click="handleSearch">搜索</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="success" :icon="Plus" class="hl-btn" @click="$router.push('/product/edit')">新增商品</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" class="hl-table">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="图片" width="100">
          <template #default="{ row }">
            <div class="hl-image hl-image-md">
              <el-image :src="firstImage(row.images)" fit="cover">
                <template #error>
                  <div class="hl-image-placeholder">无图</div>
                </template>
              </el-image>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="商品信息" min-width="220">
          <template #default="{ row }">
            <div class="hl-product-info">
              <div class="hl-product-name">{{ row.name }}</div>
              <div class="hl-product-sub">{{ getCategoryName(row.categoryId) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="供货信息" min-width="220">
          <template #default="{ row }">
            <div class="hl-product-info">
              <div class="hl-product-name">{{ sourceTypeLabel(row.sourceType, row.supplierId) }}</div>
              <div class="hl-product-sub">
                {{ getSupplierName(row.supplierId) }} / {{ externalSkuLabel(row) }}
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="售价" width="110">
          <template #default="{ row }">
            <div class="hl-product-price">¥{{ formatMoney(row.price) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="经营口径" min-width="240">
          <template #default="{ row }">
            <div class="hl-product-info">
              <div class="hl-product-name">{{ businessHeadline(row) }}</div>
              <div class="hl-product-sub">{{ businessDetail(row) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="100" />
        <el-table-column prop="sales" label="销量" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :class="row.status === 1 ? 'hl-tag-online' : 'hl-tag-offline'" effect="light">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="default" type="primary" link :icon="Edit" @click="$router.push(`/product/edit/${row.id}`)">编辑</el-button>
            <el-button size="default" :type="row.status === 1 ? 'warning' : 'success'" link @click="toggleStatus(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-popconfirm title="确定删除该商品吗？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="default" type="danger" link :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { deleteProduct, getCategoryList, getProductPage, getSupplierList, updateProductStatus } from '../../api'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const categories = ref([])
const suppliers = ref([])

const query = reactive({
  keyword: '',
  categoryId: null,
  supplyType: null,
  supplierId: null,
  status: null,
  page: 1,
  size: 10
})

const STATIC_PRODUCT_IMAGES = new Set([
  '1.png', '2.png', '3.png', '4.png',
  'band.png', 'blood-pressure.png', 'cane.png', 'foot-bath.png',
  'kettle.png', 'kneepad.png', 'oatmeal.png', 'protein.png',
  'smartphone.png', 'sweater.png'
])

const DEFAULT_IMAGE = '/static/products/oatmeal.png'

const firstImage = (images) => {
  const listValue = typeof images === 'string' ? images.split(',') : images
  const raw = String(listValue?.[0] || '').trim()
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

const getCategoryName = (categoryId) => {
  const category = categories.value.find((item) => item.id === categoryId)
  return category ? category.name : '-'
}

const getSupplierName = (supplierId) => {
  if (!supplierId) return '平台自营'
  const supplier = suppliers.value.find((item) => item.id === supplierId)
  return supplier ? supplier.name : `供应商#${supplierId}`
}

const sourceTypeLabel = (value, supplierId) => ({
  SELF_OPERATED: '自营商品',
  CURATED_SUPPLIER: '第三方商品',
  CHANNEL_PARTNER: '第三方商品'
}[value || (!supplierId ? 'SELF_OPERATED' : '')] || value || '-')

const externalSkuLabel = (row) => {
  if (row?.externalSkuId) return row.externalSkuId
  return row?.supplierId ? '未填写外部SKU' : '自营商品'
}

const formatMoney = (value) => {
  const amount = Number(value || 0)
  return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
}

const formatTaxRate = (value) => {
  if (value === null || value === undefined || value === '') return '未配置'
  const rate = Number(value)
  return Number.isFinite(rate) ? `${rate.toFixed(2)}%` : '未配置'
}

const taxModeLabel = (row) => {
  if ((row.sourceType || (!row.supplierId ? 'SELF_OPERATED' : '')) === 'SELF_OPERATED') {
    return '自营'
  }
  return row.taxRateMode === 'CUSTOM' ? '商品覆盖' : '供应商默认'
}

const effectiveTaxRate = (row) => {
  if ((row.sourceType || (!row.supplierId ? 'SELF_OPERATED' : '')) === 'SELF_OPERATED') {
    return null
  }
  if (row.taxRateMode === 'CUSTOM' && row.taxRate != null) {
    return row.taxRate
  }
  const supplier = suppliers.value.find((item) => item.id === row.supplierId)
  return supplier?.defaultTaxRate ?? row.taxRate ?? null
}

const businessHeadline = (row) => {
  if ((row.sourceType || (!row.supplierId ? 'SELF_OPERATED' : '')) === 'SELF_OPERATED') {
    return `成本 ¥${row.purchasePrice != null ? formatMoney(row.purchasePrice) : '-'} / 毛利 ${row.grossMargin != null ? `¥${formatMoney(row.grossMargin)}` : '待补'}`
  }
  return `税率 ${formatTaxRate(effectiveTaxRate(row))} / ${taxModeLabel(row)}`
}

const businessDetail = (row) => {
  if ((row.sourceType || (!row.supplierId ? 'SELF_OPERATED' : '')) === 'SELF_OPERATED') {
    return '自营订单按实付金额统计销售额，按实付金额减采购成本统计毛利。'
  }
  return '第三方订单按实付金额 × 税率统计抽成收益。'
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getProductPage({ ...query })
    list.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.page = 1
  loadData()
}

const handleSupplyTypeChange = (value) => {
  if (value === 'SELF_OPERATED') {
    query.supplierId = null
  }
}

const toggleStatus = async (row) => {
  const nextStatus = row.status === 1 ? 0 : 1
  await updateProductStatus(row.id, nextStatus)
  row.status = nextStatus
  ElMessage.success('状态已更新')
}

const handleDelete = async (id) => {
  await deleteProduct(id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(async () => {
  const [categoryRes, supplierRes] = await Promise.all([
    getCategoryList(),
    getSupplierList()
  ])
  categories.value = (categoryRes.data || []).filter((item) => item.status === 1)
  suppliers.value = supplierRes.data || []
  loadData()
})
</script>

<style scoped>
.hl-image-placeholder {
  width: 100%;
  height: 100%;
  background: var(--hl-bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--hl-text-placeholder);
  font-size: 12px;
}

.hl-product-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hl-product-name {
  font-weight: 600;
  color: var(--hl-text-primary);
}

.hl-product-sub {
  font-size: 12px;
  color: var(--hl-text-secondary);
}

.hl-product-price {
  font-weight: 700;
  color: var(--hl-primary-ink);
}
</style>
