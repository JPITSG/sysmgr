'use strict';

const fs = require('fs');
const zlib = require('zlib');

const ZIP_EOCD_SIGNATURE = 0x06054b50;
const ZIP_CENTRAL_SIGNATURE = 0x02014b50;
const ZIP_LOCAL_SIGNATURE = 0x04034b50;
const ZIP_EOCD_MIN_BYTES = 22;
const ZIP_MAX_COMMENT_BYTES = 0xffff;
const ZIP_MAX_CENTRAL_BYTES = 64 * 1024 * 1024;
const MAX_MANIFEST_BYTES = 16 * 1024 * 1024;

const RES_STRING_POOL_TYPE = 0x0001;
const RES_XML_TYPE = 0x0003;
const RES_XML_START_ELEMENT_TYPE = 0x0102;
const STRING_POOL_UTF8_FLAG = 0x00000100;
const TYPE_STRING = 0x03;
const TYPE_FIRST_INT = 0x10;
const TYPE_LAST_INT = 0x1f;
const NO_INDEX = 0xffffffff;

function readApkVersion(apkPath) {
  const fd = fs.openSync(apkPath, 'r');
  try {
    const stat = fs.fstatSync(fd);
    if (!stat.isFile() || stat.size < ZIP_EOCD_MIN_BYTES) {
      throw new Error('APK is empty or is not a regular file');
    }
    const manifest = readZipEntry(fd, stat.size, 'AndroidManifest.xml');
    return parseAndroidManifest(manifest);
  } finally {
    fs.closeSync(fd);
  }
}

function readZipEntry(fd, fileSize, wantedName) {
  const tailLength = Math.min(fileSize, ZIP_EOCD_MIN_BYTES + ZIP_MAX_COMMENT_BYTES);
  const tail = readExactly(fd, tailLength, fileSize - tailLength);
  let eocdOffset = -1;
  for (let offset = tail.length - ZIP_EOCD_MIN_BYTES; offset >= 0; offset -= 1) {
    if (tail.readUInt32LE(offset) !== ZIP_EOCD_SIGNATURE) {
      continue;
    }
    const commentLength = tail.readUInt16LE(offset + 20);
    if (offset + ZIP_EOCD_MIN_BYTES + commentLength === tail.length) {
      eocdOffset = offset;
      break;
    }
  }
  if (eocdOffset < 0) {
    throw new Error('APK ZIP end record is missing');
  }

  const diskNumber = tail.readUInt16LE(eocdOffset + 4);
  const centralDisk = tail.readUInt16LE(eocdOffset + 6);
  const entryCount = tail.readUInt16LE(eocdOffset + 10);
  const centralSize = tail.readUInt32LE(eocdOffset + 12);
  const centralOffset = tail.readUInt32LE(eocdOffset + 16);
  if (diskNumber !== 0 || centralDisk !== 0) {
    throw new Error('multi-disk APK ZIP files are not supported');
  }
  if (entryCount === 0xffff || centralSize === 0xffffffff || centralOffset === 0xffffffff) {
    throw new Error('ZIP64 APK files are not supported');
  }
  if (centralSize < 1 || centralSize > ZIP_MAX_CENTRAL_BYTES
      || centralOffset + centralSize > fileSize) {
    throw new Error('APK ZIP central directory is invalid');
  }

  const central = readExactly(fd, centralSize, centralOffset);
  let cursor = 0;
  for (let index = 0; index < entryCount; index += 1) {
    if (cursor + 46 > central.length
        || central.readUInt32LE(cursor) !== ZIP_CENTRAL_SIGNATURE) {
      throw new Error('APK ZIP central directory entry is invalid');
    }
    const flags = central.readUInt16LE(cursor + 8);
    const method = central.readUInt16LE(cursor + 10);
    const expectedCrc = central.readUInt32LE(cursor + 16);
    const compressedSize = central.readUInt32LE(cursor + 20);
    const uncompressedSize = central.readUInt32LE(cursor + 24);
    const nameLength = central.readUInt16LE(cursor + 28);
    const extraLength = central.readUInt16LE(cursor + 30);
    const commentLength = central.readUInt16LE(cursor + 32);
    const localOffset = central.readUInt32LE(cursor + 42);
    const entryEnd = cursor + 46 + nameLength + extraLength + commentLength;
    if (entryEnd > central.length) {
      throw new Error('APK ZIP central directory is truncated');
    }
    const name = central.toString('utf8', cursor + 46, cursor + 46 + nameLength);
    cursor = entryEnd;
    if (name !== wantedName) {
      continue;
    }
    if ((flags & 0x0001) !== 0) {
      throw new Error('encrypted APK manifests are not supported');
    }
    if (compressedSize > MAX_MANIFEST_BYTES || uncompressedSize > MAX_MANIFEST_BYTES) {
      throw new Error('APK manifest is too large');
    }
    return readLocalEntry(fd, fileSize, localOffset, compressedSize,
      uncompressedSize, method, expectedCrc);
  }
  throw new Error('APK does not contain AndroidManifest.xml');
}

function readLocalEntry(fd, fileSize, localOffset, compressedSize,
                        uncompressedSize, method, expectedCrc) {
  if (localOffset + 30 > fileSize) {
    throw new Error('APK manifest local header is truncated');
  }
  const local = readExactly(fd, 30, localOffset);
  if (local.readUInt32LE(0) !== ZIP_LOCAL_SIGNATURE) {
    throw new Error('APK manifest local header is invalid');
  }
  const nameLength = local.readUInt16LE(26);
  const extraLength = local.readUInt16LE(28);
  const dataOffset = localOffset + 30 + nameLength + extraLength;
  if (dataOffset + compressedSize > fileSize) {
    throw new Error('APK manifest data is truncated');
  }
  const compressed = readExactly(fd, compressedSize, dataOffset);
  let manifest;
  if (method === 0) {
    manifest = compressed;
  } else if (method === 8) {
    manifest = zlib.inflateRawSync(compressed, {maxOutputLength: MAX_MANIFEST_BYTES});
  } else {
    throw new Error(`unsupported APK manifest compression method ${method}`);
  }
  if (manifest.length !== uncompressedSize) {
    throw new Error('APK manifest size does not match its ZIP entry');
  }
  if (crc32(manifest) !== expectedCrc) {
    throw new Error('APK manifest checksum does not match its ZIP entry');
  }
  return manifest;
}

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
    }
  }
  return (~crc) >>> 0;
}

function readExactly(fd, length, position) {
  const buffer = Buffer.alloc(length);
  let offset = 0;
  while (offset < length) {
    const count = fs.readSync(fd, buffer, offset, length - offset, position + offset);
    if (count < 1) {
      throw new Error('unexpected end of APK');
    }
    offset += count;
  }
  return buffer;
}

function parseAndroidManifest(manifest) {
  if (manifest.length < 8) {
    throw new Error('Android manifest is empty');
  }
  const firstTextByte = firstNonWhitespaceByte(manifest);
  if (firstTextByte === 0x3c) {
    return parseTextManifest(manifest.toString('utf8'));
  }
  return parseBinaryManifest(manifest);
}

function firstNonWhitespaceByte(buffer) {
  for (const byte of buffer) {
    if (byte !== 0x09 && byte !== 0x0a && byte !== 0x0d && byte !== 0x20) {
      return byte;
    }
  }
  return -1;
}

function parseTextManifest(xml) {
  const manifestTag = xml.match(/<manifest\b[^>]*>/i);
  if (!manifestTag) {
    throw new Error('text Android manifest has no manifest element');
  }
  const versionName = xmlAttribute(manifestTag[0], 'versionName');
  const versionCodeText = xmlAttribute(manifestTag[0], 'versionCode');
  const packageName = xmlAttribute(manifestTag[0], 'package');
  const versionCode = Number(versionCodeText);
  return validateVersionInfo({versionName, versionCode, packageName});
}

function xmlAttribute(tag, localName) {
  const match = tag.match(new RegExp(`(?:[A-Za-z_][\\w.-]*:)?${localName}\\s*=\\s*(["'])(.*?)\\1`, 'i'));
  return match ? decodeXmlEntities(match[2]) : '';
}

function decodeXmlEntities(value) {
  return value.replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&');
}

function parseBinaryManifest(buffer) {
  const rootType = buffer.readUInt16LE(0);
  const rootHeaderSize = buffer.readUInt16LE(2);
  const rootSize = buffer.readUInt32LE(4);
  if (rootType !== RES_XML_TYPE || rootHeaderSize < 8
      || rootSize < rootHeaderSize || rootSize > buffer.length) {
    throw new Error('Android binary manifest header is invalid');
  }

  let strings = null;
  let offset = rootHeaderSize;
  while (offset + 8 <= rootSize) {
    const type = buffer.readUInt16LE(offset);
    const headerSize = buffer.readUInt16LE(offset + 2);
    const chunkSize = buffer.readUInt32LE(offset + 4);
    if (headerSize < 8 || chunkSize < headerSize || offset + chunkSize > rootSize) {
      throw new Error('Android binary manifest chunk is invalid');
    }
    if (type === RES_STRING_POOL_TYPE) {
      strings = parseStringPool(buffer, offset, headerSize, chunkSize);
    } else if (type === RES_XML_START_ELEMENT_TYPE && strings) {
      const result = parseStartElement(buffer, offset, headerSize, chunkSize, strings);
      if (result) {
        return validateVersionInfo(result);
      }
    }
    offset += chunkSize;
  }
  throw new Error('Android binary manifest has no manifest element');
}

function parseStringPool(buffer, chunkOffset, headerSize, chunkSize) {
  if (headerSize < 28 || chunkOffset + headerSize > buffer.length) {
    throw new Error('Android manifest string pool header is invalid');
  }
  const stringCount = buffer.readUInt32LE(chunkOffset + 8);
  const flags = buffer.readUInt32LE(chunkOffset + 16);
  const stringsStart = buffer.readUInt32LE(chunkOffset + 20);
  const offsetsStart = chunkOffset + headerSize;
  const dataStart = chunkOffset + stringsStart;
  const chunkEnd = chunkOffset + chunkSize;
  if (stringCount > 1000000 || offsetsStart + stringCount * 4 > chunkEnd
      || dataStart < offsetsStart || dataStart > chunkEnd) {
    throw new Error('Android manifest string pool is invalid');
  }
  const offsets = new Array(stringCount);
  for (let index = 0; index < stringCount; index += 1) {
    offsets[index] = buffer.readUInt32LE(offsetsStart + index * 4);
  }
  const utf8 = (flags & STRING_POOL_UTF8_FLAG) !== 0;
  const cache = new Map();
  return function stringAt(index) {
    if (index === NO_INDEX) {
      return '';
    }
    if (!Number.isInteger(index) || index < 0 || index >= offsets.length) {
      throw new Error('Android manifest string index is invalid');
    }
    if (cache.has(index)) {
      return cache.get(index);
    }
    let cursor = dataStart + offsets[index];
    if (cursor < dataStart || cursor >= chunkEnd) {
      throw new Error('Android manifest string offset is invalid');
    }
    let value;
    if (utf8) {
      cursor = skipUtf8Length(buffer, cursor, chunkEnd);
      const byteLength = readUtf8Length(buffer, cursor, chunkEnd);
      cursor = byteLength.next;
      if (cursor + byteLength.value > chunkEnd) {
        throw new Error('Android manifest UTF-8 string is truncated');
      }
      value = buffer.toString('utf8', cursor, cursor + byteLength.value);
    } else {
      const charLength = readUtf16Length(buffer, cursor, chunkEnd);
      cursor = charLength.next;
      const byteLength = charLength.value * 2;
      if (cursor + byteLength > chunkEnd) {
        throw new Error('Android manifest UTF-16 string is truncated');
      }
      value = buffer.toString('utf16le', cursor, cursor + byteLength);
    }
    cache.set(index, value);
    return value;
  };
}

function skipUtf8Length(buffer, offset, end) {
  return readUtf8Length(buffer, offset, end).next;
}

function readUtf8Length(buffer, offset, end) {
  if (offset >= end) {
    throw new Error('Android manifest UTF-8 length is truncated');
  }
  const first = buffer[offset];
  if ((first & 0x80) === 0) {
    return {value: first, next: offset + 1};
  }
  if (offset + 1 >= end) {
    throw new Error('Android manifest UTF-8 length is truncated');
  }
  return {value: ((first & 0x7f) << 8) | buffer[offset + 1], next: offset + 2};
}

function readUtf16Length(buffer, offset, end) {
  if (offset + 2 > end) {
    throw new Error('Android manifest UTF-16 length is truncated');
  }
  const first = buffer.readUInt16LE(offset);
  if ((first & 0x8000) === 0) {
    return {value: first, next: offset + 2};
  }
  if (offset + 4 > end) {
    throw new Error('Android manifest UTF-16 length is truncated');
  }
  const second = buffer.readUInt16LE(offset + 2);
  return {value: (first & 0x7fff) * 0x10000 + second, next: offset + 4};
}

function parseStartElement(buffer, chunkOffset, headerSize, chunkSize, stringAt) {
  const extensionOffset = chunkOffset + headerSize;
  const chunkEnd = chunkOffset + chunkSize;
  if (extensionOffset + 20 > chunkEnd) {
    throw new Error('Android manifest start element is truncated');
  }
  const elementName = stringAt(buffer.readUInt32LE(extensionOffset + 4));
  if (elementName !== 'manifest') {
    return null;
  }
  const attributeStart = buffer.readUInt16LE(extensionOffset + 8);
  const attributeSize = buffer.readUInt16LE(extensionOffset + 10);
  const attributeCount = buffer.readUInt16LE(extensionOffset + 12);
  const attributesOffset = extensionOffset + attributeStart;
  if (attributeSize < 20 || attributesOffset < extensionOffset
      || attributesOffset + attributeSize * attributeCount > chunkEnd) {
    throw new Error('Android manifest attributes are invalid');
  }

  const values = {versionName: '', versionCode: 0, packageName: ''};
  for (let index = 0; index < attributeCount; index += 1) {
    const attributeOffset = attributesOffset + index * attributeSize;
    const name = stringAt(buffer.readUInt32LE(attributeOffset + 4));
    if (name !== 'versionName' && name !== 'versionCode' && name !== 'package') {
      continue;
    }
    const rawValue = buffer.readUInt32LE(attributeOffset + 8);
    const dataType = buffer[attributeOffset + 15];
    const data = buffer.readUInt32LE(attributeOffset + 16);
    let value = '';
    if (rawValue !== NO_INDEX) {
      value = stringAt(rawValue);
    } else if (dataType === TYPE_STRING) {
      value = stringAt(data);
    } else if (dataType >= TYPE_FIRST_INT && dataType <= TYPE_LAST_INT) {
      value = String(data);
    }
    if (name === 'versionName') {
      values.versionName = value;
    } else if (name === 'versionCode') {
      values.versionCode = Number(value);
    } else {
      values.packageName = value;
    }
  }
  return values;
}

function validateVersionInfo(info) {
  const versionName = String(info.versionName || '').trim();
  const packageName = String(info.packageName || '').trim();
  const versionCode = Number(info.versionCode);
  if (!versionName) {
    throw new Error('APK manifest does not declare versionName');
  }
  if (!Number.isSafeInteger(versionCode) || versionCode < 1) {
    throw new Error('APK manifest does not declare a valid versionCode');
  }
  if (!packageName) {
    throw new Error('APK manifest does not declare a package name');
  }
  return {versionName, versionCode, packageName};
}

module.exports = {readApkVersion};
