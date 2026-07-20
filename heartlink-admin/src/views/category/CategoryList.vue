<template>
  <div class="hl-category-list">
    <el-button type="primary" @click="openDialog()" :icon="Plus" class="hl-btn" style="margin-bottom: 16px;">
      新增分类
    </el-button>

    <el-card shadow="hover" class="hl-table">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column label="图标" width="120">
          <template #default="{ row }">
            <el-image
              v-if="isImageUrl(row.icon)"
              :src="row.icon"
              fit="cover"
              style="width: 36px; height: 36px; border-radius: 6px;"
            />
            <span v-else>{{ row.icon || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :class="row.status === 1 ? 'hl-tag-online' : 'hl-tag-offline'" size="default" effect="light">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="default" link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除该分类?" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="default" link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" class="hl-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" class="hl-form">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="图标名称或URL" />
          <div class="hl-upload-row">
            <el-upload
              action="#"
              accept="image/*"
              :show-file-list="false"
              :http-request="handleIconUpload"
            >
              <el-button type="primary" plain>上传图标</el-button>
            </el-upload>
            <span class="hl-upload-tip">可上传本地图片，上传后自动回填地址</span>
          </div>
          <el-image
            v-if="isImageUrl(form.icon)"
            :src="form.icon"
            fit="cover"
            style="width: 56px; height: 56px; border-radius: 8px; margin-top: 10px;"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving" class="hl-btn">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getCategoryList, saveCategory, deleteCategory, uploadImage } from '../../api'

const tableData = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('新增分类')
const formRef = ref()

const form = reactive({
  id: null,
  name: '',
  icon: '',
  sort: 0,
  status: 1
})

const rules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getCategoryList()
    tableData.value = res.data || []
  } catch (e) { /* ignore */ }
  loading.value = false
}

const openDialog = (row) => {
  if (row) {
    dialogTitle.value = '编辑分类'
    Object.assign(form, { id: row.id, name: row.name, icon: row.icon, sort: row.sort, status: row.status })
  } else {
    dialogTitle.value = '新增分类'
    Object.assign(form, { id: null, name: '', icon: '', sort: 0, status: 1 })
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  try {
    await formRef.value.validate()
  } catch { return }

  saving.value = true
  try {
    await saveCategory({ ...form })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) { /* ignore */ }
  saving.value = false
}

const handleDelete = async (id) => {
  try {
    await deleteCategory(id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) { /* ignore */ }
}

const isImageUrl = (value) => {
  const text = String(value || '').trim()
  if (!text) return false
  return /^(https?:\/\/|\/upload\/|\/static\/|data:)/i.test(text)
}

const handleIconUpload = async (options) => {
  try {
    const res = await uploadImage(options.file)
    const url = res?.data?.url || res?.data?.relativeUrl
    if (!url) {
      throw new Error('上传成功但未返回图片地址')
    }
    form.icon = url
    ElMessage.success('图标上传成功')
    if (typeof options.onSuccess === 'function') {
      options.onSuccess(res)
    }
  } catch (e) {
    ElMessage.error(e?.message || '图标上传失败')
    if (typeof options.onError === 'function') {
      options.onError(e)
    }
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.hl-upload-row {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.hl-upload-tip {
  font-size: 12px;
  color: #909399;
}
</style>
