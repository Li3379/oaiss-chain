// Enterprise pages 09-11, rebuilt with the fixed TABLE link branch.
export const INDEX_OFFSET = 8;
export const PAGES = [
  {
    name: '09 双向拍卖',
    body: `
main=SCREEN({name:'09 双向拍卖',x:PX,y:PY,navItems:NAV_ENT,tab:1,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:2});
PAGEHEAD(main,{crumbs:['交易市场','双向拍卖'],title:'碳配额双向拍卖',sub:'集合竞价撮合买卖单；成交后配额与碳币自动结算并写入链上。',
 actions:[{l:'去挂单平台',i:'gavel',kind:'ghost'},{l:'创建订单',i:'plus',kind:'primary'}]});
ai=F(main,{n:'AIAdvice',dir:'horizontal',w:'fill_container',pad:[14,16],gap:16,align:'center',fill:'$noteBg',stroke:'$noteBd',sw:1,rad:2});
I(ai,{n:'Ico',i:'sparkles',s:17,col:C.acc});
aic=COL(ai,{w:'fill_container',gap:3});
T(aic,{n:'T',c:'AI 履约建议：本季度配额缺口 12.0 tCO₂e',fs:13,fw:'500',col:C.t1});
T(aic,{n:'D',c:'按当前参考价 58.20 元/吨测算，补足缺口预估成本 698.40 碳币；建议在 7 天内分批建仓，避免月末集中买价上浮。',fs:11.5,col:C.t2,lh:1.55});
PILL(ai,{l:'风险 中',tone:'warn'});
TABS(main,{on:0,items:['全部挂单','我的挂单','撮合结果']});
grid=ROW(main,{gap:14});
leftCol=COL(grid,{w:'fill_container',gap:14});
book=PANEL(leftCol,{n:'BookPanel',t:'全部挂单',i:'list-ordered',meta:'共 18 笔 · 每 5 秒刷新',right:[{l:'查询',kind:'ghost'}]});
bk=PANEL_BODY(book);
TABLE(bk,{zebra:true,cols:[{l:'序号',w:52},{l:'方向',w:80},{l:'数量（吨）',w:130,a:'r'},{l:'价格（元/吨）',w:140,a:'r'},{l:'状态',w:108},{l:'创建时间',w:170}],
 rows:[[{mono:'1'},{pill:'买入',tone:'live'},{mono:'100.0'},{mono:'58.20'},{pill:'待撮合',tone:'warn'},{text:'2026-06-01 09:12',col:'$textSecondary'}],
 [{mono:'2'},{pill:'卖出',tone:'danger'},{mono:'50.0'},{mono:'57.90'},{pill:'待撮合',tone:'warn'},{text:'2026-06-01 09:20',col:'$textSecondary'}],
 [{mono:'3'},{pill:'买入',tone:'live'},{mono:'120.0'},{mono:'58.50'},{pill:'部分成交',tone:'info'},{text:'2026-06-01 08:55',col:'$textSecondary'}],
 [{mono:'4'},{pill:'卖出',tone:'danger'},{mono:'80.0'},{mono:'57.60'},{pill:'已成交',tone:'live'},{text:'2026-06-01 08:41',col:'$textSecondary'}],
 [{mono:'5'},{pill:'买入',tone:'live'},{mono:'30.0'},{mono:'59.10'},{pill:'已撤销',tone:'neutral'},{text:'2026-05-31 17:02',col:'$textSecondary'}]]});
match=PANEL(leftCol,{n:'MatchPanel',t:'撮合结果',i:'git-merge',meta:'最近 2 笔'});
mb=PANEL_BODY(match);
TABLE(mb,{cols:[{l:'序号',w:52},{l:'买单 ID',w:120},{l:'卖单 ID',w:120},{l:'成交数量',w:130,a:'r'},{l:'成交价',w:130,a:'r'},{l:'成交时间',w:170}],
 rows:[[{mono:'1'},{link:'ORD-1042'},{link:'ORD-1038'},{mono:'80.0'},{mono:'57.90'},{text:'2026-06-01 08:47',col:'$textSecondary'}],
 [{mono:'2'},{link:'ORD-1036'},{link:'ORD-1031'},{mono:'40.0'},{mono:'58.10'},{text:'2026-05-31 15:22',col:'$textSecondary'}]]});
rightCol=COL(grid,{w:390,gap:14});
form=PANEL(rightCol,{n:'OrderPanel',t:'创建订单',i:'gavel',meta:'配额 · 碳币结算'});
ob=PANEL_BODY(form,{pad:[16,16],gap:12});
FIELD(ob,{l:'买卖方向',v:'买入',i:'chevron-down'});
FIELD(ob,{l:'数量（吨）',v:'100.0'});
FIELD(ob,{l:'价格（元/吨）',v:'58.20',i:'sparkles'});
T(ob,{n:'UsePred',c:'采用 AI 参考价 58.20 元/吨',fs:10.5,col:C.acc,w:280});
H(ob,{n:'D',h:1,col:C.line});
KV(ob,{k:'预估成交金额',v:'5,820.00 碳币',col:C.acc});
KV(ob,{k:'可用碳币',v:'7,240.00'});
KV(ob,{k:'可用配额',v:'38,000 tCO₂e'});
BTN(ob,{l:'确认挂单',kind:'primary',w:'fill_container',h:38});
guide=F(rightCol,{n:'Guide',dir:'horizontal',w:'fill_container',pad:[11,13],gap:9,align:'start',fill:'$noteBg',stroke:'$noteBd',sw:1,rad:2});
I(guide,{n:'I',i:'help-circle',s:14,col:C.cyan});
T(guide,{n:'L',c:'未成交怎么办？集合竞价每 5 秒撮合一次，价格未匹配的挂单会继续排队，可随时撤销。',fs:11.5,col:C.t2,w:330,lh:1.6});`,
  },  {
    name: '10 P2P交易',
    body: `
main=SCREEN({name:'10 P2P交易',x:PX,y:PY,navItems:NAV_ENT,tab:1,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:3});
PAGEHEAD(main,{crumbs:['交易市场','P2P 交易'],title:'P2P 点对点交易',sub:'与指定企业定向协议转让配额，双方确认后结算上链。',
 actions:[{l:'创建 P2P 交易',i:'plus',kind:'primary'}]});
stats=ROW(main,{gap:12});
STAT(stats,{l:'进行中交易',v:'2',u:'笔',i:'clock'});
STAT(stats,{l:'本年成交',v:'14',u:'笔',i:'check-check'});
STAT(stats,{l:'本年转让量',v:'1,240',u:'tCO₂e',i:'arrow-left-right'});
STAT(stats,{l:'平均单价',v:'58.36',u:'元/吨',i:'tag'});
filt=ROW(main,{gap:10,align:'end'});
FIELD(filt,{l:'交易对手',ph:'企业名称',w:220,i:'search'});
FIELD(filt,{l:'身份',v:'全部',w:140,i:'chevron-down'});
FIELD(filt,{l:'交易编号',ph:'如 TRX-001',w:180});
BTN(filt,{l:'查询',kind:'primary'});
BTN(filt,{l:'重置',kind:'ghost'});
tbl=PANEL(main,{n:'P2PPanel',t:'交易列表',i:'arrow-left-right',meta:'共 14 笔 · 按创建时间倒序'});
tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,cols:[{l:'序号',w:52},{l:'交易编号',w:126},{l:'买方',w:200},{l:'卖方',w:200},{l:'数量（吨）',w:110,a:'r'},{l:'单价',w:96,a:'r'},{l:'总金额',w:120,a:'r'},{l:'状态',w:100},{l:'操作',w:90,a:'r'}],
 rows:[[{mono:'1'},{link:'TRX-2026-014'},'绿色能源科技有限公司','清洁能源发展有限公司',{mono:'50.0'},{mono:'58.20'},{mono:'2,910.00'},{pill:'待处理',tone:'warn'},{text:'去确认'}],
 [{mono:'2'},{link:'TRX-2026-013'},'绿色能源科技有限公司','东方热电集团',{mono:'120.0'},{mono:'57.80'},{mono:'6,936.00'},{pill:'已完成',tone:'live'},{text:'查看'}],
 [{mono:'3'},{link:'TRX-2026-012'},'华北建材有限公司','绿色能源科技有限公司',{mono:'80.0'},{mono:'58.50'},{mono:'4,680.00'},{pill:'处理中',tone:'info'},{text:'查看'}],
 [{mono:'4'},{link:'TRX-2026-011'},'低碳制造股份有限公司','绿色能源科技有限公司',{mono:'35.0'},{mono:'59.00'},{mono:'2,065.00'},{pill:'已取消',tone:'neutral'},{text:'查看'}],
 [{mono:'5'},{link:'TRX-2026-010'},'绿色能源科技有限公司','低碳制造股份有限公司',{mono:'60.0'},{mono:'57.40'},{mono:'3,444.00'},{pill:'失败',tone:'danger'},{text:'查看'}]]});
PAGER(tb,{count:'共 14 条 · 每页 10 条'});`,
  },  {
    name: '11 订单管理',
    body: `
main=SCREEN({name:'11 订单管理',x:PX,y:PY,navItems:NAV_ENT,tab:1,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:1});
PAGEHEAD(main,{crumbs:['交易市场','订单管理'],title:'我的订单',sub:'检索并撤销本人全部交易订单，覆盖拍卖与 P2P 两类。',
 actions:[{l:'导出订单',i:'download',kind:'ghost'}]});
stats=ROW(main,{gap:12});
STAT(stats,{l:'全部订单',v:'47',u:'笔',i:'receipt-text'});
STAT(stats,{l:'待处理',v:'5',u:'笔',i:'clock',d:'其中拍卖 3 · P2P 2'});
STAT(stats,{l:'已完成',v:'38',u:'笔',i:'check-check'});
STAT(stats,{l:'累计成交额',v:'18,420.50',u:'碳币',i:'coins'});
filt=ROW(main,{gap:10,align:'end'});
FIELD(filt,{l:'交易编号',ph:'如 TRX-001',w:200,i:'search'});
FIELD(filt,{l:'交易类型',v:'全部',w:170,i:'chevron-down'});
FIELD(filt,{l:'日期范围',v:'2026-05-01 至 2026-06-01',w:260,i:'calendar'});
BTN(filt,{l:'查询',kind:'primary'});
BTN(filt,{l:'重置',kind:'ghost'});
tbl=PANEL(main,{n:'OrdersPanel',t:'订单列表',i:'receipt-text',meta:'共 47 笔 · 支持勾选批量撤销'});
tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,cols:[{l:'序号',w:52},{l:'交易编号',w:122},{l:'类型',w:110},{l:'交易对手',w:190},{l:'数量',w:84,a:'r'},{l:'单价',w:92,a:'r'},{l:'总金额',w:118,a:'r'},{l:'状态',w:100},{l:'创建时间',w:170},{l:'操作',w:80,a:'r'}],
 rows:[[{mono:'1'},{link:'TRX-2026-014'},{pill:'拍卖交易',tone:'info'},'清洁能源发展有限公司',{mono:'1.0'},{mono:'58.20'},{mono:'58.20'},{pill:'待处理',tone:'warn'},{text:'2026-06-01 09:12',col:'$textSecondary'},{text:'撤销'}],
 [{mono:'2'},{link:'TRX-2026-013'},{pill:'P2P',tone:'live'},'东方热电集团',{mono:'3.0'},{mono:'57.80'},{mono:'173.40'},{pill:'处理中',tone:'info'},{text:'2026-06-01 08:40',col:'$textSecondary'},{text:'查看'}],
 [{mono:'3'},{link:'TRX-2026-012'},{pill:'拍卖交易',tone:'info'},'华北建材有限公司',{mono:'12.0'},{mono:'58.50'},{mono:'702.00'},{pill:'已完成',tone:'live'},{text:'2026-05-28 14:30',col:'$textSecondary'},{text:'查看'}],
 [{mono:'4'},{link:'TRX-2026-011'},{pill:'P2P',tone:'live'},'低碳制造股份有限公司',{mono:'35.0'},{mono:'59.00'},{mono:'2,065.00'},{pill:'已取消',tone:'neutral'},{text:'2026-05-20 16:48',col:'$textSecondary'},{text:'查看'}],
 [{mono:'5'},{link:'TRX-2026-010'},{pill:'拍卖交易',tone:'info'},'绿色能源科技有限公司',{mono:'60.0'},{mono:'57.40'},{mono:'3,444.00'},{pill:'失败',tone:'danger'},{text:'2026-05-18 10:02',col:'$textSecondary'},{text:'查看'}],
 [{mono:'6'},{link:'TRX-2026-009'},{pill:'P2P',tone:'live'},'清洁能源发展有限公司',{mono:'22.0'},{mono:'58.10'},{mono:'1,278.20'},{pill:'已完成',tone:'live'},{text:'2026-05-15 09:22',col:'$textSecondary'},{text:'查看'}]]});
PAGER(tb,{count:'共 47 条 · 每页 10 条'});`,
  },
];
