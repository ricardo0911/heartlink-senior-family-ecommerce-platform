<template>
  <view class="container">
    <view class="hero-card">
      <text class="hero-title">SOS 联系人</text>
      <text class="hero-subtitle">由子女统一维护长辈求助时需要同步的联系人与补充说明</text>
    </view>

    <view class="action-card">
      <view class="card-head">
        <text class="card-title">紧急联系人备注</text>
        <text class="card-note">按“姓名 电话 关系”分行填写</text>
      </view>
      <text class="card-desc">长辈端保留一键 SOS 按钮，联系人和补充说明由子女端维护会更稳妥。</text>

      <textarea
        v-model="contacts"
        class="contacts-input"
        maxlength="500"
        :placeholder="contactsPlaceholder"
      />

      <view class="template-grid">
        <view class="template-chip" @click="appendTemplate('女儿', '13800000000', '第一联系人')">
          <text class="template-chip-text">加入子女模板</text>
        </view>
        <view class="template-chip" @click="appendTemplate('社区医院', '021-12345678', '就医咨询')">
          <text class="template-chip-text">加入医院模板</text>
        </view>
        <view class="template-chip" @click="appendTemplate('邻居', '13900000000', '备用联系人')">
          <text class="template-chip-text">加入备用联系人</text>
        </view>
      </view>

      <view class="secondary-btn" @click="saveContacts">
        <text class="secondary-btn-text">{{ saving ? '保存中...' : '保存联系人信息' }}</text>
      </view>
    </view>

    <view class="tip-card">
      <text class="tip-title">维护建议</text>
      <text class="tip-text">1. 至少保留 1 位直系家属电话和 1 位备用联系人。</text>
      <text class="tip-text">2. 可附上常去医院、过敏信息或居住地址，便于紧急协助。</text>
      <text class="tip-text">3. 更换号码后及时更新，避免长辈求助时联系不上。</text>
    </view>
  </view>
</template>

<script>
import { sosApi } from '../../api/index.js'

export default {
  data() {
    return {
      contacts: '',
      contactsPlaceholder: [
        '例如：',
        '女儿 13800000000 第一联系人',
        '社区医院 021-12345678 就医咨询',
        '邻居王阿姨 13900000000 备用联系人'
      ].join('\n'),
      saving: false
    }
  },
  onShow() {
    this.loadContacts()
  },
  methods: {
    async loadContacts() {
      try {
        const res = await sosApi.contacts()
        this.contacts = String(res.data || '')
      } catch (e) {
        console.error(e)
        this.contacts = ''
      }
    },
    appendTemplate(name, phone, relation) {
      const line = `${name} ${phone} ${relation}`
      if (!this.contacts.trim()) {
        this.contacts = line
        return
      }
      if (!this.contacts.includes(line)) {
        this.contacts = `${this.contacts.trim()}\n${line}`
      }
    },
    async saveContacts() {
      if (this.saving) return
      try {
        this.saving = true
        await sosApi.saveContacts(this.contacts)
        uni.showToast({ title: '已保存', icon: 'success' })
      } catch (e) {
        console.error(e)
        uni.showToast({ title: e?.message || '保存失败，请稍后重试', icon: 'none' })
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style>
page {
  background: linear-gradient(180deg, #f7fafc 0%, #eefbf3 100%);
  min-height: 100vh;
  color: #0f172a;
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.container {
  padding: 24rpx 24rpx 120rpx;
}

.hero-card {
  background: linear-gradient(135deg, #166534 0%, #16a34a 55%, #4ade80 100%);
  border-radius: 28rpx;
  padding: 30rpx;
  box-shadow: 0 16rpx 36rpx rgba(22, 163, 74, 0.2);
}

.hero-title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #ffffff;
}

.hero-subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.9);
}

.action-card,
.tip-card {
  margin-top: 22rpx;
  background: rgba(255, 255, 255, 0.98);
  border-radius: 28rpx;
  padding: 24rpx;
  box-shadow: 0 12rpx 30rpx rgba(15, 118, 110, 0.08);
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.card-title,
.tip-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #0f172a;
}

.card-note {
  font-size: 24rpx;
  color: #64748b;
}

.card-desc {
  display: block;
  margin-top: 14rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #475569;
}

.contacts-input {
  width: 100%;
  min-height: 300rpx;
  margin-top: 18rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: #f8fafc;
  box-sizing: border-box;
  font-size: 28rpx;
  line-height: 1.7;
  color: #0f172a;
}

.template-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 18rpx;
}

.template-chip {
  min-width: 220rpx;
  height: 68rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: #ecfdf5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.template-chip-text {
  font-size: 24rpx;
  font-weight: 600;
  color: #166534;
}

.secondary-btn {
  margin-top: 22rpx;
  width: 100%;
  height: 88rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #16a34a 0%, #22c55e 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.secondary-btn-text {
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
}

.tip-text {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #475569;
}
</style>
