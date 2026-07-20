<template>
  <view class="skeleton-wrap">
    <view class="skeleton-card" v-for="n in count" :key="n">
      <view class="skeleton-image shimmer"></view>
      <view class="skeleton-body">
        <view class="skeleton-line long shimmer"></view>
        <view class="skeleton-line medium shimmer"></view>
        <view class="skeleton-row">
          <view class="skeleton-line short shimmer"></view>
          <view class="skeleton-pill shimmer"></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  name: 'SkeletonCards',
  props: {
    count: {
      type: Number,
      default: 2
    }
  }
}
</script>

<style>
.skeleton-wrap {
  display: flex;
  flex-direction: column;
  gap: 30rpx;
}

.skeleton-card {
  background: #fff;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
}

.skeleton-image {
  width: 100%;
  height: 400rpx;
  background: #f0f0f0;
}

.skeleton-body {
  padding: 30rpx;
}

.skeleton-line {
  height: 28rpx;
  border-radius: 14rpx;
  margin-bottom: 18rpx;
  background: #efefef;
}

.skeleton-line.long {
  width: 88%;
}

.skeleton-line.medium {
  width: 56%;
}

.skeleton-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18rpx;
}

.skeleton-line.short {
  width: 28%;
  margin-bottom: 0;
}

.skeleton-pill {
  width: 180rpx;
  height: 56rpx;
  border-radius: 28rpx;
  background: #efefef;
}

.shimmer {
  position: relative;
  overflow: hidden;
}

.shimmer::after {
  content: '';
  position: absolute;
  top: 0;
  left: -150%;
  width: 120%;
  height: 100%;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0), rgba(255, 255, 255, 0.55), rgba(255, 255, 255, 0));
  animation: shimmer 1.2s infinite;
}

@keyframes shimmer {
  100% {
    left: 130%;
  }
}
</style>
