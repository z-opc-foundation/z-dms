/**
 * 基座 UI 桶（@yuku123/z-frontend-common/ui）的类型声明。
 *
 * 【为什么手写而不是等基座出 d.ts】
 * 基座是纯 JS 包（dist 里只有 js，没有 .d.ts），exports 的 './ui' 子路径也没配 types，
 * TS 直接 import 会报 TS7016 "Could not find a declaration file"。本仓是 TS 仓，
 * 所以只能像已有的 z-frontend-common.d.ts 那样手写一份最小声明。
 *
 * 【维护约定】
 * 这里只声明本仓真正用到的组件；**props 以基座源码 src/components/ui/*.jsx 为准**，
 * 基座升版改了签名要回来同步。
 */
declare module '@yuku123/z-frontend-common/ui' {
  import type { ReactElement, ReactNode } from 'react'

  // 组件文档页（lead 008 §7）：基座提供 TOC + 两栏 + 零依赖 markdown 引擎，
  // 各仓只提供 DOC.md 内容（编译期 ?raw 内联）。
  export function ComponentDoc(props: {
    markdown: string
    title?: string
    tocWidth?: number
    tocLevels?: number[]
    height?: string
    markdownComponents?: Record<string, unknown>
    renderMarkdown?: (args: { markdown: string; components: Record<string, unknown> }) => ReactNode
  }): ReactElement
}
