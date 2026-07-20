<template>
  <view class="container">
    <view class="header">
      <text class="title">健康档案</text>
      <text class="subtitle">一个账号最多维护两个人的健康档案，主档案会用于默认健康分析</text>
    </view>

    <view class="card profile-card">
      <view class="profile-head">
        <view>
          <text class="card-title">档案切换</text>
          <text class="card-subtitle">当前最多支持 2 个人</text>
        </view>
        <view v-if="canAddProfile" class="add-btn" @click="addProfile">+ 添加家人</view>
      </view>

      <view class="profile-list">
        <view
          v-for="profile in profiles"
          :key="profile.id"
          class="profile-item"
          :class="{ active: isSelectedProfile(profile) }"
          @click="selectProfile(profile)"
        >
          <view class="profile-item-head">
            <text class="profile-name">{{ getProfileDisplayName(profile) }}</text>
            <text v-if="profile.isPrimary === 1" class="primary-badge">主档案</text>
          </view>
          <text class="profile-meta">{{ getProfileRelation(profile) }}</text>
        </view>
      </view>

      <text v-if="!canAddProfile" class="limit-note">已达到 2 人上限，如需切换默认分析对象，可直接设为主档案。</text>

      <view v-if="currentProfile && currentProfile.id" class="profile-actions">
        <view class="action-btn" @click="editCurrentProfile">编辑当前档案</view>
        <view
          v-if="currentProfile.isPrimary !== 1"
          class="action-btn action-btn-light"
          @click="setPrimaryProfile"
        >设为主档案</view>
      </view>
      <text class="profile-tip">主档案会被商品健康分析、子女端健康档案和健康预警默认读取。</text>
    </view>

    <view class="card">
      <view class="card-head-row">
        <text class="card-title">基本信息</text>
        <text class="card-side">{{ getProfileDisplayName(currentProfile) }}</text>
      </view>
      <view class="info-grid">
        <view class="info-item">
          <text class="info-label">档案名称</text>
          <text class="info-value">{{ getProfileDisplayName(currentProfile) }}</text>
        </view>
        <view class="info-item">
          <text class="info-label">关系</text>
          <text class="info-value">{{ getProfileRelation(currentProfile) }}</text>
        </view>
        <view class="info-item">
          <text class="info-label">年龄</text>
          <text class="info-value">{{ currentProfile.age ? currentProfile.age + '岁' : '未填写' }}</text>
        </view>
        <view class="info-item">
          <text class="info-label">身高</text>
          <text class="info-value">{{ currentProfile.height ? currentProfile.height + 'cm' : '未填写' }}</text>
        </view>
        <view class="info-item">
          <text class="info-label">体重</text>
          <text class="info-value">{{ currentProfile.weight ? currentProfile.weight + 'kg' : '未填写' }}</text>
        </view>
        <view class="info-item">
          <text class="info-label">衣码 / 鞋码</text>
          <text class="info-value">{{ formatSizeInfo(currentProfile) }}</text>
        </view>
      </view>
    </view>

    <view class="card">
      <view class="card-head-row">
        <text class="card-title">健康情况</text>
      </view>
      <view class="section-block">
        <text class="section-label">慢性病</text>
        <view class="tag-list" v-if="currentProfile.chronicDiseases && currentProfile.chronicDiseases.length > 0">
          <view class="tag warning" v-for="item in currentProfile.chronicDiseases" :key="item">{{ item }}</view>
        </view>
        <text v-else class="empty-text">未填写</text>
      </view>
      <view class="section-block">
        <text class="section-label">过敏源</text>
        <view class="tag-list" v-if="currentProfile.allergies && currentProfile.allergies.length > 0">
          <view class="tag danger" v-for="item in currentProfile.allergies" :key="item">{{ item }}</view>
        </view>
        <text v-else class="empty-text">未填写</text>
      </view>
    </view>

    <view class="card">
      <view class="card-head-row">
        <text class="card-title">生活偏好</text>
      </view>
      <view class="section-block">
        <text class="section-label">兴趣 / 运动习惯</text>
        <view class="tag-list" v-if="currentProfile.hobbies && currentProfile.hobbies.length > 0">
          <view class="tag info" v-for="item in currentProfile.hobbies" :key="item">{{ item }}</view>
        </view>
        <text v-else class="empty-text">未填写</text>
      </view>
      <view class="section-block">
        <text class="section-label">其他偏好</text>
        <text class="paragraph-text">{{ currentProfile.preferences || '未填写' }}</text>
      </view>
    </view>

    <view class="card ai-card">
      <view class="card-head-row">
        <text class="card-title">AI 健康建议</text>
        <view class="refresh-btn" @click="refreshAI">刷新</view>
      </view>
      <view class="ai-box">
        <text class="ai-text">{{ aiSuggestion }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { authApi, profileApi, aiApi } from '../../api/index.js'

const EMPTY_PROFILE = {
  id: null,
  profileName: '',
  relation: '',
  isPrimary: 0,
  age: null,
  height: null,
  weight: null,
  chronicDiseases: [],
  allergies: [],
  hobbies: [],
  clothingSize: '',
  shoeSize: null,
  preferences: ''
}

export default {
  data() {
    return {
      profiles: [],
      selectedProfileId: null,
      currentUser: null,
      aiSuggestion: '完善档案后，这里会结合当前人的健康情况生成更具体的建议。',
      loading: false
    }
  },
  computed: {
    currentProfile() {
      const matched = this.profiles.find(item => item.id === this.selectedProfileId)
      return matched || this.profiles[0] || EMPTY_PROFILE
    },
    canAddProfile() {
      return this.profiles.length < 2
    }
  },
  onShow() {
    this.loadHealthData()
  },
  methods: {
    normalizeProfile(profile) {
      return {
        ...EMPTY_PROFILE,
        ...profile,
        chronicDiseases: Array.isArray(profile?.chronicDiseases) ? profile.chronicDiseases : [],
        allergies: Array.isArray(profile?.allergies) ? profile.allergies : [],
        hobbies: Array.isArray(profile?.hobbies) ? profile.hobbies : []
      }
    },
    async loadHealthData() {
      try {
        uni.showLoading({ title: '加载中...' })
        const [profileRes, userRes] = await Promise.all([
          profileApi.getMyList(),
          authApi.getCurrentUser()
        ])
        this.profiles = (profileRes.data || []).map(item => this.normalizeProfile(item))
        this.currentUser = userRes.data || null

        const stillExists = this.profiles.some(item => item.id === this.selectedProfileId)
        if (!stillExists) {
          const primary = this.profiles.find(item => item.isPrimary === 1)
          this.selectedProfileId = primary?.id || this.profiles[0]?.id || null
        }
        this.aiSuggestion = this.getFallbackSuggestion()
      } catch (e) {
        console.error(e)
      } finally {
        uni.hideLoading()
      }
    },
    isSelectedProfile(profile) {
      return profile.id === this.selectedProfileId
    },
    selectProfile(profile) {
      this.selectedProfileId = profile.id
      this.aiSuggestion = this.getFallbackSuggestion(profile)
    },
    getProfileDisplayName(profile) {
      if (!profile) return '未选择'
      return profile.profileName || this.currentUser?.nickname || '本人'
    },
    getProfileRelation(profile) {
      if (!profile) return '未填写'
      return profile.relation || (profile.isPrimary === 1 ? '本人' : '家人')
    },
    formatSizeInfo(profile) {
      const parts = []
      if (profile?.clothingSize) {
        parts.push(`衣码 ${profile.clothingSize}`)
      }
      if (profile?.shoeSize) {
        parts.push(`鞋码 ${profile.shoeSize}`)
      }
      return parts.length > 0 ? parts.join(' / ') : '未填写'
    },
    addProfile() {
      if (!this.canAddProfile) {
        uni.showToast({ title: '最多只能添加2个人', icon: 'none' })
        return
      }
      uni.navigateTo({ url: '/pages/health-edit/health-edit' })
    },
    editCurrentProfile() {
      if (!this.currentProfile?.id) return
      uni.navigateTo({ url: `/pages/health-edit/health-edit?profileId=${this.currentProfile.id}` })
    },
    async setPrimaryProfile() {
      if (!this.currentProfile?.id || this.currentProfile.isPrimary === 1) {
        return
      }
      try {
        uni.showLoading({ title: '设置中...' })
        await profileApi.setMyPrimary(this.currentProfile.id)
        uni.showToast({ title: '已设为主档案', icon: 'success' })
        await this.loadHealthData()
      } catch (e) {
        console.error(e)
      } finally {
        uni.hideLoading()
      }
    },
    getAiPrompt(profile = this.currentProfile) {
      const name = this.getProfileDisplayName(profile)
      const relation = this.getProfileRelation(profile)
      const diseases = profile.chronicDiseases.length > 0 ? profile.chronicDiseases.join('、') : '无'
      const allergies = profile.allergies.length > 0 ? profile.allergies.join('、') : '无'
      const hobbies = profile.hobbies.length > 0 ? profile.hobbies.join('、') : '未填写'
      return `请基于以下健康档案，给出4条简洁、可执行、适合老年人的建议：档案名称${name}，关系${relation}，年龄${profile.age || '未知'}岁，身高${profile.height || '未知'}cm，体重${profile.weight || '未知'}kg，慢性病${diseases}，过敏源${allergies}，兴趣习惯${hobbies}，其他偏好${profile.preferences || '未填写'}。`
    },
    getFallbackSuggestion(profile = this.currentProfile) {
      const suggestions = []
      if (profile.chronicDiseases.length > 0) {
        suggestions.push(`1. 已记录慢性病：${profile.chronicDiseases.join('、')}，建议持续关注相关饮食和作息。`)
      } else {
        suggestions.push('1. 当前还没有记录慢性病信息，建议把长期需要关注的问题补充完整。')
      }
      if (profile.allergies.length > 0) {
        suggestions.push(`2. 已记录过敏源：${profile.allergies.join('、')}，购买食品和护理用品前先核对成分。`)
      } else {
        suggestions.push('2. 如果有食物或药物过敏，建议尽快补充到档案中，方便后续避开风险。')
      }
      if (profile.height && profile.weight) {
        suggestions.push('3. 身高体重信息已完整，后续可以更稳定地做健康分析和商品筛选。')
      } else {
        suggestions.push('3. 建议补充身高和体重，后续推荐会更准确。')
      }
      if (profile.hobbies.length > 0 || profile.preferences) {
        suggestions.push('4. 生活偏好已记录，后续更适合按真实生活习惯安排照护与推荐。')
      } else {
        suggestions.push('4. 可以补充兴趣习惯和饮食偏好，让家人更容易准备合适的内容和商品。')
      }
      return suggestions.join('\n\n')
    },
    async refreshAI() {
      if (!this.currentProfile?.id) return
      this.loading = true
      try {
        uni.showLoading({ title: '分析中...' })
        const res = await aiApi.chat(this.getAiPrompt(), '长辈健康档案建议')
        this.aiSuggestion = res?.reply || this.getFallbackSuggestion()
        uni.showToast({ title: '建议已更新', icon: 'success' })
      } catch (e) {
        console.error(e)
        this.aiSuggestion = this.getFallbackSuggestion()
        uni.showToast({ title: '已切换为本地建议', icon: 'none' })
      } finally {
        this.loading = false
        uni.hideLoading()
      }
    }
  }
}
</script>

<style>
page {
  background-color: #f5f7fb;
}

.container {
  padding: 28rpx;
  padding-bottom: 120rpx;
}

.header {
  margin-bottom: 24rpx;
}

.title {
  display: block;
  font-size: 46rpx;
  font-weight: 700;
  color: #203038;
}

.subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #6f7f87;
}

.card {
  background: #ffffff;
  border-radius: 28rpx;
  padding: 28rpx;
  margin-bottom: 22rpx;
  box-shadow: 0 10rpx 26rpx rgba(15, 44, 60, 0.06);
}

.profile-card {
  background: linear-gradient(180deg, #ffffff 0%, #f7fcfb 100%);
}

.profile-head,
.card-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.card-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #203038;
}

.card-subtitle,
.card-side {
  font-size: 24rpx;
  color: #7a8a92;
}

.add-btn,
.refresh-btn {
  padding: 12rpx 22rpx;
  border-radius: 999rpx;
  background: #e5f6f2;
  color: #0f7d6d;
  font-size: 24rpx;
  font-weight: 700;
}

.profile-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 20rpx;
}

.profile-item {
  padding: 22rpx;
  border-radius: 22rpx;
  border: 2rpx solid #d8ece7;
  background: #f8fcfb;
}

.profile-item.active {
  border-color: #19a28d;
  background: #eefaf7;
  box-shadow: 0 8rpx 20rpx rgba(25, 162, 141, 0.12);
}

.profile-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14rpx;
}

.profile-name {
  font-size: 32rpx;
  font-weight: 700;
  color: #1f2f36;
}

.primary-badge {
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  background: #0f8f7d;
  color: #ffffff;
  font-size: 22rpx;
  font-weight: 700;
}

.profile-meta {
  display: block;
  margin-top: 10rpx;
  font-size: 25rpx;
  color: #718189;
}

.limit-note,
.profile-tip {
  display: block;
  margin-top: 16rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #7b8a90;
}

.profile-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
}

.action-btn {
  flex: 1;
  text-align: center;
  padding: 18rpx 20rpx;
  border-radius: 18rpx;
  background: linear-gradient(135deg, #1aa38e 0%, #0f8e7c 100%);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
}

.action-btn-light {
  background: #edf4fb;
  color: #45606d;
}

.info-grid {
  display: flex;
  flex-wrap: wrap;
  margin-top: 18rpx;
}

.info-item {
  width: 50%;
  margin-bottom: 18rpx;
}

.info-label {
  display: block;
  font-size: 24rpx;
  color: #7a8a92;
}

.info-value {
  display: block;
  margin-top: 8rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2f36;
}

.section-block {
  margin-top: 18rpx;
}

.section-label {
  display: block;
  margin-bottom: 14rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: #4b616a;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.tag {
  padding: 12rpx 22rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 600;
}

.tag.warning {
  background: #fff2db;
  color: #9c6113;
}

.tag.danger {
  background: #ffe6e5;
  color: #b23c39;
}

.tag.info {
  background: #e7f3ff;
  color: #2b6898;
}

.empty-text,
.paragraph-text,
.ai-text {
  font-size: 26rpx;
  line-height: 1.8;
  color: #5f727b;
  white-space: pre-wrap;
}

.ai-card {
  background: linear-gradient(180deg, #fff7f1 0%, #fffdf9 100%);
}

.ai-box {
  margin-top: 18rpx;
  padding: 22rpx;
  border-radius: 20rpx;
  background: #ffffff;
  border: 1rpx solid #f4dfcf;
}
</style>
