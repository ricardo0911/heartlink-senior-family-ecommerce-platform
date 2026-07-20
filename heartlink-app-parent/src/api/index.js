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

const uploadFileRequest = (options) => {
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
                try {
                    payload = typeof payload === 'string' ? JSON.parse(payload || '{}') : payload
                } catch (e) {}

                if (res.statusCode === 200) {
                    if (payload && payload.code === 200) {
                        resolve(payload)
                    } else if (payload && payload.code === 401) {
                        redirectToLogin()
                        reject(payload)
                    } else {
                        uni.showToast({
                            title: (payload && payload.message) || '上传失败',
                            icon: 'none'
                        })
                        reject(payload || res)
                    }
                } else if (res.statusCode === 401) {
                    redirectToLogin()
                    reject(payload || res)
                } else {
                    uni.showToast({
                        title: '上传失败',
                        icon: 'none'
                    })
                    reject(payload || res)
                }
            },
            fail: (err) => {
                uni.showToast({
                    title: '上传失败',
                    icon: 'none'
                })
                reject(err)
            }
        })
    })
}

const getFileNameFromPath = (filePath = '') => {
    const normalized = String(filePath || '').split('?')[0].replace(/\\/g, '/')
    const segments = normalized.split('/')
    return segments[segments.length - 1] || ''
}

const getFileExtFromPath = (filePath = '') => {
    const fileName = getFileNameFromPath(filePath)
    const dotIndex = fileName.lastIndexOf('.')
    if (dotIndex < 0) return ''
    return fileName.slice(dotIndex).toLowerCase()
}

export const appConfig = {
    apiBaseUrl: BASE_URL,
    aiBaseUrl: AI_BASE_URL,
    wsChatUrl: WS_BASE_URL
}

const aiRequest = (options) => {
    return new Promise((resolve, reject) => {
        uni.request({
            url: AI_BASE_URL + options.url,
            method: options.method || 'GET',
            data: options.data || {},
            header: {
                'Content-Type': 'application/json',
                ...options.header
            },
            success: (res) => {
                if (res.statusCode === 200) {
                    resolve(res.data)
                } else {
                    reject(res)
                }
            },
            fail: (err) => reject(err)
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
        return request({ url: `/auth/wx-login?code=${code}&role=PARENT`, method: 'POST' })
        // #endif
        // #ifndef MP-WEIXIN
        const mockOpenid = encodeURIComponent(getOrCreateWxMockOpenid('PARENT'))
        return request({ url: `/auth/wx-login?code=${code}&role=PARENT&mockOpenid=${mockOpenid}`, method: 'POST' })
        // #endif
    },
    getCurrentUser: () => request({ url: '/auth/current' })
}

// 家庭绑定 API
export const familyApi = {
    generateCode: (relation = '妈妈') => request({ url: `/family/generate-code?relation=${relation}`, method: 'POST' }),
    getMyParents: () => request({ url: '/family/my-parents' }),
    confirmBind: (bindCode) => request({ url: `/family/confirm-bind?bindCode=${encodeURIComponent(bindCode)}`, method: 'POST' }),
    getMyChildren: () => request({ url: '/family/my-children' })
}

// 健康档案 API
export const profileApi = {
    getProfile: (parentId) => request({ url: `/profile/${parentId}` }),
    getMy: () => request({ url: '/profile/my' }),
    getMyList: () => request({ url: '/profile/my/list' }),
    saveMy: (data) => request({ url: '/profile/my/save', method: 'POST', data }),
    setMyPrimary: (profileId) => request({ url: `/profile/my/${profileId}/primary`, method: 'POST' }),
    saveProfile: (data) => request({ url: '/profile/save', method: 'POST', data })
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
    searchByImage: (image) => request({
        url: '/product/search-by-image',
        method: 'POST',
        data: { image }
    })
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
    getMyLiked: () => request({ url: '/shelf/my-liked' }),
    getMyShelf: (params) => request({ url: '/shelf/my-shelf', data: params }),
    react: (shelfId, reaction) => request({ url: `/shelf/${shelfId}/react?reaction=${reaction}`, method: 'POST' }),
    collect: (productId) => request({ url: `/shelf/collect?productId=${productId}`, method: 'POST' })
}

export const orderApi = {
    getParentOrders: (params) => request({ url: '/order/parent-orders', data: params }),
    getOrder: (orderId) => request({ url: `/order/${orderId}` }),
    getLogistics: (orderId, refresh = true) => request({ url: `/order/${orderId}/logistics`, data: { refresh } }),
    confirmReceive: (orderId) => request({ url: `/order/${orderId}/confirm-receive`, method: 'POST' })
}

// 代付请求 API（长辈端）
export const paymentRequestApi = {
    // 创建代付请求
    create: (orderId, message) => request({
        url: `/payment-request/create?orderId=${orderId}&message=${encodeURIComponent(message || '')}`,
        method: 'POST'
    }),
    // 按商品创建代付请求（自动创建/复用待付款订单）
    createByProduct: (productId, message) => request({
        url: `/payment-request/create-by-product?productId=${productId}&message=${encodeURIComponent(message || '')}`,
        method: 'POST'
    }),
    // 获取长辈的代付请求列表
    getParentRequests: (params) => request({ url: '/payment-request/parent-requests', data: params })
}

export const aiApi = {
    chat: (message, context) => aiRequest({
        url: '/api/chat',
        method: 'POST',
        data: { message, context }
    }),
    tts: (text, rate = 'slow') => new Promise((resolve, reject) => {
        uni.request({
            url: AI_BASE_URL + '/api/tts',
            method: 'POST',
            data: { text, rate },
            responseType: 'arraybuffer',
            header: {
                'Content-Type': 'application/json'
            },
            success: (res) => {
                if (res.statusCode === 200 && res.data) {
                    resolve({
                        arrayBuffer: res.data,
                        headers: res.header || {}
                    })
                } else {
                    reject(res)
                }
            },
            fail: (err) => reject(err)
        })
    })
}

// 消息 API
export const messageApi = {
    send: (data) => request({ url: '/message/send', method: 'POST', data }),
    uploadVoice: (filePath) => uploadFileRequest({
        url: '/message/upload-voice',
        filePath,
        name: 'file',
        formData: {
            clientFileName: getFileNameFromPath(filePath),
            clientExt: getFileExtFromPath(filePath)
        }
    }),
    list: (params) => request({ url: '/message/list', data: params }),
    unreadCount: () => request({ url: '/message/unread-count' }),
    markRead: (familyBindId) => request({ url: `/message/read/${familyBindId}`, method: 'POST' })
}

// SOS API
export const sosApi = {
    trigger: () => request({ url: '/sos/trigger', method: 'POST' })
}

export default {
    authApi,
    familyApi,
    profileApi,
    productApi,
    shelfApi,
    orderApi,
    paymentRequestApi,
    aiApi,
    messageApi,
    sosApi
}
