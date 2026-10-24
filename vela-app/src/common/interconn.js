// 池鱼单词 - 手环端互连模块（基于喵喵电子书 AGPL 开源代码改造）
import interconnect from "@system.interconnect";

export default class Interconn {
  callbacks = {};
  listeners = [];
  constructor() {
    this.conn = interconnect.instance();
    this.conn.onmessage = ({ data }) => {
      let msg = null;
      try { msg = JSON.parse(data) } catch (e) {
        // 收到无法解析的消息：记录诊断（不静默丢弃），下次问题可查
        try {
          var p = (data && data.length) ? data.length : 0
          globalThis.diag && globalThis.diag('parse', 'FAIL len=' + p)
        } catch (e2) { }
        return
      }
      const { tag, ...payload } = msg;
      if (this.callbacks[tag]) this.callbacks[tag](payload);
    };
    this.conn.onclose = () => this.emit("close");
    this.conn.onerror = () => this.emit("error");
    this.conn.onopen = () => this.emit("open");
  }
  on(tag, callback) {
    this.callbacks[tag] = callback;
  }
  off(tag) {
    delete this.callbacks[tag];
  }
  addEventListener(callback) {
    return this.listeners.push(callback) - 1;
  }
  removeEventListener(index) {
    this.listeners.splice(index, 1);
  }
  emit(evt) {
    this.listeners.forEach(fn => fn(evt));
  }
  send(tag, payload) {
    const data = typeof payload === "object" ? { ...payload, tag } : { msg: payload, tag };
    return new Promise((resolve, reject) => {
      this.conn.send({ data, success: resolve, fail: reject });
    });
  }
  get state() {
    return this.conn.getApkStatus();
  }
}
