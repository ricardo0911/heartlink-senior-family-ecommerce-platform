/**
 * API 请求封装
 */

const trimTrailingSlash = (url = '') => String(url).replace(/\/+$/, '')

const normalizeApiBase = (url = '') => {
    const cleaned = trimTrailingSlash(url)
    if (!cleaned) return ''
    return /\/api$/i.test(cleaned) ? cleaned : `${cleaned}/api`
}

const isPrivateIp = (host = '') => (
    /^127\./.test(host)
    || /^10\./.test(host)
    || /^192\.168\./.test(host)
    || /^172\.(1[6-9]|2\d|3[0-1])\./.test(host)
)

const isAllowedRuntimeHost = (host = '') => host === 'localhost' || isPrivateIp(host)

const sanitizeRuntimeBase = (url = '') => {
    const value = String(url || '').trim()
    if (!value) return ''
    try {
        const parsed = new URL(value)
        const protocol = parsed.protocol.toLowerCase()
        if (protocol !== 'http:' && protocol !== 'https:') return ''
        if (!isAllowedRuntimeHost(parsed.hostname)) return ''
        return `${protocol}//${parsed.host}${parsed.pathname || ''}`
    } catch (e) {
        return ''
    }
}

const normalizeConfiguredBase = (url = '', options = {}) => {
    const { allowWs = false } = options
    const value = String(url || '').trim()
    if (!value) return ''
    try {
        const parsed = new URL(value)
        const protocol = parsed.protocol.toLowerCase()
        const allowedProtocols = allowWs
            ? ['http:', 'https:', 'ws:', 'wss:']
            : ['http:', 'https:']
        if (!allowedProtocols.includes(protocol)) return ''
        return `${protocol}//${parsed.host}${parsed.pathname || ''}`
    } catch (e) {
        return ''
    }
}

const toWsProtocolBase = (url = '') => String(url || '')
    .replace(/^https:/i, 'wss:')
    .replace(/^http:/i, 'ws:')

const enforceSecureInProd = (url = '', options = {}) => {
    const { ws = false } = options
    const value = String(url || '').trim()
    if (!value) return ''
    try {
        const parsed = new URL(value)
        const host = parsed.hostname || ''
        if (!(runtimeEnv.MODE === 'production' || runtimeEnv.NODE_ENV === 'production')) {
            return `${parsed.protocol}//${parsed.host}${parsed.pathname || ''}`
        }
        if (isAllowedRuntimeHost(host)) {
            return `${parsed.protocol}//${parsed.host}${parsed.pathname || ''}`
        }
        let protocol = parsed.protocol.toLowerCase()
        if (ws) {
            if (protocol === 'http:') protocol = 'ws:'
            if (protocol === 'https:') protocol = 'wss:'
            if (protocol === 'ws:') protocol = 'wss:'
        } else {
            if (protocol === 'http:') protocol = 'https:'
        }
        return `${protocol}//${parsed.host}${parsed.pathname || ''}`
    } catch (e) {
        return value
    }
}

const normalizeWsBase = (url = '') => {
    const cleaned = trimTrailingSlash(url)
    if (!cleaned) return ''
    if (/\/ws\/chat$/i.test(cleaned)) return cleaned
    if (/^wss?:\/\/[^/]+$/i.test(cleaned)) return `${cleaned}/ws/chat`
    return cleaned
}

const runtimeEnv = (() => {
    if (typeof process === 'undefined' || !process || !process.env) return {}
    return process.env
})()
const isProd = (runtimeEnv.MODE === 'production' || runtimeEnv.NODE_ENV === 'production')
const readStorageOverride = (key) => (isProd ? '' : uni.getStorageSync(key))
const h5Origin = (typeof window !== 'undefined' && window.location && window.location.origin) ? window.location.origin : ''

const envApiBase = normalizeConfiguredBase(runtimeEnv.VITE_API_BASE_URL)
const runtimeApiBase = sanitizeRuntimeBase(readStorageOverride('API_BASE_URL') || readStorageOverride('baseUrl'))
const BASE_URL = normalizeApiBase(
    enforceSecureInProd(envApiBase || runtimeApiBase || h5Origin || 'http://127.0.0.1:8089')
)
const API_ORIGIN = BASE_URL.replace(/\/api$/i, '')
const rewriteSameApiUploadUrl = (url = '') => {
    try {
        const parsed = new URL(String(url || '').trim())
        const apiOrigin = new URL(API_ORIGIN)
        if (!/^\/upload\//i.test(parsed.pathname || '')) return ''
        if ((parsed.hostname || '').toLowerCase() !== (apiOrigin.hostname || '').toLowerCase()) return ''
        return `${API_ORIGIN}${parsed.pathname || ''}${parsed.search || ''}`
    } catch (e) {
        return ''
    }
}

export const toAbsoluteMediaUrl = (value) => {
    const raw = String(value || '').trim()
    if (!raw) return ''
    if (/^data:/i.test(raw)) return raw
    if (/^https?:\/\//i.test(raw)) return rewriteSameApiUploadUrl(raw) || raw
    if (raw.startsWith('//')) return `https:${raw}`
    if (raw.startsWith('/')) return `${API_ORIGIN}${raw}`
    return `${API_ORIGIN}/${raw}`
}

const envAiBase = normalizeConfiguredBase(runtimeEnv.VITE_AI_BASE_URL)
const runtimeAiBase = sanitizeRuntimeBase(readStorageOverride('AI_BASE_URL'))
const AI_BASE_URL = trimTrailingSlash(
    enforceSecureInProd(envAiBase || runtimeAiBase || 'http://127.0.0.1:8090')
)

const envWsBase = normalizeConfiguredBase(runtimeEnv.VITE_WS_BASE_URL, { allowWs: true })
const storageWsUrl = readStorageOverride('WS_BASE_URL')
const cleanStorageWsUrl = (storageWsUrl && !storageWsUrl.includes('127.0.0.1')) ? storageWsUrl : ''
const runtimeWsBase = normalizeWsBase(
    toWsProtocolBase(sanitizeRuntimeBase(cleanStorageWsUrl))
)
const WS_BASE_URL = normalizeWsBase(
    enforceSecureInProd(
        toWsProtocolBase(envWsBase)
    || runtimeWsBase
    || `${API_ORIGIN.replace(/^http/i, (v) => (v.toLowerCase() === 'https' ? 'wss' : 'ws'))}/ws/chat`,
        { ws: true }
    )
)

// 获取token
const getToken = () => {
    return uni.getStorageSync('token') || ''
}

const getOrCreateWxMockOpenid = (role = 'USER') => {
    const safeRole = String(role || 'USER').toUpperCase()
    const key = `WX_MOCK_OPENID_${safeRole}`
    let value = String(uni.getStorageSync(key) || '').trim()
    if (!value) {
        value = `${safeRole}_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
        uni.setStorageSync(key, value)
    }
    return value
}

// 防止多个401同时触发重复跳转
let isRedirectingToLogin = false

const redirectToLogin = () => {
    if (isRedirectingToLogin) return
    isRedirectingToLogin = true
    uni.removeStorageSync('token')
    uni.removeStorageSync('userInfo')
    uni.reLaunch({
        url: '/pages/login/login',
        complete: () => {
            setTimeout(() => { isRedirectingToLogin = false }, 1000)
        }
    })
}

// 请求封装
const request = (options) => {
    return new Promise((resolve, reject) => {
        uni.request({
            url: BASE_URL + options.url,
            method: options.method || 'GET',
            data: options.data || {},
            header: {
                'Content-Type': 'application/json',
                'Authorization': getToken(),
                ...options.header
            },
            success: (res) => {
                if (res.statusCode === 200) {
                    if (res.data.code === 200) {
                        resolve(res.data)
                    } else if (res.data.code === 401) {
                        redirectToLogin()
                        reject(res.data)
                    } else {
                        uni.showToast({
                            title: res.data.message || '请求失败',
                            icon: 'none'
                        })
                        reject(res.data)
                    }
                } else if (res.statusCode === 401) {
                    redirectToLogin()
                    reject(res)
                } else {
                    reject(res)
                }
            },
            fail: (err) => {
                uni.showToast({
                    title: '网络错误',
                    icon: 'none'
                })
                reject(err)
            }
        })
    })
}

const uploadFile = (options) => {
    return new Promise((resolve, reject) => {
        uni.uploadFile({
            url: BASE_URL + options.url,
            filePath: options.filePath,
            name: options.name || 'file',
            formData: options.formData || {},
            header: {
                'Authorization': getToken(),
                ...options.header
            },
            success: (res) => {
                let payload = res.data
                if (typeof payload === 'string') {
                    try {
                        payload = JSON.parse(payload)
                    } catch (e) {
                        reject(e)
                        return
                    }
                }
                if (res.statusCode === 200 && payload?.code === 200) {
                    resolve(payload)
                    return
                }
                const message = payload?.message || '上传失败'
                uni.showToast({
                    title: message,
                    icon: 'none'
                })
                reject(payload || res)
            },
            fail: (err) => {
                uni.showToast({
                    title: '网络错误',
                    icon: 'none'
                })
                reject(err)
            }
        })
    })
}

// 认证 API
export const authApi = {
    login: (data) => request({ url: '/auth/login', method: 'POST', data }),
    register: (data) => request({ url: '/auth/register', method: 'POST', data }),
    resetPassword: (data) => request({ url: '/auth/reset-password', method: 'POST', data }),
    wxLogin: (code) => {
        // #ifdef MP-WEIXIN
        return request({ url: `/auth/wx-login?code=${code}&role=CHILD`, method: 'POST' })
        // #endif
        // #ifndef MP-WEIXIN
        const mockOpenid = encodeURIComponent(getOrCreateWxMockOpenid('CHILD'))
        return request({ url: `/auth/wx-login?code=${code}&role=CHILD&mockOpenid=${mockOpenid}`, method: 'POST' })
        // #endif
    },
    bindWx: (code) => {
        // #ifdef MP-WEIXIN
        return request({ url: `/auth/bind-wx?code=${code}`, method: 'POST' })
        // #endif
        // #ifndef MP-WEIXIN
        const mockOpenid = encodeURIComponent(getOrCreateWxMockOpenid('CHILD'))
        return request({ url: `/auth/bind-wx?code=${code}&mockOpenid=${mockOpenid}`, method: 'POST' })
        // #endif
    },
    getCurrentUser: () => request({ url: '/auth/current' }),
    updateCurrentUser: (data) => request({ url: '/auth/current', method: 'PUT', data }),
    uploadAvatar: (filePath) => uploadFile({ url: '/auth/avatar', filePath, name: 'file' })
}

// 家庭绑定 API
export const familyApi = {
    generateCode: (relation = '妈妈') => request({ url: `/family/generate-code?relation=${relation}`, method: 'POST' }),
    getMyParents: () => request({ url: '/family/my-parents' })
}

// 健康档案 API
export const profileApi = {
    getProfile: (parentId) => request({ url: `/profile/${parentId}` }),
    saveProfile: (data) => request({ url: '/profile/save', method: 'POST', data }),
    getHealthAlerts: (parentId) => request({ url: `/profile/${parentId}/health-alerts` })
}

// 商品 API
export const productApi = {
    getProducts: (params) => request({ url: '/product/page', data: params }),
    getProduct: (id) => request({ url: `/product/${id}` }),
    getProductWithAnalysis: (id, parentId) => request({ url: `/product/${id}/with-analysis?parentId=${parentId}` }),
    getRecommend: (parentId, limit = 10) => {
        let url = `/product/recommend?limit=${limit}`;
        if (parentId != null) url += `&parentId=${parentId}`;
        return request({ url });
    },
    searchByImage: (data) => request({ url: '/product/search-by-image', method: 'POST', data })
}

// 智能货架 API
export const shelfApi = {
    push: (parentId, productId, voiceMessage) => request({
        url: `/shelf/push?parentId=${parentId}&productId=${productId}&voiceMessage=${encodeURIComponent(voiceMessage || '')}`,
        method: 'POST'
    }),
    batchPush: (parentId, productIds, voiceMessage) => request({
        url: `/shelf/batch-push?parentId=${parentId}&voiceMessage=${encodeURIComponent(voiceMessage || '')}`,
        method: 'POST',
        data: productIds
    }),
    getLikedItems: (parentId) => request({ url: `/shelf/liked/${parentId}` }),
    getChildList: (params) => request({ url: '/shelf/child-list', data: params }),
    familyShelf: (parentId, params) => request({ url: `/shelf/family-shelf/${parentId}`, data: params })
}

// 订单 API
export const orderApi = {
    create: (data) => request({ url: '/order/create', method: 'POST', data }),
    pay: (orderId, data = {}) => request({ url: `/order/${orderId}/pay`, method: 'POST', data }),
    getChildOrders: (params) => request({ url: '/order/child-orders', data: params }),
    getOrder: (orderId) => request({ url: `/order/${orderId}` }),
    getLogistics: (orderId, refresh = true) => request({ url: `/order/${orderId}/logistics`, data: { refresh } }),
    confirmReceive: (orderId) => request({ url: `/order/${orderId}/confirm-receive`, method: 'POST' }),
    generateGreeting: (orderId) => request({ url: `/order/${orderId}/greeting`, method: 'POST' }),
    uploadRefundImage: (filePath) => uploadFile({ url: '/order/refund/upload-image', filePath, name: 'file' }),
    refund: (orderId, data) => request({ url: `/order/${orderId}/refund`, method: 'POST', data }),
    approveRefund: (orderId) => request({ url: `/order/${orderId}/refund-approve`, method: 'POST' }),
    rejectRefund: (orderId) => request({ url: `/order/${orderId}/refund-reject`, method: 'POST' })
}

// 代付请求 API
export const paymentRequestApi = {
    // 获取待付款请求列表
    getPending: () => request({ url: '/payment-request/pending' }),
    // 获取待付款数量
    getPendingCount: () => request({ url: '/payment-request/pending-count' }),
    // 获取子女的所有代付请求
    getChildRequests: (params) => request({ url: '/payment-request/child-requests', data: params }),
    // 付款
    pay: (requestId, data = {}) => request({ url: `/payment-request/${requestId}/pay`, method: 'POST', data }),
    // 拒绝
    reject: (requestId) => request({ url: `/payment-request/${requestId}/reject`, method: 'POST' })
}

// 分类 API
export const categoryApi = {
    getList: () => request({ url: '/category/list' })
}

// AI服务 BASE_URL
export const appConfig = {
    apiBaseUrl: BASE_URL,
    aiBaseUrl: AI_BASE_URL,
    wsChatUrl: WS_BASE_URL
}

// AI请求封装（不需要token）
const aiRequest = (options) => {
    return new Promise((resolve, reject) => {
        uni.request({
            url: AI_BASE_URL + options.url,
            method: options.method || 'GET',
            data: options.data || {},
            header: {
                'Content-Type': 'application/json'
            },
            success: (res) => {
                if (res.statusCode === 200) {
                    resolve(res.data)
                } else {
                    reject(res)
                }
            },
            fail: (err) => {
                reject(err)
            }
        })
    })
}

const aiUploadFile = (options) => {
    return new Promise((resolve, reject) => {
        uni.uploadFile({
            url: AI_BASE_URL + options.url,
            filePath: options.filePath,
            name: options.name || 'file',
            formData: options.formData || {},
            success: (res) => {
                let payload = res.data
                if (typeof payload === 'string') {
                    try {
                        payload = JSON.parse(payload)
                    } catch (e) {
                        reject(e)
                        return
                    }
                }

                if (res.statusCode === 200) {
                    resolve(payload)
                    return
                }

                const message = payload?.message || payload?.detail || 'AI处理失败'
                reject({ ...(payload || {}), message, statusCode: res.statusCode })
            },
            fail: (err) => {
                reject(err)
            }
        })
    })
}

// AI 对话 API
export const aiApi = {
    chat: (message, context) => aiRequest({
        url: '/api/chat',
        method: 'POST',
        data: { message, context }
    }),
    createTryOnTask: async (filePath, data = {}) => {
        const res = await uploadFile({
            url: '/ai/tryon/tasks',
            filePath,
            name: 'person_image',
            formData: data
        })
        return res?.data || res
    },
    getTryOnTask: async (taskId) => {
        const res = await request({
            url: `/ai/tryon/tasks/${encodeURIComponent(taskId)}`
        })
        return res?.data || res
    }
}

// 平台推荐 API (调用AI服务)
export const platformApi = {
    getRecommend: (params) => aiRequest({
        url: '/api/recommend/platform',
        method: 'POST',
        data: params
    }),
    getCategories: () => aiRequest({ url: '/api/category/list' })
}

// 会员积分 API
export const memberApi = {
    // 获取会员信息
    getInfo: () => request({ url: '/member/info' }),
    // 计算折扣价
    calculateDiscount: (price) => request({ url: `/member/calculate-discount?price=${price}` }),
    // 使用积分抵扣
    usePoints: (points) => request({ url: `/member/use-points?points=${points}`, method: 'POST' }),
    // 签到获取积分
    checkIn: () => request({ url: '/member/check-in', method: 'POST' })
}

// 消息 API
export const messageApi = {
    send: (data) => request({ url: '/message/send', method: 'POST', data }),
    list: (params) => request({ url: '/message/list', data: params }),
    unreadCount: () => request({ url: '/message/unread-count' }),
    markRead: (familyBindId) => request({ url: `/message/read/${familyBindId}`, method: 'POST' })
}

// 地址 API
export const addressApi = {
    save: (data) => request({ url: '/address/save', method: 'POST', data }),
    list: () => request({ url: '/address/list' }),
    delete: (id) => request({ url: `/address/${id}`, method: 'DELETE' }),
    setDefault: (id) => request({ url: `/address/${id}/default`, method: 'POST' })
}

// 评价 API
export const reviewApi = {
    productReviews: (productId, params) => request({ url: `/review/product/${productId}`, data: params })
}

// 优惠券 API
export const couponApi = {
    available: () => request({ url: '/coupon/available' }),
    receive: (id) => request({ url: `/coupon/${id}/receive`, method: 'POST' }),
    exchange: (id) => request({ url: `/coupon/${id}/exchange`, method: 'POST' }),
    my: () => request({ url: '/coupon/my' }),
    usable: (params) => request({ url: '/coupon/usable', data: params })
}

// SOS API
export const sosApi = {
    contacts: () => request({ url: '/sos/contacts' }),
    saveContacts: (data) => request({ url: '/sos/contacts', method: 'POST', data })
}

// 周报 API
export const reportApi = {
    weekly: (parentId) => request({ url: `/profile/${parentId}/weekly-report` })
}

export default {
    authApi,
    familyApi,
    profileApi,
    productApi,
    shelfApi,
    orderApi,
    paymentRequestApi,
    categoryApi,
    aiApi,
    platformApi,
    memberApi,
    messageApi,
    addressApi,
    reviewApi,
    couponApi,
    sosApi,
    reportApi
}
