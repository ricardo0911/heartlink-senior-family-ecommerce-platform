<template>
  <div class="hl-product-edit">
    <el-page-header
      @back="router.back()"
      title="返回"
      :content="isEdit ? '编辑商品' : '新增商品'"
      style="margin-bottom: 20px"
    />

    <el-card shadow="hover" class="hl-card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="116px"
        style="max-width: 900px"
        v-loading="loading"
        class="hl-form"
      >
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入商品名称" />
        </el-form-item>

        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="分类" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
                <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源类型" prop="sourceType">
              <el-select v-model="form.sourceType" style="width: 100%">
                <el-option v-for="item in sourceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="供应商">
              <el-select
                v-model="form.supplierId"
                clearable
                :disabled="isSelfOperated"
                :placeholder="isSelfOperated ? '自营商品无需选择' : '请选择供应商'"
                style="width: 100%"
              >
                <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name" :value="supplier.id">
                  <div class="hl-select-option">
                    <span>{{ supplier.name }}</span>
                    <span class="hl-option-sub">{{ supplier.code }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="履约方式">
              <el-select v-model="form.deliveryMode" style="width: 100%">
                <el-option v-for="item in deliveryModeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="售价" prop="price">
              <el-input-number v-model="form.price" :precision="2" :min="0" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原价">
              <el-input-number v-model="form.originalPrice" :precision="2" :min="0" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="采购价">
              <el-input-number v-model="form.purchasePrice" :precision="2" :min="0" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="经营口径">
              <div class="hl-inline-stack">
                <div class="hl-inline-text">{{ businessModeText }}</div>
                <div class="hl-inline-sub">{{ businessHintText }}</div>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <template v-if="!isSelfOperated">
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="税率模式">
                <el-select v-model="form.taxRateMode" style="width: 100%">
                  <el-option label="继承供应商" value="INHERIT_SUPPLIER" />
                  <el-option label="商品单独覆盖" value="CUSTOM" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="有效税率">
                <div class="hl-inline-stack">
                  <div class="hl-inline-text">{{ effectiveTaxRateText }}</div>
                  <div class="hl-inline-sub">{{ effectiveTaxRateHint }}</div>
                </div>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row v-if="form.taxRateMode === 'CUSTOM'" :gutter="24">
            <el-col :span="12">
              <el-form-item label="商品税率(%)">
                <el-input-number
                  v-model="form.taxRate"
                  :min="0"
                  :max="100"
                  :precision="2"
                  :step="0.5"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="外部SKU编号">
                <el-input v-model="form.externalSkuId" placeholder="用于对接供应商商品编号" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item v-else label="外部SKU编号">
            <el-input v-model="form.externalSkuId" placeholder="用于对接供应商商品编号" />
          </el-form-item>
        </template>

        <el-form-item v-else label="毛利预估">
          <div class="hl-inline-stack">
            <div class="hl-inline-text">{{ grossMarginText }}</div>
            <div class="hl-inline-sub">自营商品按售价减采购价计算毛利，退款后会在退款完成当月冲回。</div>
          </div>
        </el-form-item>

        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="来源状态">
              <el-select v-model="form.sourceStatus" style="width: 100%">
                <el-option label="有效" value="ACTIVE" />
                <el-option label="暂停" value="PAUSED" />
                <el-option label="下线" value="OFFLINE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存" prop="stock">
              <el-input-number v-model="form.stock" :min="0" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="商品图片">
          <el-input
            v-model="imagesText"
            type="textarea"
            :rows="2"
            placeholder="图片 URL，多个用逗号分隔"
          />
          <div class="hl-upload-row">
            <el-upload action="#" accept="image/*" :show-file-list="false" :http-request="handleImageUpload">
              <el-button type="primary" plain>上传图片</el-button>
            </el-upload>
            <span class="hl-upload-tip">支持本地图片上传，上传成功后会自动回填到上方输入框</span>
          </div>
          <div class="hl-image-list" v-if="previewImages.length > 0">
            <div class="hl-image-item" v-for="(img, index) in previewImages" :key="img + index">
              <el-image :src="img" fit="cover" class="hl-image-preview" />
              <el-button size="small" type="danger" link @click="removeImage(index)">删除</el-button>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="商品描述">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="商品描述" />
        </el-form-item>

        <el-form-item label="健康标签">
          <el-select
            v-model="form.healthTags"
            multiple
            filterable
            allow-create
            placeholder="选择或输入健康标签"
            style="width: 100%"
          >
            <el-option v-for="tag in healthTagOptions" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>

        <el-form-item label="警告标签">
          <el-select
            v-model="form.warningTags"
            multiple
            filterable
            allow-create
            placeholder="选择或输入警告标签"
            style="width: 100%"
          >
            <el-option v-for="tag in warningTagOptions" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSave" :loading="saving" class="hl-btn">保存</el-button>
          <el-button @click="router.push('/product')" class="hl-btn">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCategoryList, getProduct, getSupplierList, saveProduct, uploadImage } from '../../api'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const loading = ref(false)
const saving = ref(false)
const categories = ref([])
const suppliers = ref([])
const imagesText = ref('')

const isEdit = computed(() => Boolean(route.params.id))
const isSelfOperated = computed(() => form.sourceType === 'SELF_OPERATED')
const currentSupplier = computed(() => suppliers.value.find((item) => item.id === form.supplierId) || null)

const sourceTypeOptions = [
  { label: '平台自营', value: 'SELF_OPERATED' },
  { label: '精选供货', value: 'CURATED_SUPPLIER' },
  { label: '渠道合作', value: 'CHANNEL_PARTNER' }
]

const deliveryModeOptions = [
  { label: '平台发货', value: 'PLATFORM_SHIP' },
  { label: '平台转运', value: 'PLATFORM_FORWARD' },
  { label: '供应商直发', value: 'SUPPLIER_DIRECT' }
]

const healthTagOptions = ['低糖', '无盐', '低脂', '高纤维', '易消化', '易操作']
const warningTagOptions = ['含糖高', '含盐高', '操作复杂', '需冷藏']

const parseImageList = (value) => String(value || '')
  .split(',')
  .map((item) => item.trim())
  .filter(Boolean)

const previewImages = computed(() => parseImageList(imagesText.value))

const grossMarginText = computed(() => {
  if (form.price == null || form.purchasePrice == null) return '-'
  return `¥${(Number(form.price) - Number(form.purchasePrice)).toFixed(2)}`
})

const effectiveTaxRate = computed(() => {
  if (isSelfOperated.value) return null
  if (form.taxRateMode === 'CUSTOM') return form.taxRate
  return currentSupplier.value?.defaultTaxRate ?? null
})

const effectiveTaxRateText = computed(() => {
  if (effectiveTaxRate.value == null || effectiveTaxRate.value === '') return '未配置'
  return `${Number(effectiveTaxRate.value).toFixed(2)}%`
})

const effectiveTaxRateHint = computed(() => {
  if (isSelfOperated.value) return '自营商品不使用税率抽成'
  if (form.taxRateMode === 'CUSTOM') return '当前按商品单独税率计算第三方收益'
  return currentSupplier.value ? `当前继承供应商默认税率：${currentSupplier.value.name}` : '请先绑定供应商后再继承默认税率'
})

const businessModeText = computed(() => (
  isSelfOperated.value ? '自营商品：统计销售额和毛利' : '第三方商品：按税率抽成统计收益'
))

const businessHintText = computed(() => (
  isSelfOperated.value
    ? '完成收货后计入销售额和毛利，退款完成后在退款当月冲回。'
    : '完成收货后按订单实付金额 × 税率计入收益，退款完成后在退款当月冲回。'
))

const form = reactive({
  id: null,
  name: '',
  categoryId: null,
  supplierId: null,
  price: 0,
  originalPrice: 0,
  purchasePrice: null,
  sourceType: 'SELF_OPERATED',
  taxRateMode: 'INHERIT_SUPPLIER',
  taxRate: null,
  externalSkuId: '',
  sourceStatus: 'ACTIVE',
  deliveryMode: 'PLATFORM_SHIP',
  stock: 0,
  description: '',
  healthTags: [],
  warningTags: [],
  status: 1
})

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  price: [{ required: true, message: '请输入售价', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }]
}

watch(() => form.sourceType, (value) => {
  if (value === 'SELF_OPERATED') {
    form.supplierId = null
    form.taxRateMode = 'INHERIT_SUPPLIER'
    form.taxRate = null
    form.deliveryMode = 'PLATFORM_SHIP'
    return
  }

  if (!form.supplierId && suppliers.value.length > 0) {
    form.supplierId = suppliers.value[0].id
  }
  if (!form.taxRateMode) {
    form.taxRateMode = 'INHERIT_SUPPLIER'
  }
  if (!form.deliveryMode || form.deliveryMode === 'PLATFORM_SHIP') {
    form.deliveryMode = 'PLATFORM_FORWARD'
  }
})

watch(() => form.supplierId, (supplierId) => {
  if (!supplierId) {
    if (!isSelfOperated.value) {
      form.taxRateMode = 'INHERIT_SUPPLIER'
    }
    return
  }
  if (isSelfOperated.value) {
    form.sourceType = 'CURATED_SUPPLIER'
  }
})

const loadBaseData = async () => {
  const [categoryRes, supplierRes] = await Promise.all([
    getCategoryList(),
    getSupplierList()
  ])
  categories.value = (categoryRes.data || []).filter((item) => item.status === 1)
  suppliers.value = supplierRes.data || []
}

const loadProduct = async () => {
  if (!route.params.id) return
  loading.value = true
  try {
    const res = await getProduct(route.params.id)
    const product = res.data || {}
    Object.assign(form, {
      id: product.id,
      name: product.name,
      categoryId: product.categoryId,
      supplierId: product.supplierId,
      price: product.price,
      originalPrice: product.originalPrice,
      purchasePrice: product.purchasePrice,
      sourceType: product.sourceType || 'SELF_OPERATED',
      taxRateMode: product.taxRateMode || 'INHERIT_SUPPLIER',
      taxRate: product.taxRate == null ? null : Number(product.taxRate),
      externalSkuId: product.externalSkuId || '',
      sourceStatus: product.sourceStatus || 'ACTIVE',
      deliveryMode: product.deliveryMode || 'PLATFORM_SHIP',
      stock: product.stock,
      description: product.description,
      healthTags: product.healthTags || [],
      warningTags: product.warningTags || [],
      status: product.status
    })
    imagesText.value = (product.images || []).join(', ')
  } finally {
    loading.value = false
  }
}

const validateBeforeSubmit = () => {
  if (!isSelfOperated.value && !form.supplierId) {
    ElMessage.warning('第三方商品必须绑定供应商')
    return false
  }
  if (!isSelfOperated.value && form.taxRateMode === 'CUSTOM' && (form.taxRate === null || form.taxRate === undefined)) {
    ElMessage.warning('请选择商品税率')
    return false
  }
  return true
}

const handleSave = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!validateBeforeSubmit()) return

  saving.value = true
  try {
    await saveProduct({
      ...form,
      supplierId: isSelfOperated.value ? null : form.supplierId,
      taxRateMode: isSelfOperated.value ? null : form.taxRateMode,
      taxRate: isSelfOperated.value ? null : (form.taxRate == null ? null : Number(form.taxRate)),
      externalSkuId: isSelfOperated.value ? '' : form.externalSkuId,
      images: parseImageList(imagesText.value)
    })
    ElMessage.success('保存成功')
    router.push('/product')
  } finally {
    saving.value = false
  }
}

const handleImageUpload = async (options) => {
  try {
    const res = await uploadImage(options.file)
    const url = res?.data?.relativeUrl || res?.data?.url
    if (!url) {
      throw new Error('上传成功但未返回图片地址')
    }
    const list = parseImageList(imagesText.value)
    if (!list.includes(url)) {
      list.push(url)
      imagesText.value = list.join(', ')
    }
    ElMessage.success('图片上传成功')
    if (typeof options.onSuccess === 'function') {
      options.onSuccess(res)
    }
  } catch (error) {
    ElMessage.error(error?.message || '图片上传失败')
    if (typeof options.onError === 'function') {
      options.onError(error)
    }
  }
}

const removeImage = (index) => {
  const list = parseImageList(imagesText.value)
  list.splice(index, 1)
  imagesText.value = list.join(', ')
}

onMounted(async () => {
  await loadBaseData()
  await loadProduct()
})
</script>

<style scoped>
.hl-product-edit {
  max-width: 920px;
}

.hl-select-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.hl-option-sub {
  color: var(--hl-text-secondary);
  font-size: 12px;
}

.hl-inline-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hl-inline-text {
  color: var(--hl-text-primary);
  font-weight: 600;
}

.hl-inline-sub {
  color: var(--hl-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.hl-upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.hl-upload-tip {
  color: #909399;
  font-size: 12px;
}

.hl-image-list {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.hl-image-item {
  width: 120px;
}

.hl-image-preview {
  width: 120px;
  height: 120px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}
</style>
