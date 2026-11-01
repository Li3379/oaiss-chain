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

  /**
   * 断言顶栏一级页签。
   *
   * 与 expectSidebar 的区别：侧栏只渲染 rail === false 的叶子，而顶栏页签
   * 是**按 tab 去重**后的结果——多个菜单共用同一 tab 时（撮合控制台、成交审核
   * 都是 tab: 'trade'），顶栏只出现一次「交易市场」。
   * 所以不能拿菜单项名去断言顶栏，必须断言页签文案。
   */
  async expectTopTabs(expectedTabs: string[]): Promise<void> {
    for (const tab of expectedTabs) {
      await expect(this.page.locator('.top-tab', { hasText: tab }).first()).toBeVisible()
    }
  }

  /** 顶栏页签总数（去重后的 tab 数） */
  async expectTopTabCount(count: number): Promise<void> {
    await expect(this.page.locator('.top-tab')).toHaveCount(count)
  }

  /** 图标栏条数（企业 12 / 审核 5 / 监管 2 / 管理 6，含「搜索查询」入口） */
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
