<template>
  <div class="hl-user-list">
    <el-card shadow="hover" class="hl-search-card">
      <el-form :inline="true" :model="queryParams" class="hl-form">
        <el-form-item label="关键字">
          <el-input
            v-model="queryParams.keyword"
            placeholder="昵称 / 手机号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" class="hl-btn" @click="handleSearch">搜索</el-button>
          <el-button class="hl-btn" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" class="hl-table">
      <template #header>
        <div class="hl-card-header">
          <span>{{ pageTitle }}</span>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />

        <el-table-column label="用户信息" min-width="240">
          <template #default="{ row }">
            <div class="hl-user-main">
              <el-avatar :src="row.avatar" :size="40">
                {{ avatarFallback(row.nickname) }}
              </el-avatar>
              <div class="hl-user-text">
                <div class="hl-user-name">{{ row.nickname || `${roleLabel(row.role)}用户` }}</div>
                <div class="hl-user-sub">{{ row.phone || '-' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag effect="plain">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="积分" width="100">
          <template #default="{ row }">
            {{ formatPoints(row.points) }}
          </template>
        </el-table-column>

        <el-table-column v-if="isChildPage" label="信用分" width="110">
          <template #default="{ row }">
            <el-tag :type="creditScoreTagType(row.creditScore)" effect="light">
              {{ formatCreditScore(row.creditScore) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column v-if="isChildPage" label="权益等级" width="120">
          <template #default="{ row }">
            <span class="hl-user-member-level">{{ memberLevelLabel(row.memberLevel) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :class="row.status === 1 ? 'hl-tag-online' : 'hl-tag-offline'" effect="light">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="注册时间" width="180">
          <template #default="{ row }">
            {{ formatDatetime(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="default" link type="primary" :icon="View" @click="openBindingsDialog(row)">
              查看{{ bindingTargetLabel }}
            </el-button>
            <el-button
              v-if="isChildPage"
              size="default"
              link
              type="primary"
              @click="handleCreditScoreChange(row)"
            >
              信用分
            </el-button>
            <el-button
              size="default"
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="handleStatusChange(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && tableData.length === 0"
        :description="`暂无${pageTitle}数据`"
        class="hl-empty"
      />

      <el-pagination
        v-model:current-page="queryParams.page"
        v-model:page-size="queryParams.size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <el-dialog
      v-model="bindingsDialog.visible"
      :title="`${bindingsDialog.userName || '-'} 的${bindingTargetLabel}绑定`"
      width="720px"
    >
      <el-table :data="bindingsDialog.rows" v-loading="bindingsDialog.loading" stripe>
        <el-table-column prop="bindId" label="绑定ID" width="90" />

        <el-table-column :label="`${bindingTargetLabel}信息`" min-width="240">
          <template #default="{ row }">
            <div class="hl-user-text">
              <div class="hl-user-name">{{ row.nickname || '-' }}</div>
              <div class="hl-user-sub">{{ row.phone || '-' }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="relation" label="关系" width="120" />

        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            {{ roleLabel(row.role) }}
          </template>
        </el-table-column>

        <el-table-column label="绑定时间" width="180">
          <template #default="{ row }">
            {{ formatDatetime(row.bindTime) }}
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!bindingsDialog.loading && bindingsDialog.rows.length === 0"
        :description="`暂无${bindingTargetLabel}绑定`"
        class="hl-empty"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserBindings, getUserPage, updateUserCreditScore, updateUserStatus } from '../../api'

const route = useRoute()

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: ''
})

const bindingsDialog = reactive({
  visible: false,
  loading: false,
  userName: '',
  rows: []
})

const roleMap = {
  CHILD: '子女',
  PARENT: '长辈',
  ADMIN: '管理员'
}

const benefitLevelMap = {
  0: '普通',
  1: '银卡',
  2: '金卡',
  3: '钻石'
}

const pageRole = computed(() => String(route.meta?.role || 'CHILD').toUpperCase())
const isChildPage = computed(() => pageRole.value === 'CHILD')
const pageTitle = computed(() => roleMap[pageRole.value] ? `${roleMap[pageRole.value]}管理` : '用户管理')
const bindingTargetLabel = computed(() => (isChildPage.value ? '长辈' : '子女'))

const roleLabel = (role) => roleMap[String(role || '').toUpperCase()] || role || '-'
const memberLevelLabel = (level) => benefitLevelMap[level] || '-'

const avatarFallback = (nickname) => {
  const text = String(nickname || '').trim()
  return text ? text.slice(0, 1) : 'U'
}

const formatPoints = (value) => (value == null ? '-' : value)
const formatCreditScore = (value) => (value == null ? '100' : String(value))

const creditScoreTagType = (value) => {
  const score = Number(value)
  if (Number.isFinite(score) && score < 50) return 'danger'
  if (Number.isFinite(score) && score > 90) return 'success'
  return 'warning'
}

const formatDatetime = (value) => {
  if (!value) return '-'
  const date = new Date(String(value).replace('T', ' ').replace(/-/g, '/'))
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      page: queryParams.page,
      size: queryParams.size,
      role: pageRole.value
    }
    const keyword = String(queryParams.keyword || '').trim()
    if (keyword) {
      params.keyword = keyword
    }
    const res = await getUserPage(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    tableData.value = []
    total.value = 0
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
  queryParams.keyword = ''
  loadData()
}

const handleStatusChange = async (row) => {
  const nextStatus = row.status === 1 ? 0 : 1
  try {
    await updateUserStatus(row.id, nextStatus)
    ElMessage.success('状态已更新')
    row.status = nextStatus
  } catch {
    ElMessage.error('状态更新失败')
  }
}

const handleCreditScoreChange = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入 0 到 100 之间的信用分',
      '调整信用分',
      {
        inputValue: formatCreditScore(row.creditScore),
        inputPattern: /^(100|[1-9]?\d)$/,
        inputErrorMessage: '请输入 0 到 100 的整数'
      }
    )
    const nextScore = Number(value)
    await updateUserCreditScore(row.id, nextScore)
    row.creditScore = nextScore
    ElMessage.success('信用分已更新')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('信用分更新失败')
    }
  }
}

const openBindingsDialog = async (row) => {
  bindingsDialog.visible = true
  bindingsDialog.loading = true
  bindingsDialog.userName = row.nickname || `${roleLabel(row.role)}用户`
  bindingsDialog.rows = []
  try {
    const res = await getUserBindings(row.id)
    bindingsDialog.rows = Array.isArray(res.data) ? res.data : []
  } catch {
    bindingsDialog.rows = []
    ElMessage.error('绑定关系加载失败')
  } finally {
    bindingsDialog.loading = false
  }
}

watch(
  pageRole,
  () => {
    queryParams.page = 1
    queryParams.size = 10
    queryParams.keyword = ''
    loadData()
  },
  { immediate: true }
)
</script>

<style scoped>
.hl-user-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hl-user-text {
  min-width: 0;
}

.hl-user-name {
  font-weight: 600;
  color: var(--hl-text-primary);
}

.hl-user-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--hl-text-secondary);
}

.hl-user-member-level {
  color: var(--hl-primary);
  font-weight: 600;
}
</style>
