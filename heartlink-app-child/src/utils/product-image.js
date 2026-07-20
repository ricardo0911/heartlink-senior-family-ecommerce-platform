import { toAbsoluteMediaUrl } from '../api/index.js'

const DEFAULT_PRODUCT_IMAGE = '/static/products/oatmeal.png'
const LOCAL_FAVORITES_KEY = 'CHILD_LOCAL_FAVORITES'

const LOCAL_PRODUCT_IMAGES = new Set([
  '1.png',
  '2.png',
  '3.png',
  '4.png',
  'blood-pressure.png',
  'foot-bath.png',
  'oatmeal.png',
  'smartphone.png',
  'sweater.png',
  'cane.png'
])

const KEYWORD_IMAGE_RULES = [
  { pattern: /(血压|pressure)/i, image: '/static/products/blood-pressure.png' },
  { pattern: /(泡脚|足浴|bath)/i, image: '/static/products/foot-bath.png' },
  { pattern: /(燕麦|麦片|oat)/i, image: '/static/products/oatmeal.png' },
  { pattern: /(手机|phone)/i, image: '/static/products/smartphone.png' },
  { pattern: /(毛衣|sweater|针织)/i, image: '/static/products/sweater.png' },
  { pattern: /(拐杖|手杖|cane|walker|护膝|kneepad|knee)/i, image: '/static/products/cane.png' }
]

const isWechatMiniProgram = (() => {
  let value = false
  // #ifdef MP-WEIXIN
  value = true
  // #endif
  return value
})()

const parseFavorites = (value) => {
  if (Array.isArray(value)) return value
  if (typeof value === 'string' && value.trim()) {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed : []
    } catch (e) {
      return []
    }
  }
  return []
}

const normalizeListField = (value) => {
  if (Array.isArray(value)) return value.filter(Boolean)
  if (typeof value === 'string' && value.trim()) {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed.filter(Boolean) : [value]
    } catch (e) {
      return [value]
    }
  }
  return []
}

const normalizeFavoriteProduct = (product = {}) => {
  const id = Number(product.id || 0)
  if (!id) return null
  return {
    id,
    name: String(product.name || '').trim() || '收藏商品',
    price: product.price,
    description: String(product.description || '').trim(),
    images: normalizeListField(product.images),
    healthTags: normalizeListField(product.healthTags),
    warningTags: normalizeListField(product.warningTags),
    favoritedAt: product.favoritedAt || new Date().toISOString()
  }
}

const writeLocalFavorites = (list) => {
  uni.setStorageSync(LOCAL_FAVORITES_KEY, JSON.stringify(Array.isArray(list) ? list : []))
}

export const getDefaultProductImage = (hint = '') => {
  const text = String(hint || '').trim()
  if (!text) return DEFAULT_PRODUCT_IMAGE

  const match = KEYWORD_IMAGE_RULES.find((item) => item.pattern.test(text))
  return match ? match.image : DEFAULT_PRODUCT_IMAGE
}

export const resolveProductImageUrl = (value, options = {}) => {
  const fallback = getDefaultProductImage(options.productName || options.hint)
  const text = String(value || '').trim()

  if (!text) return fallback
  if (/^data:/i.test(text)) return text

  if (text.startsWith('/static/products/')) {
    const fileName = text.split('/').pop() || ''
    return LOCAL_PRODUCT_IMAGES.has(fileName) ? text : fallback
  }

  if (/^https?:\/\//i.test(text)) {
    const resolved = toAbsoluteMediaUrl(text) || text
    if (/^https:\/\//i.test(resolved)) {
      return resolved
    }
    if (/^http:\/\//i.test(resolved)) {
      return isWechatMiniProgram ? fallback : resolved
    }
    return fallback
  }

  if (text.startsWith('/upload/') || text.startsWith('upload/')) {
    const resolved = toAbsoluteMediaUrl(text)
    if (!resolved) return fallback
    if (isWechatMiniProgram && /^http:\/\//i.test(resolved)) {
      return fallback
    }
    return resolved
  }

  return fallback
}

export const getLocalFavorites = () => {
  return parseFavorites(uni.getStorageSync(LOCAL_FAVORITES_KEY))
    .map((item) => normalizeFavoriteProduct(item))
    .filter(Boolean)
    .sort((left, right) => String(right.favoritedAt || '').localeCompare(String(left.favoritedAt || '')))
}

export const isLocalFavorite = (productId) => {
  const id = Number(productId || 0)
  if (!id) return false
  return getLocalFavorites().some((item) => item.id === id)
}

export const toggleLocalFavorite = (product) => {
  const normalized = normalizeFavoriteProduct(product)
  if (!normalized) {
    return { favorited: false, list: getLocalFavorites() }
  }

  const favorited = !isLocalFavorite(normalized.id)
  const list = favorited
    ? [normalized, ...getLocalFavorites().filter((item) => item.id !== normalized.id)]
    : getLocalFavorites().filter((item) => item.id !== normalized.id)

  writeLocalFavorites(list)
  return { favorited, list }
}
