export const LOGISTICS_COMPANIES = [
  { code: 'SF', name: '顺丰速运' },
  { code: 'JD', name: '京东快递' },
  { code: 'EMS', name: 'EMS' },
  { code: 'YTO', name: '圆通速递' },
  { code: 'ZTO', name: '中通快递' },
  { code: 'STO', name: '申通快递' },
  { code: 'YD', name: '韵达快递' },
  { code: 'DBL', name: '德邦快递' },
  { code: 'UC', name: '优速快递' },
  { code: 'HTKY', name: '百世快递' },
  { code: 'ZJS', name: '宅急送' },
  { code: 'YZPY', name: '邮政快递包裹' }
]

export const findLogisticsCompany = (code) => {
  const normalized = String(code || '').trim().toUpperCase()
  return LOGISTICS_COMPANIES.find((item) => item.code === normalized) || null
}
