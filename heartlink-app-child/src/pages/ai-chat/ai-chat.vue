<template>
  <view class="container">
    <view class="header">
      <text class="title">AI顾问</text>
      <text class="subtitle" v-if="selectedParentName">已连接 {{ selectedParentName }} 的关怀档案</text>
      <text class="subtitle warn" v-else>请先绑定长辈后再使用</text>
    </view>

    <scroll-view
      class="message-list"
      scroll-y
      :scroll-into-view="scrollToId"
      scroll-with-animation
    >
      <view v-if="!loading && parentList.length === 0" class="empty-wrap">
        <text class="empty-title">还没有绑定长辈</text>
        <text class="empty-text" user-select>绑定长辈后，AI 会结合健康档案和推荐商品给你更稳妥的选购建议。</text>
        <view class="empty-btn" @click="goBind">
          <text class="empty-btn-text">立即绑定</text>
        </view>
      </view>

      <view v-else-if="parentList.length > 0">
        <view class="conversation-meta">
          <scroll-view scroll-x class="parent-scroll" show-scrollbar="false">
            <view class="parent-tabs">
              <view
                v-for="item in parentList"
                :key="item.bind.id"
                class="parent-chip"
                :class="{ active: String(item.parent.id) === String(selectedParentId) }"
                @click="switchParent(item)"
              >
                <image class="parent-avatar" :src="item.parent.avatar || '/static/default-avatar.png'" mode="aspectFill"></image>
                <text class="parent-name">{{ getParentLabel(item) }}</text>
              </view>
            </view>
          </scroll-view>

          <view class="context-strip">
            <image class="context-avatar" :src="selectedParentAvatar" mode="aspectFill"></image>
            <view class="context-info">
              <text class="context-name">{{ selectedParentName }}</text>
              <text class="context-note" user-select>{{ profileDigest }}</text>
            </view>
          </view>

          <view class="context-section" v-if="recommendList.length > 0">
            <text class="section-label">候选商品</text>
            <view class="product-tags">
              <view
                class="product-tag"
                v-for="item in recommendList.slice(0, 3)"
                :key="item.id"
                @click="goProductDetail(item)"
              >
                <text class="product-tag-text">{{ item.name }}</text>
              </view>
            </view>
          </view>

          <view class="context-section">
            <text class="section-label">试试这样问</text>
            <view class="prompt-list">
              <view
                v-for="prompt in quickPrompts"
                :key="prompt"
                class="prompt-chip"
                @click="sendPreset(prompt)"
              >
                <text class="prompt-text">{{ prompt }}</text>
              </view>
            </view>
          </view>
        </view>

        <view
          class="message-row"
          v-for="(msg, index) in messages"
          :key="msg.id || msg.localId || index"
          :class="{ mine: msg.isMine }"
        >
          <view class="message-stack">
            <view class="bubble" :class="{ 'mine-bubble': msg.isMine, loading: msg.loading }">
              <text v-if="!msg.isMine" class="bubble-role">AI顾问</text>
              <text class="bubble-text" user-select>{{ msg.content }}</text>
              <text class="bubble-time">{{ formatTime(msg.createTime) }}</text>
            </view>

            <view class="message-products" v-if="msg.products && msg.products.length">
              <view
                class="message-product-card"
                v-for="product in msg.products"
                :key="`${msg.localId || msg.id}-${product.id}`"
                @click="goProductDetail(product)"
              >
                <image
                  class="message-product-image"
                  :src="getProductImage(product)"
                  mode="aspectFill"
                ></image>
                <view class="message-product-content">
                  <view class="message-product-top">
                    <text class="message-product-name">{{ product.name }}</text>
                    <text class="message-product-price">¥{{ product.price }}</text>
                  </view>
                  <text
                    class="message-product-desc"
                    v-if="product.aiRecommendReason || product.description"
                    user-select
                  >{{ product.aiRecommendReason || product.description }}</text>
                  <view class="message-product-tags" v-if="product.healthTags && product.healthTags.length">
                    <text class="message-product-tag" v-for="tag in product.healthTags.slice(0, 2)" :key="tag">{{ tag }}</text>
                  </view>
                  <view class="message-product-actions">
                    <view class="message-product-btn secondary" @click.stop="goProductDetail(product)">查看详情</view>
                    <view
                      class="message-product-btn primary"
                      :class="{ disabled: creatingProductId === product.id }"
                      @click.stop="createOrder(product)"
                    >
                      {{ creatingProductId === product.id ? '下单中...' : '自己下单' }}
                    </view>
                  </view>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view id="msg-bottom" style="height: 8rpx;"></view>
    </scroll-view>

    <view class="input-bar">
      <input
        class="msg-input"
        v-model="inputText"
        :disabled="parentList.length === 0 || replying"
        placeholder="输入你想问的送礼或选购问题..."
        confirm-type="send"
        @confirm="sendMessage"
      />
      <view
        class="send-btn"
        :class="{ active: canSend }"
        @click="sendMessage"
      >
        <text class="send-text">{{ replying ? '思考中' : '发送' }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { aiApi, familyApi, productApi, profileApi, orderApi } from '../../api/index.js'
import { resolveProductImageUrl } from '../../utils/product-image.js'

export default {
  data() {
    return {
      parentList: [],
      selectedParentId: '',
      selectedBindId: '',
      selectedParentName: '',
      selectedParentAvatar: '/static/default-avatar.png',
      parentProfile: null,
      recommendList: [],
      creatingProductId: null,
      messages: [],
      inputText: '',
      scrollToId: '',
      replying: false,
      loading: false,
      aiUnavailable: false,
      aiFallbackNotified: false
    }
  },
  computed: {
    canSend() {
      return this.parentList.length > 0 && !!this.inputText.trim() && !this.replying
    },
    quickPrompts() {
      const diseaseText = this.joinList(this.parentProfile?.chronicDiseases)
      const hobbyText = this.joinList(this.parentProfile?.hobbies)
      const target = this.selectedParentName || '长辈'
      return [
        `根据${target}的档案推荐 3 件实用好物`,
        `${target}${diseaseText !== '暂无明显健康重点' ? `有${diseaseText}` : ''}，送什么更稳妥`,
        `预算 300 元内，给${target}买什么更合适`,
        `${hobbyText !== '暂无明显兴趣偏好' ? `${target}喜欢${hobbyText}，` : ''}优先推荐哪类商品`
      ]
    },
    profileDigest() {
      if (!this.parentProfile) {
        return '正在读取长辈健康档案和近期推荐商品'
      }
      const parts = []
      if (this.parentProfile.age) parts.push(`${this.parentProfile.age}岁`)
      if (Array.isArray(this.parentProfile.chronicDiseases) && this.parentProfile.chronicDiseases.length > 0) {
        parts.push(`慢性病：${this.parentProfile.chronicDiseases.slice(0, 2).join('、')}`)
      }
      if (Array.isArray(this.parentProfile.hobbies) && this.parentProfile.hobbies.length > 0) {
        parts.push(`爱好：${this.parentProfile.hobbies.slice(0, 2).join('、')}`)
      }
      if (this.parentProfile.preferences) {
        parts.push(`偏好：${this.parentProfile.preferences}`)
      }
      return parts.length > 0 ? parts.join(' ｜ ') : '档案较少，AI 会先按通用关怀场景给出建议'
    }
  },
  onShow() {
    this.initPage()
  },
  methods: {
    async initPage() {
      this.loading = true
      try {
        const res = await familyApi.getMyParents()
        this.parentList = Array.isArray(res.data) ? res.data : []
        if (!this.parentList.length) {
          this.selectedParentId = ''
          this.selectedBindId = ''
          this.parentProfile = null
          this.recommendList = []
          this.messages = []
          return
        }

        const cachedParentId = String(uni.getStorageSync('AI_CHAT_PARENT_ID') || '')
        const matched = this.parentList.find((item) => String(item.parent?.id) === cachedParentId)
        await this.switchParent(matched || this.parentList[0], { silent: true })
      } catch (e) {
        console.error(e)
        uni.showToast({ title: '加载长辈信息失败', icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    getParentLabel(item) {
      return item?.bind?.relation || item?.parent?.nickname || '家人'
    },
    async switchParent(item, options = {}) {
      if (!item || !item.parent) return
      this.selectedParentId = String(item.parent.id || '')
      this.selectedBindId = String(item.bind?.id || '')
      this.selectedParentName = this.getParentLabel(item)
      this.selectedParentAvatar = item.parent.avatar || '/static/default-avatar.png'
      uni.setStorageSync('AI_CHAT_PARENT_ID', this.selectedParentId)

      await this.loadParentContext()
      this.seedConversation()

      if (!options.silent) {
        uni.showToast({ title: `已切换到${this.selectedParentName}`, icon: 'none' })
      }
    },
    async loadParentContext() {
      try {
        const [profileRes, recommendRes] = await Promise.all([
          profileApi.getProfile(this.selectedParentId),
          productApi.getRecommend(this.selectedParentId, 6)
        ])
        this.parentProfile = profileRes.data || {}
        this.recommendList = this.normalizeProductList(Array.isArray(recommendRes.data) ? recommendRes.data : [])
      } catch (e) {
        console.error(e)
        this.parentProfile = this.parentProfile || {}
        this.recommendList = []
      }
    },
    normalizeProductList(products) {
      const source = Array.isArray(products) ? products : []
      return source.map((item) => ({
        ...item,
        images: Array.isArray(item?.images)
          ? item.images
          : (item?.images ? [item.images] : [])
      }))
    },
    getProductImage(product) {
      const images = Array.isArray(product?.images) ? product.images : []
      const fallback = resolveProductImageUrl('', { productName: product?.name || '' })
      for (const item of images) {
        const resolved = resolveProductImageUrl(item, { productName: product?.name || '' })
        if (resolved && resolved !== fallback) return resolved
      }
      return fallback
    },
    getRecommendationScore(product, text) {
      const sourceText = String(text || '')
      if (!sourceText) return 0

      let score = 0
      const name = String(product?.name || '').trim()
      if (name && sourceText.includes(name)) score += 10

      const keywords = [
        ...(Array.isArray(product?.healthTags) ? product.healthTags : []),
        ...(Array.isArray(product?.warningTags) ? product.warningTags : []),
        product?.aiRecommendReason,
        product?.description
      ]

      keywords
        .map((item) => String(item || '').trim())
        .filter(Boolean)
        .forEach((keyword) => {
          if (sourceText.includes(keyword)) score += 3
        })

      return score
    },
    pickReplyProducts(question, reply, candidateProducts = []) {
      const candidates = this.normalizeProductList(
        candidateProducts.length ? candidateProducts : this.recommendList
      )
      if (!candidates.length) return []

      const text = `${question || ''}\n${reply || ''}`
      const named = candidates.filter((product) => {
        const name = String(product?.name || '').trim()
        return name && text.includes(name)
      })
      if (named.length) return named.slice(0, 3)

      const ranked = candidates
        .map((product, index) => ({
          product,
          index,
          score: this.getRecommendationScore(product, text)
        }))
        .sort((left, right) => {
          if (right.score !== left.score) return right.score - left.score
          return left.index - right.index
        })

      if (ranked[0]?.score > 0) {
        return ranked.slice(0, 3).map((item) => item.product)
      }

      if (/(推荐|适合|商品|产品|礼物|预算|买|下单)/.test(text)) {
        return candidates.slice(0, 3)
      }

      return []
    },
    seedConversation() {
      const intro = this.recommendList.length > 0
        ? `我是连心选 AI 推荐顾问，已经结合 ${this.selectedParentName} 的档案筛出 ${this.recommendList.slice(0, 3).map((item) => item.name).join('、')} 等候选商品。你可以继续问我预算、送礼场景、健康适配或替代方案。`
        : `我是连心选 AI 推荐顾问。当前已读取 ${this.selectedParentName} 的基础信息，你可以直接问我“送什么更合适”“预算怎么选”或“慢性病场景下该避开什么”。`

      this.messages = [this.createAssistantMessage(intro, {
        products: this.pickReplyProducts('推荐商品', intro, this.recommendList)
      })]
      this.scrollToBottom()
    },
    createUserMessage(content) {
      return {
        localId: `user-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        content,
        createTime: new Date().toISOString(),
        isMine: true
      }
    },
    createAssistantMessage(content, extra = {}) {
      return {
        localId: `ai-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        content,
        createTime: new Date().toISOString(),
        isMine: false,
        ...extra
      }
    },
    sendPreset(prompt) {
      this.inputText = prompt
      this.sendMessage()
    },
    async sendMessage() {
      const content = this.inputText.trim()
      if (!content || !this.parentList.length || this.replying) return

      const userMessage = this.createUserMessage(content)
      const pendingMessage = this.createAssistantMessage('正在整理适合的推荐建议...', { loading: true, products: [] })
      this.messages = [...this.messages, userMessage, pendingMessage]
      this.inputText = ''
      this.replying = true
      this.scrollToBottom()

      try {
        let reply = ''
        let remoteProducts = []

        if (!this.aiUnavailable) {
          const res = await aiApi.chat(content, this.buildAiContext())
          reply = String(res?.reply || '').trim()
          remoteProducts = Array.isArray(res?.products) ? res.products : []
        }

        if (!reply) {
          reply = this.getFallbackReply(content)
        }

        const products = this.pickReplyProducts(
          content,
          reply,
          remoteProducts
        )
        this.replacePendingMessage(pendingMessage.localId, reply, products)
      } catch (e) {
        if (this.isAiUnavailableError(e)) {
          this.aiUnavailable = true
          if (!this.aiFallbackNotified) {
            this.aiFallbackNotified = true
            uni.showToast({
              title: 'AI服务不可用，已切换推荐模式',
              icon: 'none'
            })
          }
        } else {
          console.error(e)
        }
        const reply = this.getFallbackReply(content)
        this.replacePendingMessage(
          pendingMessage.localId,
          reply,
          this.pickReplyProducts(content, reply)
        )
      } finally {
        this.replying = false
        this.scrollToBottom()
      }
    },
    replacePendingMessage(localId, content, products = []) {
      this.messages = this.messages.map((item) => {
        if (item.localId !== localId) return item
        return {
          ...item,
          content,
          products,
          loading: false,
          createTime: new Date().toISOString()
        }
      })
    },
    isAiUnavailableError(error) {
      const errMsg = String(error?.errMsg || error?.message || '').toLowerCase()
      const statusCode = Number(error?.statusCode || 0)
      return statusCode >= 500
        || errMsg.includes('request:fail')
        || errMsg.includes('connection refused')
        || errMsg.includes('timeout')
        || errMsg.includes('network')
    },
    buildAiContext() {
      const profile = this.parentProfile || {}
      const productLines = this.recommendList.length > 0
        ? this.recommendList.map((item) => {
          const tags = Array.isArray(item.healthTags) && item.healthTags.length > 0
            ? `，标签：${item.healthTags.slice(0, 3).join('、')}`
            : ''
          return `${item.name}，价格${item.price || '未知'}元${tags}`
        }).join('；')
        : '暂无候选商品'

      return [
        `服务对象：${this.selectedParentName}`,
        `年龄：${profile.age || '未知'}`,
        `慢性病：${this.joinList(profile.chronicDiseases)}`,
        `过敏源：${this.joinList(profile.allergies, '暂无明确过敏信息')}`,
        `兴趣爱好：${this.joinList(profile.hobbies, '暂无明显兴趣偏好')}`,
        `其他偏好：${profile.preferences || '暂无'}`,
        `当前候选商品：${productLines}`,
        '请用子女视角给出简洁、可执行、偏推荐导购风格的建议，优先说明为什么适合，必要时给出避坑提醒。'
      ].join('\n')
    },
    getFallbackReply(question) {
      const target = this.selectedParentName || '长辈'
      const relatedProducts = this.pickReplyProducts(question, question)
        .slice(0, 3)
        .map((item) => item.name)
      const topProducts = relatedProducts.length > 0
        ? relatedProducts
        : this.recommendList.slice(0, 3).map((item) => item.name)
      const productText = topProducts.length > 0 ? `当前可优先参考：${topProducts.join('、')}。` : ''
      return `我先按 ${target} 的档案给你一个稳妥建议：优先选操作简单、日常可持续使用、和健康场景相关的商品。${productText}如果你告诉我预算、送礼场景，或者想重点解决的问题，我可以继续帮你细化。`
    },
    joinList(value, fallback = '暂无明显健康重点') {
      return Array.isArray(value) && value.length > 0 ? value.join('、') : fallback
    },
    formatTime(time) {
      if (!time) return ''
      const date = new Date(time)
      const now = new Date()
      const isToday = date.toDateString() === now.toDateString()
      const hour = String(date.getHours()).padStart(2, '0')
      const minute = String(date.getMinutes()).padStart(2, '0')
      return isToday ? `${hour}:${minute}` : `${date.getMonth() + 1}/${date.getDate()} ${hour}:${minute}`
    },
    scrollToBottom() {
      this.$nextTick(() => {
        this.scrollToId = ''
        this.$nextTick(() => {
          this.scrollToId = 'msg-bottom'
        })
      })
    },
    goProductDetail(product) {
      const productId = Number(product?.id || 0)
      if (!productId) return
      let url = `/pages/product-detail/product-detail?id=${productId}`
      if (this.selectedParentId) {
        url += `&parentId=${this.selectedParentId}`
      }
      uni.navigateTo({ url })
    },
    async createOrder(product) {
      if (!product?.id || this.creatingProductId === product.id) return
      if (!this.selectedParentId) {
        uni.showToast({ title: '请先绑定长辈', icon: 'none' })
        return
      }

      this.creatingProductId = product.id
      try {
        const res = await orderApi.create({
          productId: product.id,
          parentId: this.selectedParentId,
          quantity: 1,
          generateGreeting: true
        })
        const orderId = res?.data?.id
        if (!orderId) {
          throw new Error('Missing order id')
        }
        uni.navigateTo({ url: `/pages/payment/payment?orderId=${orderId}` })
      } catch (e) {
        console.error(e)
        uni.showToast({ title: '下单失败', icon: 'none' })
      } finally {
        this.creatingProductId = null
      }
    },
    goBind() {
      uni.navigateTo({ url: '/pages/bind/bind' })
    }
  }
}
</script>

<style>
page {
  background-color: #f5f5f5;
  height: 100%;
}

.container {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.header {
  padding: 28rpx 24rpx 18rpx;
  background: #fff;
  border-bottom: 1rpx solid #f0f0f0;
}

.title {
  display: block;
  font-size: 36rpx;
  color: #222;
  font-weight: 600;
}

.subtitle {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #52c41a;
}

.subtitle.warn {
  color: #faad14;
}

.message-list {
  flex: 1;
  padding: 22rpx;
}

.empty-wrap {
  padding-top: 220rpx;
  text-align: center;
}

.empty-title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: #0f172a;
}

.empty-text {
  display: block;
  margin-top: 14rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #666;
}

.empty-btn {
  margin: 28rpx auto 0;
  width: 240rpx;
  height: 82rpx;
  border-radius: 41rpx;
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e8e 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-btn-text {
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}

.conversation-meta {
  margin-bottom: 20rpx;
}

.parent-scroll {
  white-space: nowrap;
}

.parent-tabs {
  display: inline-flex;
  gap: 16rpx;
  padding-bottom: 6rpx;
}

.parent-chip {
  min-width: 170rpx;
  padding: 14rpx 18rpx;
  border-radius: 999rpx;
  background: #fff;
  border: 1rpx solid #f0f0f0;
  display: flex;
  align-items: center;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.parent-chip.active {
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e8e 100%);
  border-color: transparent;
}

.parent-avatar {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  flex-shrink: 0;
}

.parent-name {
  margin-left: 10rpx;
  font-size: 24rpx;
  color: #333;
  font-weight: 600;
}

.parent-chip.active .parent-name {
  color: #fff;
}

.context-strip {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
  padding: 18rpx 20rpx;
  border-radius: 22rpx;
  background: #fff;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.context-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  flex-shrink: 0;
}

.context-info {
  margin-left: 16rpx;
  flex: 1;
}

.context-name {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #333;
}

.context-note {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #666;
}

.context-section {
  margin-top: 16rpx;
}

.section-label {
  display: block;
  font-size: 24rpx;
  font-weight: 600;
  color: #999;
}

.product-tags,
.prompt-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 12rpx;
}

.product-tag {
  padding: 12rpx 18rpx;
  border-radius: 999rpx;
  background: #fff;
  border: 1rpx solid #f0f0f0;
}

.product-tag-text {
  font-size: 22rpx;
  color: #666;
}

.prompt-chip {
  padding: 14rpx 18rpx;
  border-radius: 18rpx;
  background: #fff;
  border: 1rpx solid #f0f0f0;
}

.prompt-text {
  font-size: 24rpx;
  line-height: 1.5;
  color: #333;
}

.message-row {
  display: flex;
  margin-bottom: 20rpx;
}

.message-row.mine {
  justify-content: flex-end;
}

.message-stack {
  max-width: 76%;
}

.bubble {
  padding: 18rpx 22rpx;
  border-radius: 18rpx;
  background: #fff;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.06);
}

.mine-bubble {
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e8e 100%);
}

.bubble.loading {
  background: #f8fafc;
}

.bubble-role {
  display: block;
  margin-bottom: 10rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: #999;
}

.bubble-text {
  color: #333;
  font-size: 30rpx;
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-wrap;
}

.mine-bubble .bubble-text {
  color: #fff;
}

.bubble-time {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #999;
}

.mine-bubble .bubble-time {
  color: rgba(255, 255, 255, 0.72);
  text-align: right;
}

.message-products {
  margin-top: 12rpx;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.message-product-card {
  display: flex;
  align-items: stretch;
  gap: 16rpx;
  padding: 18rpx;
  border-radius: 22rpx;
  background: #fff;
  border: 1rpx solid #f0f0f0;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.message-product-image {
  width: 144rpx;
  height: 144rpx;
  border-radius: 18rpx;
  background: #e2e8f0;
  flex-shrink: 0;
}

.message-product-content {
  flex: 1;
  min-width: 0;
}

.message-product-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

.message-product-name {
  flex: 1;
  font-size: 28rpx;
  font-weight: 700;
  color: #333;
  line-height: 1.5;
}

.message-product-price {
  font-size: 28rpx;
  font-weight: 700;
  color: #ff6b6b;
  white-space: nowrap;
}

.message-product-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 23rpx;
  line-height: 1.6;
  color: #666;
}

.message-product-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 12rpx;
}

.message-product-tag {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: #fff3f3;
  color: #ff6b6b;
  font-size: 20rpx;
}

.message-product-actions {
  display: flex;
  gap: 12rpx;
  margin-top: 16rpx;
}

.message-product-btn {
  min-width: 132rpx;
  height: 60rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: 600;
}

.message-product-btn.primary {
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e8e 100%);
  color: #fff;
}

.message-product-btn.secondary {
  background: #f6f6f6;
  color: #666;
}

.message-product-btn.disabled {
  opacity: 0.55;
}

.input-bar {
  display: flex;
  align-items: center;
  padding: 16rpx 20rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #eee;
}

.msg-input {
  flex: 1;
  height: 76rpx;
  padding: 0 24rpx;
  border-radius: 38rpx;
  background: #f6f6f6;
  font-size: 30rpx;
  color: #333;
}

.send-btn {
  margin-left: 14rpx;
  height: 76rpx;
  min-width: 120rpx;
  border-radius: 38rpx;
  background: #ddd;
  display: flex;
  align-items: center;
  justify-content: center;
}

.send-btn.active {
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e8e 100%);
}

.send-text {
  color: #fff;
  font-size: 30rpx;
  font-weight: 600;
}
</style>
