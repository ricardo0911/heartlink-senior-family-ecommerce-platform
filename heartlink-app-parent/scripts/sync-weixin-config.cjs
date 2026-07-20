const fs = require('fs')
const path = require('path')

const root = process.cwd()
const distDir = path.join(root, 'dist', 'build', 'mp-weixin')

if (!fs.existsSync(distDir)) {
  process.exit(0)
}

const sourceConfig = path.join(root, 'project.config.json')
const sourcePrivateConfig = path.join(root, 'project.private.config.json')
const targetConfig = path.join(distDir, 'project.config.json')
const targetPrivateConfig = path.join(distDir, 'project.private.config.json')

if (fs.existsSync(sourceConfig)) {
  fs.copyFileSync(sourceConfig, targetConfig)
}

if (fs.existsSync(sourcePrivateConfig)) {
  fs.copyFileSync(sourcePrivateConfig, targetPrivateConfig)
}

console.log('synced weixin project config to dist/build/mp-weixin')
