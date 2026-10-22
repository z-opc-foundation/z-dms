/// <reference types="vite/client" />

/**
 * vite 客户端类型引用。
 *
 * 【为什么需要这个文件】
 * 本仓是 TS 仓，而组件文档页（lead 008 §7）用 `import docMarkdown from '../DOC.md?raw'`
 * 把 DOC.md 在编译期内联成字符串。`?raw` 这个后缀属于 vite 约定，
 * 只有 `vite/client` 类型里才有 `declare module '*?raw'` 的声明 —— 没有这个引用，
 * `tsc --noEmit` 会直接报 TS2307「Cannot find module '../DOC.md?raw'」。
 *
 * 同样的引用在 z-surl-suit 里已有先例（那边还额外声明了本仓允许注入的环境变量）。
 */
