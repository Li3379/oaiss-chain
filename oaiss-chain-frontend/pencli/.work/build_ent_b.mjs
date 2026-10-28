// Enterprise pages 13-18 (grid indices 12..17).
export const INDEX_OFFSET = 12;
export const PAGES = [
  // idx 12 -> 13
  {
    name: '13 信誉评分',
    body: `
main=SCREEN({name:'13 信誉评分',x:PX,y:PY,navItems:NAV_ENT,tab:2,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:5});
PAGEHEAD(main,{crumbs:['企业资产','信誉评分'],title:'信誉评分',sub:'信用分决定交易权限与配额额度；每次违规或奖励都会留下可追溯的流水。',
 actions:[{l:'评分规则说明',i:'book-open',kind:'ghost'}]});
grid=ROW(main,{gap:14});
score=F(grid,{n:'ScoreCard',dir:'vertical',w:340,h:186,pad:[20,22],gap:10,just:'space_between',fill:'$surface',stroke:'$hairline',sw:1,rad:3});
sh=ROW(score,{w:'fill_container',just:'space_between',align:'center'});
CAPS(sh,{c:'当前信用评分',fs:10.5,w:110});
PILL(sh,{l:'良好',tone:'info'});
sv=ROW(score,{gap:8,align:'end'});
T(sv,{n:'V',c:'88',fs:56,fw:'500',mono:true,col:C.acc});
T(sv,{n:'U',c:'/ 100 分',fs:12,col:C.t3});
T(score,{n:'Sub',c:'行业排名 第 3 名 · 交易权限：允许',fs:11.5,col:C.t2,w:290});
T(score,{n:'T',c:'最近更新 2026-05-31 10:12',fs:10.5,mono:true,col:C.t3,w:280});
rightCol=COL(grid,{gap:14});
stats=ROW(rightCol,{gap:12});
STAT(stats,{l:'本年奖励加分',v:'+18',u:'分',i:'trending-up',tone:'live',h:86});
STAT(stats,{l:'本年违规扣分',v:'−12',u:'分',i:'trending-down',tone:'danger',h:86});
STAT(stats,{l:'距优秀等级',v:'+2',u:'分',i:'target',h:86});
rule=PANEL(rightCol,{n:'RulePanel',t:'等级与交易权限',i:'shield-check',meta:'按当前评分实时判定'});
rb=PANEL_BODY(rule,{pad:[14,18]});
LEGEND(rb,{items:[{l:'优秀  ≥ 90 分',v:'允许交易',c:'#C6F24E'},{l:'良好  ≥ 80 分',v:'允许交易',c:'#5FB8CE'},{l:'警告  ≥ 60 分',v:'允许交易',pt:true},{l:'危险  ≥ 40 分',v:'限额交易',c:'#E0A94A'},{l:'冻结  < 40 分',v:'禁止交易',c:'#FF6B5A'}]});
hist=PANEL(main,{n:'HistPanel',t:'评分历史',i:'history',meta:'共 12 条 · 按时间倒序'});
hb=PANEL_BODY(hist);
TABLE(hb,{zebra:true,cols:[{l:'序号',w:56},{l:'事件类型',w:150},{l:'变化值',w:110,a:'r'},{l:'原因'},{l:'时间',w:190}],
 rows:[[{mono:'1'},{pill:'良好行为奖励',tone:'live'},{mono:'+5',col:'$accent'},'连续 4 个季度按时提交月度核算报告','2026-05-20 10:12'],
 [{mono:'2'},{pill:'迟交报告',tone:'danger'},{mono:'−10',col:'$stateDanger'},'2026 年 3 月月度核算报告迟交 5 天','2026-05-22 10:12'],
 [{mono:'3'},{pill:'轻微违规',tone:'warn'},{mono:'−2',col:'$stateWarn'},'2026-Q1 报告缺少能源台账发票附件','2026-04-18 14:30'],
 [{mono:'4'},{pill:'良好行为奖励',tone:'live'},{mono:'+8',col:'$accent'},'主动配合第三方核查，数据完整率 100%','2026-03-05 09:40']]});
rank=PANEL(main,{n:'RankPanel',t:'行业天梯榜',i:'trophy-2',meta:'2026 年度 · 共 26 家企业',
 right:[{l:'全部排名',kind:'ghost'}]});
kb=PANEL_BODY(rank);
TABLE(kb,{zebra:true,cols:[{l:'序号',w:56},{l:'企业名称'},{l:'评分',w:96,a:'r'},{l:'等级',w:100},{l:'交易权限',w:110}],
 rows:[[{mono:'1'},'低碳制造股份有限公司',{mono:'95'},{pill:'优秀',tone:'live'},{text:'允许'}],
 [{mono:'2'},'清洁能源发展有限公司',{mono:'92'},{pill:'优秀',tone:'live'},{text:'允许'}],
 [{mono:'3'},{link:'绿色能源科技有限公司'},{mono:'88',col:'$accent'},{pill:'良好',tone:'info'},{text:'允许',col:'$accent'}],
 [{mono:'4'},'东方热电集团',{mono:'76'},{pill:'警告',tone:'warn'},{text:'允许'}],
 [{mono:'5'},'华北建材有限公司',{mono:'52'},{pill:'危险',tone:'danger'},{text:'受限',col:'$stateWarn'}]]});`,
  },
  // idx 13 -> 14
  {
    name: '14 区块链浏览器',
    body: `
main=SCREEN({name:'14 区块链浏览器',x:PX,y:PY,navItems:NAV_ENT,tab:0,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:7});
PAGEHEAD(main,{crumbs:['区块链','区块链浏览器'],title:'区块链浏览器',sub:'查看 Fabric 网络状态、区块与交易记录，并凭交易哈希追溯存证内容。',
 actions:[{l:'刷新最新区块',i:'refresh-cw',kind:'ghost'}]});
grid=ROW(main,{gap:14});
conn=PANEL(grid,{n:'ConnPanel',t:'连接状态',i:'radio',meta:'通道 mychannel',
 right:[{l:'运行中',kind:'ghost',i:'activity'}]});
cb=PANEL_BODY(conn,{pad:[14,18]});
PILL(cb,{l:'网络在线 · 延迟 12ms',tone:'live'});
H(cb,{n:'D',h:1,col:C.line});
KV(cb,{k:'运行模式',v:'Fabric 2.5 · Gateway'});
KV(cb,{k:'通道名称',v:'mychannel'});
KV(cb,{k:'链码',v:'carbon-chaincode'});
KV(cb,{k:'组织节点',v:'2 个 peer · 1 个 orderer'});
KV(cb,{k:'当前区块高度',v:'#1,284'});
lookup=PANEL(grid,{n:'LookupPanel',t:'交易哈希查询',i:'search',meta:'64 位十六进制'});
lb=PANEL_BODY(lookup,{pad:[16,18],gap:12});
FIELD(lb,{l:'交易哈希',v:'9f2c7d51a8e34b06c1d9f7e2b5a04c8d13e6f92a7b4c0d58e1f3a6b9c2d7e40f'});
r2=ROW(lb,{gap:10,align:'center'});
BTN(r2,{l:'查询交易',kind:'primary',i:'search'});
T(r2,{n:'Hint',c:'支持粘贴 txId；查询结果含区块高度、时间戳与存证摘要',fs:11,col:C.t3,w:280});
note=F(lb,{n:'Note',dir:'horizontal',w:'fill_container',pad:[10,12],gap:9,align:'start',fill:'$noteBg',stroke:'$noteBd',sw:1,rad:2});
I(note,{n:'I',i:'info',s:13,col:C.acc});
T(note,{n:'L',c:'每份审核通过的排放报告都会写入一条交易，交易哈希是唯一且不可篡改的凭证。',fs:11.5,col:C.t2,w:340,lh:1.6});
TABS(main,{on:0,items:['最新区块','交易记录']});
blk=PANEL(main,{n:'BlockPanel',t:'最新区块',i:'boxes',meta:'共 1,284 个 · 实时刷新'});
bb=PANEL_BODY(blk);
TABLE(bb,{zebra:true,cols:[{l:'序号',w:56},{l:'区块高度',w:110,a:'r'},{l:'区块哈希（截断显示）',w:320},{l:'区块类型',w:130},{l:'交易数量',w:110,a:'r'},{l:'矿工地址',w:160}],
 rows:[[{mono:'1'},{mono:'#1,284'},{mono:'a3f19c2e…2c7d4e08'},{pill:'常规区块',tone:'info'},{mono:'3'},{mono:'peer0.org1'}],
 [{mono:'2'},{mono:'#1,283'},{mono:'7b2e0d94…c1e29d7b'},{pill:'常规区块',tone:'info'},{mono:'2'},{mono:'peer0.org1'}],
 [{mono:'3'},{mono:'#1,282'},{mono:'1c8f5a3b…d8c70e3b'},{pill:'常规区块',tone:'info'},{mono:'1'},{mono:'peer0.org2'}],
 [{mono:'4'},{mono:'#1,281'},{mono:'0e4b7c1a…f9e62d5a'},{pill:'常规区块',tone:'info'},{mono:'2'},{mono:'peer0.org2'}],
 [{mono:'5'},{mono:'#1,280'},{mono:'5d2a8f04…e12d704f'},{pill:'创世区块',tone:'live'},{mono:'0'},{mono:'orderer0'}]],
 rh:44});`,
  },
  // idx 14 -> 15
  {
    name: '15 AI市场预测',
    body: `
main=SCREEN({name:'15 AI市场预测',x:PX,y:PY,navItems:NAV_ENT,tab:2,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:9,pad:[18,22],gap:14});
PAGEHEAD(main,{crumbs:['数据智能','AI 市场预测'],title:'碳价智能预测驾驶舱',sub:'基于历史成交与配额供需的碳价趋势预测，支持情景推演沙盘与可解释性分析。',
 actions:[{l:'导出预测报告',i:'download',kind:'ghost'},{l:'前往双向拍卖挂单',i:'gavel',kind:'primary'}]});
bar=ROW(main,{gap:12,align:'end'});
FIELD(bar,{l:'预测类型',v:'碳价预测',w:190,i:'chevron-down'});
seg=ROW(bar,{gap:0,align:'center',w:262,h:36,fill:'$surfaceSunken',stroke:C.line,sw:1,rad:2});
['7 天','30 天','90 天','180 天'].forEach((s,i)=>{
 b=F(seg,{n:'Seg',dir:'horizontal',w:64,h:34,just:'center',align:'center',rad:1,fill:i===2?'$raisedActive':'#00000000'});
 T(b,{n:'L',c:s,fs:11.5,col:i===2?C.t1:C.t3,fw:i===2?'500':'400'});});
F(bar,{n:'Spacer',dir:'horizontal',w:'fill_container',h:1,fill:'#00000000'});
PILL(bar,{l:'模型运行正常 · 更新于 09:20',tone:'live'});
stats=ROW(main,{gap:12});
STAT(stats,{l:'现货基准价',v:'58.20',u:'元/吨',i:'tag',d:'较昨日 +1.35%',dtone:'up',h:86});
STAT(stats,{l:'期末预测价',v:'62.40',u:'元/吨',i:'trending-up',d:'90 天后 · 区间 58.9 ~ 66.1',h:86});
STAT(stats,{l:'模型拟合优度',v:'91.2',u:'%',i:'activity',d:'R² · 样本 1,024 条',h:86});
STAT(stats,{l:'算法架构',v:'1.0.0',u:'版',i:'cpu',d:'LSTM + 注意力机制',h:86});
grid=ROW(main,{gap:14});
chartCol=COL(grid,{w:'fill_container',gap:14});
price=PANEL(chartCol,{n:'PricePanel',t:'碳价趋势与预测区间',i:'chart-line',meta:'元 / tCO₂e · 未来 90 天',
 right:[{l:'日线',kind:'ghost'},{l:'周线',kind:'ghost'}]});
pb=PANEL_BODY(price,{pad:[14,18]});
CHART(pb,{h:236,yLabels:['70','65','60','55','50'],xLabels:['3月','4月','5月','6月','7月','8月','9月'],draw:function(plot){
 w=F(plot,{n:'LW',dir:'horizontal',w:'fill_container',h:236,just:'space_between',align:'end',gap:0});
 [['52.4','实际'],['55.1','实际'],['57.3','实际'],['58.2','实际'],['60.6','预测'],['61.8','预测'],['62.4','预测']].forEach((d,i)=>{
  c=F(w,{n:'P',dir:'vertical',w:'fill_container',h:236,just:'end',align:'center',gap:6});
  R(c,{n:'B',w:26,h:Math.round((parseFloat(d[0])-46)*9),fill:i<4?'$chart3':C.accDim,rad:1});
  T(c,{n:'V',c:d[0],fs:10,mono:true,col:i<4?C.t2:C.acc});
  T(c,{n:'K',c:d[1],fs:8.5,col:C.t3});
 });}});
XAI=PANEL(chartCol,{n:'XAIPanel',t:'可解释性分析 · 特征权重',i:'brain',meta:'SHAP 值归因',
 right:[{l:'查看明细',kind:'ghost'}]});
xb=PANEL_BODY(XAI,{pad:[14,18],gap:12});
[['配额基准线紧缩幅度','38%',0.38],['动力煤价格指数','28%',0.28],['区域绿电消纳替代率','21%',0.21],['重点行业开工率','13%',0.13]].forEach(x=>{
 METER(xb,{l:x[0],v:x[1],pct:x[2],w:560});
});
side=COL(grid,{w:390,gap:14});
sand=PANEL(side,{n:'SandPanel',t:'情景推演沙盘',i:'sliders-horizontal',meta:'拖动后自动重算',
 right:[{l:'重置参数',kind:'ghost'}]});
sb=PANEL_BODY(sand,{pad:[16,18],gap:16});
SLIDER(sb,{l:'动力煤价格变动率',v:'+5.0%',pct:0.62});
SLIDER(sb,{l:'配额基准线紧缩幅度',v:'−3.0%',pct:0.42});
SLIDER(sb,{l:'区域绿电消纳替代率',v:'12.0%',pct:0.24});
H(sb,{n:'D',h:1,col:C.line});
KV(sb,{k:'推演期末碳价',v:'63.85 元/吨',col:C.acc});
KV(sb,{k:'较基准变化',v:'+1.45 元/吨',col:C.acc});
strat=PANEL(side,{n:'StratPanel',t:'交易策略建议',i:'lightbulb',meta:'综合置信度 91.2%'});
tbb=PANEL_BODY(strat,{pad:[14,18],gap:10});
T(tbb,{n:'S',c:'建议在未来 7~10 天分批建仓，目标成本控制在 60 元/吨以内；若配额基准线继续收紧，可延后至 62 元/吨区间追高。',fs:12,col:C.t2,w:330,lh:1.75});
CALLOUT(tbb,{tone:'warn',i:'triangle-alert',t:'预测仅供参考',c:'碳价受政策与供需影响，实际成交价可能偏离区间。'});
BTN(tbb,{l:'前往双向拍卖挂单',kind:'primary',w:'fill_container',h:38,i:'gavel'});`,
  },
  // idx 15 -> 16
  {
    name: '16 AI企业推理',
    body: `
main=SCREEN({name:'16 AI企业推理',x:PX,y:PY,navItems:NAV_ENT,tab:2,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:9,pad:[18,22],gap:14});
PAGEHEAD(main,{crumbs:['数据智能','AI 企业推理'],title:'AI 企业推理',sub:'对排放结构、履约历史与工艺受控度做合规风险推断，输出六维体检与减排路线。',
 actions:[{l:'刷新推理',i:'refresh-cw',kind:'ghost'},{l:'生成低碳体检白皮书',i:'file-text',kind:'primary'}]});
ent=F(main,{n:'EntCard',dir:'horizontal',w:'fill_container',pad:[14,18],gap:22,align:'center',fill:'$surface',stroke:C.line,sw:1,rad:2});
ec=F(ent,{n:'EC',dir:'vertical',gap:4,w:340});
T(ec,{n:'N',c:'绿色能源科技有限公司',fs:15,fw:'600',col:C.t1});
T(ec,{n:'M',c:'新能源 · 统一社会信用代码 91110108MA01XXXX2N',fs:11,col:C.t3,w:330});
V(ent,{w:1,h:44,col:C.line});
[['信用分','85（AAA 级）'],['季度排放','150.00 tCO₂e / 季'],['模型版本','v1.0 · 2026-05-30']].forEach(x=>{
 c=COL(ent,{w:220,gap:3});
 T(c,{n:'K',c:x[0],fs:10,col:C.t3,w:90});
 T(c,{n:'V',c:x[1],fs:13,fw:'500',mono:true,col:C.t1,w:200});});
F(ent,{n:'Spacer',dir:'horizontal',w:'fill_container',h:1,fill:'#00000000'});
PILL(ent,{l:'合规',tone:'live'});
stats=ROW(main,{gap:12});
STAT(stats,{l:'合规状态',v:'合规',i:'shield-check',d:'未触发红线规则',h:88});
STAT(stats,{l:'置信度评分',v:'96',u:'%',i:'activity',d:'样本充足 · 特征完整',h:88});
STAT(stats,{l:'异常检测',v:'无异常',i:'scan-eye',d:'数据平稳受控',h:88});
STAT(stats,{l:'综合低碳健康分',v:'92',u:'/ 100',i:'heart-pulse',d:'行业前 12%',h:88});
grid=ROW(main,{gap:14});
radarCol=COL(grid,{w:'fill_container',gap:14});
rad=PANEL(radarCol,{n:'RadarPanel',t:'六维体检雷达',i:'radar',meta:'本企业 vs 国家行业基准',
 right:[{l:'维度下钻',kind:'ghost'}]});
rr=PANEL_BODY(rad,{pad:[16,18],gap:12});
[['范围一 燃烧受控度','92','88'],['范围二 绿电清洁度','86','72'],['信用履约历史','95','84'],['报告周期规律性','90','80'],['碳配额流动储备','78','76'],['脱硫工艺受控度','88','82']].forEach(x=>{
 r=F(rr,{n:'Dim',dir:'horizontal',w:'fill_container',gap:14,align:'center'});
 T(r,{n:'K',c:x[0],fs:12,col:C.t2,w:150});
 tr=F(r,{n:'Track',dir:'horizontal',w:320,h:6,fill:'$track',rad:1});
 R(tr,{n:'Fill',w:Math.round(parseFloat(x[1])*3.2),h:6,fill:C.acc,rad:1});
 T(r,{n:'V',c:x[1],fs:12,mono:true,col:C.acc,w:40});
 T(r,{n:'B',c:'基准 '+x[2],fs:10.5,mono:true,col:C.t3,w:70});});
side=COL(grid,{w:420,gap:14});
risk=PANEL(side,{n:'RiskPanel',t:'风险因素',i:'triangle-alert',meta:'共 3 项 · 按等级排序'});
kb=PANEL_BODY(risk,{pad:[14,18],gap:12});
CALLOUT(kb,{tone:'danger',i:'circle-alert',t:'存在离群扰动 · 异常分 −0.199',c:'2026 年 4 月月度排放较同口径预期偏低 11%，建议核查活动数据来源。'});
CALLOUT(kb,{tone:'warn',i:'triangle-alert',t:'碳配额流动储备偏低',c:'当前可交易配额仅占总量 24%，若下季度配额收紧可能影响履约。'});
CALLOUT(kb,{tone:'info',i:'info',t:'关注级 · 报告周期规律性',c:'近 6 个月有 2 次迟交，建议设置提前 5 天的内部提醒。'});
road=PANEL(side,{n:'RoadPanel',t:'三阶段减排路线',i:'route',meta:'按投入产出比排序'});
orb=PANEL_BODY(road,{pad:[14,18],gap:12});
[['近期 0~6 个月','余热回收与照明改造，预计减排 1,200 tCO₂e'],['中期 6~18 个月','分布式光伏扩容至 5MW，预计减排 4,800 tCO₂e'],['远期 18 个月以上','CCUS 示范装置投运，预计减排 9,000 tCO₂e']].forEach((x,i)=>{
 r=F(orb,{n:'Phase',dir:'horizontal',w:'fill_container',gap:11,align:'start'});
 d=F(r,{n:'Dot',dir:'horizontal',w:18,h:18,just:'center',align:'center',rad:9,fill:i===0?C.acc:'$track',stroke:i===0?'#00000000':C.line,sw:1});
 T(d,{n:'N',c:String(i+1),fs:9.5,mono:true,col:i===0?'$onAccent':C.t3});
 c=COL(r,{w:'fill_container',gap:2});
 T(c,{n:'T',c:x[0],fs:12.5,fw:'500',col:C.t1,w:130});
 T(c,{n:'D',c:x[1],fs:11.5,col:C.t2,w:220,lh:1.55});});`,
  },
  // idx 16 -> 17
  {
    name: '17 企业信息',
    body: `
main=SCREEN({name:'17 企业信息',x:PX,y:PY,navItems:NAV_ENT,tab:2,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:4});
PAGEHEAD(main,{crumbs:['企业资产','企业信息'],title:'企业信息',sub:'企业档案、配额与碳币余额；联系方式可自助维护。',
 actions:[{l:'编辑联系方式',i:'pencil',kind:'primary'}]});
NOTE(main,{i:'info',c:'企业名称与统一社会信用代码为注册信息，如需变更请联系平台管理员。'});
grid=ROW(main,{gap:14});
info=PANEL(grid,{n:'InfoPanel',t:'企业档案',i:'building-2',meta:'注册信息'});
ib=PANEL_BODY(info,{pad:[16,18]});
KV(ib,{k:'企业名称',v:'绿色能源科技有限公司'});
KV(ib,{k:'统一社会信用代码',v:'91110108MA01XXXX2N'});
KV(ib,{k:'所属行业',v:'新能源 · 电力生产'});
KV(ib,{k:'联系人',v:'李四'});
KV(ib,{k:'联系电话',v:'138 0013 8001'});
KV(ib,{k:'注册日期',v:'2021-06-18'});
KV(ib,{k:'注册地址',v:'北京市昌平区科技园区双碳大道 18 号'});
side=COL(grid,{w:520,gap:14});
quota=PANEL(side,{n:'QuotaPanel',t:'碳配额概览',i:'wallet',meta:'2026 年度'});
qb=PANEL_BODY(quota,{pad:[16,18],gap:12});
qr=ROW(qb,{gap:12});
qq=COL(qr,{gap:12});
MiniStat(qq,{l:'总配额',v:'50,000',u:'tCO₂e',fs:22});
MiniStat(qq,{l:'已用配额',v:'12,000',u:'tCO₂e',fs:22});
qq2=COL(qr,{gap:12});
MiniStat(qq2,{l:'可交易配额',v:'38,000',u:'tCO₂e',fs:22,col:'$accent'});
MiniStat(qq2,{l:'碳币余额',v:'1,250',u:'枚',fs:22,col:'$accent'});
H(qb,{n:'D',h:1,col:C.line});
usage=F(qb,{n:'Usage',dir:'vertical',w:'fill_container',gap:7});
ur=ROW(usage,{just:'space_between',w:'fill_container',align:'center'});
T(ur,{n:'L',c:'配额使用率',fs:12,col:C.t2,w:90});
T(ur,{n:'V',c:'24.00%',fs:12,mono:true,col:C.acc,w:60});
tr=F(usage,{n:'Track',dir:'horizontal',w:460,h:8,fill:'$track',rad:2});
R(tr,{n:'Fill',w:110,h:8,fill:C.acc,rad:2});
CALLOUT(qb,{tone:'live',i:'check-circle-2',t:'配额状态正常',c:'剩余可交易配额充足，未触及履约预警线。'});
cert=PANEL(side,{n:'CertPanel',t:'准入证书',i:'file-badge',meta:'平台签发'});
cnb=PANEL_BODY(cert,{pad:[16,18]});
KV(cnb,{k:'证书编号',v:'CERT-2026-001'});
KV(cnb,{k:'签发日期',v:'2026-01-15'});
KV(cnb,{k:'证书状态',v:'有效',col:C.acc});
KV(cnb,{k:'签发机构',v:'中国碳排放权注册登记机构'});`,
  },
  // idx 17 -> 18
  {
    name: '18 个人中心',
    body: `
main=SCREEN({name:'18 个人中心',x:PX,y:PY,navItems:NAV_ENT,tab:3,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:10});
PAGEHEAD(main,{crumbs:['个人','个人中心'],title:'个人中心',sub:'维护个人资料、修改密码，并管理用于报告签名的 RSA 密钥与准入证书。',
 actions:[{l:'刷新密钥状态',i:'refresh-cw',kind:'ghost'}]});
TABS(main,{on:0,items:['基本信息','修改密码']});
grid=ROW(main,{gap:14});
prof=PANEL(grid,{n:'ProfPanel',t:'基本信息',i:'user-round',meta:'带 * 为必填'});
pb=PANEL_BODY(prof,{pad:[16,18],gap:12});
pr1=ROW(pb,{gap:12});
FIELD(pr1,{l:'用户名',v:'enterprise001',w:'fill_container'});
FIELD(pr1,{l:'角色',v:'企业碳管理员',w:'fill_container'});
pr2=ROW(pb,{gap:12});
FIELD(pr2,{l:'真实姓名 *',v:'李四',w:'fill_container'});
FIELD(pr2,{l:'邮箱 *',v:'lisi@greenenergy.com',w:'fill_container'});
pr3=ROW(pb,{gap:12});
FIELD(pr3,{l:'手机号',v:'138 0013 8001',w:'fill_container'});
FIELD(pr3,{l:'公司',v:'绿色能源科技有限公司',w:'fill_container'});
pr4=ROW(pb,{gap:12,align:'end'});
FIELD(pr4,{l:'地址',v:'北京市昌平区科技园区双碳大道 18 号',w:'fill_container'});
c=COL(pr4,{w:180,gap:6});
CAPS(c,{c:'在线停留时长',fs:10.5,w:120});
b=F(c,{n:'Box',dir:'horizontal',w:'fill_container',h:36,pad:[0,11],align:'center',fill:'$surfaceSunken',stroke:C.line,sw:1,rad:2});
T(b,{n:'V',c:'0 小时 12 分 30 秒',fs:12.5,mono:true,col:C.t2,w:150});
BTN(pb,{l:'保存修改',kind:'primary',i:'check'});
side=COL(grid,{w:460,gap:14});
cert=PANEL(side,{n:'CertPanel',t:'我的准入证书',i:'file-badge',meta:'平台签发'});
cb=PANEL_BODY(cert,{pad:[16,18]});
KV(cb,{k:'证书状态',v:'有效',col:C.acc});
KV(cb,{k:'证书编号',v:'CERT-2026-001'});
KV(cb,{k:'签发日期',v:'2026-01-15'});
KV(cb,{k:'有效期至',v:'2027-01-14'});
sig=PANEL(side,{n:'SigPanel',t:'数字签名',i:'key-round',meta:'RSA-2048 / SHA256',
 right:[{l:'生成密钥对',kind:'ghost',i:'plus'}]});
sb=PANEL_BODY(sig,{pad:[16,18]});
PILL(sb,{l:'密钥状态：有效',tone:'live'});
H(sb,{n:'D',h:1,col:C.line});
KV(sb,{k:'密钥版本',v:'v1'});
KV(sb,{k:'密钥用途',v:'报告签名与加密'});
KV(sb,{k:'创建时间',v:'2026-01-16 09:22'});
KV(sb,{k:'过期时间',v:'2027-01-15 09:22'});
T(sb,{n:'K',c:'公钥预览',fs:10,fw:'500',col:C.t3,ls:1.4,w:120});
pk=F(sb,{n:'PkBox',dir:'horizontal',w:'fill_container',h:64,pad:[10,12],fill:'$surfaceSunken',stroke:C.line,sw:1,rad:2});
T(pk,{n:'V',c:'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0f2c7d51a8e34b06c1d9f7e2b5a04c8d13e6f9…',fs:10.5,mono:true,col:C.t2,w:400,lh:1.6});
NOTE(side,{i:'shield-check',c:'私钥经二次加密保存在服务端，不会以明文形式离开系统；更换密钥后旧报告仍需用旧版本公钥验签。'});`,
  },
];
// no-op
