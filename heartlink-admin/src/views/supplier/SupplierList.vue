<template>
  <div class="hl-supplier-list">
    <el-card shadow="hover" class="hl-search-card">
      <el-form :inline="true" :model="query" class="hl-form">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="供应商名称/编号" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px;">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" class="hl-btn" @click="loadData">搜索</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="success" :icon="Plus" class="hl-btn" @click="openDialog()">新增供应商</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" class="hl-table">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="code" label="编号" width="140" />
        <el-table-column label="供应商信息" min-width="220">
          <template #default="{ row }">
            <div class="hl-supplier-main">
              <div class="hl-supplier-name">{{ row.name }}</div>
              <div class="hl-supplier-sub">{{ supplierTypeLabel(row.type) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="联系人" min-width="180">
          <template #default="{ row }">
            <div>{{ row.contactName || '-' }}</div>
            <div class="hl-supplier-sub">{{ row.contactPhone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="结算方式" width="140">
          <template #default="{ row }">
            {{ settlementModeLabel(row.settlementMode) }}
          </template>
        </el-table-column>
        <el-table-column label="默认税率" width="140">
          <template #default="{ row }">
            {{ formatTaxRate(row.defaultTaxRate) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :class="row.status === 1 ? 'hl-tag-online' : 'hl-tag-offline'" effect="light">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="default" link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button
              size="default"
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-popconfirm title="确定删除该供应商吗？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="default" link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" class="hl-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="108px" class="hl-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="编号" prop="code">
              <el-input v-model="form.code" placeholder="如：SUP-001" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="类型" prop="type">
              <el-select v-model="form.type" style="width: 100%">
                <el-option label="精选供货" value="CURATED_SUPPLIER" />
                <el-option label="平台渠道" value="CHANNEL_PARTNER" />
                <el-option label="品牌直供" value="BRAND_DIRECT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结算方式" prop="settlementMode">
              <el-select v-model="form.settlementMode" style="width: 100%">
                <el-option label="人工对账" value="MANUAL" />
                <el-option label="月结" value="MONTHLY" />
                <el-option label="预付" value="PREPAID" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="默认税率(%)" prop="defaultTaxRate">
              <el-input-number
                v-model="form.defaultTaxRate"
                :min="0"
                :max="100"
                :precision="2"
                :step="0.5"
                style="width: 100%"
                placeholder="第三方抽成比例"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="form.contactName" placeholder="联系人姓名" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.contactPhone" placeholder="联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="记录合作范围、税率约定、结算口径等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="hl-btn" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { deleteSupplier, getSupplierList, saveSupplier, updateSupplierStatus } from '../../api'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('新增供应商')
const formRef = ref()
const tableData = ref([])

const query = reactive({
  keyword: '',
  status: null
})

const form = reactive({
  id: null,
  name: '',
  code: '',
  type: 'CURATED_SUPPLIER',
  status: 1,
  contactName: '',
  contactPhone: '',
  settlementMode: 'MANUAL',
  defaultTaxRate: null,
  remark: ''
})

const rules = {
  name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入供应商编号', trigger: 'blur' }]
}

const supplierTypeLabel = (value) => ({
  CURATED_SUPPLIER: '精选供货',
  CHANNEL_PARTNER: '平台渠道',
  BRAND_DIRECT: '品牌直供'
}[value] || value || '-')

const settlementModeLabel = (value) => ({
  MANUAL: '人工对账',
  MONTHLY: '月结',
  PREPAID: '预付'
}[value] || value || '-')

const formatTaxRate = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const rate = Number(value)
  return Number.isFinite(rate) ? `${rate.toFixed(2)}%` : '-'
}

const resetForm = () => {
  Object.assign(form, {
    id: null,
    name: '',
    code: '',
    type: 'CURATED_SUPPLIER',
    status: 1,
    contactName: '',
    contactPhone: '',
    settlementMode: 'MANUAL',
    defaultTaxRate: null,
    remark: ''
  })
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getSupplierList({ ...query })
    tableData.value = res.data || []
  } finally {
    loading.value = false
  }
}

const openDialog = (row) => {
  resetForm()
  if (row) {
    dialogTitle.value = '编辑供应商'
    Object.assign(form, {
      ...row,
      defaultTaxRate: row.defaultTaxRate == null ? null : Number(row.defaultTaxRate)
    })
  } else {
    dialogTitle.value = '新增供应商'
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    await saveSupplier({
      ...form,
      defaultTaxRate: form.defaultTaxRate == null ? null : Number(form.defaultTaxRate)
    })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

const handleToggleStatus = async (row) => {
  const nextStatus = row.status === 1 ? 0 : 1
  await updateSupplierStatus(row.id, nextStatus)
  row.status = nextStatus
  ElMessage.success('状态已更新')
}

const handleDelete = async (id) => {
  await deleteSupplier(id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.hl-supplier-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hl-supplier-name {
  font-weight: 600;
  color: var(--hl-text-primary);
}

.hl-supplier-sub {
  font-size: 12px;
  color: var(--hl-text-secondary);
}
</style>
