<script>
import { orderApi } from './api/index.js'

const DELIVERY_POLL_INTERVAL = 5000
const DELIVERY_REMIND_SNOOZE_MS = 30000
const DELIVERY_REMIND_SUPPRESS_FOREVER = Number.MAX_SAFE_INTEGER
const DELIVERY_NOTICE_KEY = 'HL_CHILD_DELIVERY_NOTICE_CACHE'
const ORDER_RECEIVE_CONFIRMED_EVENT = 'HL_ORDER_RECEIVE_CONFIRMED'

let deliveryPollTimer = null
let deliveryPollPending = false
let deliveryReminderVisible = false
let deliveryConfirmPending = false

const hasSession = () => Boolean(String(uni.getStorageSync('token') || '').trim())

const readNoticeCache = (storageKey) => {
  try {
    const raw = uni.getStorageSync(storageKey)
    if (!raw) return {}
    return typeof raw === 'string' ? (JSON.parse(raw) || {}) : (raw || {})
  } catch (error) {
    return {}
  }
}

const writeNoticeCache = (storageKey, payload) => {
  try {
    uni.setStorageSync(storageKey, JSON.stringify(payload))
  } catch (error) {}
}

const normalizeNoticeEntry = (entry) => {
  if (!entry) return null
  if (typeof entry === 'string') {
    return {
      version: entry,
      snoozeUntil: 0
    }
  }
  if (typeof entry === 'object') {
    return {
      version: entry.version,
      snoozeUntil: Number(entry.snoozeUntil || 0)
    }
  }
  return null
}

const setNoticeEntry = (storageKey, orderId, version, snoozeUntil = 0) => {
  const cacheKey = String(orderId || '')
  if (!cacheKey) {
    return
  }
  const noticeCache = readNoticeCache(storageKey)
  noticeCache[cacheKey] = { version, snoozeUntil }
  writeNoticeCache(storageKey, noticeCache)
}

const clearNoticeEntry = (storageKey, orderId) => {
  const cacheKey = String(orderId || '')
  if (!cacheKey) {
    return
  }
  const noticeCache = readNoticeCache(storageKey)
  if (!(cacheKey in noticeCache)) {
    return
  }
  delete noticeCache[cacheKey]
  writeNoticeCache(storageKey, noticeCache)
}

const resolveNoticeVersion = (order) => (
  order?.logisticsUpdatedAt
  || order?.updatedAt
  || order?.completedAt
  || order?.status
  || 'SIGNED'
)

const isDeliveredOrder = (order) => (
  Boolean(order)
  && order.status === 'SHIPPED'
  && order.logisticsStatus === 'DELIVERED'
)

const buildConfirmedOrderPayload = (order, payload = {}) => {
  const confirmedAt = payload?.completedAt
    || payload?.logisticsUpdatedAt
    || order?.completedAt
    || order?.logisticsUpdatedAt
    || new Date().toISOString()
  return {
    ...(order || {}),
    ...(payload || {}),
    id: payload?.id || order?.id,
    status: 'COMPLETED',
    logisticsStatus: 'SIGNED',
    logisticsStatusText: payload?.logisticsStatusText || '\u5df2\u7b7e\u6536',
    completedAt: confirmedAt,
    logisticsUpdatedAt: payload?.logisticsUpdatedAt || confirmedAt
  }
}

const showDeliveryReminder = (order, storageKey) => {
  const noticeVersion = resolveNoticeVersion(order)
  const productName = String(order?.productName || '您的商品')
  deliveryReminderVisible = true
  uni.showModal({
    title: '物流到达提醒',
    content: productName + ' 已送达，请确认签收。',
    confirmText: '立即签收',
    cancelText: '稍后处理',
    success: async (res) => {
      if (!order?.id) {
        return
      }
      if (!res.confirm) {
        setNoticeEntry(storageKey, order.id, noticeVersion, Date.now() + DELIVERY_REMIND_SNOOZE_MS)
        return
      }
      if (deliveryConfirmPending) {
        return
      }
      deliveryConfirmPending = true
      try {
        const res = await orderApi.confirmReceive(order.id)
        const confirmedOrder = buildConfirmedOrderPayload(order, res?.data)
        uni.$emit(ORDER_RECEIVE_CONFIRMED_EVENT, confirmedOrder)
        setNoticeEntry(storageKey, order.id, resolveNoticeVersion(confirmedOrder), DELIVERY_REMIND_SUPPRESS_FOREVER)
        uni.showToast({ title: '已签收', icon: 'success' })
      } catch (error) {
        clearNoticeEntry(storageKey, order.id)
        console.error('child confirm receive failed', error)
      } finally {
        deliveryConfirmPending = false
      }
    },
    complete: () => {
      deliveryReminderVisible = false
    }
  })
}

const pollDeliveredOrders = async (loader, storageKey) => {
  if (!hasSession() || deliveryPollPending || deliveryReminderVisible) {
    return
  }

  deliveryPollPending = true
  try {
    const res = await loader({ page: 1, size: 50 })
    const records = Array.isArray(res?.data?.records) ? res.data.records : []
    const deliveredOrders = records.filter(isDeliveredOrder)
    if (!deliveredOrders.length) {
      return
    }

    const noticeCache = readNoticeCache(storageKey)
    const nextOrder = deliveredOrders.find((item) => {
      const cacheKey = String(item?.id || '')
      if (!cacheKey) {
        return false
      }
      const noticeEntry = normalizeNoticeEntry(noticeCache[cacheKey])
      const noticeVersion = resolveNoticeVersion(item)
      if (!noticeEntry) {
        return true
      }
      if (noticeEntry.version !== noticeVersion) {
        return true
      }
      return noticeEntry.snoozeUntil <= Date.now()
    })

    if (!nextOrder) {
      return
    }

    showDeliveryReminder(nextOrder, storageKey)
  } catch (error) {
    console.error('child delivery reminder poll failed', error)
  } finally {
    deliveryPollPending = false
  }
}

export default {
  onLaunch() {
    console.log('HeartLink Child App Launch')
    this.startDeliveryReminderPolling()
  },
  onShow() {
    console.log('HeartLink Child App Show')
    this.startDeliveryReminderPolling()
    this.pollDeliveryReminder()
  },
  onHide() {
    console.log('HeartLink Child App Hide')
    this.stopDeliveryReminderPolling()
  },
  methods: {
    startDeliveryReminderPolling() {
      if (deliveryPollTimer || !hasSession()) {
        return
      }
      deliveryPollTimer = setInterval(() => {
        this.pollDeliveryReminder()
      }, DELIVERY_POLL_INTERVAL)
    },
    stopDeliveryReminderPolling() {
      if (deliveryPollTimer) {
        clearInterval(deliveryPollTimer)
        deliveryPollTimer = null
      }
    },
    async pollDeliveryReminder() {
      await pollDeliveredOrders(orderApi.getChildOrders, DELIVERY_NOTICE_KEY)
    }
  }
}
</script>

<style>
page {
  background:
    radial-gradient(circle at 16% 10%, rgba(10, 132, 255, 0.16), transparent 36%),
    radial-gradient(circle at 84% 90%, rgba(48, 209, 88, 0.1), transparent 30%),
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
