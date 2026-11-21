/**
 * 基座代码编辑器子路径（@yuku123/z-frontend-common/code）的类型声明。
 *
 * 【为什么手写而不是等基座出 d.ts】
 * 基座是纯 JS 包（dist 里只有 js，没有 .d.ts），exports 的 './code' 子路径也没配 types，
 * TS 直接 import 会报 TS7016 "Could not find a declaration file"。本仓是 TS 仓，
 * 所以像 z-frontend-common.d.ts / z-frontend-common-ui.d.ts 那样手写一份最小声明。
 *
 * 【维护约定】
 * 这里只声明本仓真正用到的导出；**签名以基座源码 src/components/code/ 为准**，
 * 基座升版改了签名要回来同步。
 */
declare module '@yuku123/z-frontend-common/code' {
  import type { CSSProperties, ReactElement } from 'react'

  /** 代码编辑器（CodeMirror 6 壳 + 语言注册表 + 自研 ANTLR(g4) 桥）。 */
  export function ZCodeEditor(props: {
    value?: string
    onChange?: (value: string) => void
    /** 语言键：sql / sql-antlr / json / json-antlr / xml / yaml / java / javascript / groovy / lua / shell / properties / toml，或 registerLanguage 注册过的名字 */
    language?: string
    /** 像素数字或 CSS 高度串，默认 200 */
    height?: number | string
    readOnly?: boolean
    placeholder?: string
    dark?: boolean
    style?: CSSProperties
  }): ReactElement

  /** 注册一种语言（support 为 CodeMirror LanguageSupport/Extension，linter 为诊断扩展）。 */
  export function registerLanguage(
    name: string,
    def: { support?: unknown; linter?: unknown; aliases?: string[] },
  ): void

  /** 取语言定义；未知语言返回 null（编辑器退化为纯文本）。 */
  export function getLanguage(name: string): { support?: unknown; linter?: unknown } | null

  /** 列出全部已注册语言键。 */
  export function listLanguages(): string[]

  /** z-script 的 dslType → 编辑器语言映射。 */
  export const DSL_TYPE_LANGUAGE: Record<string, string>
}
