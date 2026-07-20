<template>
  <view class="page">
    <view class="hero-card">
      <view class="hero-badge">AI试穿</view>
      <text class="hero-title">给长辈先看上身效果</text>
      <text class="hero-desc">上传一张正面全身照，系统会按你选择的服饰类型生成试穿图。</text>
      <view class="tips-box">
        <text class="tips-item">模特图建议：单人、正面、全身、光线清晰</text>
        <text class="tips-item">服饰图建议：平铺、单件、无遮挡、少留白</text>
      </view>
    </view>

    <view class="section-card" v-if="product.image">
      <text class="section-title">当前商品</text>
      <view class="garment-card" @click="previewImages(product.image)">
        <image class="garment-image" :src="product.image" mode="aspectFill"></image>
        <view class="garment-body">
          <text class="garment-name">{{ product.name || '服饰商品' }}</text>
          <text class="garment-desc">{{ product.description || '当前商品将作为试穿服饰使用。' }}</text>
        </view>
      </view>
    </view>

    <view class="section-card">
      <text class="section-title">模特照片</text>
      <view v-if="personImagePath" class="person-preview" @click="previewImages(personImagePath)">
        <image class="person-image" :src="personImagePath" mode="aspectFill"></image>
      </view>
      <view v-else class="empty-box">
        <text class="empty-title">还没有选择模特照片</text>
        <text class="empty-desc">请上传长辈正面全身照，手脚尽量完整、无遮挡。</text>
      </view>
      <button class="secondary-btn choose-btn" @click="choosePersonImage">
        {{ personImagePath ? '重新选择照片' : '选择模特照片' }}
      </button>
    </view>

    <view class="section-card">
      <text class="section-title">试穿设置</text>

      <view class="field-block">
        <text class="field-label">服饰类型</text>
        <view class="option-grid">
          <view
            v-for="item in roleOptions"
            :key="item.value"
            class="option-chip"
            :class="{ active: garmentRole === item.value }"
            @click="selectGarmentRole(item.value)"
          >
            {{ item.label }}
          </view>
        </view>
      </view>

      <view class="field-block" v-if="canPreserveOriginal()">
        <text class="field-label">补全策略</text>
        <view class="switch-row">
          <view class="switch-copy">
            <text class="switch-title">{{ preserveHintTitle() }}</text>
            <text class="switch-desc">关闭时由模型随机生成另一半穿搭。</text>
          </view>
          <switch color="#f97316" :checked="preserveOriginalOther" @change="onPreserveChange"></switch>
        </view>
      </view>

      <view class="field-block">
        <text class="field-label">人脸策略</text>
        <view class="option-grid">
          <view
            v-for="item in faceOptions"
            :key="item.value ? 'keep' : 'new'"
            class="option-chip"
            :class="{ active: restoreFace === item.value }"
            @click="restoreFace = item.value"
          >
            {{ item.label }}
          </view>
        </view>
      </view>

      <view class="field-block">
        <text class="field-label">输出分辨率</text>
        <view class="option-grid">
          <view
            v-for="item in resolutionOptions"
            :key="item.value"
            class="option-chip"
            :class="{ active: resolution === item.value }"
            @click="resolution = item.value"
          >
            {{ item.label }}
          </view>
        </view>
      </view>
    </view>

    <view class="section-card" v-if="taskId || taskStatus">
      <view class="result-head">
        <text class="section-title result-title">任务状态</text>
        <text class="status-badge" :class="statusClass(taskStatus)">{{ statusLabel(taskStatus) }}</text>
      </view>
      <text class="status-desc">{{ statusDescription() }}</text>
      <text class="task-id" v-if="taskId">任务 ID：{{ taskId }}</text>
      <text class="error-text" v-if="errorMessage">{{ errorMessage }}</text>
    </view>

    <view class="section-card" v-if="resultImageUrl">
      <view class="result-head">
        <text class="section-title result-title">试穿结果</text>
        <text class="result-tip">点击图片可放大查看</text>
      </view>
      <view class="result-preview" @click="previewImages(resultImageUrl)">
        <image class="result-image" :src="resultImageUrl" mode="widthFix"></image>
      </view>
      <button class="secondary-btn choose-btn" @click="previewImages(resultImageUrl)">查看大图</button>
    </view>

    <view class="action-bar">
      <button
        class="primary-btn"
        :loading="submitting"
        :disabled="submitting || isTaskRunning() || !personImagePath"
        @click="submitTryOn"
      >
        {{ actionButtonText() }}
      </button>
    </view>
  </view>
</template>

<script>
import { aiApi } from '../../api/index.js'

const TRY_ON_PRODUCT_KEY = 'CHILD_TRY_ON_PRODUCT'

const ROLE_OPTIONS = [
  { value: 'top', label: '上装' },
  { value: 'bottom', label: '下装' },
  { value: 'dress', label: '连衣裙/连体衣' }
]

const FACE_OPTIONS = [
  { value: true, label: '保留原脸' },
  { value: false, label: '随机新脸' }
]

const RESOLUTION_OPTIONS = [
  { value: -1, label: '原图尺寸' },
  { value: 1024, label: '576×1024' },
  { value: 1280, label: '720×1280' }
]

const DRESS_KEYWORDS = /(连衣|裙|礼服|dress|gown|jumpsuit|连体)/i
const BOTTOM_KEYWORDS = /(裤|裙裤|半身裙|长裤|短裤|阔腿|牛仔|pants|trousers|jeans|skirt)/i

export default {
  data() {
    return {
      product: {
        id: 0,
        name: '',
        image: '',
        description: ''
      },
      roleOptions: ROLE_OPTIONS,
      faceOptions: FACE_OPTIONS,
      resolutionOptions: RESOLUTION_OPTIONS,
      garmentRole: 'top',
      restoreFace: true,
      preserveOriginalOther: false,
      resolution: -1,
      personImagePath: '',
      taskId: '',
      taskStatus: '',
      resultImageUrl: '',
      dashscopeImageUrl: '',
      errorMessage: '',
      submitting: false,
      pollTimer: null
    }
  },
  onLoad() {
    this.loadProduct()
  },
  onUnload() {
    this.stopPolling()
  },
  methods: {
    loadProduct() {
      const cached = uni.getStorageSync(TRY_ON_PRODUCT_KEY)
      if (!cached || !cached.image) {
        uni.showModal({
          title: '缺少商品信息',
          content: '请先从商品详情页进入 AI试穿。',
          showCancel: false,
          success: () => {
            uni.navigateBack({ delta: 1 })
          }
        })
        return
      }

      this.product = {
        id: Number(cached.id || 0),
        name: cached.name || '服饰商品',
        image: cached.image || '',
        description: cached.description || ''
      }
      this.garmentRole = this.inferGarmentRole(cached)
      uni.removeStorageSync(TRY_ON_PRODUCT_KEY)
    },
    inferGarmentRole(product) {
      const text = [
        product?.name,
        product?.description,
        product?.categoryName
      ]
        .filter(Boolean)
        .join(' ')

      if (DRESS_KEYWORDS.test(text)) return 'dress'
      if (BOTTOM_KEYWORDS.test(text)) return 'bottom'
      return 'top'
    },
    choosePersonImage() {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: (res) => {
          const filePath = Array.isArray(res.tempFilePaths) ? res.tempFilePaths[0] : ''
          if (!filePath) return
          this.personImagePath = filePath
          this.resetTaskState()
        }
      })
    },
    selectGarmentRole(role) {
      this.garmentRole = role
      if (!this.canPreserveOriginal()) {
        this.preserveOriginalOther = false
      }
    },
    canPreserveOriginal() {
      return this.garmentRole === 'top' || this.garmentRole === 'bottom'
    },
    preserveHintTitle() {
      return this.garmentRole === 'bottom'
        ? '保留模特原有上装'
        : '保留模特原有下装'
    },
    onPreserveChange(event) {
      this.preserveOriginalOther = !!event?.detail?.value
    },
    resetTaskState() {
      this.stopPolling()
      this.taskId = ''
      this.taskStatus = ''
      this.resultImageUrl = ''
      this.dashscopeImageUrl = ''
      this.errorMessage = ''
    },
    extractErrorMessage(error) {
      return String(
        error?.message
        || error?.detail
        || error?.data?.message
        || error?.errMsg
        || 'AI试穿失败'
      ).trim()
    },
    async submitTryOn() {
      if (!this.personImagePath) {
        uni.showToast({ title: '请先选择模特照片', icon: 'none' })
        return
      }
      if (!this.product.image) {
        uni.showToast({ title: '缺少商品图片', icon: 'none' })
        return
      }

      this.resetTaskState()
      this.submitting = true
      try {
        const result = await aiApi.createTryOnTask(this.personImagePath, {
          garment_role: this.garmentRole,
          garment_image_url: this.product.image,
          preserve_original_other: this.canPreserveOriginal() && this.preserveOriginalOther ? 'true' : 'false',
          restore_face: this.restoreFace ? 'true' : 'false',
          resolution: String(this.resolution)
        })

        if (!result?.taskId) {
          throw new Error(result?.message || result?.detail || '试穿任务创建失败')
        }

        this.taskId = result.taskId
        this.taskStatus = result.taskStatus || 'PENDING'
        uni.showToast({ title: '任务已提交', icon: 'none' })
        this.schedulePoll(1200)
      } catch (error) {
        const message = this.extractErrorMessage(error)
        this.errorMessage = message
        uni.showToast({ title: message, icon: 'none' })
      } finally {
        this.submitting = false
      }
    },
    schedulePoll(delay = 3000) {
      this.stopPolling()
      this.pollTimer = setTimeout(() => {
        this.pollTryOnTask()
      }, delay)
    },
    stopPolling() {
      if (this.pollTimer) {
        clearTimeout(this.pollTimer)
        this.pollTimer = null
      }
    },
    isTaskRunning() {
      return !!this.taskId && !!this.taskStatus && !['FAILED', 'SUCCEEDED', 'UNKNOWN', 'CANCELED'].includes(this.taskStatus)
    },
    async pollTryOnTask() {
      if (!this.taskId) return

      try {
        const result = await aiApi.getTryOnTask(this.taskId)
        this.taskStatus = result?.taskStatus || 'UNKNOWN'

        if (result?.resultImageUrl || result?.dashscopeImageUrl) {
          this.resultImageUrl = result.resultImageUrl || result.dashscopeImageUrl
          this.dashscopeImageUrl = result.dashscopeImageUrl || ''
          this.stopPolling()
          uni.showToast({ title: '试穿完成', icon: 'success' })
          return
        }

        if (this.taskStatus === 'FAILED' || this.taskStatus === 'UNKNOWN' || this.taskStatus === 'CANCELED') {
          this.stopPolling()
          this.errorMessage = result?.message || '试穿失败'
          uni.showToast({ title: this.errorMessage, icon: 'none' })
          return
        }

        this.schedulePoll()
      } catch (error) {
        const message = this.extractErrorMessage(error)
        this.errorMessage = message
        this.schedulePoll(4000)
      }
    },
    actionButtonText() {
      if (this.submitting) return '提交中...'
      if (this.isTaskRunning()) {
        return '任务处理中...'
      }
      return '开始试穿'
    },
    statusLabel(status) {
      const map = {
        PENDING: '排队中',
        'PRE-PROCESSING': '预处理中',
        RUNNING: '生成中',
        'POST-PROCESSING': '后处理中',
        SUCCEEDED: '已完成',
        FAILED: '失败',
        UNKNOWN: '未知'
      }
      return map[status] || '未开始'
    },
    statusClass(status) {
      if (status === 'SUCCEEDED') return 'is-success'
      if (status === 'FAILED' || status === 'UNKNOWN') return 'is-error'
      if (status) return 'is-running'
      return ''
    },
    statusDescription() {
      if (this.errorMessage) return this.errorMessage
      if (this.resultImageUrl) return '结果已生成，可以点击下方图片放大查看。'
      if (!this.taskId) return '提交后会显示试穿任务进度。'
      return '试穿任务一般需要 15-30 秒，请稍候。'
    },
    previewImages(current) {
      const urls = [current, this.dashscopeImageUrl]
        .filter(Boolean)
        .filter((item, index, list) => list.indexOf(item) === index)

      if (!urls.length) return
      uni.previewImage({
        current,
        urls
      })
    }
  }
}
</script>

<style>
page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top right, rgba(249, 115, 22, 0.16), transparent 32%),
    linear-gradient(180deg, #fff7ed 0%, #fff1f2 48%, #fff 100%);
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.page {
  min-height: 100vh;
  padding: 28rpx 24rpx 180rpx;
  box-sizing: border-box;
}

.hero-card,
.section-card {
  background: rgba(255, 255, 255, 0.94);
  border: 1rpx solid rgba(251, 146, 60, 0.18);
  border-radius: 28rpx;
  box-shadow: 0 16rpx 44rpx rgba(124, 45, 18, 0.08);
}

.hero-card {
  padding: 32rpx 30rpx;
}

.section-card {
  margin-top: 22rpx;
  padding: 28rpx;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #fb923c 0%, #ec4899 100%);
  color: #fff;
  font-size: 22rpx;
  font-weight: 700;
}

.hero-title {
  display: block;
  margin-top: 18rpx;
  font-size: 40rpx;
  font-weight: 700;
  color: #7c2d12;
  line-height: 1.35;
}

.hero-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #9a3412;
}

.tips-box {
  margin-top: 18rpx;
  padding: 18rpx 20rpx;
  border-radius: 20rpx;
  background: rgba(255, 247, 237, 0.92);
}

.tips-item {
  display: block;
  font-size: 23rpx;
  line-height: 1.7;
  color: #b45309;
}

.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #7c2d12;
}

.garment-card {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-top: 20rpx;
  padding: 18rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #fff7ed 0%, #fff1f2 100%);
}

.garment-image {
  width: 180rpx;
  height: 180rpx;
  border-radius: 20rpx;
  background: #fde68a;
  flex-shrink: 0;
}

.garment-body {
  flex: 1;
  min-width: 0;
}

.garment-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #7c2d12;
  line-height: 1.45;
}

.garment-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #9a3412;
}

.empty-box,
.person-preview,
.result-preview {
  margin-top: 20rpx;
  border-radius: 24rpx;
  overflow: hidden;
  background: #fff7ed;
}

.empty-box {
  padding: 44rpx 30rpx;
  border: 2rpx dashed rgba(249, 115, 22, 0.25);
  text-align: center;
}

.empty-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #9a3412;
}

.empty-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #c2410c;
}

.person-image {
  width: 100%;
  height: 560rpx;
  display: block;
}

.choose-btn {
  margin-top: 20rpx;
}

.field-block + .field-block {
  margin-top: 24rpx;
}

.field-label {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #7c2d12;
}

.option-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 18rpx;
}

.option-chip {
  min-width: 180rpx;
  padding: 18rpx 24rpx;
  border-radius: 999rpx;
  border: 2rpx solid rgba(249, 115, 22, 0.18);
  background: #fff;
  text-align: center;
  font-size: 24rpx;
  color: #9a3412;
  box-sizing: border-box;
}

.option-chip.active {
  background: linear-gradient(135deg, rgba(249, 115, 22, 0.14) 0%, rgba(236, 72, 153, 0.12) 100%);
  border-color: #f97316;
  color: #7c2d12;
  font-weight: 600;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  margin-top: 18rpx;
  padding: 20rpx;
  border-radius: 22rpx;
  background: #fff7ed;
}

.switch-copy {
  flex: 1;
  min-width: 0;
}

.switch-title {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #7c2d12;
}

.switch-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  line-height: 1.6;
  color: #9a3412;
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.result-title {
  margin-bottom: 0;
}

.result-tip,
.status-desc,
.task-id,
.error-text {
  display: block;
  margin-top: 14rpx;
  font-size: 23rpx;
  line-height: 1.7;
}

.result-tip,
.status-desc,
.task-id {
  color: #9a3412;
}

.error-text {
  color: #dc2626;
}

.status-badge {
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 700;
}

.status-badge.is-running {
  background: rgba(249, 115, 22, 0.12);
  color: #c2410c;
}

.status-badge.is-success {
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
}

.status-badge.is-error {
  background: rgba(220, 38, 38, 0.1);
  color: #dc2626;
}

.result-image {
  width: 100%;
  display: block;
}

.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 20rpx 24rpx 34rpx;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(16rpx);
  box-shadow: 0 -12rpx 30rpx rgba(124, 45, 18, 0.08);
}

.primary-btn,
.secondary-btn {
  height: 86rpx;
  line-height: 86rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 600;
  border: none;
}

.primary-btn {
  width: 100%;
  background: linear-gradient(135deg, #f97316 0%, #ec4899 100%);
  color: #fff;
  box-shadow: 0 12rpx 24rpx rgba(236, 72, 153, 0.18);
}

.primary-btn[disabled] {
  opacity: 0.65;
}

.secondary-btn {
  width: 100%;
  background: rgba(255, 247, 237, 0.96);
  color: #9a3412;
  border: 2rpx solid rgba(249, 115, 22, 0.14);
}
</style>
