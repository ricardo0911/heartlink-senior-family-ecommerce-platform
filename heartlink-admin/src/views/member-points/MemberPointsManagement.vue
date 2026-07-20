<template>
  <div class="member-points-page">
    <section class="hl-page-header">
      <div>
        <h1 class="hl-page-title">积分管理</h1>
        <p class="hl-page-subtitle">配置每日签到积分，并指定哪些优惠券可以用会员积分兑换。</p>
      </div>
      <el-button type="primary" :icon="RefreshRight" :loading="loading" @click="loadData">
        刷新数据
      </el-button>
    </section>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="8">
        <el-card shadow="hover" class="hl-card">
          <template #header>
            <div class="hl-card-header">
              <div>
                <div class="hl-card-title">积分奖励规则</div>
                <div class="hl-card-subtitle">当前先支持配置每日签到奖励积分。</div>
              </div>
            </div>
          </template>

          <el-form label-position="top" class="hl-form">
            <el-form-item label="每日签到积分">
              <el-input-number v-model="dailyCheckInPoints" :min="0" :max="9999" style="width: 100%" />
            </el-form-item>
          </el-form>

          <div class="summary-list">
            <div class="summary-item">
              <span class="summary-label">子女端显示</span>
              <span class="summary-value">{{ dailyCheckInPoints }} 积分 / 次</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">兑换提醒</span>
              <span class="summary-value">优惠券兑换时直接扣减会员积分</span>
            </div>
          </div>

          <el-button type="primary" class="hl-btn" :loading="savingConfig" @click="handleSaveConfig">
            保存签到配置
          </el-button>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="16">
        <el-card shadow="hover" class="hl-table">
          <template #header>
            <div class="hl-card-header">
              <div>
                <div class="hl-card-title">积分兑换优惠券</div>
                <div class="hl-card-subtitle">开启后，子女端会展示“积分兑换”，不会再走免费领取。</div>
              </div>
            </div>
          </template>

          <el-table :data="couponList" v-loading="loading" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" label="优惠券" min-width="180" show-overflow-tooltip />
            <el-table-column label="类型" width="120">
              <template #default="{ row }">
                {{ row.type === 'PERCENT' ? '折扣券' : '满减券' }}
              </template>
            </el-table-column>
            <el-table-column label="面值" width="120">
              <template #default="{ row }">
                {{ row.type === 'PERCENT' ? `${row.value} 折` : `¥${row.value}` }}
              </template>
            </el-table-column>
            <el-table-column label="券状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
                  {{ row.status === 1 ? '启用中' : '已停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="库存" width="120">
              <template #default="{ row }">
                {{ row.remainCount || 0 }} / {{ row.totalCount || 0 }}
              </template>
            </el-table-column>
            <el-table-column label="可积分兑换" width="130">
              <template #default="{ row }">
                <el-switch
                  v-model="row.exchangeEnabled"
                  :active-value="1"
                  :inactive-value="0"
                />
              </template>
            </el-table-column>
            <el-table-column label="所需积分" width="160">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.exchangePoints"
                  :min="0"
                  :step="10"
                  :disabled="row.exchangeEnabled !== 1"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  :loading="savingCouponId === row.id"
                  @click="handleSaveCoupon(row)"
                >
                  保存
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshRight } from '@element-plus/icons-vue'
import {
  getExchangeCouponList,
  getMemberPointsConfig,
  saveMemberPointsConfig,
  updateCouponExchangeConfig
} from '../../api'

const loading = ref(false)
const savingConfig = ref(false)
const savingCouponId = ref(null)
const dailyCheckInPoints = ref(10)
const couponList = ref([])

const normalizeCoupon = (item = {}) => ({
  ...item,
  exchangeEnabled: Number(item.exchangeEnabled || 0),
  exchangePoints: Number(item.exchangePoints || 0)
})

const loadData = async () => {
  loading.value = true
  try {
    const [configRes, couponRes] = await Promise.all([
      getMemberPointsConfig(),
      getExchangeCouponList()
    ])
    dailyCheckInPoints.value = Number(configRes.data?.dailyCheckInPoints ?? 10)
    couponList.value = Array.isArray(couponRes.data)
      ? couponRes.data.map(normalizeCoupon)
      : []
  } finally {
    loading.value = false
  }
}

const handleSaveConfig = async () => {
  if (dailyCheckInPoints.value < 0) {
    ElMessage.warning('每日签到积分不能小于 0')
    return
  }
  savingConfig.value = true
  try {
    await saveMemberPointsConfig({ dailyCheckInPoints: dailyCheckInPoints.value })
    ElMessage.success('签到积分已更新')
  } finally {
    savingConfig.value = false
  }
}

const handleSaveCoupon = async (row) => {
  if (row.exchangeEnabled === 1 && Number(row.exchangePoints || 0) <= 0) {
    ElMessage.warning('开启积分兑换后，所需积分必须大于 0')
    return
  }
  savingCouponId.value = row.id
  try {
    const res = await updateCouponExchangeConfig(row.id, {
      exchangeEnabled: row.exchangeEnabled,
      exchangePoints: row.exchangePoints
    })
    Object.assign(row, normalizeCoupon(res.data || row))
    ElMessage.success('兑换配置已保存')
  } finally {
    savingCouponId.value = null
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.hl-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.summary-list {
  display: grid;
  gap: 12px;
  margin-bottom: 20px;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 14px;
  background: #f8fafc;
}

.summary-label {
  color: var(--hl-text-secondary);
}

.summary-value {
  color: var(--hl-text-primary);
  font-weight: 600;
}
</style>
