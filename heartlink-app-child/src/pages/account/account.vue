<template>
  <view class="page">
    <view class="hero">
      <text class="hero-title">编辑资料</text>
      <text class="hero-subtitle">点击头像可以更换头像，保存后会同步到子女端个人页。</text>
    </view>

    <view class="card">
      <view class="avatar-row" @click="pickAvatar">
        <view class="field-copy">
          <text class="field-label">头像</text>
          <text class="field-hint">{{ uploading ? '头像上传中...' : '点击更换头像' }}</text>
        </view>
        <view class="avatar-action">
          <image class="avatar" :src="avatarPreview" mode="aspectFill"></image>
          <text class="arrow">></text>
        </view>
      </view>

      <view class="field">
        <text class="field-label">昵称</text>
        <input
          class="field-input"
          v-model="form.nickname"
          maxlength="30"
          placeholder="请输入昵称"
        />
      </view>

      <view class="field">
        <text class="field-label">手机号</text>
        <input class="field-input is-readonly" :value="form.phone" disabled />
      </view>

      <view class="field">
        <text class="field-label">身份</text>
        <input class="field-input is-readonly" :value="roleLabel" disabled />
      </view>
    </view>

    <view class="tips-card">
      <text class="tips-title">资料说明</text>
      <text class="tips-text">昵称和头像会展示在子女端个人页，方便家人快速识别当前账号。</text>
    </view>

    <view class="bottom-bar">
      <button class="save-btn" :loading="saving" @click="saveProfile">保存资料</button>
    </view>
  </view>
</template>

<script>
import { authApi, toAbsoluteMediaUrl } from '../../api/index.js'

const DEFAULT_AVATAR = '/static/default-avatar.png'

export default {
  data() {
    return {
      loading: false,
      uploading: false,
      saving: false,
      form: {
        nickname: '',
        avatar: '',
        phone: '',
        role: ''
      }
    }
  },
  computed: {
    avatarPreview() {
      return this.form.avatar ? toAbsoluteMediaUrl(this.form.avatar) : DEFAULT_AVATAR
    },
    roleLabel() {
      return this.form.role === 'CHILD' ? '子女版' : (this.form.role || '用户')
    }
  },
  onLoad() {
    this.loadProfile()
  },
  methods: {
    async loadProfile() {
      this.loading = true
      try {
        const res = await authApi.getCurrentUser()
        this.applyUser(res.data)
      } catch (e) {
        console.error(e)
      } finally {
        this.loading = false
      }
    },
    applyUser(user = {}) {
      this.form.nickname = String(user.nickname || '').trim()
      this.form.avatar = String(user.avatar || '').trim()
      this.form.phone = String(user.phone || '')
      this.form.role = String(user.role || '')
    },
    chooseImage() {
      return new Promise((resolve, reject) => {
        uni.chooseImage({
          count: 1,
          sizeType: ['compressed'],
          sourceType: ['album', 'camera'],
          success: (res) => resolve(res.tempFilePaths?.[0] || ''),
          fail: (err) => {
            if (String(err?.errMsg || '').includes('cancel')) {
              resolve('')
              return
            }
            reject(err)
          }
        })
      })
    },
    async pickAvatar() {
      if (this.uploading) return
      try {
        const filePath = await this.chooseImage()
        if (!filePath) return
        this.uploading = true
        uni.showLoading({ title: '上传中' })
        const res = await authApi.uploadAvatar(filePath)
        this.form.avatar = res.data?.url || res.data?.relativeUrl || ''
        uni.showToast({ title: '头像已更新', icon: 'success' })
      } catch (e) {
        console.error(e)
        uni.showToast({
          title: e?.message || '头像上传失败',
          icon: 'none'
        })
      } finally {
        this.uploading = false
        uni.hideLoading()
      }
    },
    async saveProfile() {
      const nickname = String(this.form.nickname || '').trim()
      if (!nickname) {
        uni.showToast({ title: '请输入昵称', icon: 'none' })
        return
      }
      this.saving = true
      try {
        const res = await authApi.updateCurrentUser({
          nickname,
          avatar: this.form.avatar || ''
        })
        const user = res.data || {
          ...this.form,
          nickname
        }
        this.applyUser(user)
        uni.setStorageSync('userInfo', user)
        uni.showToast({ title: '资料已保存', icon: 'success' })
        setTimeout(() => {
          uni.navigateBack()
        }, 500)
      } catch (e) {
        console.error(e)
        uni.showToast({
          title: e?.message || '保存失败',
          icon: 'none'
        })
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style>
page {
  min-height: 100vh;
  background: linear-gradient(180deg, #dbeafe 0%, #edf5ff 52%, #f6faff 100%);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.page {
  min-height: 100vh;
  padding: 28rpx 24rpx 180rpx;
  box-sizing: border-box;
}

.hero {
  background: linear-gradient(140deg, #2563eb 0%, #3b82f6 52%, #60a5fa 100%);
  border-radius: 30rpx;
  padding: 34rpx 30rpx;
  box-shadow: 0 18rpx 42rpx rgba(37, 99, 235, 0.2);
}

.hero-title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #ffffff;
}

.hero-subtitle {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.88);
}

.card,
.tips-card {
  margin-top: 22rpx;
  background: rgba(255, 255, 255, 0.98);
  border-radius: 28rpx;
  padding: 28rpx 24rpx;
  border: 1rpx solid #dbeafe;
  box-shadow: 0 12rpx 28rpx rgba(12, 74, 110, 0.08);
}

.avatar-row,
.field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
}

.avatar-row {
  padding-bottom: 24rpx;
  border-bottom: 1rpx solid #e5eefb;
}

.field {
  padding-top: 24rpx;
}

.field-copy {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.field-label,
.tips-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #102a43;
}

.field-hint,
.tips-text {
  font-size: 24rpx;
  line-height: 1.7;
  color: #6b7a90;
}

.avatar-action {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.avatar {
  width: 108rpx;
  height: 108rpx;
  border-radius: 50%;
  border: 4rpx solid #ffffff;
  box-shadow: 0 8rpx 18rpx rgba(59, 130, 246, 0.18);
}

.arrow {
  font-size: 36rpx;
  color: #7c8da5;
}

.field-input {
  flex: 1;
  min-height: 72rpx;
  border-radius: 20rpx;
  background: #f8fbff;
  border: 2rpx solid #e5eefb;
  padding: 0 22rpx;
  font-size: 28rpx;
  color: #102a43;
  text-align: right;
  box-sizing: border-box;
}

.field-input.is-readonly {
  color: #7c8da5;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 20rpx 24rpx 34rpx;
  background: rgba(246, 250, 255, 0.96);
  backdrop-filter: blur(12rpx);
  box-sizing: border-box;
}

.save-btn {
  height: 92rpx;
  line-height: 92rpx;
  border: none;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #2563eb 0%, #3b82f6 100%);
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 700;
  box-shadow: 0 14rpx 30rpx rgba(37, 99, 235, 0.24);
}

button::after {
  border: none;
}
</style>
