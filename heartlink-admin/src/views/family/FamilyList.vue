<template>
  <div class="hl-family-list">
    <el-card shadow="hover" class="hl-card">
      <template #header>
        <div class="hl-card-header">
          <span>家庭绑定关系</span>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe class="hl-table">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="childId" label="子女ID" width="100" />
        <el-table-column prop="parentId" label="长辈ID" width="100" />
        <el-table-column label="关系" width="120">
          <template #default="{ row }">
            <span style="color: var(--hl-primary); font-weight: 600;">
              {{ row.relation || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="绑定码" width="120">
          <template #default="{ row }">
            <el-tag size="default" effect="plain" type="info">
              {{ row.bindCode }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="绑定时间" width="180" />
      </el-table>

      <el-empty
        v-if="!loading && tableData.length === 0"
        description="暂无绑定数据"
        class="hl-empty"
        :image-size="100"
      />

      <el-pagination
        v-if="tableData.length > 0"
        v-model:current-page="queryParams.page"
        v-model:page-size="queryParams.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getFamilyList } from '../../api'

const tableData = ref([])
const loading = ref(false)
const total = ref(0)

const queryParams = reactive({ page: 1, size: 10 })

const loadData = async () => {
  loading.value = true
  try {
    const res = await getFamilyList()
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.data?.length || 0
  } catch (e) { /* ignore */ }
  loading.value = false
}

onMounted(() => loadData())
</script>

<style scoped>
/* Additional page-specific styles */
</style>
