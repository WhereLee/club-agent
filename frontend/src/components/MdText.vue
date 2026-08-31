<script setup>
// AI 文本 Markdown 渲染组件：
// - marked 解析（仅 assistant 侧内容；用户输入保持纯文本插值）
// - 手写净化：剥离 script/iframe 等危险标签、on* 事件属性、javascript: 伪协议
//   （内容为自家 LLM 产出，非任意外部输入；净化作为纵深防御）
// - 样式收口在 .md-body 作用域内，不污染页面全局
import { computed } from 'vue'
import { marked } from 'marked'

const props = defineProps({
  text: { type: String, default: '' },
})

marked.setOptions({ gfm: true, breaks: true })

/** 危险标签整段剥离（含内容） */
const BLOCK_STRIP = /<\s*\/?\s*(script|iframe|object|embed|style|link|meta|form)\b[^>]*>/gi
/** 剩余标签内的 on* 事件属性 */
const ATTR_EVENT = /\s+on\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi
/** javascript: 伪协议（href/src 值） */
const ATTR_JS_URL = /(href|src)\s*=\s*(["']?)\s*javascript:[^"'>\s]*\2/gi

function sanitize(html) {
  return html
    .replace(BLOCK_STRIP, '')
    .replace(ATTR_EVENT, '')
    .replace(ATTR_JS_URL, '$1=$2#$2')
}

const html = computed(() => {
  const t = props.text || ''
  if (!t.trim()) return ''
  try {
    return sanitize(marked.parse(t))
  } catch (e) {
    // 解析异常兜底：回退纯文本（转义尖括号）
    return t.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }
})
</script>

<template>
  <!-- eslint-disable-next-line vue/no-v-html —— 内容经 sanitize 净化，仅用于 AI 产出渲染 -->
  <div class="md-body" v-html="html"></div>
</template>

<style scoped>
.md-body { word-break: break-word; white-space: normal; }
/* 抵消容器的 white-space: pre-wrap（气泡类），让 Markdown 块级语义生效 */
.md-body :deep(*) { white-space: normal; }
.md-body :deep(pre) { white-space: pre-wrap; background: #f7f8fa; border-radius: 4px; padding: 6px 8px; overflow-x: auto; }
.md-body :deep(code) { background: #f7f8fa; border-radius: 3px; padding: 1px 4px; font-size: 0.9em; }
.md-body :deep(p) { margin: 0 0 6px; }
.md-body :deep(p:last-child) { margin-bottom: 0; }
.md-body :deep(ul), .md-body :deep(ol) { margin: 4px 0 6px; padding-left: 20px; }
.md-body :deep(li) { margin: 2px 0; }
.md-body :deep(h1), .md-body :deep(h2), .md-body :deep(h3),
.md-body :deep(h4), .md-body :deep(h5), .md-body :deep(h6) {
  margin: 8px 0 4px; font-size: 1em; font-weight: 700; line-height: 1.5;
}
.md-body :deep(blockquote) { margin: 4px 0; padding: 2px 10px; border-left: 3px solid #dcdfe6; color: #606266; }
.md-body :deep(table) { border-collapse: collapse; margin: 6px 0; }
.md-body :deep(th), .md-body :deep(td) { border: 1px solid #e4e7ed; padding: 3px 8px; }
.md-body :deep(a) { color: #409eff; text-decoration: none; }
.md-body :deep(hr) { border: none; border-top: 1px solid #e4e7ed; margin: 8px 0; }
</style>
