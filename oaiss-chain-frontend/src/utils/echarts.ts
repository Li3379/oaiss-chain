import * as echarts from 'echarts/core'

import { BarChart, LineChart, PieChart, RadarChart } from 'echarts/charts'

import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  DatasetComponent,
  TitleComponent,
  DataZoomComponent,
  RadarComponent,
  MarkPointComponent,
} from 'echarts/components'

import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  RadarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  DatasetComponent,
  TitleComponent,
  DataZoomComponent,
  RadarComponent,
  MarkPointComponent,
  CanvasRenderer,
])

export default echarts
