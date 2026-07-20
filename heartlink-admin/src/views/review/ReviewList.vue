<template>
  <div class="hl-review-list">
    <el-card shadow="hover" class="hl-search-card">
      <el-form :inline="true" class="hl-form">
        <el-form-item label="商品ID">
          <el-input v-model="queryParams.productId" placeholder="请输入商品ID" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :icon="Search" class="hl-btn">搜索</el-button>
          <el-button @click="resetSearch" class="hl-btn">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="hover" class="hl-table">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="productId" label="商品ID" width="100" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column label="评分" width="180">
          <template #default="{ row }">
            <div class="hl-review-rating">
              <el-rate :model-value="row.rating" disabled show-score />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
        <el-table-column label="图片" width="160">
          <template #default="{ row }">
            <template v-if="row.images && row.images.length">
              <div class="hl-review-images">
                <el-image
                  v-for="(img, i) in row.images"
                  :key="i"
                  :src="img"
                  :preview-src-list="row.images"
                  fit="cover"
                />
              </div>
            </template>
            <span v-else style="color: var(--hl-text-placeholder);">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确定删除该评价?" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="default" link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tableData.length === 0" description="暂无评价数据" class="hl-empty" />

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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getReviewPage, deleteReview } from '../../api'

const tableData = ref([])
const loading = ref(false)
const total = ref(0)

const queryParams = reactive({ page: 1, size: 10, productId: '' })

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      page: queryParams.page,
      size: queryParams.size
    }
    const productId = String(queryParams.productId || '').trim()
    if (productId) {
      params.productId = productId
    }
    const res = await getReviewPage(params)
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
  queryParams.productId = ''
  loadData()
}

const handleDelete = async (id) => {
  try {
    await deleteReview(id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) { /* ignore */ }
}

onMounted(() => loadData())
</script>

<style scoped>
/* Additional page-specific styles */
</style>
