// Enterprise pages 04-12 (grid indices 3..11).
export const INDEX_OFFSET = 3;
export const PAGES = [
  // idx 3 -> 04
  {
    name: '04 排放数据',
    body: `
main=SCREEN({name:'04 排放数据',x:PX,y:PY,navItems:NAV_ENT,tab:2,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:4});
PAGEHEAD(main,{crumbs:['企业资产','排放数据'],title:'排放数据',sub:'查看本企业的排放评级、行业排名与 AI 预测；评级由平台按行业基准自动计算。',
 actions:[{l:'导出评级报告',i:'download',kind:'ghost'}]});
stats=ROW(main,{gap:12});
STAT(stats,{l:'当前评级',v:'A',u:'级',i:'award',d:'行业前 12%',dtone:'up'});
STAT(stats,{l:'本期总排放',v:'370.5',u:'tCO₂e',i:'factory',d:'行业均值 512.0'});
STAT(stats,{l:'综合评分',v:'92',u:'/100',i:'gauge',d:'较上期 +3'});
STAT(stats,{l:'行业排名',v:'3',u:'/ 26',i:'trophy-2',d:'较上期 ↑2 位',dtone:'up'});
TABS(main,{on:0,items:['排放评级','行业排名','AI 预测']});
grid=ROW(main,{gap:14});
leftCol=COL(grid,{w:520,gap:14});
card=PANEL(leftCol,{n:'RatingCard',t:'本期排放评级',i:'award',meta:'2026-Q1'});
cb=PANEL_BODY(card,{pad:[18,18]});
grade=ROW(cb,{gap:16,align:'center'});
gbox=F(grade,{n:'Grade',dir:'horizontal',w:88,h:88,just:'center',align:'center',fill:'$navActive',stroke:'$hairline',sw:1,rad:3});
T(gbox,{n:'G',c:'A',fs:46,fw:'600',mono:true,col:C.acc});
gcol=COL(grade,{w:'fill_container',gap:6});
T(gcol,{n:'T',c:'优秀 · 行业领先',fs:16,fw:'500',col:C.t1});
T(gcol,{n:'D',c:'本期排放低于行业均值 27.6%，连续 4 个季度保持 A 级。',fs:12,col:C.t2,lh:1.6,w:340});
PILL(gcol,{l:'评级已生效',tone:'live'});
H(cb,{n:'Div',h:1,col:C.line});
KV(cb,{k:'总排放量',v:'370.5 tCO₂e'});
KV(cb,{k:'行业均值',v:'512.0 tCO₂e'});
KV(cb,{k:'综合评分',v:'92 / 100',col:C.acc});
KV(cb,{k:'评级时间',v:'2026-04-01 09:00'});
trend=PANEL(leftCol,{n:'TrendCard',t:'评级变化趋势',i:'trending-up',meta:'近 4 个季度'});
tbb=PANEL_BODY(trend,{pad:[16,18]});
BARS(tbb,{h:120,data:[88,90,91,92],labels:['2025-Q2','2025-Q3','2025-Q4','2026-Q1'],hi:3,bw:26});
rightCol=COL(grid,{gap:14});
rank=PANEL(rightCol,{n:'RankCard',t:'行业排名',i:'trophy-2',meta:'2026 年度 · 共 26 家',right:[{l:'全部排名',kind:'ghost'}]});
rb=PANEL_BODY(rank);
TABLE(rb,{cols:[{l:'排名',w:64},{l:'企业名称'},{l:'总排放量',w:130,a:'r'},{l:'评级',w:76},{l:'评分',w:70,a:'r'}],
 rows:[[{mono:'1'},'低碳制造股份有限公司',{mono:'310.2'},{pill:'A',tone:'live'},{mono:'95'}],
 [{mono:'2'},'清洁能源发展有限公司',{mono:'342.8'},{pill:'A',tone:'live'},{mono:'93'}],
 [{mono:'3',col:'$accent'},'绿色能源科技有限公司',{mono:'370.5',col:'$textPrimary'},{pill:'A',tone:'live'},{mono:'92'}],
 [{mono:'4'},'东方热电集团',{mono:'408.6'},{pill:'B',tone:'info'},{mono:'86'}],
 [{mono:'5'},'华北建材有限公司',{mono:'452.1'},{pill:'B',tone:'info'},{mono:'81'}]]});
pred=PANEL(rightCol,{n:'PredCard',t:'AI 预测',i:'sparkles',meta:'模型 v1.0 · R² 0.912'});
pbb=PANEL_BODY(pred,{pad:[14,18]});
pr=ROW(pbb,{gap:14,align:'end'});
FIELD(pr,{l:'预测月数',v:'6 个月',w:150,i:'chevron-down'});
FIELD(pr,{l:'基准期',v:'2026-Q1',w:150,i:'calendar'});
BTN(pr,{l:'重新预测',kind:'primary',i:'sparkles'});
H(pbb,{n:'D2',h:1,col:C.line});
LEGEND(pbb,{items:[{l:'2026-04  预测排放',v:'374.8 tCO₂e',c:'$chartTrack'},{l:'2026-05  预测排放',v:'381.2 tCO₂e',c:'$chartTrack'},{l:'2026-06  预测排放',v:'384.2 tCO₂e',c:C.acc}]});
T(pbb,{n:'Conf',c:'置信度 88% · 依据近 8 个季度排放与产量数据',fs:11,col:C.t3,w:420});`,
  },
  // idx 4 -> 05
  {
    name: '05 碳中和项目',
    body: `
main=SCREEN({name:'05 碳中和项目',x:PX,y:PY,navItems:NAV_ENT,tab:0,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:8});
PAGEHEAD(main,{crumbs:['碳中和','项目列表'],title:'碳中和项目',sub:'申报减排项目并推进生命周期：草稿 → 待审核 → 已批准 → 实施中 → 已完成。',
 actions:[{l:'导出项目台账',i:'download',kind:'ghost'},{l:'创建项目',i:'plus',kind:'primary'}]});
stats=ROW(main,{gap:12});
STAT(stats,{l:'项目总数',v:'6',u:'个',i:'folder-tree'});
STAT(stats,{l:'实施中',v:'2',u:'个',i:'activity',d:'光伏一期 · 节能改造'});
STAT(stats,{l:'预期减排合计',v:'46,500',u:'tCO₂e',i:'leaf'});
STAT(stats,{l:'实际减排合计',v:'18,600',u:'tCO₂e',i:'check-check',d:'完成率 40.0%'});
tbl=PANEL(main,{n:'ProjPanel',t:'项目列表',i:'folder-tree',meta:'共 6 个 · 按创建时间倒序'});
tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,rh:48,cols:[
 {l:'序号',w:56},{l:'项目名称'},{l:'项目类型',w:110},{l:'预期减排量',w:130,a:'r'},{l:'状态',w:100},{l:'起止日期',w:190},{l:'操作',w:140,a:'r'}],
 rows:[
  [{mono:'1'},'2026 分布式光伏一期',{text:'新能源',col:'$textSecondary'},{mono:'12,000 吨'},{pill:'实施中',tone:'live'},{text:'2026-01-01 ~ 2026-12-31',col:'$textSecondary'},{text:'更新监测数据'}],
  [{mono:'2'},'燃煤锅炉节能改造',{text:'节能改造',col:'$textSecondary'},{mono:'8,500 吨'},{pill:'实施中',tone:'live'},{text:'2026-03-01 ~ 2026-11-30',col:'$textSecondary'},{text:'更新监测数据'}],
  [{mono:'3'},'厂区碳汇造林一期',{text:'碳汇造林',col:'$textSecondary'},{mono:'12,000 吨'},{pill:'已批准',tone:'info'},{text:'2026-06-01 ~ 2027-05-31',col:'$textSecondary'},{text:'提交审核'}],
  [{mono:'4'},'CCUS 示范装置',{text:'CCUS',col:'$textSecondary'},{mono:'9,000 吨'},{pill:'待审核',tone:'warn'},{text:'2026-07-01 ~ 2027-06-30',col:'$textSecondary'},{text:'撤回申请'}],
  [{mono:'5'},'余热回收二期',{text:'节能改造',col:'$textSecondary'},{mono:'5,000 吨'},{pill:'已完成',tone:'live'},{text:'2025-01-01 ~ 2025-12-31',col:'$textSecondary'},{text:'申请认证'}],
  [{mono:'6'},'老旧机组燃料替代',{text:'节能改造',col:'$textSecondary'},{mono:'3,200 吨'},{pill:'已驳回',tone:'danger'},{text:'2025-05-01 ~ 2025-10-31',col:'$textSecondary'},{text:'查看驳回意见'}]
 ]});
PAGER(tb,{count:'共 6 个 · 每页 10 个'});`,
  },
  // idx 5 -> 06
  {
    name: '06 项目详情',
    body: `
main=SCREEN({name:'06 项目详情',x:PX,y:PY,navItems:NAV_ENT,tab:0,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:8});
PAGEHEAD(main,{crumbs:['碳中和','项目列表','2026 分布式光伏一期'],title:'2026 分布式光伏一期',sub:'新能源 · 预期减排 12,000 吨 · 2026-01-01 至 2026-12-31',
 actions:[{l:'终止项目',i:'x-circle',kind:'ghost'},{l:'申请认证',i:'badge-check',kind:'primary'}]});
bar=ROW(main,{gap:12,align:'center',h:34});
PILL(bar,{l:'实施中',tone:'live'});
PILL(bar,{l:'监测数据已更新至 2026-05',tone:'info'});
T(bar,{n:'M',c:'创建于 2026-01-02 · 最近更新 2026-05-31 16:20',fs:11.5,col:C.t3});
TABS(main,{on:0,items:['项目信息','监测数据']});
grid=ROW(main,{gap:14});
leftCol=COL(grid,{w:760,gap:14});
info=PANEL(leftCol,{n:'InfoPanel',t:'项目信息',i:'info'});
ib=PANEL_BODY(info,{pad:[16,18]});
KV(ib,{k:'项目名称',v:'2026 分布式光伏一期'});
KV(ib,{k:'项目类型',v:'新能源 · 分布式光伏'});
KV(ib,{k:'预期减排量',v:'12,000 吨'});
KV(ib,{k:'实际减排量',v:'8,600.5 吨',col:C.acc});
KV(ib,{k:'开始日期',v:'2026-01-01'});
KV(ib,{k:'结束日期',v:'2026-12-31'});
KV(ib,{k:'创建时间',v:'2026-01-02 09:14'});
KV(ib,{k:'更新时间',v:'2026-05-31 16:20'});
KV(ib,{k:'状态',v:'实施中',col:'$accent'});
H(ib,{n:'Dv',h:1,col:C.line});
T(ib,{n:'Lbl',c:'项目描述',fs:10,fw:'500',col:C.t3,ls:1.4});
T(ib,{n:'Desc',c:'在厂区屋顶及车棚建设 3.2MW 分布式光伏，自发自用、余电上网。按电网排放因子折算年减排量。',fs:12.5,col:C.t2,w:700,lh:1.75});
prog=PANEL(leftCol,{n:'ProgressPanel',t:'减排进度',i:'activity',meta:'实际 / 预期 · 时间进度 41.5%'});
ppb=PANEL_BODY(prog,{pad:[16,18]});
track=F(ppb,{n:'Track',dir:'horizontal',w:700,h:10,fill:'$track',rad:2});
R(track,{n:'Fill',w:500,h:10,fill:C.acc,rad:2});
leg=ROW(ppb,{gap:24,align:'center'});
[['已完成减排','8,600.5 吨'],['剩余目标','3,399.5 吨'],['完成率','71.7%']].forEach(x=>{
 c=COL(leg,{w:150,gap:2});
 T(c,{n:'V',c:x[1],fs:14,fw:'500',mono:true,col:C.t1});
 T(c,{n:'L',c:x[0],fs:10.5,col:C.t3});});
rightCol=COL(grid,{gap:14});
life=PANEL(rightCol,{n:'LifePanel',t:'生命周期',i:'git-branch',meta:'当前阶段 4 / 6'});
lb=PANEL_BODY(life,{pad:[16,18]});
[['填报项目','2026-01-02',true],['提交审核','2026-01-05',true],['专家核证','2026-01-12',true],['实施中','2026-02-01',true],['申请认证','待发起',false],['完成','—',false]].forEach((s,i)=>{
 r=F(lb,{n:'Step',dir:'horizontal',w:'fill_container',gap:11,align:'start'});
 col=F(r,{n:'Col',dir:'vertical',w:14,align:'center',gap:0,h:46});
 E(col,{n:'Dot',w:9,h:9,fill:s[2]?C.acc:'$track',stroke:s[2]?'#00000000':C.line,sw:1});
 if(i<5)R(col,{n:'Line',w:1,h:37,fill:s[2]?C.acc:'$track'});
 c=COL(r,{w:'fill_container',gap:2});
 T(c,{n:'T',c:s[0],fs:12.5,col:s[2]?C.t1:C.t3,fw:s[2]?'500':'400'});
 T(c,{n:'D',c:s[1],fs:10.5,mono:true,col:C.t3});
});
chain=PANEL(rightCol,{n:'ChainPanel',t:'链上存证',i:'blocks'});
cbn=PANEL_BODY(chain,{pad:[14,18]});
T(cbn,{n:'K',c:'最近一次存证交易',fs:10,col:C.t3});
T(cbn,{n:'V',c:'7c1e4b90f2a8d6351e0b47c9a2f58d3e6b1c04a7f92e35d8c6b0a4f17e93d2c8',fs:10.5,mono:true,col:C.acc,w:520,lh:1.7});
KV(cbn,{k:'区块高度',v:'#1,277'});
KV(cbn,{k:'存证时间',v:'2026-05-31 16:20'});`,
  },
  // idx 6 -> 07
  {
    name: '07 碳核算公式',
    body: `
main=SCREEN({name:'07 碳核算公式',x:PX,y:PY,navItems:NAV_ENT,tab:0,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:0});
PAGEHEAD(main,{crumbs:['碳中和','碳核算公式'],title:'碳核算公式计算器',sub:'按 GB/T 32150 与电网排放因子法计算排放量，结果可一键带入申报草稿。',
 actions:[{l:'载入样本',i:'file-input',kind:'ghost'},{l:'带入申报草稿',i:'arrow-up-right',kind:'primary'}]});
TABS(main,{on:0,items:['发电行业','电网行业']});
grid=ROW(main,{gap:14});
leftCol=COL(grid,{w:840,gap:14});
base=PANEL(leftCol,{n:'BasePanel',t:'基础信息',i:'building-2'});
bb=PANEL_BODY(base,{pad:[16,18]});
br1=ROW(bb,{gap:12});
FIELD(br1,{l:'报告年份',v:'2026 年',w:180,i:'calendar'});
FIELD(br1,{l:'企业名称',v:'绿色能源科技有限公司',w:'fill_container'});
fuel=PANEL(leftCol,{n:'FuelPanel',t:'燃料燃烧参数',i:'flame',meta:'按燃料类型分别填写四参数',right:[{l:'实测反推',kind:'ghost',i:'calculator'}]});
fb=PANEL_BODY(fuel);
TABLE(fb,{cols:[{l:'燃料类型',w:120},{l:'消耗量',w:130,a:'r'},{l:'低位发热量',w:140,a:'r'},{l:'含碳量',w:120,a:'r'},{l:'碳氧化率',w:120,a:'r'}],
 rows:[['原煤',{mono:'10,000 t'},{mono:'20.908'},{mono:'0.0263'},{mono:'98.0%'}],
 ['洗精煤',{mono:'2,400 t'},{mono:'26.344'},{mono:'0.0254'},{mono:'98.5%'}],
 ['其他洗煤',{mono:'800 t'},{mono:'16.400'},{mono:'0.0250'},{mono:'97.0%'}],
 ['型煤',{mono:'—'},{mono:'—'},{mono:'—'},{mono:'—'}],
 ['其他煤制品',{mono:'—'},{mono:'—'},{mono:'—'},{mono:'—'}]]});
des=PANEL(leftCol,{n:'DesPanel',t:'脱硫过程排放',i:'beaker'});
db=PANEL_BODY(des,{pad:[16,18]});
dr=ROW(db,{gap:12});
FIELD(dr,{l:'碳酸盐消耗量 (t)',v:'500',w:'fill_container'});
FIELD(dr,{l:'脱硫排放因子',v:'0.44',w:'fill_container'});
FIELD(dr,{l:'脱硫转化率',v:'0.90',w:'fill_container'});
rightCol=COL(grid,{gap:14});
res=PANEL(rightCol,{n:'ResultPanel',t:'计算结果',i:'sigma',meta:'2026 年度'});
rb=PANEL_BODY(res,{pad:[18,18]});
CAPS(rb,{c:'燃料燃烧排放合计',fs:10,w:150});
bigr=ROW(rb,{gap:8,align:'end'});
T(bigr,{n:'V',c:'2,412.6',fs:36,fw:'500',mono:true,col:C.acc});
T(bigr,{n:'U',c:'tCO₂e',fs:12,col:C.t3});
H(rb,{n:'D1',h:1,col:C.line});
KV(rb,{k:'脱硫过程排放',v:'198.0 tCO₂e'});
KV(rb,{k:'净购入电力排放',v:'312.4 tCO₂e'});
KV(rb,{k:'总排放量',v:'2,923.0 tCO₂e',col:C.acc});
H(rb,{n:'D2',h:1,col:C.line});
T(rb,{n:'F',c:'公式：E = Σ(FC × NCV × CC × OF × 44/12) + 脱硫排放 + 净购电排放',fs:11,col:C.t3,w:400,lh:1.7});
chk=PANEL(rightCol,{n:'CheckPanel',t:'校核提示',i:'circle-check'});
cb=PANEL_BODY(chk,{pad:[14,18],gap:10});
CALLOUT(cb,{tone:'live',i:'check-circle-2',t:'三项参数均在国标合理区间内',c:'低位发热量与含碳量偏离行业均值不足 5%，无需补充说明。'});
CALLOUT(cb,{tone:'warn',i:'triangle-alert',t:'型煤与其他煤制品未填写',c:'若本年度确有消耗，请补录后重新计算；空白项按零计。'});
MINI(cb,{l:'计算时间',v:'2026-05-31 17:04'});`,
  },
  // idx 7 -> 08
  {
    name: '08 数据可视化',
    body: `
main=SCREEN({name:'08 数据可视化',x:PX,y:PY,navItems:NAV_ENT,tab:2,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:4,pad:[18,22],gap:14});
PAGEHEAD(main,{crumbs:['企业资产','数据可视化'],title:'数据可视化',sub:'碳币、配额与信用一屏总览；六张图表随筛选条件联动。',
 actions:[{l:'市场预测驾驶舱',i:'line-chart',kind:'ghost'},{l:'企业风控体检',i:'shield-alert',kind:'primary'}]});
bar=ROW(main,{gap:10,align:'end'});
FIELD(bar,{l:'资产编号',ph:'如 CR-001',w:190,i:'search'});
FIELD(bar,{l:'报告类型',v:'全部',w:150,i:'chevron-down'});
FIELD(bar,{l:'核算周期',v:'2026-Q1',w:160,i:'calendar'});
BTN(bar,{l:'查询',kind:'primary'});
BTN(bar,{l:'重置',kind:'ghost'});
seg=ROW(bar,{gap:0,align:'center',w:158,h:36,fill:'$surfaceSunken',stroke:C.line,sw:1,rad:2});
['日','月','年'].forEach((s,i)=>{
 b=F(seg,{n:'Seg',dir:'horizontal',w:52,h:34,just:'center',align:'center',rad:1,fill:i===1?'$raisedActive':'#00000000'});
 T(b,{n:'L',c:s,fs:12,col:i===1?C.t1:C.t3,fw:i===1?'500':'400'});});
PILL(bar,{l:'准入证书 有效',tone:'live'});
stats=ROW(main,{gap:12});
STAT(stats,{l:'碳币总数',v:'12',u:'枚',i:'coins',d:'本月 +3 枚',dtone:'up',h:88});
STAT(stats,{l:'碳额度总数',v:'50',u:'tCO₂e',i:'wallet',d:'已用 12 · 余 38',h:88});
STAT(stats,{l:'信用积分',v:'88',u:'分',i:'shield-check',d:'良好级 · 行业第 3',h:88});
STAT(stats,{l:'本年累计排放',v:'370.5',u:'tCO₂e',i:'factory',d:'同比 −4.2%',dtone:'up',h:88});
g1=ROW(main,{gap:14,h:246});
c1=PANEL(g1,{n:'C1',t:'碳交易总笔数',i:'chart-column',meta:'按月'});
c1b=PANEL_BODY(c1,{pad:[14,16]});
BARS(c1b,{h:150,data:[6,9,4,11,8,13],labels:['1月','2月','3月','4月','5月','6月'],hi:5,bw:22});
c3=PANEL(g1,{n:'C3',t:'AI 建议月度配额',i:'sparkles',meta:'模型 v1.0'});
c3b=PANEL_BODY(c3,{pad:[14,16]});
BARS(c3b,{h:150,data:[42,38,45,40,36,34],labels:['7月','8月','9月','10月','11月','12月'],bw:22});
g2=ROW(main,{gap:14,h:236});
c4=PANEL(g2,{n:'C4',t:'碳排放构成',i:'chart-pie',meta:'2026-Q1'});
c4b=PANEL_BODY(c4,{pad:[14,18]});
PROPBAR(c4b,{w:560,unit:' tCO₂e',segs:[{l:'范围一 燃烧',v:'214.6',c:C.acc},{l:'范围二 电力',v:'96.4',c:'$chart2'},{l:'范围三 运输',v:'38.2',c:'$chart3'},{l:'脱硫过程',v:'21.3',c:'$chart4'}]});
c5=PANEL(g2,{n:'C5',t:'碳交易统计',i:'arrow-left-right',meta:'近 12 个月'});
c5b=PANEL_BODY(c5,{pad:[14,18]});
PROPBAR(c5b,{w:560,unit:'',segs:[{l:'交易支出占比',v:'62.0%',c:'$chart2'},{l:'交易数量占比',v:'38.0%',c:C.acc}]});
MINI(c5b,{l:'累计成交笔数',v:'47 笔'});
c6=PANEL(g2,{n:'C6',t:'信用积分趋势',i:'trending-up',meta:'近 6 个月'});
c6b=PANEL_BODY(c6,{pad:[14,18]});
CHART(c6b,{h:132,yLabels:['95','90','85','80'],xLabels:['1月','2月','3月','4月','5月','6月'],draw:function(plot){
 bw=F(plot,{n:'Lw',dir:'horizontal',w:'fill_container',h:132,align:'end',gap:6});
 ['82','84','84','86','87','88'].forEach((v,i)=>{
  c=F(bw,{n:'P',dir:'vertical',w:'fill_container',h:132,just:'end',align:'center',gap:4});
  R(c,{n:'B',w:20,h:Math.round((parseInt(v)-78)*9),fill:i===5?C.acc:'$chartTrack',rad:1});
  T(c,{n:'V',c:v,fs:9,mono:true,col:i===5?C.acc:C.t3});
 });}});`,
  },
  // idx 8 -> 09

  // idx 9 -> 10

  // idx 10 -> 11

  // idx 11 -> 12
  {
    name: '12 碳币账户',
    body: `
main=SCREEN({name:'12 碳币账户',x:PX,y:PY,navItems:NAV_ENT,tab:2,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:6});
PAGEHEAD(main,{crumbs:['企业资产','碳币账户'],title:'碳币账户',sub:'碳币用于配额交易结算；每笔流水都会记录交易后余额，便于对账。',
 actions:[{l:'转账',i:'send',kind:'primary'}]});
grid=ROW(main,{gap:14});
bal=F(grid,{n:'Balance',dir:'vertical',w:420,h:118,pad:[18,20],gap:6,just:'space_between',fill:'$surface',stroke:'$hairline',sw:1,rad:3});
bt=ROW(bal,{w:'fill_container',just:'space_between',align:'center'});
CAPS(bt,{c:'账户余额',fs:10.5,w:90});
PILL(bt,{l:'账户正常',tone:'live'});
bv=ROW(bal,{gap:8,align:'end'});
T(bv,{n:'V',c:'1,250.00',fs:38,fw:'500',mono:true,col:C.acc});
T(bv,{n:'U',c:'碳币',fs:12,col:C.t3});
T(bal,{n:'Sub',c:'累计充值 5,000.00 · 累计消费 3,750.00',fs:11,col:C.t3,w:360});
cols=COL(grid,{w:'fill_container',gap:12});
st=ROW(cols,{gap:12});
STAT(st,{l:'累计充值',v:'5,000.00',u:'碳币',i:'arrow-down-to-line',h:84});
STAT(st,{l:'累计消费',v:'3,750.00',u:'碳币',i:'arrow-up-from-line',h:84});
STAT(st,{l:'本月净流入',v:'+240.00',u:'碳币',i:'trending-up',tone:'live',h:84});
flow=PANEL(main,{n:'FlowPanel',t:'资金流水',i:'list-ordered',meta:'共 24 条 · 按时间倒序'});
fb=PANEL_BODY(flow);
TABLE(fb,{zebra:true,cols:[{l:'序号',w:52},{l:'交易编号',w:130},{l:'类型',w:100},{l:'金额',w:130,a:'r'},{l:'交易后余额',w:140,a:'r'},{l:'备注'},{l:'时间',w:170}],
 rows:[[{mono:'1'},{mono:'CCT001',col:'$textSecondary'},{pill:'充值',tone:'live'},{mono:'+25.00',col:'$accent'},{mono:'1,250.00'},'月度配额补充','2026-06-01 09:00'],
 [{mono:'2'},{mono:'CCT000',col:'$textSecondary'},{pill:'转出',tone:'danger'},{mono:'−120.00',col:'$stateDanger'},{mono:'1,225.00'},'P2P 交易 TRX-2026-013','2026-05-28 14:31'],
 [{mono:'3'},{mono:'CCT999',col:'$textSecondary'},{pill:'转入',tone:'live'},{mono:'+80.00',col:'$accent'},{mono:'1,345.00'},'P2P 交易 TRX-2026-012','2026-05-26 11:15'],
 [{mono:'4'},{mono:'CCT998',col:'$textSecondary'},{pill:'购买',tone:'info'},{mono:'−702.00',col:'$stateDanger'},{mono:'1,265.00'},'拍卖成交 ORD-1038','2026-05-28 14:28'],
 [{mono:'5'},{mono:'CCT997',col:'$textSecondary'},{pill:'冻结',tone:'warn'},{mono:'−200.00',col:'$stateWarn'},{mono:'1,967.00'},'挂单保证金 ORD-1042','2026-05-20 10:06'],
 [{mono:'6'},{mono:'CCT996',col:'$textSecondary'},{pill:'解冻',tone:'neutral'},{mono:'+200.00',col:'$textSecondary'},{mono:'2,167.00'},'撤单退还 ORD-1036','2026-05-19 16:40']]});
PAGER(fb,{count:'共 24 条 · 每页 10 条'});`,
  },
];
