<script>
export default {
  onLaunch() {
    console.log('HeartLink Parent App Launch')
    try {
      const accountInfo = (uni.getAccountInfoSync && uni.getAccountInfoSync()) || {}
      const appId = accountInfo.miniProgram && accountInfo.miniProgram.appId
      if (appId === 'touristappid') {
        uni.showModal({
          title: '当前为游客模式',
          content: '微信能力受限，登录态可能异常。请在开发者工具项目详情中配置 AppID：wxc36959075d178439 后重新编译。',
          showCancel: false
        })
      }
    } catch (e) {}
  },
  onShow() {
    console.log('HeartLink Parent App Show')
  },
  onHide() {
    console.log('HeartLink Parent App Hide')
  }
}
</script>

<style>
page {
  background:
    radial-gradient(circle at 12% 8%, rgba(10, 132, 255, 0.16), transparent 36%),
    radial-gradient(circle at 88% 92%, rgba(48, 209, 88, 0.12), transparent 30%),
    linear-gradient(180deg, #f4f6fa 0%, #eef2f7 45%, #f7f9fc 100%);
  color: var(--hl-text-primary);
  font-family: 'SF Pro Text', 'SF Pro Display', 'PingFang SC', 'Segoe UI', 'Microsoft YaHei', sans-serif;
}

:root {
  --hl-primary: #0a84ff;
  --hl-primary-2: #3ea6ff;
  --hl-primary-3: #64d2ff;
  --hl-secondary: #30d158;
  --hl-warning: #ff9f0a;
  --hl-danger: #ff453a;

  --hl-text-primary: #111111;
  --hl-text-secondary: #445066;
  --hl-text-muted: #6e7b90;

  --hl-surface: #ffffff;
  --hl-surface-alt: #f3f8ff;
  --hl-border: #dbe4ef;

  --hl-radius-sm: 14rpx;
  --hl-radius-md: 20rpx;
  --hl-radius-lg: 28rpx;
  --hl-radius-xl: 36rpx;

  --hl-shadow-sm: 0 6rpx 16rpx rgba(16, 24, 40, 0.07);
  --hl-shadow-md: 0 14rpx 30rpx rgba(16, 24, 40, 0.1);
  --hl-shadow-lg: 0 24rpx 52rpx rgba(16, 24, 40, 0.14);

  --hl-motion-fast: 160ms cubic-bezier(0.22, 0.61, 0.36, 1);
  --hl-motion-base: 240ms cubic-bezier(0.22, 0.61, 0.36, 1);
}

.safe-area-bottom {
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
}

.hl-page-shell {
  padding: 24rpx;
}

.hl-card {
  background: rgba(255, 255, 255, 0.9);
  border: 1rpx solid var(--hl-border);
  border-radius: var(--hl-radius-lg);
  box-shadow: var(--hl-shadow-sm);
}

.hl-glass-card {
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(10rpx) saturate(140%);
  border: 1rpx solid rgba(255, 255, 255, 0.75);
  border-radius: var(--hl-radius-lg);
  box-shadow: var(--hl-shadow-md);
}

.hl-primary-btn {
  background: linear-gradient(140deg, var(--hl-primary) 0%, var(--hl-primary-2) 56%, var(--hl-primary-3) 100%);
  color: #fff;
  border: none;
  border-radius: 999rpx;
  min-height: 88rpx;
  line-height: 88rpx;
  font-size: 30rpx;
  font-weight: 650;
  box-shadow: 0 12rpx 24rpx rgba(10, 132, 255, 0.28);
}

.hl-primary-btn[disabled] {
  opacity: 0.72;
}

.hl-secondary-btn {
  background: #fff;
  color: var(--hl-primary);
  border: 2rpx solid rgba(10, 132, 255, 0.28);
  border-radius: 999rpx;
  min-height: 84rpx;
  line-height: 84rpx;
  font-size: 28rpx;
  font-weight: 600;
}

.hl-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 44rpx;
  padding: 0 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: var(--hl-primary);
  background: rgba(10, 132, 255, 0.1);
}

.hl-title {
  color: var(--hl-text-primary);
  font-size: 38rpx;
  font-weight: 700;
  letter-spacing: 0.3rpx;
}

.hl-subtitle {
  color: var(--hl-text-secondary);
  font-size: 26rpx;
  line-height: 1.6;
}

button::after {
  border: none;
}

</style>
