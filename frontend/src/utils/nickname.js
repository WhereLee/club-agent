// 昵称校验：与后端 @Nickname 注解保持一致
// 字符集：中文/英文字母/数字（表情、空格、标点、控制字符一律不允许）
// 长度：显示宽度折算（汉字 2、英文数字 1），总宽 4-24 → 纯中文 2-12 字 / 纯英文 4-24 字符 / 混合按宽度
export const NICKNAME_MESSAGE = '昵称限 2-12 个汉字或 4-24 位英文/数字（混合按显示宽度折算），不支持表情符号'

export function isNicknameValid(value) {
  if (!value) return false
  if (!/^[\u4e00-\u9fa5A-Za-z0-9]+$/.test(value)) return false
  let width = 0
  for (const ch of value) {
    width += /[\u4e00-\u9fa5]/.test(ch) ? 2 : 1
  }
  return width >= 4 && width <= 24
}
