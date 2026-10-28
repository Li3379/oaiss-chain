// Enterprise pages batch B: carbon formula, dashboard, auction, P2P, orders, carbon coin.
export const pages = [
  {
    name: '07-CarbonFormula',
    code: String.raw`
const main=SCREEN({name:"07 碳核算公式",x:0,y:4900,navItems:NAV_ENT,tab:0,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:0});
PAGEHEAD(main,{crumbs:["碳中和","碳核算公式"],title:"碳核算公式计算器",sub:"按 GB/T 32150 与电网排放因子法计算排放量，结果可一键带入申报草稿。",
 actions:[{l:"载入样本",i:"file-input",kind:"ghost"},{l:"带入申报草稿",i:"arrow-up-right",kind:"primary"}]});
TABS(main,{on:0,items:["发电行业","电网行业"]});
const grid=ROW(main,{gap:14});
const leftCol=COL(grid,{w:840,gap:14});
const base=PANEL(leftCol,{n:"BasePanel",t:"基础信息",i:"building-2"});
const bb=PANEL_BODY(base,{pad:[16,18]});
const br1=ROW(bb,{gap:12});
FIELD(br1,{l:"报告年份",v:"2026 年",w:180,i:"calendar"});
FIELD(br1,{l:"企业名称",v:"绿色能源科技有限公司",w:"fill_container"});
const fuel=PANEL(leftCol,{n:"FuelPanel",t:"燃料燃烧参数",i:"flame",meta:"按燃料类型分别填写四参数",
 right:[{l:"实测反推",kind:"ghost",i:"calculator"}]});
const fb=PANEL_BODY(fuel);
TABLE(fb,{cols:[{l:"燃料类型",w:120},{l:"消耗量",w:130,a:"r"},{l:"低位发热量",w:140,a:"r"},{l:"含碳量",w:120,a:"r"},{l:"碳氧化率",w:120,a:"r"}],
 rows:[["原煤",{mono:"10,000 t"},{mono:"20.908"},{mono:"0.0263"},{mono:"98.0%"}],
 ["洗精煤",{mono:"2,400 t"},{mono:"26.344"},{mono:"0.0254"},{mono:"98.5%"}],
 ["其他洗煤",{mono:"800 t"},{mono:"16.400"},{mono:"0.0250"},{mono:"97.0%"}],
 ["型煤",{mono:"—"},{mono:"—"},{mono:"—"},{mono:"—"}],
 ["其他煤制品",{mono:"—"},{mono:"—"},{mono:"—"},{mono:"—"}]]});
const des=PANEL(leftCol,{n:"DesPanel",t:"脱硫过程排放",i:"beaker"});
const db=PANEL_BODY(des,{pad:[16,18]});
const dr=ROW(db,{gap:12});
FIELD(dr,{l:"碳酸盐消耗量 (t)",v:"500",w:"fill_container"});
FIELD(dr,{l:"脱硫排放因子",v:"0.44",w:"fill_container"});
FIELD(dr,{l:"脱硫转化率",v:"0.90",w:"fill_container"});

const rightCol=COL(grid,{gap:14});
const res=PANEL(rightCol,{n:"ResultPanel",t:"计算结果",i:"sigma",meta:"2026 年度"});
const rb=PANEL_BODY(res,{pad:[18,18]});
CAPS(rb,{c:"燃料燃烧排放合计",fs:10});
const bigr=ROW(rb,{gap:8,align:"end"});
T(bigr,{n:"V",c:"2,412.6",fs:36,fw:"500",mono:true,col:C.acc,lh:1.05});
T(bigr,{n:"U",c:"tCO₂e",fs:12,col:C.t3});
H(rb,{n:"D1",h:1,col:C.line});
KV(rb,{k:"脱硫过程排放",v:"198.0 tCO₂e"});
KV(rb,{k:"净购入电力排放",v:"312.4 tCO₂e"});
KV(rb,{k:"总排放量",v:"2,923.0 tCO₂e",col:C.acc});
H(rb,{n:"D2",h:1,col:C.line});
T(rb,{n:"F",c:"公式：E = Σ(FC × NCV × CC × OF × 44/12) + 脱硫排放 + 净购电排放",fs:11,col:C.t3,w:"fill_container",lh:1.7});
const chk=PANEL(rightCol,{n:"CheckPanel",t:"校核提示",i:"circle-check"});
const cb=PANEL_BODY(chk,{pad:[14,18],gap:10});
CALLOUT(cb,{tone:"live",i:"check-circle-2",t:"三项参数均在国标合理区间内",c:"低位发热量与含碳量偏离行业均值不足 5%，无需补充说明。"});
CALLOUT(cb,{tone:"warn",i:"triangle-alert",t:"型煤与其他煤制品未填写",c:"若本年度确有消耗，请补录后重新计算；空白项按零计。"});
MINI(cb,{l:"计算时间",v:"2026-05-31 17:04"});
`
  },
  {
    name: '08-CompanyDashboard',
    code: String.raw`
const main=SCREEN({name:"08 数据可视化",x:1480,y:4900,navItems:NAV_ENT,tab:2,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:4,pad:[18,22],gap:14});
PAGEHEAD(main,{crumbs:["企业资产","数据可视化"],title:"数据可视化",sub:"碳币、配额与信用一屏总览；六张图表随筛选条件联动。",
 actions:[{l:"市场预测驾驶舱",i:"line-chart",kind:"ghost"},{l:"企业风控体检",i:"shield-alert",kind:"primary"}]});
const bar=ROW(main,{gap:10,align:"end"});
FIELD(bar,{l:"资产编号",ph:"如 CR-001",w:190,i:"search"});
FIELD(bar,{l:"报告类型",v:"全部",w:150,i:"chevron-down"});
FIELD(bar,{l:"核算周期",v:"2026-Q1",w:160,i:"calendar"});
BTN(bar,{l:"查询",kind:"primary"});
BTN(bar,{l:"重置",kind:"ghost"});
const seg=ROW(bar,{gap:0,align:"center",w:"fit_content",h:36,fill:"#0A1310",stroke:C.line,sw:1,rad:2});
["日","月","年"].forEach((s,i)=>{
 const b=F(seg,{n:"Seg",dir:"horizontal",w:52,h:34,just:"center",align:"center",rad:1,fill:i===1?"#1B2C24":"#00000000"});
 T(b,{n:"L",c:s,fs:12,col:i===1?C.t1:C.t3,fw:i===1?"500":"400"});});
PILL(bar,{l:"准入证书 有效",tone:"live"});
const stats=ROW(main,{gap:12});
STAT(stats,{l:"碳币总数",v:"12",u:"枚",i:"coins",d:"本月 +3 枚",dtone:"up",h:88});
STAT(stats,{l:"碳额度总数",v:"50",u:"tCO₂e",i:"wallet",d:"已用 12 · 余 38",h:88});
STAT(stats,{l:"信用积分",v:"88",u:"分",i:"shield-check",d:"良好级 · 行业第 3",h:88});
STAT(stats,{l:"本年累计排放",v:"370.5",u:"tCO₂e",i:"factory",d:"同比 −4.2%",dtone:"up",h:88});
const g1=ROW(main,{gap:14,h:250});
const c1=PANEL(g1,{n:"C1",t:"碳交易总笔数",i:"chart-column",meta:"按月"});
const c1b=PANEL_BODY(c1,{pad:[14,16]});
BARS(c1b,{h:150,data:[6,9,4,11,8,13],labels:["1月","2月","3月","4月","5月","6月"],hi:5,bw:22});
const c2=PANEL(g1,{n:"C2",t:"AI 预测排放趋势",i:"chart-line",meta:"未来 6 个月",
 right:[{l:"详情",kind:"ghost"}]});
const c2b=PANEL_BODY(c2,{pad:[14,16]});
CHART(c2b,{h:150,yLabels:["400","300","200","100","0"],xLabels:["4月","5月","6月","7月","8月","9月"],draw:function(plot){
 const f=F(plot,{n:"LineWrap",dir:"horizontal",w:"fill_container",h:150,align:"end"});
 const pts=[[0,96],[1,104],[2,112],[3,118],[4,126],[5,132]];
 const line=Insert(f,{type:"path",name:"Trend",geometry:"M0,42 L1,38 L2,34 L3,30 L4,26 L5,22",width:0,height:0,stroke:C.acc,strokeWidth:2});
 pts.forEach((p,i)=>{
  const c=F(f,{n:"Pt",dir:"vertical",w:"fill_container",h:150,just:"end",align:"center"});
  E(c,{n:"Dot",w:6,h:6,fill:i>2?C.acc:"#2B4A3A"});
  T(c,{n:"V",c:(i>2?"预测":"实际"),fs:8.5,mono:true,col:C.t3});
 });
}});
const c3=PANEL(g1,{n:"C3",t:"AI 建议月度配额",i:"sparkles",meta:"模型 v1.0"});
const c3b=PANEL_BODY(c3,{pad:[14,16]});
BARS(c3b,{h:150,data:[42,38,45,40,36,34],labels:["7月","8月","9月","10月","11月","12月"],bw:22});
const g2=ROW(main,{gap:14,h:236});
const c4=PANEL(g2,{n:"C4",t:"碳排放构成",i:"chart-pie",meta:"2026-Q1"});
const c4b=PANEL_BODY(c4,{pad:[14,16]});
PROPBAR(c4b,{w:640,unit:" tCO₂e",segs:[{l:"范围一 燃烧",v:"214.6",c:C.acc},{l:"范围二 电力",v:"96.4",c:"#3E7A63"},{l:"范围三 运输",v:"38.2",c:"#2B5A4A"},{l:"脱硫过程",v:"21.3",c:"#22453A"}]});
const c5=PANEL(g2,{n:"C5",t:"碳交易统计",i:"arrow-left-right",meta:"近 12 个月"});
const c5b=PANEL_BODY(c5,{pad:[14,16]});
PROPBAR(c5b,{w:640,unit:"",segs:[{l:"交易支出占比",v:"62.0%",c:"#3E7A63"},{l:"交易数量占比",v:"38.0%",c:C.acc}]});
MINI(c5b,{l:"累计成交笔数",v:"47 笔"});
const c6=PANEL(g2,{n:"C6",t:"信用积分趋势",i:"trending-up",meta:"近 6 个月"});
const c6b=PANEL_BODY(c6,{pad:[14,16]});
CHART(c6b,{h:136,yLabels:["95","90","85","80"],xLabels:["1月","2月","3月","4月","5月","6月"],draw:function(plot){
 const f=F(plot,{n:"Lw",dir:"horizontal",w:"fill_container",h:136,align:"end"});
 const w=["82","84","84","86","87","88"];
 w.forEach((v,i)=>{
  const c=F(f,{n:"P",dir:"vertical",w:"fill_container",h:136,just:"end",align:"center",gap:4});
  const bh=Math.round((parseInt(v)-78)*9);
  R(c,{n:"B",w:18,h:bh,fill:i===5?C.acc:"#204134",rad:1});
  T(c,{n:"V",c:v,fs:9,mono:true,col:i===5?C.acc:C.t3});
 });
}});
`
  },
  {
    name: '09-TradingMarket',
    code: String.raw`
const main=SCREEN({name:"09 双向拍卖",x:0,y:5880,navItems:NAV_ENT,tab:1,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:2});
PAGEHEAD(main,{crumbs:["交易市场","双向拍卖"],title:"碳配额双向拍卖",sub:"集合竞价撮合买卖单；成交后配额与碳币自动结算并写入链上。",
 actions:[{l:"去挂单平台",i:"gavel",kind:"ghost"},{l:"创建订单",i:"plus",kind:"primary"}]});
const ai=F(main,{n:"AIAdvice",dir:"horizontal",w:"fill_container",pad:[14,16],gap:16,align:"center",fill:"#0B1916",stroke:"#1B342C",sw:1,rad:2});
I(ai,{n:"Ico",i:"sparkles",s:17,col:C.acc});
const aic=COL(ai,{w:"fill_container",gap:3});
T(aic,{n:"T",c:"AI 履约建议：本季度配额缺口 12.0 tCO₂e",fs:13,fw:"500",col:C.t1});
T(aic,{n:"D",c:"按当前参考价 58.20 元/吨测算，补足缺口预估成本 698.40 碳币；建议在 7 天内分批建仓，避免月末集中买价上浮。",fs:11.5,col:C.t2,w:"fill_container",lh:1.55});
PILL(ai,{l:"风险 中",tone:"warn"});
TABS(main,{on:0,items:["全部挂单","我的挂单","撮合结果"]});
const grid=ROW(main,{gap:14});
const leftCol=COL(grid,{w:"fill_container",gap:14});
const book=PANEL(leftCol,{n:"BookPanel",t:"全部挂单",i:"list-ordered",meta:"共 18 笔 · 每 5 秒刷新",
 right:[{l:"查询",kind:"ghost"}]});
const bk=PANEL_BODY(book);
TABLE(bk,{zebra:true,cols:[{l:"序号",w:52},{l:"方向",w:80},{l:"数量（吨）",w:130,a:"r"},{l:"价格（元/吨）",w:140,a:"r"},{l:"状态",w:108},{l:"创建时间",w:150}],
 rows:[[{mono:"1"},{pill:"买入",tone:"live"},{mono:"100.0"},{mono:"58.20"},{pill:"待撮合",tone:"warn"},{text:"2026-06-01 09:12",col:"$textSecondary"}],
 [{mono:"2"},{pill:"卖出",tone:"danger"},{mono:"50.0"},{mono:"57.90"},{pill:"待撮合",tone:"warn"},{text:"2026-06-01 09:20",col:"$textSecondary"}],
 [{mono:"3"},{pill:"买入",tone:"live"},{mono:"120.0"},{mono:"58.50"},{pill:"部分成交",tone:"info"},{text:"2026-06-01 08:55",col:"$textSecondary"}],
 [{mono:"4"},{pill:"卖出",tone:"danger"},{mono:"80.0"},{mono:"57.60"},{pill:"已成交",tone:"live"},{text:"2026-06-01 08:41",col:"$textSecondary"}],
 [{mono:"5"},{pill:"买入",tone:"live"},{mono:"30.0"},{mono:"59.10"},{pill:"已撤销",tone:"neutral"},{text:"2026-05-31 17:02",col:"$textSecondary"}]]});
const match=PANEL(leftCol,{n:"MatchPanel",t:"撮合结果",i:"git-merge",meta:"最近 3 笔"});
const mb=PANEL_BODY(match);
TABLE(mb,{cols:[{l:"序号",w:52},{l:"买单 ID",w:100},{l:"卖单 ID",w:100},{l:"成交数量",w:120,a:"r"},{l:"成交价",w:120,a:"r"},{l:"成交时间",w:150}],
 rows:[[{mono:"1"},{mono:"ORD-1042"},{mono:"ORD-1038"},{mono:"80.0"},{mono:"57.90"},{text:"2026-06-01 08:47",col:"$textSecondary"}],
 [{mono:"2"},{mono:"ORD-1036"},{mono:"ORD-1031"},{mono:"40.0"},{mono:"58.10"},{text:"2026-05-31 15:22",col:"$textSecondary"}]]});

const rightCol=COL(grid,{w:390,gap:14});
const form=PANEL(rightCol,{n:"OrderPanel",t:"创建订单",i:"gavel",meta:"配额 · 碳币结算"});
const ob=PANEL_BODY(form,{pad:[16,16],gap:12});
FIELD(ob,{l:"买卖方向",v:"买入",i:"chevron-down"});
FIELD(ob,{l:"数量（吨）",v:"100.0"});
FIELD(ob,{l:"价格（元/吨）",v:"58.20",i:"sparkles"});
T(ob,{n:"UsePred",c:"采用 AI 参考价 58.20 元/吨",fs:10.5,col:C.acc,w:"fill_container"});
H(ob,{n:"D",h:1,col:C.line});
KV(ob,{k:"预估成交金额",v:"5,820.00 碳币",col:C.acc});
KV(ob,{k:"可用碳币",v:"7,240.00"});
KV(ob,{k:"可用配额",v:"38,000 tCO₂e"});
BTN(ob,{l:"确认挂单",kind:"primary",w:"fill_container",h:38});
const guide=F(rightCol,{n:"Guide",dir:"horizontal",w:"fill_container",pad:[11,13],gap:9,align:"start",fill:"#101F2A",stroke:"#1E3D4A",sw:1,rad:2});
I(guide,{n:"I",i:"help-circle",s:14,col:C.cyan});
T(guide,{n:"L",c:"未成交怎么办？集合竞价每 5 秒撮合一次，价格未匹配的挂单会继续排队，可随时撤销。",fs:11.5,col:C.t2,w:"fill_container",lh:1.6});
`
  },
  {
    name: '10-TradingP2P',
    code: String.raw`
const main=SCREEN({name:"10 P2P交易",x:1480,y:5880,navItems:NAV_ENT,tab:1,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:3});
PAGEHEAD(main,{crumbs:["交易市场","P2P 交易"],title:"P2P 点对点交易",sub:"与指定企业定向协议转让配额，双方确认后结算上链。",
 actions:[{l:"创建 P2P 交易",i:"plus",kind:"primary"}]});
const stats=ROW(main,{gap:12});
STAT(stats,{l:"进行中交易",v:"2",u:"笔",i:"clock"});
STAT(stats,{l:"本年成交",v:"14",u:"笔",i:"check-check"});
STAT(stats,{l:"本年转让量",v:"1,240",u:"tCO₂e",i:"arrow-left-right"});
STAT(stats,{l:"平均单价",v:"58.36",u:"元/吨",i:"tag"});
const filt=ROW(main,{gap:10,align:"end"});
FIELD(filt,{l:"交易对手",ph:"企业名称",w:220,i:"search"});
FIELD(filt,{l:"身份",v:"全部",w:140,i:"chevron-down"});
FIELD(filt,{l:"交易编号",ph:"如 TRX-001",w:180});
BTN(filt,{l:"查询",kind:"primary"});
BTN(filt,{l:"重置",kind:"ghost"});
const tbl=PANEL(main,{n:"P2PPanel",t:"交易列表",i:"arrow-left-right",meta:"共 14 笔 · 按创建时间倒序"});
const tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,cols:[{l:"序号",w:52},{l:"交易编号",w:120},{l:"买方",w:190},{l:"卖方",w:190},{l:"数量（吨）",w:110,a:"r"},{l:"单价",w:96,a:"r"},{l:"总金额",w:120,a:"r"},{l:"状态",w:100},{l:"创建时间",w:132},{l:"操作",w:96,a:"r"}],
 rows:[[{mono:"1"},{link:"TRX-2026-014"},"绿色能源科技有限公司","清洁能源发展有限公司",{mono:"50.0"},{mono:"58.20"},{mono:"2,910.00"},{pill:"待处理",tone:"warn"},{text:"2026-06-01 09:05",col:"$textSecondary"},{text:"去确认"}],
 [{mono:"2"},{link:"TRX-2026-013"},"绿色能源科技有限公司","东方热电集团",{mono:"120.0"},{mono:"57.80"},{mono:"6,936.00"},{pill:"已完成",tone:"live"},{text:"2026-05-28 14:30",col:"$textSecondary"},{text:"查看"}],
 [{mono:"3"},{link:"TRX-2026-012"},"华北建材有限公司","绿色能源科技有限公司",{mono:"80.0"},{mono:"58.50"},{mono:"4,680.00"},{pill:"处理中",tone:"info"},{text:"2026-05-26 11:12",col:"$textSecondary"},{text:"查看"}],
 [{mono:"4"},{link:"TRX-2026-011"},"低碳制造股份有限公司","绿色能源科技有限公司",{mono:"35.0"},{mono:"59.00"},{mono:"2,065.00"},{pill:"已取消",tone:"neutral"},{text:"2026-05-20 16:48",col:"$textSecondary"},{text:"查看"}],
 [{mono:"5"},{link:"TRX-2026-010"},"绿色能源科技有限公司","低碳制造股份有限公司",{mono:"60.0"},{mono:"57.40"},{mono:"3,444.00"},{pill:"失败",tone:"danger"},{text:"2026-05-18 10:02",col:"$textSecondary"},{text:"查看"}]]});
PAGER(tb,{count:"共 14 条 · 每页 10 条"});
`
  },
  {
    name: '11-OrdersManage',
    code: String.raw`
const main=SCREEN({name:"11 订单管理",x:0,y:6860,navItems:NAV_ENT,tab:1,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:1});
PAGEHEAD(main,{crumbs:["交易市场","订单管理"],title:"我的订单",sub:"检索并撤销本人全部交易订单，覆盖拍卖与 P2P 两类。",
 actions:[{l:"导出订单",i:"download",kind:"ghost"}]});
const stats=ROW(main,{gap:12});
STAT(stats,{l:"全部订单",v:"47",u:"笔",i:"receipt-text"});
STAT(stats,{l:"待处理",v:"5",u:"笔",i:"clock",d:"其中拍卖 3 · P2P 2"});
STAT(stats,{l:"已完成",v:"38",u:"笔",i:"check-check"});
STAT(stats,{l:"累计成交额",v:"18,420.50",u:"碳币",i:"coins"});
const filt=ROW(main,{gap:10,align:"end"});
FIELD(filt,{l:"交易编号",ph:"如 TRX-001",w:200,i:"search"});
FIELD(filt,{l:"交易类型",v:"全部",w:170,i:"chevron-down"});
FIELD(filt,{l:"日期范围",v:"2026-05-01 至 2026-06-01",w:260,i:"calendar"});
BTN(filt,{l:"查询",kind:"primary"});
BTN(filt,{l:"重置",kind:"ghost"});
const tbl=PANEL(main,{n:"OrdersPanel",t:"订单列表",i:"receipt-text",meta:"共 47 笔 · 支持勾选批量撤销"});
const tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,cols:[{l:"序号",w:52},{l:"交易编号",w:118},{l:"类型",w:104},{l:"交易对手",w:180},{l:"数量",w:88,a:"r"},{l:"单价",w:92,a:"r"},{l:"总金额",w:118,a:"r"},{l:"状态",w:100},{l:"创建时间",w:132},{l:"操作",w:96,a:"r"}],
 rows:[[{mono:"1"},{link:"TRX-2026-014"},{pill:"拍卖交易",tone:"info"},"清洁能源发展有限公司",{mono:"1.0"},{mono:"58.20"},{mono:"58.20"},{pill:"待处理",tone:"warn"},{text:"2026-06-01 09:12",col:"$textSecondary"},{text:"撤销"}],
 [{mono:"2"},{link:"TRX-2026-013"},{pill:"P2P",tone:"live"},"东方热电集团",{mono:"3.0"},{mono:"57.80"},{mono:"173.40"},{pill:"处理中",tone:"info"},{text:"2026-06-01 08:40",col:"$textSecondary"},{text:"查看"}],
 [{mono:"3"},{link:"TRX-2026-012"},{pill:"拍卖交易",tone:"info"},"华北建材有限公司",{mono:"12.0"},{mono:"58.50"},{mono:"702.00"},{pill:"已完成",tone:"live"},{text:"2026-05-28 14:30",col:"$textSecondary"},{text:"查看"}],
 [{mono:"4"},{link:"TRX-2026-011"},{pill:"P2P",tone:"live"},"低碳制造股份有限公司",{mono:"35.0"},{mono:"59.00"},{mono:"2,065.00"},{pill:"已取消",tone:"neutral"},{text:"2026-05-20 16:48",col:"$textSecondary"},{text:"查看"}],
 [{mono:"5"},{link:"TRX-2026-010"},{pill:"拍卖交易",tone:"info"},"绿色能源科技有限公司",{mono:"60.0"},{mono:"57.40"},{mono:"3,444.00"},{pill:"失败",tone:"danger"},{text:"2026-05-18 10:02",col:"$textSecondary"},{text:"查看"}],
 [{mono:"6"},{link:"TRX-2026-009"},{pill:"P2P",tone:"live"},"清洁能源发展有限公司",{mono:"22.0"},{mono:"58.10"},{mono:"1,278.20"},{pill:"已完成",tone:"live"},{text:"2026-05-15 09:22",col:"$textSecondary"},{text:"查看"}]]});
PAGER(tb,{count:"共 47 条 · 每页 10 条"});
`
  },
  {
    name: '12-CarbonCoin',
    code: String.raw`
const main=SCREEN({name:"12 碳币账户",x:1480,y:6860,navItems:NAV_ENT,tab:2,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:6});
PAGEHEAD(main,{crumbs:["企业资产","碳币账户"],title:"碳币账户",sub:"碳币用于配额交易结算；每笔流水都会记录交易后余额，便于对账。",
 actions:[{l:"转账",i:"send",kind:"primary"}]});
const grid=ROW(main,{gap:14});
const bal=F(grid,{n:"Balance",dir:"vertical",w:420,h:118,pad:[18,20],gap:6,just:"space_between",fill:"#0E1F19",stroke:"#2B4A22",sw:1,rad:3});
const bt=ROW(bal,{w:"fill_container",just:"space_between",align:"center"});
CAPS(bt,{c:"账户余额",fs:10.5});
PILL(bt,{l:"账户正常",tone:"live"});
const bv=ROW(bal,{gap:8,align:"end"});
T(bv,{n:"V",c:"1,250.00",fs:38,fw:"500",mono:true,col:C.acc,lh:1.05});
T(bv,{n:"U",c:"碳币",fs:12,col:C.t3});
T(bal,{n:"Sub",c:"累计充值 5,000.00 · 累计消费 3,750.00",fs:11,col:C.t3,w:"fill_container"});
const cols=COL(grid,{w:"fill_container",gap:12});
const st=ROW(cols,{gap:12});
STAT(st,{l:"累计充值",v:"5,000.00",u:"碳币",i:"arrow-down-to-line",h:84});
STAT(st,{l:"累计消费",v:"3,750.00",u:"碳币",i:"arrow-up-from-line",h:84});
STAT(st,{l:"本月净流入",v:"+240.00",u:"碳币",i:"trending-up",tone:"live",h:84});
const flow=PANEL(main,{n:"FlowPanel",t:"资金流水",i:"list-ordered",meta:"共 24 条 · 按时间倒序"});
const fb=PANEL_BODY(flow);
TABLE(fb,{zebra:true,cols:[{l:"序号",w:52},{l:"交易编号",w:130},{l:"类型",w:100},{l:"金额",w:130,a:"r"},{l:"交易后余额",w:140,a:"r"},{l:"备注",w:"fill_container"},{l:"时间",w:150}],
 rows:[[{mono:"1"},{mono:"CCT001",col:"$textSecondary"},{pill:"充值",tone:"live"},{mono:"+25.00",col:"$accent"},{mono:"1,250.00"},"月度配额补充","2026-06-01 09:00"],
 [{mono:"2"},{mono:"CCT000",col:"$textSecondary"},{pill:"转出",tone:"danger"},{mono:"−120.00",col:"$stateDanger"},{mono:"1,225.00"},"P2P 交易 TRX-2026-013","2026-05-28 14:31"],
 [{mono:"3"},{mono:"CCT999",col:"$textSecondary"},{pill:"转入",tone:"live"},{mono:"+80.00",col:"$accent"},{mono:"1,345.00"},"P2P 交易 TRX-2026-012","2026-05-26 11:15"],
 [{mono:"4"},{mono:"CCT998",col:"$textSecondary"},{pill:"购买",tone:"info"},{mono:"−702.00",col:"$stateDanger"},{mono:"1,265.00"},"拍卖成交 ORD-1038","2026-05-28 14:28"],
 [{mono:"5"},{mono:"CCT997",col:"$textSecondary"},{pill:"冻结",tone:"warn"},{mono:"−200.00",col:"$stateWarn"},{mono:"1,967.00"},"挂单保证金 ORD-1042","2026-05-20 10:06"],
 [{mono:"6"},{mono:"CCT996",col:"$textSecondary"},{pill:"解冻",tone:"neutral"},{mono:"+200.00",col:"$textSecondary"},{mono:"2,167.00"},"撤单退还 ORD-1036","2026-05-19 16:40"]]});
PAGER(fb,{count:"共 24 条 · 每页 10 条"});
`
  },
];
