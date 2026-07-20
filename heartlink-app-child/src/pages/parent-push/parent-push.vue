<template>
  <view class="compat-page">
    <text class="compat-text">正在返回心选...</text>
  </view>
</template>

<script>
export default {
  onLoad() {
    this.redirectToShelf()
  },
  methods: {
    redirectToShelf() {
      setTimeout(() => {
        uni.redirectTo({ url: '/pages/shelf/shelf' })
      }, 10)
    }
  }
}
</script>

<style>
page {
  min-height: 100vh;
  background: #f7fbff;
}

.compat-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.compat-text {
  font-size: 28rpx;
  color: #64748b;
}
</style>
