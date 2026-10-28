// Enterprise pages, batch 1: carbon upload (rebuild), emission data, neutral projects, neutral detail.
const RAIL = ``;
const TABS = ``;
const USER = ``;

export const pages = [
  {
    name: '01-CarbonUpload',
    code: String.raw`
const main=SCREEN({name:"01 碳核算填报",x:0,y:0,navItems:NAV_ENT,tab:0,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:0});
PAGEHEAD(main,{crumbs:["碳中和","碳核算"],title:"碳核算填报",sub:"编制排放报告、附凭证并送审；审核通过后由系统自动写入 Fabric 存证。",
 actions:[{l:"导出报表",i:"download"},{l:"创建报告",i:"plus",kind:"primary"}]});
const stats=ROW(main,{gap:12});
STAT(stats,{l:"本年度已报排放",v:"12,480.6",u:"tCO₂e",i:"factory",d:"较上年度 −4.2%",dtone:"up"});
STAT(stats,{l:"待审核报告",v:"3",u:"份",i:"clock",d:"最早提交 2026-05-28"});
STAT(stats,{l:"已上链存证",v:"41",u:"份",i:"blocks",d:"最近存证 2 小时前"});
STAT(stats,{l:"配额余额",v:"38,000",u:"tCO₂e",i:"wallet",d:"使用率 24.00%"});
const filt=F(main,{n:"Filters",dir:"horizontal",w:"fill_container",gap:10,align:"end"});
FIELD(filt,{l:"报告标题",ph:"按标题检索",w:240,i:"search"});
FIELD(filt,{l:"核算周期",ph:"如 2026-Q1",w:170});
FIELD(filt,{l:"状态",ph:"全部状态",w:150,i:"chevron-down"});
BTN(filt,{l:"查询",kind:"primary"});
BTN(filt,{l:"重置",kind:"ghost"});
const tbl=PANEL(main,{n:"ReportsPanel",t:"报告列表",i:"files",meta:"共 8 份 · 按创建时间倒序",right:[{l:"批量送审",kind:"ghost",i:"send"}]});
const body=PANEL_BODY(tbl);
TABLE(body,{zebra:true,cols:[
 {l:"报告编号",w:140},{l:"报告标题"},{l:"核算周期",w:104},{l:"总排放量",w:150,a:"r"},{l:"状态",w:104},{l:"签名",w:92},{l:"审核人",w:86},{l:"创建时间",w:126},{l:"操作",w:96,a:"r"}],
 rows:[
  ["RPT-2026-010","2026 年第一季度排放报告","2026-Q1",{mono:"370.5"},{pill:"审核中",tone:"warn"},{pill:"已签名",tone:"live"},"王审核","2026-05-31 08:12",{text:"查看详情"}],
  ["RPT-2026-009","2026 年 3 月月度核算",{text:"2026-03",col:"$textSecondary"},{mono:"128.4"},{pill:"审核通过",tone:"info"},{pill:"已签名",tone:"live"},"王审核","2026-05-28 16:40",{text:"查看详情"}],
  ["RPT-2026-008","2026 年 2 月月度核算",{text:"2026-02",col:"$textSecondary"},{mono:"141.2"},{pill:"已上链",tone:"live"},{pill:"已签名",tone:"live"},"李审核","2026-05-20 09:05",{text:"查看详情"}],
  ["RPT-2026-007","2026 年第一季度补充核算",{text:"2026-Q1",col:"$textSecondary"},{mono:"96.8"},{pill:"审核拒绝",tone:"danger"},{pill:"已签名",tone:"live"},"王审核","2026-05-18 11:22",{text:"查看详情"}],
  ["RPT-2026-006","2026 年 1 月月度核算",{text:"2026-01",col:"$textSecondary"},{mono:"134.5"},{pill:"已上链",tone:"live"},{pill:"已签名",tone:"live"},"李审核","2026-05-10 14:31",{text:"查看详情"}],
  ["RPT-2026-005","2025 年度排放报告",{text:"2025-Y",col:"$textSecondary"},{mono:"3,162.0"},{pill:"已上链",tone:"live"},{pill:"已签名",tone:"live"},"王审核","2026-04-02 10:00",{text:"查看详情"}]
 ]});
PAGER(body,{count:"共 8 条 · 每页 10 条"});
`
  },
  {
    name: '04-EmissionData',
    code: String.raw`
const main=SCREEN({name:"04 排放数据",x:1560,y:0,navItems:NAV_ENT,tab:0,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:8});
PAGEHEAD(main,{crumbs:["碳中和","排放数据"],title:"排放数据",sub:"查看本企业的排放评级、行业排名与 AI 预测，评级由平台按行业基准自动计算。",
 actions:[{l:"导出评级报告",i:"download",kind:"ghost"}]});
const stats=ROW(main,{gap:12});
STAT(stats,{l:"当前评级",v:"A",u:"级",i:"award",d:"行业前 12%",dtone:"up"});
STAT(stats,{l:"本期总排放",v:"370.5",u:"tCO₂e",i:"factory",d:"行业均值 512.0"});
STAT(stats,{l:"综合评分",v:"92",u:"/100",i:"gauge",d:"较上期 +3"});
STAT(stats,{l:"行业排名",v:"3",u:"/ 26",i:"trophy-2",d:"较上期 ↑2 位",dtone:"up"});
TABS(main,{on:0,items:["排放评级","行业排名","AI 预测"]});
const high=ROW(main,{gap:14});
const leftCol=COL(high,{w:520,gap:14});
const card=PANEL(leftCol,{n:"RatingCard",t:"本期排放评级",i:"award",meta:"2026-Q1"});
const cb=PANEL_BODY(card,{pad:[18,18]});
const grade=ROW(cb,{gap:16,align:"center"});
const gbox=F(grade,{n:"Grade",dir:"horizontal",w:88,h:88,just:"center",align:"center",fill:"#132018",stroke:"#2B4A22",sw:1,rad:3});
T(gbox,{n:"G",c:"A",fs:46,fw:"600",mono:true,col:C.acc});
const gcol=COL(grade,{w:"fill_container",gap:6});
T(gcol,{n:"T",c:"优秀 · 行业领先",fs:16,fw:"500",col:C.t1});
T(gcol,{n:"D",c:"本期排放低于行业均值 27.6%，连续 4 个季度保持 A 级。",fs:12,col:C.t2,lh:1.6,w:"fill_container"});
PILL(gcol,{l:"评级已生效",tone:"live"});
H(cb,{n:"Div",h:1,col:C.line});
KV(cb,{k:"总排放量",v:"370.5 tCO₂e"});
KV(cb,{k:"行业均值",v:"512.0 tCO₂e"});
KV(cb,{k:"综合评分",v:"92 / 100",col:C.acc});
KV(cb,{k:"评级时间",v:"2026-04-01 09:00"});
const trend=PANEL(leftCol,{n:"TrendCard",t:"评级变化趋势",i:"trending-up",meta:"近 4 个季度"});
const tb=PANEL_BODY(trend,{pad:[16,18]});
BARS(tb,{h:120,data:[88,90,91,92],labels:["2025-Q2","2025-Q3","2025-Q4","2026-Q1"],hi:3,bw:26});

const rightCol=COL(high,{gap:14});
const rank=PANEL(rightCol,{n:"RankCard",t:"行业排名",i:"trophy-2",meta:"2026 年度 · 共 26 家企业",
 right:[{l:"全部排名",kind:"ghost"}]});
const rb=PANEL_BODY(rank);
TABLE(rb,{cols:[{l:"排名",w:64},{l:"企业名称"},{l:"总排放量",w:130,a:"r"},{l:"评级",w:76},{l:"评分",w:70,a:"r"}],
 rows:[[{mono:"1"},"低碳制造股份有限公司",{mono:"310.2"},{pill:"A",tone:"live"},{mono:"95"}],
 [{mono:"2"},"清洁能源发展有限公司",{mono:"342.8"},{pill:"A",tone:"live"},{mono:"93"}],
 [{mono:"3",col:"$accent"},"绿色能源科技有限公司",{mono:"370.5",col:"$textPrimary"},{pill:"A",tone:"live"},{mono:"92"}],
 [{mono:"4"},"东方热电集团",{mono:"408.6"},{pill:"B",tone:"info"},{mono:"86"}],
 [{mono:"5"},"华北建材有限公司",{mono:"452.1"},{pill:"B",tone:"info"},{mono:"81"}]]});
const pred=PANEL(rightCol,{n:"PredCard",t:"AI 预测",i:"sparkles",meta:"模型 v1.0 · R² 0.912"});
const pbb=PANEL_BODY(pred,{pad:[14,18]});
const pr=ROW(pbb,{gap:14,align:"end"});
FIELD(pr,{l:"预测月数",v:"6 个月",w:150,i:"chevron-down"});
FIELD(pr,{l:"基准期",v:"2026-Q1",w:150,i:"calendar"});
BTN(pr,{l:"重新预测",kind:"primary",i:"sparkles"});
H(pbb,{n:"D2",h:1,col:C.line});
LEGEND(pbb,{items:[{l:"2026-04  预测排放",v:"374.8 tCO₂e",c:"#204134"},{l:"2026-05  预测排放",v:"381.2 tCO₂e",c:"#204134"},{l:"2026-06  预测排放",v:"384.2 tCO₂e",c:C.acc}]});
T(pbb,{n:"Conf",c:"置信度 88% · 依据近 8 个季度排放与产量数据",fs:11,col:C.t3,w:"fill_container"});
`
  },
  {
    name: '05-CarbonNeutral',
    code: String.raw`
const main=SCREEN({name:"05 碳中和项目",x:3120,y:0,navItems:NAV_ENT,tab:2,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:8});
PAGEHEAD(main,{crumbs:["碳中和","项目列表"],title:"碳中和项目",sub:"申报减排项目并推进生命周期：草稿 → 待审核 → 已批准 → 实施中 → 已完成。",
 actions:[{l:"导出项目台账",i:"download",kind:"ghost"},{l:"创建项目",i:"plus",kind:"primary"}]});
const stats=ROW(main,{gap:12});
STAT(stats,{l:"项目总数",v:"6",u:"个",i:"folder-tree"});
STAT(stats,{l:"实施中",v:"2",u:"个",i:"activity",d:"光伏一期 · 节能改造"});
STAT(stats,{l:"预期减排合计",v:"46,500",u:"tCO₂e",i:"leaf"});
STAT(stats,{l:"实际减排合计",v:"18,600",u:"tCO₂e",i:"check-check",d:"完成率 40.0%"});
const tbl=PANEL(main,{n:"ProjPanel",t:"项目列表",i:"folder-tree",meta:"共 6 个 · 按创建时间倒序"});
const body=PANEL_BODY(tbl);
TABLE(body,{zebra:true,rh:48,cols:[
 {l:"序号",w:56},{l:"项目名称"},{l:"项目类型",w:110},{l:"预期减排量",w:130,a:"r"},{l:"状态",w:100},{l:"起止日期",w:190},{l:"操作",w:150,a:"r"}],
 rows:[
  [{mono:"1"},"2026 分布式光伏一期",{text:"新能源",col:"$textSecondary"},{mono:"12,000 吨"},{pill:"实施中",tone:"live"},{text:"2026-01-01 ~ 2026-12-31",col:"$textSecondary"},{text:"更新监测数据"}],
  [{mono:"2"},"燃煤锅炉节能改造",{text:"节能改造",col:"$textSecondary"},{mono:"8,500 吨"},{pill:"实施中",tone:"live"},{text:"2026-03-01 ~ 2026-11-30",col:"$textSecondary"},{text:"更新监测数据"}],
  [{mono:"3"},"厂区碳汇造林一期",{text:"碳汇造林",col:"$textSecondary"},{mono:"12,000 吨"},{pill:"已批准",tone:"info"},{text:"2026-06-01 ~ 2027-05-31",col:"$textSecondary"},{text:"提交审核"}],
  [{mono:"4"},"CCUS 示范装置",{text:"CCUS",col:"$textSecondary"},{mono:"9,000 吨"},{pill:"待审核",tone:"warn"},{text:"2026-07-01 ~ 2027-06-30",col:"$textSecondary"},{text:"撤回申请"}],
  [{mono:"5"},"余热回收二期",{text:"节能改造",col:"$textSecondary"},{mono:"5,000 吨"},{pill:"已完成",tone:"live"},{text:"2025-01-01 ~ 2025-12-31",col:"$textSecondary"},{text:"申请认证"}],
  [{mono:"6"},"老旧机组燃料替代",{text:"节能改造",col:"$textSecondary"},{mono:"3,200 吨"},{pill:"已驳回",tone:"danger"},{text:"2025-05-01 ~ 2025-10-31",col:"$textSecondary"},{text:"查看驳回意见"}]
 ]});
PAGER(body,{count:"共 6 个 · 每页 10 个"});
`
  },
  {
    name: '06-NeutralDetail',
    code: String.raw`
const main=SCREEN({name:"06 项目详情",x:4680,y:0,navItems:NAV_ENT,tab:2,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:8});
PAGEHEAD(main,{crumbs:["碳中和","项目列表","2026 分布式光伏一期"],title:"2026 分布式光伏一期",sub:"新能源 · 预期减排 12,000 吨 · 2026-01-01 至 2026-12-31",
 actions:[{l:"终止项目",i:"x-circle",kind:"ghost"},{l:"申请认证",i:"badge-check",kind:"primary"}]});
const bar=ROW(main,{gap:12,align:"center",h:34});
PILL(bar,{l:"实施中",tone:"live"});
PILL(bar,{l:"监测数据已更新至 2026-05",tone:"info"});
T(bar,{n:"M",c:"创建于 2026-01-02 · 最近更新 2026-05-31 16:20",fs:11.5,col:C.t3});
TABS(main,{on:0,items:["项目信息","监测数据"]});
const grid=ROW(main,{gap:14});
const leftCol=COL(grid,{w:760,gap:14});
const info=PANEL(leftCol,{n:"InfoPanel",t:"项目信息",i:"info"});
const ib=PANEL_BODY(info,{pad:[16,18]});
KV(ib,{k:"项目名称",v:"2026 分布式光伏一期"});
KV(ib,{k:"项目类型",v:"新能源 · 分布式光伏"});
KV(ib,{k:"预期减排量",v:"12,000 吨"});
KV(ib,{k:"实际减排量",v:"8,600.5 吨",col:C.acc});
KV(ib,{k:"开始日期",v:"2026-01-01"});
KV(ib,{k:"结束日期",v:"2026-12-31"});
KV(ib,{k:"创建时间",v:"2026-01-02 09:14"});
KV(ib,{k:"更新时间",v:"2026-05-31 16:20"});
KV(ib,{k:"状态",v:"实施中",col:"$accent"});
H(ib,{n:"Dv",h:1,col:C.line});
T(ib,{n:"Lbl",c:"项目描述",fs:10,fw:"500",col:C.t3,ls:1.4});
T(ib,{n:"Desc",c:"在厂区屋顶及车棚建设 3.2MW 分布式光伏，自发自用、余电上网。按电网排放因子折算年减排量。",fs:12.5,col:C.t2,w:"fill_container",lh:1.75});
const prog=PANEL(leftCol,{n:"ProgressPanel",t:"减排进度",i:"activity",meta:"实际 / 预期 · 时间进度 41.5%"});
const pb=PANEL_BODY(prog,{pad:[16,18]});
const track=F(pb,{n:"Track",dir:"horizontal",w:"fill_container",h:10,fill:"#16241E",rad:2});
R(track,{n:"Fill",w:620,h:10,fill:C.acc,rad:2});
const leg=ROW(pb,{gap:24,align:"center"});
[["已完成减排","8,600.5 吨"],["剩余目标","3,399.5 吨"],["完成率","71.7%"]].forEach(x=>{
 const c=COL(leg,{w:"fit_content",gap:2});
 T(c,{n:"V",c:x[1],fs:14,fw:"500",mono:true,col:C.t1});
 T(c,{n:"L",c:x[0],fs:10.5,col:C.t3});});

const rightCol=COL(grid,{gap:14});
const life=PANEL(rightCol,{n:"LifePanel",t:"生命周期",i:"git-branch",meta:"当前阶段 4 / 6"});
const lb=PANEL_BODY(life,{pad:[16,18]});
[["填报项目","2026-01-02",true],["提交审核","2026-01-05",true],["专家核证","2026-01-12",true],["实施中","2026-02-01",true],["申请认证","待发起",false],["完成","—",false]].forEach((s,i)=>{
 const r=F(lb,{n:"Step",dir:"horizontal",w:"fill_container",gap:11,align:"start",pad:[0,0,0,0]});
 const col=F(r,{n:"Col",dir:"vertical",w:14,align:"center",gap:0,h:46});
 E(col,{n:"Dot",w:9,h:9,fill:s[2]?C.acc:"#16241E",stroke:s[2]?"#00000000":C.line,sw:1});
 if(i<5)R(col,{n:"Line",w:1,h:37,fill:s[2]?C.acc:"#16241E"});
 const c=COL(r,{w:"fill_container",gap:2});
 T(c,{n:"T",c:s[0],fs:12.5,col:s[2]?C.t1:C.t3,fw:s[2]?"500":"400"});
 T(c,{n:"D",c:s[1],fs:10.5,mono:true,col:C.t3});
});
const chain=PANEL(rightCol,{n:"ChainPanel",t:"链上存证",i:"blocks"});
const cbn=PANEL_BODY(chain,{pad:[14,18]});
T(cbn,{n:"K",c:"最近一次存证交易",fs:10,col:C.t3});
T(cbn,{n:"V",c:"7c1e4b90f2a8d6351e0b47c9a2f58d3e6b1c04a7f92e35d8c6b0a4f17e93d2c8",fs:10.5,mono:true,col:C.acc,w:"fill_container",lh:1.7});
KV(cbn,{k:"区块高度",v:"#1,277"});
KV(cbn,{k:"存证时间",v:"2026-05-31 16:20"});
`
  },
];
