import { type Page, expect } from '@playwright/test'

/**
 * 壳层导航断言。
 *
 * pen 稿把侧栏收敛成 72px 图标栏（64×56，图标 + 2~3 字短标签），
 * 可访问名保留完整菜单名，因此这里仍按完整名定位 role=menuitem。
 * 带子菜单的分组在图标栏里只落一个入口（其余叶子由顶栏页签进入），
 * 所以断言列表比叶子路由少——这是设计稿的规模，不是功能缺失。
 */
export class Layout {
  constructor(private page: Page) {}

  async expectSidebar(expectedItems: string[]): Promise<void> {
    for (const item of expectedItems) {
      await expect(this.page.getByRole('menuitem', { name: item })).toBeVisible()
    }
  }

  /** 图标栏条数（pen 稿：企业 12 / 审核 4 / 监管 2 / 管理 5，含「搜索查询」入口） */
  async expectRailCount(count: number): Promise<void> {
    await expect(this.page.locator('.pen-rail__item')).toHaveCount(count)
  }

  async expectUserInfo(username: string, roleLabel: string): Promise<void> {
    const header = this.page.locator('header').first()
    await expect(header.getByText(username, { exact: true })).toBeVisible()
    await expect(header.getByText(roleLabel)).toBeVisible()
  }

  async expectBreadcrumb(...segments: string[]): Promise<void> {
    // pen 壳层统一用 `.pen-crumbs`（aria-label="breadcrumb"），旧页可能仍是 .el-breadcrumb。
    // 取先命中的一个，避免因页面代际差异导致断言恒失败。
    const nav = this.page.locator('.pen-crumbs, .el-breadcrumb').first()
    await expect(nav).toBeVisible()
    for (const segment of segments) {
      await expect(nav).toContainText(segment)
    }
  }
}
