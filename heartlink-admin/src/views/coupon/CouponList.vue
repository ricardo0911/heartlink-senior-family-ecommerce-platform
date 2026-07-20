<template>
  <div class="hl-coupon-list">
    <el-card shadow="hover" class="hl-search-card">
      <el-form :inline="true" class="hl-form">
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px" @change="handleSearch">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :icon="Search" class="hl-btn">搜索</el-button>
          <el-button @click="resetSearch" class="hl-btn">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-button type="primary" @click="openDialog()" :icon="Plus" class="hl-btn" style="margin-bottom: 16px;">
      新增优惠券
    </el-button>

    <el-card shadow="hover" class="hl-table">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <span :class="['hl-coupon-type', row.type === 'FIXED' ? 'hl-type-fixed' : 'hl-type-percent']">
              {{ row.type === 'FIXED' ? '满减券' : '折扣券' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="面值" width="120">
          <template #default="{ row }">
            <span :class="['hl-coupon-value', row.type === 'FIXED' ? 'hl-value-fixed' : 'hl-value-percent']">
              {{ row.type === 'FIXED' ? `¥${row.value}` : `${row.value}折` }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最低消费" width="120">
          <template #default="{ row }">
            <span style="color: var(--hl-text-regular);">¥{{ row.minAmount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="有效期" width="220">
          <template #default="{ row }">
            <div style="font-size: var(--hl-text-sm); color: var(--hl-text-secondary); line-height: 1.6;">
              <div>{{ formatDate(row.startTime) }}</div>
              <div>~ {{ formatDate(row.endTime) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="剩余/总量" width="140">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.round((row.remainCount / row.totalCount) * 100)"
              :format="() => `${row.remainCount}/${row.totalCount}`"
              :stroke-width="8"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="default" link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button size="default" link type="success" @click="openIssueDialog(row)">发放</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.page"
        v-model:page-size="queryParams.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="550px" class="hl-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="hl-form">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="优惠券名称" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择类型" style="width: 100%">
            <el-option label="满减券" value="FIXED" />
            <el-option label="折扣券" value="PERCENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="面值" prop="value">
          <el-input-number v-model="form.value" :precision="2" :min="0" />
          <span style="margin-left: 8px; color: var(--hl-text-secondary);">
            {{ form.type === 'FIXED' ? '元' : '折 (如8.5)' }}
          </span>
        </el-form-item>
        <el-form-item label="最低消费">
          <el-input-number v-model="form.minAmount" :precision="2" :min="0" />
        </el-form-item>
        <el-form-item label="有效期">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="总数量" prop="totalCount">
          <el-input-number v-model="form.totalCount" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving" class="hl-btn">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="issueDialogVisible" title="发放优惠券" width="560px" class="hl-dialog">
      <el-form label-width="100px" class="hl-form">
        <el-form-item label="优惠券">
          <el-input :model-value="issueCouponTarget?.name || ''" disabled />
        </el-form-item>
        <el-form-item label="角色筛选">
          <el-select v-model="issueQuery.role" style="width: 180px" @change="loadIssueUsers">
            <el-option label="全部" value="" />
            <el-option label="长辈" value="PARENT" />
            <el-option label="子女" value="CHILD" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户搜索">
          <el-input v-model="issueQuery.keyword" placeholder="输入ID / 昵称 / 手机号" clearable @keyup.enter="loadIssueUsers">
            <template #append>
              <el-button @click="loadIssueUsers">搜索</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="选择用户" required>
          <el-select
            v-model="issueForm.userId"
            placeholder="请选择领取用户"
            filterable
            style="width: 100%"
            :loading="issueUserLoading"
          >
            <el-option
              v-for="user in issueUserOptions"
              :key="user.id"
              :label="formatIssueUserLabel(user)"
              :value="user.id"
            />
          </el-select>
          <div style="margin-top: 8px; color: var(--hl-text-secondary); font-size: 12px;">
            仅显示正常状态的长辈/子女用户
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleIssueCoupon" :loading="issuing" class="hl-btn">确认发放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getCouponPage, saveCoupon, updateCouponStatus, issueCoupon, getUserPage } from '../../api'

const tableData = ref([])
const loading = ref(false)
const saving = ref(false)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('新增优惠券')
const formRef = ref()
const dateRange = ref(null)
const issueDialogVisible = ref(false)
const issuing = ref(false)
const issueUserLoading = ref(false)
const issueCouponTarget = ref(null)
const issueUserOptions = ref([])

const queryParams = reactive({ page: 1, size: 10, status: null })
const issueQuery = reactive({ keyword: '', role: '' })
const issueForm = reactive({ userId: null })

const form = reactive({
  id: null,
  name: '',
  type: 'FIXED',
  value: 0,
  minAmount: 0,
  totalCount: 100,
  startTime: null,
  endTime: null,
  status: 1
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  value: [{ required: true, message: '请输入面值', trigger: 'blur' }],
  totalCount: [{ required: true, message: '请输入数量', trigger: 'blur' }]
}

const formatDate = (dt) => {
  if (!dt) return '-'
  return dt.replace('T', ' ').substring(0, 16)
}

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      page: queryParams.page,
      size: queryParams.size
    }
    if (queryParams.status !== null && queryParams.status !== undefined && queryParams.status !== '') {
      params.status = queryParams.status
    }
    const res = await getCouponPage(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) { /* ignore */ }
  loading.value = false
}

const handleSearch = () => {
  queryParams.page = 1
  loadData()
}

const resetSearch = () => {
  queryParams.page = 1
  queryParams.status = null
  loadData()
}

const openDialog = (row) => {
  if (row) {
    dialogTitle.value = '编辑优惠券'
    Object.assign(form, {
      id: row.id, name: row.name, type: row.type, value: row.value,
      minAmount: row.minAmount, totalCount: row.totalCount, status: row.status
    })
    dateRange.value = row.startTime && row.endTime ? [row.startTime, row.endTime] : null
  } else {
    dialogTitle.value = '新增优惠券'
    Object.assign(form, {
      id: null, name: '', type: 'FIXED', value: 0,
      minAmount: 0, totalCount: 100, status: 1
    })
    dateRange.value = null
  }
  dialogVisible.value = true
}

const formatIssueUserLabel = (user) => {
  const roleMap = { PARENT: '长辈', CHILD: '子女' }
  const roleText = roleMap[user.role] || user.role || '未知'
  const name = user.nickname || '未命名'
  const phone = user.phone || '无手机号'
  return `#${user.id} ${name}（${roleText} / ${phone}）`
}

const loadIssueUsers = async () => {
  issueUserLoading.value = true
  try {
    const params = { page: 1, size: 100 }
    if (issueQuery.keyword.trim()) {
      params.keyword = issueQuery.keyword.trim()
    }
    if (issueQuery.role) {
      params.role = issueQuery.role
    }
    const res = await getUserPage(params)
    const records = res.data?.records || []
    issueUserOptions.value = records.filter(user => user && user.role !== 'ADMIN' && user.status === 1)
  } catch (e) {
    issueUserOptions.value = []
  } finally {
    issueUserLoading.value = false
  }
}

const openIssueDialog = async (row) => {
  issueCouponTarget.value = row
  issueForm.userId = null
  issueQuery.keyword = ''
  issueQuery.role = ''
  issueDialogVisible.value = true
  await loadIssueUsers()
}

const handleSave = async () => {
  try { await formRef.value.validate() } catch { return }

  saving.value = true
  try {
    const data = { ...form }
    if (dateRange.value) {
      data.startTime = dateRange.value[0]
      data.endTime = dateRange.value[1]
    }
    if (!data.id) {
      data.remainCount = data.totalCount
    }
    await saveCoupon(data)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) { /* ignore */ }
  saving.value = false
}

const handleStatusChange = async (row, val) => {
  try {
    await updateCouponStatus(row.id, val)
    ElMessage.success('更新成功')
    row.status = val
  } catch (e) { loadData() }
}

const handleIssueCoupon = async () => {
  if (!issueCouponTarget.value?.id) return
  if (!issueForm.userId) {
    ElMessage.warning('请选择领取用户')
    return
  }

  issuing.value = true
  try {
    await issueCoupon(issueCouponTarget.value.id, { userId: issueForm.userId })
    ElMessage.success('发放成功')
    issueDialogVisible.value = false
    loadData()
  } catch (e) { /* ignore */ }
  issuing.value = false
}

onMounted(() => loadData())
</script>

<style scoped>
/* Additional page-specific styles */
</style>
