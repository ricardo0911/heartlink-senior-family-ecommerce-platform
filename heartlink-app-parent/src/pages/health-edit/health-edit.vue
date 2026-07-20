<template>
  <view class="container">
    <view class="header-card">
      <text class="header-title">{{ profileId ? '编辑健康档案' : '新增健康档案' }}</text>
      <text class="header-subtitle">最多维护 2 个人。主档案请回到健康档案页进行切换。</text>
    </view>

    <view class="card">
      <text class="card-title">档案身份</text>
      <view class="form-item">
        <text class="label">档案名称</text>
        <input class="input" v-model="form.profileName" placeholder="如：本人、老伴、妈妈" maxlength="20" />
      </view>
      <view class="form-item">
        <text class="label">关系</text>
        <input class="input" v-model="form.relation" placeholder="如：本人、老伴、家人" maxlength="20" />
      </view>
    </view>

    <view class="card">
      <text class="card-title">身体信息</text>
      <view class="form-item">
        <text class="label">年龄</text>
        <input class="input" type="number" v-model="form.age" placeholder="如：68" />
      </view>
      <view class="form-item">
        <text class="label">身高(cm)</text>
        <input class="input" type="digit" v-model="form.height" placeholder="如：165" />
      </view>
      <view class="form-item">
        <text class="label">体重(kg)</text>
        <input class="input" type="digit" v-model="form.weight" placeholder="如：58" />
      </view>
      <view class="form-item">
        <text class="label">衣码</text>
        <input class="input" v-model="form.clothingSize" placeholder="如：L" />
      </view>
      <view class="form-item">
        <text class="label">鞋码</text>
        <input class="input" type="number" v-model="form.shoeSize" placeholder="如：39" />
      </view>
    </view>

    <view class="card">
      <text class="card-title">健康情况</text>
      <view class="form-item textarea-item">
        <text class="label">慢性病</text>
        <textarea
          class="textarea"
          v-model="form.chronicDiseasesText"
          placeholder="多个请用逗号、顿号或换行分隔，如：高血压、糖尿病"
        />
      </view>
      <view class="form-item textarea-item">
        <text class="label">过敏源</text>
        <textarea
          class="textarea"
          v-model="form.allergiesText"
          placeholder="多个请用逗号、顿号或换行分隔，如：花粉、海鲜"
        />
      </view>
    </view>

    <view class="card">
      <text class="card-title">生活偏好</text>
      <view class="form-item textarea-item">
        <text class="label">兴趣 / 习惯</text>
        <textarea
          class="textarea"
          v-model="form.hobbiesText"
          placeholder="多个请用逗号、顿号或换行分隔，如：散步、太极、养花"
        />
      </view>
      <view class="form-item textarea-item">
        <text class="label">其他偏好</text>
        <textarea
          class="textarea"
          v-model="form.preferences"
          placeholder="如：清淡少盐、不吃辣、喜欢宽松衣物"
        />
      </view>
    </view>

    <view class="actions">
      <button class="save-btn" :disabled="saving" @click="saveProfile">
        {{ saving ? '保存中...' : '保存健康档案' }}
      </button>
    </view>
  </view>
</template>

<script>
import { profileApi } from '../../api/index.js'

const EMPTY_FORM = {
  id: null,
  profileName: '',
  relation: '',
  age: '',
  height: '',
  weight: '',
  clothingSize: '',
  shoeSize: '',
  chronicDiseasesText: '',
  allergiesText: '',
  hobbiesText: '',
  preferences: ''
}

export default {
  data() {
    return {
      profileId: null,
      saving: false,
      form: { ...EMPTY_FORM }
    }
  },
  onLoad(options) {
    this.profileId = options.profileId ? Number(options.profileId) : null
    uni.setNavigationBarTitle({
      title: this.profileId ? '编辑健康档案' : '新增健康档案'
    })
    this.loadProfile()
  },
  methods: {
    parseList(text) {
      if (!text) return []
      return text
        .split(/[\n\r,，、；;]+/)
        .map(item => item.trim())
        .filter(Boolean)
    },
    toNumberOrNull(value) {
      if (value === '' || value === null || value === undefined) {
        return null
      }
      const numberValue = Number(value)
      return Number.isNaN(numberValue) ? null : numberValue
    },
    async loadProfile() {
      if (!this.profileId) {
        this.form = {
          ...EMPTY_FORM,
          relation: '家人'
        }
        return
      }
      try {
        uni.showLoading({ title: '加载中...' })
        const res = await profileApi.getMyList()
        const list = Array.isArray(res.data) ? res.data : []
        const matched = list.find(item => Number(item.id) === this.profileId)
        if (!matched) {
          uni.showToast({ title: '档案不存在', icon: 'none' })
          setTimeout(() => uni.navigateBack(), 600)
          return
        }
        this.form = {
          id: matched.id,
          profileName: matched.profileName || '',
          relation: matched.relation || '',
          age: matched.age ?? '',
          height: matched.height ?? '',
          weight: matched.weight ?? '',
          clothingSize: matched.clothingSize || '',
          shoeSize: matched.shoeSize ?? '',
          chronicDiseasesText: Array.isArray(matched.chronicDiseases) ? matched.chronicDiseases.join('、') : '',
          allergiesText: Array.isArray(matched.allergies) ? matched.allergies.join('、') : '',
          hobbiesText: Array.isArray(matched.hobbies) ? matched.hobbies.join('、') : '',
          preferences: matched.preferences || ''
        }
      } catch (e) {
        console.error(e)
      } finally {
        uni.hideLoading()
      }
    },
    validate() {
      if (!String(this.form.profileName || '').trim()) {
        uni.showToast({ title: '请输入档案名称', icon: 'none' })
        return false
      }
      const age = this.toNumberOrNull(this.form.age)
      if (age !== null && (age < 0 || age > 130)) {
        uni.showToast({ title: '年龄范围应为0-130', icon: 'none' })
        return false
      }
      return true
    },
    async saveProfile() {
      if (this.saving || !this.validate()) return
      this.saving = true
      try {
        uni.showLoading({ title: '保存中...' })
        const payload = {
          id: this.form.id || null,
          profileName: String(this.form.profileName || '').trim(),
          relation: String(this.form.relation || '').trim() || '家人',
          age: this.toNumberOrNull(this.form.age),
          height: this.toNumberOrNull(this.form.height),
          weight: this.toNumberOrNull(this.form.weight),
          clothingSize: this.form.clothingSize ? String(this.form.clothingSize).trim() : null,
          shoeSize: this.toNumberOrNull(this.form.shoeSize),
          chronicDiseases: this.parseList(this.form.chronicDiseasesText),
          allergies: this.parseList(this.form.allergiesText),
          hobbies: this.parseList(this.form.hobbiesText),
          preferences: this.form.preferences ? String(this.form.preferences).trim() : null
        }
        await profileApi.saveMy(payload)
        uni.showToast({ title: '保存成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 600)
      } catch (e) {
        console.error(e)
      } finally {
        this.saving = false
        uni.hideLoading()
      }
    }
  }
}
</script>

<style>
page {
  background: #f5f7fb;
}

.container {
  padding: 24rpx;
  padding-bottom: 60rpx;
}

.header-card,
.card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 10rpx 24rpx rgba(18, 44, 63, 0.06);
}

.header-title {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
  color: #1f2f36;
}

.header-subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #708089;
}

.card-title {
  display: block;
  margin-bottom: 18rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: #213038;
}

.form-item {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #eef2f4;
}

.form-item:last-child {
  border-bottom: none;
}

.textarea-item {
  display: block;
}

.label {
  width: 170rpx;
  flex-shrink: 0;
  font-size: 26rpx;
  color: #54666f;
}

.input {
  flex: 1;
  min-height: 78rpx;
  padding: 0 18rpx;
  border-radius: 16rpx;
  background: #f8fbfc;
  font-size: 28rpx;
  color: #203038;
  box-sizing: border-box;
}

.textarea {
  width: 100%;
  min-height: 140rpx;
  margin-top: 14rpx;
  padding: 20rpx;
  border-radius: 18rpx;
  background: #f8fbfc;
  font-size: 28rpx;
  line-height: 1.7;
  color: #203038;
  box-sizing: border-box;
}

.actions {
  margin-top: 12rpx;
}

.save-btn {
  width: 100%;
  background: linear-gradient(135deg, #1aa38e 0%, #0f8e7c 100%);
  color: #fff;
  border: none;
  border-radius: 999rpx;
  font-size: 32rpx;
  font-weight: 700;
}

.save-btn[disabled] {
  opacity: 0.72;
}
</style>
