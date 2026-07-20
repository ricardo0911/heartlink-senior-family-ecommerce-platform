import request from '../utils/request'

// Auth
export const login = (data) => request.post('/auth/login', data)
export const getAdminInfo = () => request.get('/auth/info')
export const logout = () => request.post('/auth/logout')

// Dashboard
export const getDashboardStats = () => request.get('/dashboard/stats')
export const getRecentOrders = () => request.get('/dashboard/recent-orders')
export const getSalesTrend = (days = 7) => request.get('/dashboard/sales-trend', { params: { days } })
export const getInventoryAlerts = (threshold = 10, limit = 8) =>
  request.get('/dashboard/inventory-alerts', { params: { threshold, limit } })
export const getSettlementOverview = (params) => request.get('/settlement/overview', { params })
export const getSettlementTrend = (params) => request.get('/settlement/trend', { params })
export const getSettlementOrders = (params) => request.get('/settlement/orders', { params })

// Product
export const getProductPage = (params) => request.get('/product/page', { params })
export const getProduct = (id) => request.get(`/product/${id}`)
export const saveProduct = (data) => request.post('/product/save', data)
export const updateProductStatus = (id, status) => request.post(`/product/${id}/status`, null, { params: { status } })
export const deleteProduct = (id) => request.delete(`/product/${id}`)

// Category
export const getCategoryList = () => request.get('/category/list')
export const saveCategory = (data) => request.post('/category/save', data)
export const deleteCategory = (id) => request.delete(`/category/${id}`)
export const getSupplierList = (params) => request.get('/supplier/list', { params })
export const getSupplier = (id) => request.get(`/supplier/${id}`)
export const saveSupplier = (data) => request.post('/supplier/save', data)
export const updateSupplierStatus = (id, status) => request.post(`/supplier/${id}/status`, null, { params: { status } })
export const deleteSupplier = (id) => request.delete(`/supplier/${id}`)
export const uploadImage = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/upload/image', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// Order
export const getOrderPage = (params) => request.get('/order/page', { params })
export const getRefundPage = (params) => request.get('/order/refund/page', { params })
export const getLogisticsPage = (params) => request.get('/order/logistics/page', { params })
export const getOrder = (id) => request.get(`/order/${id}`)
export const shipOrder = (id, data) => request.post(`/order/${id}/ship`, data)
export const getOrderLogistics = (id, refresh = true) => request.get(`/order/${id}/logistics`, { params: { refresh } })
export const approveRefund = (id) => request.post(`/order/${id}/refund-approve`)
export const rejectRefund = (id) => request.post(`/order/${id}/refund-reject`)

// User
export const getUserPage = (params) => request.get('/user/page', { params })
export const getUser = (id) => request.get(`/user/${id}`)
export const updateUserStatus = (id, status) => request.post(`/user/${id}/status`, null, { params: { status } })
export const updateUserCreditScore = (id, creditScore) => request.post(`/user/${id}/credit-score`, null, { params: { creditScore } })

// Coupon
export const getCouponPage = (params) => request.get('/coupon/page', { params })
export const saveCoupon = (data) => request.post('/coupon/save', data)
export const updateCouponStatus = (id, status) => request.post(`/coupon/${id}/status`, null, { params: { status } })
export const issueCoupon = (id, data) => request.post(`/coupon/${id}/issue`, data)
export const getMemberPointsConfig = () => request.get('/member-points/config')
export const saveMemberPointsConfig = (data) => request.post('/member-points/config', data)
export const getExchangeCouponList = () => request.get('/member-points/exchange-coupons')
export const updateCouponExchangeConfig = (id, data) => request.post(`/member-points/exchange-coupons/${id}`, data)

// Review
export const getReviewPage = (params) => request.get('/review/page', { params })
export const deleteReview = (id) => request.delete(`/review/${id}`)

// Family
export const getFamilyList = () => request.get('/family/list')
export const getUserBindings = (id) => request.get(`/family/user/${id}/bindings`)
