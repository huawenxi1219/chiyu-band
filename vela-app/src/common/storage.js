// 池鱼单词 - 文件存储封装（基于喵喵电子书 AGPL 开源代码改造）
import file from '@system.file'

var storageFile = {}
const FILE_PATH = 'internal://files/words/data.json'

function readAll() {
  return new Promise(function (resolve) {
    file.readText({
      uri: FILE_PATH,
      success: function (data) {
        try { resolve(JSON.parse(data.text)) } catch (e) { resolve({}) }
      },
      fail: function () { resolve({}) }
    })
  })
}

function writeAll(obj) {
  return new Promise(function (resolve) {
    file.writeText({
      uri: FILE_PATH,
      text: JSON.stringify(obj),
      success: function () { resolve(true) },
      fail: function () { resolve(false) }
    })
  })
}

storageFile.get = function (key, def) {
  return readAll().then(function (obj) {
    var v = obj[key]
    return (v === undefined || v === null) ? (def === undefined ? '' : def) : v
  })
}

storageFile.set = function (key, value) {
  return readAll().then(function (obj) {
    obj[key] = value
    return writeAll(obj)
  })
}

storageFile.getAll = readAll
storageFile.saveAll = writeAll

export default storageFile
