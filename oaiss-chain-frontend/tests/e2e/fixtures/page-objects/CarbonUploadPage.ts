import { type Page, expect } from '@playwright/test'

/**
 * 企业端「上传审核」页对象。
 *
 * 选择器说明：本页在 pen 壳层迁移后已从旧的 `.section-card` / `.search-form`
 * 结构换成 `.upload-page` + `FilterBar` + `DataPanel` 组件族。
 * 旧选择器在本页恒为 0，会让断言恒失败（不是功能回归，是选择器过期）。
 */
export class CarbonUploadPage {
  constructor(private page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/enterprise/carbon/upload')
    await this.page.waitForLoadState('domcontentloaded')
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/enterprise\/carbon\/upload/)
    await expect(this.page.locator('.upload-page').first()).toBeVisible()
    await expect(
      this.page.locator('.el-button').filter({ hasText: /创建|新建|Create/ }).first(),
    ).toBeVisible()
  }

  async expectTableHeaders(): Promise<void> {
    await expect(this.page.locator('.el-table').first()).toBeVisible()
    await expect(this.page.getByRole('columnheader').first()).toBeVisible()
  }

  async expectEmptyState(): Promise<void> {
    // pen 实现下空态由 EmptyHint 或 el-table 内置空块承担，两者取其一
    await expect(
      this.page.locator('.el-table__empty-block, .el-empty, [class*=empty-hint]').first(),
    ).toBeVisible()
  }

  async expectSearchForm(): Promise<void> {
    const filter = this.page.getByRole('region').first()
    await expect(filter).toBeVisible()
    await expect(filter.locator('input').first()).toBeVisible()
    await expect(
      filter.locator('.el-button').filter({ hasText: /查询|Search/ }).first(),
    ).toBeVisible()
  }
}
