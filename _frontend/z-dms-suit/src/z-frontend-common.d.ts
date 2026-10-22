declare module '@yuku123/z-frontend-common' {
  import type { ReactElement, ReactNode } from 'react'

  export interface AppUser { name?: string; role?: string }
  export interface AppIcon { icon?: ReactNode; color?: string; label?: string }
  export interface AppLayoutMenuItem {
    key: string
    label?: ReactNode
    icon?: ReactNode
    disabled?: boolean
    children?: AppLayoutMenuItem[]
  }

  export function AppLayout(props: {
    menuItems?: AppLayoutMenuItem[]
    appTitle?: string
    appShort?: string
    appVersion?: string
    appIcon?: AppIcon
    appUser?: AppUser | null
    loginPath?: string
    headerExtra?: ReactNode
    loading?: boolean
  }): ReactElement

  export function createRequest(options?: {
    baseURL?: string
    timeout?: number
    tokenKey?: string
    userInfoKey?: string
    unauthorizedRedirect?: string
  }): {
    request: (config: Record<string, unknown>) => Promise<unknown>
    get: (url: string, config?: Record<string, unknown>) => Promise<unknown>
    post: (url: string, data?: unknown, config?: Record<string, unknown>) => Promise<unknown>
    put: (url: string, data?: unknown, config?: Record<string, unknown>) => Promise<unknown>
    delete: (url: string, config?: Record<string, unknown>) => Promise<unknown>
    interceptors: {
      request: { use: (fn: (config: any) => any) => void }
      response: { use: (ok: (r: any) => any, err: (e: any) => any) => void }
    }
  }

  export const ErrorBoundary: React.ComponentType<{ children?: ReactNode; onReset?: () => void }>

  export function StatusTag(props: {
    status: string | number
    map?: Record<string, { color?: string; text: string }>
  }): ReactElement
}
