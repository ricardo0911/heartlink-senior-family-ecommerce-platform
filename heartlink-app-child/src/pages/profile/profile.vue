<template>
  <view class="container">
    <view class="header">
      <text class="title">长辈健康档案</text>
      <text class="subtitle">完善信息，AI自动筛选适合的商品</text>
    </view>

    <view class="form-section">
      <!-- 基本信息 -->
      <view class="section-card">
        <view class="section-title"><view class="section-icon">人</view> 基本信息</view>
        <view class="form-item">
          <text class="label">年龄</text>
          <input class="input" type="number" v-model="profile.age" placeholder="请输入年龄" />
        </view>
        <view class="form-item">
          <text class="label">身高(cm)</text>
          <input class="input" type="digit" v-model="profile.height" placeholder="请输入身高" />
        </view>
        <view class="form-item">
          <text class="label">体重(kg)</text>
          <input class="input" type="digit" v-model="profile.weight" placeholder="请输入体重" />
        </view>
      </view>

      <!-- 穿戴尺码 -->
      <view class="section-card">
        <view class="section-title"><view class="section-icon">衣</view> 穿戴尺码</view>
        <view class="form-item">
          <text class="label">衣服尺码</text>
          <view class="size-tags">
            <view 
              class="size-tag" 
              v-for="size in clothingSizes" 
              :key="size"
              :class="{ active: profile.clothingSize === size }"
              @click="profile.clothingSize = size"
            >{{ size }}</view>
          </view>
        </view>
        <view class="form-item">
          <text class="label">鞋码</text>
          <input class="input" type="number" v-model="profile.shoeSize" placeholder="请输入鞋码" />
        </view>
      </view>

      <!-- 慢性疾病 -->
      <view class="section-card">
        <view class="section-title"><view class="section-icon">健</view> 健康状况</view>
        <text class="section-desc">选择后，AI会自动过滤不适合的商品</text>
        <view class="disease-tags">
          <view 
            class="disease-tag" 
            v-for="disease in commonDiseases" 
            :key="disease"
            :class="{ active: profile.chronicDiseases && profile.chronicDiseases.includes(disease) }"
            @click="toggleDisease(disease)"
          >{{ disease }}</view>
        </view>
      </view>

      <!-- 过敏源 -->
      <view class="section-card">
        <view class="section-title"><view class="section-icon">警</view> 过敏源</view>
        <view class="allergy-tags">
          <view 
            class="allergy-tag" 
            v-for="allergy in commonAllergies" 
            :key="allergy"
            :class="{ active: profile.allergies && profile.allergies.includes(allergy) }"
            @click="toggleAllergy(allergy)"
          >{{ allergy }}</view>
        </view>
        <view class="form-item">
          <text class="label">其他过敏源</text>
          <input class="input" v-model="otherAllergy" placeholder="请输入其他过敏源" @confirm="addAllergy" />
        </view>
      </view>

      <!-- 兴趣爱好 -->
      <view class="section-card">
        <view class="section-title"><view class="section-icon">趣</view> 兴趣爱好</view>
        <text class="section-desc">帮助AI推荐更合适的商品</text>
        <view class="hobby-tags">
          <view 
            class="hobby-tag" 
            v-for="hobby in commonHobbies" 
            :key="hobby"
            :class="{ active: profile.hobbies && profile.hobbies.includes(hobby) }"
            @click="toggleHobby(hobby)"
          >{{ hobby }}</view>
        </view>
      </view>

      <!-- 其他偏好 -->
      <view class="section-card">
        <view class="section-title"><view class="section-icon">记</view> 其他偏好</view>
        <textarea 
          class="textarea" 
          v-model="profile.preferences" 
          placeholder="例如：喜欢红色、不喜欢太复杂的操作..."
          maxlength="200"
        ></textarea>
      </view>
    </view>

    <!-- 保存按钮 -->
    <view class="bottom-bar safe-area-bottom">
      <button class="btn-save" @click="saveProfile">保存档案</button>
    </view>
  </view>
</template>

<script>
import { profileApi } from '../../api/index.js'

export default {
  data() {
    return {
      parentId: null,
      profile: {
        age: null,
        height: null,
        weight: null,
        clothingSize: '',
        shoeSize: null,
        chronicDiseases: [],
        allergies: [],
        hobbies: [],
        preferences: ''
      },
      otherAllergy: '',
      clothingSizes: ['S', 'M', 'L', 'XL', 'XXL', 'XXXL'],
      commonDiseases: ['高血压', '糖尿病', '高血糖', '高血脂', '痛风', '心脏病', '老寒腿', '关节炎', '骨质疏松'],
      commonAllergies: ['海鲜', '牛奶', '鸡蛋', '花生', '坚果', '小麦', '大豆', '芒果'],
      commonHobbies: ['广场舞', '太极', '养花', '钓鱼', '书法', '象棋', '旅游', '摄影', '烹饪', '散步']
    }
  },
  onLoad(options) {
    this.parentId = options.parentId
    if (this.parentId) {
      this.loadProfile()
    }
  },
  methods: {
    async loadProfile() {
      try {
        const res = await profileApi.getProfile(this.parentId)
        if (res.data) {
          this.profile = {
            ...this.profile,
            ...res.data,
            chronicDiseases: res.data.chronicDiseases || [],
            allergies: res.data.allergies || [],
            hobbies: res.data.hobbies || []
          }
        }
      } catch (e) {
        console.error(e)
      }
    },
    toggleDisease(disease) {
      if (!this.profile.chronicDiseases) {
        this.profile.chronicDiseases = []
      }
      const idx = this.profile.chronicDiseases.indexOf(disease)
      if (idx > -1) {
        this.profile.chronicDiseases.splice(idx, 1)
      } else {
        this.profile.chronicDiseases.push(disease)
      }
    },
    toggleAllergy(allergy) {
      if (!this.profile.allergies) {
        this.profile.allergies = []
      }
      const idx = this.profile.allergies.indexOf(allergy)
      if (idx > -1) {
        this.profile.allergies.splice(idx, 1)
      } else {
        this.profile.allergies.push(allergy)
      }
    },
    addAllergy() {
      if (this.otherAllergy && !this.profile.allergies.includes(this.otherAllergy)) {
        this.profile.allergies.push(this.otherAllergy)
        this.otherAllergy = ''
      }
    },
    toggleHobby(hobby) {
      if (!this.profile.hobbies) {
        this.profile.hobbies = []
      }
      const idx = this.profile.hobbies.indexOf(hobby)
      if (idx > -1) {
        this.profile.hobbies.splice(idx, 1)
      } else {
        this.profile.hobbies.push(hobby)
      }
    },
    async saveProfile() {
      try {
        await profileApi.saveProfile({
          ...this.profile,
          parentId: this.parentId
        })
        uni.showToast({ title: '保存成功', icon: 'success' })
        setTimeout(() => {
          uni.navigateBack()
        }, 1000)
      } catch (e) {
        console.error(e)
      }
    }
  }
}
</script>

<style>
page {
  background: linear-gradient(160deg, #e0f2fe 0%, #f0f9ff 60%, #f8fafc 100%);
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
  min-height: 100vh;
}

.container {
  min-height: 100vh;
  background: transparent;
  padding-bottom: 150rpx;
}

.header {
  background: linear-gradient(135deg, #0369a1 0%, #0ea5e9 50%, #38bdf8 100%);
  padding: 60rpx 30rpx;
}

.title {
  display: block;
  font-size: 40rpx;
  font-weight: 600;
  color: #fff;
}

.subtitle {
  display: block;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.9);
  margin-top: 8rpx;
}

.form-section {
  padding: 20rpx;
}

.section-card {
  background: #fff;
  border-radius: 24rpx;
  padding: 30rpx;
  margin-bottom: 20rpx;
  border: 1rpx solid #dbeafe;
  box-shadow: 0 8rpx 22rpx rgba(12, 74, 110, 0.08);
  animation: fade-up 0.35s ease both;
}

.section-icon {
  display: inline-flex;
  width: 48rpx;
  height: 48rpx;
  background: linear-gradient(135deg, #e0f2fe 0%, #bae6fd 100%);
  border-radius: 12rpx;
  align-items: center;
  justify-content: center;
  color: #0c4a6e;
  font-size: 24rpx;
  font-weight: 700;
  margin-right: 12rpx;
}

.section-title {
  display: flex;
  align-items: center;
  font-size: 32rpx;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 8rpx;
}

.section-desc {
  display: block;
  font-size: 24rpx;
  color: #64748b;
  margin-bottom: 20rpx;
}

.form-item {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}

.form-item:last-child {
  border-bottom: none;
}

.label {
  width: 180rpx;
  font-size: 28rpx;
  color: #475569;
}

.input {
  flex: 1;
  font-size: 28rpx;
  color: #0f172a;
}

.size-tags, .disease-tags, .allergy-tags, .hobby-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 16rpx;
}

.size-tag, .disease-tag, .allergy-tag, .hobby-tag {
  padding: 16rpx 28rpx;
  background: #f5f5f5;
  border-radius: 30rpx;
  font-size: 26rpx;
  color: #64748b;
}

.size-tag.active {
  background: linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%);
  color: #fff;
}

.disease-tag.active {
  background: #fef3c7;
  color: #92400e;
  border: 2rpx solid #f59e0b;
}

.allergy-tag.active {
  background: #fee2e2;
  color: #991b1b;
  border: 2rpx solid #dc2626;
}

.hobby-tag.active {
  background: #dcfce7;
  color: #166534;
  border: 2rpx solid #22c55e;
}

.textarea {
  width: 100%;
  height: 160rpx;
  background: #f5f5f5;
  border-radius: 16rpx;
  padding: 20rpx;
  font-size: 28rpx;
  margin-top: 16rpx;
  box-sizing: border-box;
  color: #0f172a;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  padding: 20rpx 30rpx;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.btn-save {
  background: linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%);
  color: #fff;
  border: none;
  border-radius: 50rpx;
  height: 100rpx;
  line-height: 100rpx;
  font-size: 34rpx;
  font-weight: 500;
  box-shadow: 0 8rpx 24rpx rgba(3, 105, 161, 0.35);
}

@keyframes fade-up {
  from {
    opacity: 0;
    transform: translateY(20rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .section-card {
    animation: none;
  }
}
</style>
