/**
 * 门户页配图（来源：pencli 设计稿的 imagery 资产）。
 *
 * 取舍依据 design-taste-frontend/references/imagery.md：
 *   §3 真实世界照片胜过插画；§7 已展示数据/界面的页面不再放同义插画。
 * 因此只保留语义成立的实拍图，并把原先的外链图床换成本地资源。
 */
import forestAerial from '../assets/pen/forest-aerial.jpg'
import solarFarm from '../assets/pen/solar-farm.jpg'
import dataCenter from '../assets/pen/datacenter.jpg'

export { LOGIN_ART, SOLAR_FARM_ART, DATA_CENTER_ART } from './penImagery'

/** 门户首屏：碳汇意象（晨雾森林航拍） */
export const HERO_BG: string = forestAerial

/** 系统介绍区：清洁能源生产侧（光伏电站航拍） */
export const INTRO_BG: string = solarFarm

/** 生态图集：三张语义成立的实拍（碳汇 / 清洁能源 / 联盟链载体） */
export const GALLERY_IMAGES: string[] = [forestAerial, solarFarm, dataCenter]