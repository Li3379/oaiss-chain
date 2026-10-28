#!/usr/bin/env node
/**
 * Bundle Size Analysis
 *
 * Analyzes the Vite build output and reports bundle sizes.
 * Can be used in CI to track size regressions.
 *
 * Usage: node scripts/analyze-bundle.mjs [--json] [--fail-on-regression]
 *
 * @author OAISS Chain Team
 * @since 1.3.0
 */

import { readdirSync, statSync, writeFileSync, readFileSync, existsSync } from 'node:fs'
import { join, extname } from 'node:path'

const DIST_DIR = join(import.meta.dirname, '..', 'dist', 'assets')
const BUDGET_FILE = join(import.meta.dirname, '..', '.bundle-budget.json')
const REPORT_FILE = join(import.meta.dirname, '..', 'bundle-report.json')

const args = process.argv.slice(2)
const jsonOutput = args.includes('--json')
const failOnRegression = args.includes('--fail-on-regression')

// Default size budgets (in KB)
const DEFAULT_BUDGETS = {
  'total-js': 1500,        // Total JS < 1.5MB
  'total-css': 300,         // Total CSS < 300KB
  'single-chunk': 500,      // No single chunk > 500KB
  'echarts-total': 700,     // ECharts total < 700KB
  'element-plus-total': 700 // Element Plus total < 700KB
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

function analyze() {
  if (!existsSync(DIST_DIR)) {
    console.error('Error: dist/assets not found. Run "npm run build" first.')
    process.exit(1)
  }

  const files = readdirSync(DIST_DIR)
    .filter(f => f.endsWith('.js') || f.endsWith('.css'))
    .map(f => ({
      name: f,
      size: statSync(join(DIST_DIR, f)).size,
      type: extname(f).slice(1)
    }))
    .sort((a, b) => b.size - a.size)

  const jsFiles = files.filter(f => f.type === 'js')
  const cssFiles = files.filter(f => f.type === 'css')

  const totalJs = jsFiles.reduce((sum, f) => sum + f.size, 0)
  const totalCss = cssFiles.reduce((sum, f) => sum + f.size, 0)

  const echartsSize = files
    .filter(f => f.name.includes('echarts') || f.name.includes('zrender'))
    .reduce((sum, f) => sum + f.size, 0)

  const elementPlusSize = files
    .filter(f => f.name.includes('element-plus'))
    .reduce((sum, f) => sum + f.size, 0)

  const report = {
    timestamp: new Date().toISOString(),
    totals: {
      js: formatSize(totalJs),
      css: formatSize(totalCss),
      combined: formatSize(totalJs + totalCss),
      jsBytes: totalJs,
      cssBytes: totalCss
    },
    libraries: {
      echarts: formatSize(echartsSize),
      elementPlus: formatSize(elementPlusSize)
    },
    chunks: files.map(f => ({
      name: f.name,
      size: formatSize(f.size),
      sizeBytes: f.size,
      type: f.type
    }))
  }

  // Load budgets
  let budgets = DEFAULT_BUDGETS
  if (existsSync(BUDGET_FILE)) {
    budgets = { ...DEFAULT_BUDGETS, ...JSON.parse(readFileSync(BUDGET_FILE, 'utf8')) }
  }

  // Check budgets
  const violations = []
  if (totalJs / 1024 > budgets['total-js']) {
    violations.push(`Total JS ${formatSize(totalJs)} exceeds budget ${budgets['total-js']} KB`)
  }
  if (totalCss / 1024 > budgets['total-css']) {
    violations.push(`Total CSS ${formatSize(totalCss)} exceeds budget ${budgets['total-css']} KB`)
  }
  for (const f of files) {
    if (f.size / 1024 > budgets['single-chunk']) {
      violations.push(`Chunk ${f.name} ${formatSize(f.size)} exceeds budget ${budgets['single-chunk']} KB`)
    }
  }
  if (echartsSize / 1024 > budgets['echarts-total']) {
    violations.push(`ECharts total ${formatSize(echartsSize)} exceeds budget ${budgets['echarts-total']} KB`)
  }
  if (elementPlusSize / 1024 > budgets['element-plus-total']) {
    violations.push(`Element Plus total ${formatSize(elementPlusSize)} exceeds budget ${budgets['element-plus-total']} KB`)
  }

  report.budgets = budgets
  report.violations = violations
  report.pass = violations.length === 0

  // Output
  if (jsonOutput) {
    console.log(JSON.stringify(report, null, 2))
  } else {
    console.log('\n=== Bundle Size Report ===\n')
    console.log(`  Total JS:  ${formatSize(totalJs)}`)
    console.log(`  Total CSS: ${formatSize(totalCss)}`)
    console.log(`  Combined:  ${formatSize(totalJs + totalCss)}\n`)
    console.log('  Libraries:')
    console.log(`    ECharts:      ${formatSize(echartsSize)}`)
    console.log(`    Element Plus: ${formatSize(elementPlusSize)}\n`)
    console.log('  Top chunks:')
    files.slice(0, 10).forEach(f => {
      console.log(`    ${f.name.padEnd(50)} ${formatSize(f.size).padStart(10)}`)
    })

    if (violations.length > 0) {
      console.log('\n  ⚠ Budget violations:')
      violations.forEach(v => console.log(`    - ${v}`))
    } else {
      console.log('\n  ✅ All size budgets passed')
    }
  }

  // Save report
  writeFileSync(REPORT_FILE, JSON.stringify(report, null, 2))
  console.log(`\nReport saved to: ${REPORT_FILE}`)

  if (failOnRegression && violations.length > 0) {
    process.exit(1)
  }
}

analyze()
