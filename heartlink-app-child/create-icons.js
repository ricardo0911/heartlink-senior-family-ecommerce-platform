const fs = require('fs');
const path = require('path');

// 创建简单的81x81像素PNG图标
// 微信小程序tabBar图标要求：PNG格式，建议81x81px，小于40KB

function createSimplePNG(color, outputPath) {
    // PNG文件头和基本结构
    // 创建一个简单的81x81单色PNG
    const width = 81;
    const height = 81;

    // PNG签名
    const signature = Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);

    // IHDR chunk
    const ihdrData = Buffer.alloc(13);
    ihdrData.writeUInt32BE(width, 0);  // 宽度
    ihdrData.writeUInt32BE(height, 4); // 高度
    ihdrData.writeUInt8(8, 8);         // 位深度
    ihdrData.writeUInt8(2, 9);         // 颜色类型 (RGB)
    ihdrData.writeUInt8(0, 10);        // 压缩方法
    ihdrData.writeUInt8(0, 11);        // 过滤方法
    ihdrData.writeUInt8(0, 12);        // 隔行扫描

    const ihdrChunk = createChunk('IHDR', ihdrData);

    // 创建简单的图像数据 - 单色填充
    const rawData = [];
    const [r, g, b] = color;

    for (let y = 0; y < height; y++) {
        rawData.push(0); // 过滤类型: None
        for (let x = 0; x < width; x++) {
            rawData.push(r, g, b);
        }
    }

    // 使用zlib压缩
    const zlib = require('zlib');
    const compressed = zlib.deflateSync(Buffer.from(rawData));
    const idatChunk = createChunk('IDAT', compressed);

    // IEND chunk
    const iendChunk = createChunk('IEND', Buffer.alloc(0));

    // 合并所有部分
    const png = Buffer.concat([signature, ihdrChunk, idatChunk, iendChunk]);
    fs.writeFileSync(outputPath, png);
    console.log(`Created: ${outputPath} (${png.length} bytes)`);
}

function createChunk(type, data) {
    const typeBuffer = Buffer.from(type);
    const length = Buffer.alloc(4);
    length.writeUInt32BE(data.length, 0);

    const crcData = Buffer.concat([typeBuffer, data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(crcData), 0);

    return Buffer.concat([length, typeBuffer, data, crc]);
}

function crc32(buffer) {
    let crc = 0xFFFFFFFF;
    const table = [];

    for (let i = 0; i < 256; i++) {
        let c = i;
        for (let j = 0; j < 8; j++) {
            c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
        }
        table[i] = c;
    }

    for (let i = 0; i < buffer.length; i++) {
        crc = table[(crc ^ buffer[i]) & 0xFF] ^ (crc >>> 8);
    }

    return (crc ^ 0xFFFFFFFF) >>> 0;
}

const iconsDir = path.join(__dirname, 'src/static/icons');

// 灰色 #999999 = [153, 153, 153]
// 红色 #FF6B6B = [255, 107, 107]
const grayColor = [153, 153, 153];
const redColor = [255, 107, 107];

createSimplePNG(grayColor, path.join(iconsDir, 'home.png'));
createSimplePNG(redColor, path.join(iconsDir, 'home-active.png'));
createSimplePNG(grayColor, path.join(iconsDir, 'shop.png'));
createSimplePNG(redColor, path.join(iconsDir, 'shop-active.png'));
createSimplePNG(grayColor, path.join(iconsDir, 'heart.png'));
createSimplePNG(redColor, path.join(iconsDir, 'heart-active.png'));
createSimplePNG(grayColor, path.join(iconsDir, 'user.png'));
createSimplePNG(redColor, path.join(iconsDir, 'user-active.png'));

console.log('Done! All icons created.');
