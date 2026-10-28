// Admin pages (grid indices 23..27) -> 24..28.
export const INDEX_OFFSET = 23;
export const PAGES = [
  // idx 23 -> 24
  {
    name: '24 用户管理',
    body: `
main=SCREEN({name:'24 用户管理',x:PX,y:PY,navItems:NAV_ADM,tab:0,tabs:TABS_ADM,user:U_ADM.user,role:U_ADM.role,initial:U_ADM.initial,live:U_ADM.live,nav:0});
PAGEHEAD(main,{crumbs:['系统管理','用户管理'],title:'用户管理',sub:'全平台账号名册与启停管控；禁用后该账号立即失去登录与接口访问权限。',
 actions:[{l:'导出账号名册',i:'download',kind:'ghost'}]});
stats=ROW(main,{gap:12});
STAT(stats,{l:'账号总数',v:'5',u:'个',i:'users'});
STAT(stats,{l:'启用中',v:'5',u:'个',i:'check-check',tone:'live'});
STAT(stats,{l:'已禁用',v:'0',u:'个',i:'ban'});
STAT(stats,{l:'本周新增',v:'1',u:'个',i:'user-plus',d:'企业账号 1 个'});
filt=ROW(main,{gap:10,align:'end'});
FIELD(filt,{l:'用户类型',v:'全部',w:170,i:'chevron-down'});
FIELD(filt,{l:'状态',v:'全部',w:150,i:'chevron-down'});
FIELD(filt,{l:'关键词',ph:'用户名 / 邮箱',w:240,i:'search'});
BTN(filt,{l:'查询',kind:'primary'});
BTN(filt,{l:'重置',kind:'ghost'});
tbl=PANEL(main,{n:'UserPanel',t:'账号列表',i:'users',meta:'共 5 个 · 切换状态前需二次确认'});
tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,cols:[{l:'用户名',w:180},{l:'邮箱'},{l:'用户类型',w:130},{l:'状态',w:110},{l:'创建时间',w:180},{l:'操作',w:120,a:'r'}],
 rows:[
  ['admin','admin@oaiss.com',{pill:'管理员',tone:'info'},{pill:'启用',tone:'live'},{text:'2026-01-01 00:00',col:'$textSecondary'},{text:'禁用'}],
  ['enterprise001','enterprise001@example.com',{pill:'企业',tone:'neutral'},{pill:'启用',tone:'live'},{text:'2026-01-15 09:20',col:'$textSecondary'},{text:'禁用'}],
  ['enterprise002','enterprise002@example.com',{pill:'企业',tone:'neutral'},{pill:'启用',tone:'live'},{text:'2026-02-08 14:05',col:'$textSecondary'},{text:'禁用'}],
  ['reviewer001','reviewer001@example.com',{pill:'审核员',tone:'live'},{pill:'启用',tone:'live'},{text:'2026-01-15 09:22',col:'$textSecondary'},{text:'禁用'}],
  ['thirdparty001','thirdparty001@example.com',{pill:'第三方',tone:'warn'},{pill:'启用',tone:'live'},{text:'2026-03-12 10:40',col:'$textSecondary'},{text:'禁用'}]
 ]});
PAGER(tb,{count:'共 5 条 · 每页 10 条'});`,
  },
  // idx 24 -> 25
  {
    name: '25 碳核算管理',
    body: `
main=SCREEN({name:'25 碳核算管理',x:PX,y:PY,navItems:NAV_ADM,tab:1,tabs:TABS_ADM,user:U_ADM.user,role:U_ADM.role,initial:U_ADM.initial,live:U_ADM.live,nav:1});
PAGEHEAD(main,{crumbs:['系统管理','碳核算管理'],title:'碳核算管理',sub:'全平台碳报告总账的只读监管视图，用于核对数量与状态分布。',
 actions:[{l:'导出总账',i:'download',kind:'ghost'}]});
stats=ROW(main,{gap:12});
STAT(stats,{l:'报告总数',v:'1,284',u:'份',i:'files'});
STAT(stats,{l:'待审核',v:'37',u:'份',i:'clock'});
STAT(stats,{l:'已通过',v:'986',u:'份',i:'check-check',tone:'live'});
STAT(stats,{l:'已上链',v:'41',u:'份',i:'blocks',tone:'live'});
NOTE(main,{i:'info',c:'本页为只读监管视图，不提供编辑与删除入口；如需修正数据请联系对应企业重新提交。'});
filt=ROW(main,{gap:10,align:'end'});
FIELD(filt,{l:'关键词',ph:'报告编号 / 企业名称 / 报告标题',w:340,i:'search'});
FIELD(filt,{l:'状态',v:'全部',w:150,i:'chevron-down'});
BTN(filt,{l:'查询',kind:'primary'});
BTN(filt,{l:'重置',kind:'ghost'});
tbl=PANEL(main,{n:'CarbonPanel',t:'碳核算报告总账',i:'files',meta:'共 1,284 份 · 按创建时间倒序'});
tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,cols:[{l:'报告编号',w:140},{l:'企业名称',w:230},{l:'报告标题'},{l:'总排放量',w:140,a:'r'},{l:'状态',w:104},{l:'审核员',w:100},{l:'创建时间',w:180}],
 rows:[
  ['RPT-2024-001','绿色能源科技有限公司','2024-Q1 碳报告',{mono:'1,700.00'},{pill:'审核通过',tone:'info'},'王审核',{text:'2026-05-31 09:00',col:'$textSecondary'}],
  ['RPT-2026-010','绿色能源科技有限公司','2026 年第一季度排放报告',{mono:'370.50'},{pill:'已提交',tone:'warn'},'—',{text:'2026-05-31 08:12',col:'$textSecondary'}],
  ['RPT-2026-009','低碳制造股份有限公司','2026 年 3 月月度核算',{mono:'128.40'},{pill:'审核通过',tone:'info'},'王审核',{text:'2026-05-28 16:40',col:'$textSecondary'}],
  ['RPT-2026-008','绿色能源科技有限公司','2026 年 2 月月度核算',{mono:'141.20'},{pill:'已上链',tone:'live'},'李审核',{text:'2026-05-20 09:05',col:'$textSecondary'}],
  ['RPT-2026-007','绿色能源科技有限公司','2026 年第一季度补充核算',{mono:'96.80'},{pill:'审核拒绝',tone:'danger'},'王审核',{text:'2026-05-18 11:22',col:'$textSecondary'}]
 ]});
PAGER(tb,{count:'共 1,284 条 · 每页 10 条'});`,
  },
  // idx 25 -> 26
  {
    name: '26 系统配置',
    body: `
main=SCREEN({name:'26 系统配置',x:PX,y:PY,navItems:NAV_ADM,tab:0,tabs:TABS_ADM,user:U_ADM.user,role:U_ADM.role,initial:U_ADM.initial,live:U_ADM.live,nav:2});
PAGEHEAD(main,{crumbs:['系统管理','系统配置'],title:'系统配置',sub:'平台底层参数的只读视图；参数由后端配置文件下发，前端不提供修改入口。',
 actions:[{l:'刷新',i:'refresh-cw',kind:'ghost'}]});
NOTE(main,{i:'lock',c:'配置项只读。修改需在服务端 application.yml 或环境变量中调整，重启后端后生效。'});
filt=ROW(main,{gap:10,align:'end'});
FIELD(filt,{l:'设置描述',ph:'按描述检索',w:260,i:'search'});
FIELD(filt,{l:'设置名称',ph:'如 enableBlockChain',w:260});
BTN(filt,{l:'查询',kind:'primary'});
BTN(filt,{l:'重置',kind:'ghost'});
tbl=PANEL(main,{n:'CfgPanel',t:'配置项列表',i:'settings-2',meta:'共 6 条'});
tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,rh:48,cols:[{l:'设置描述',w:260},{l:'设置名称',w:260},{l:'设置值'},{l:'修改人',w:120}],
 rows:[
  ['系统展示名称','systemName',{mono:'双碳链动系统'},'后端'],
  ['区块链集成开关','enableBlockChain',{pill:'true',tone:'live'},'后端'],
  ['最大上传体积','maxUploadSize',{mono:'50MB'},'后端'],
  ['会话超时时长（分钟）','sessionTimeout',{mono:'120'},'后端'],
  ['登录验证码开关','enableCaptcha',{pill:'true',tone:'live'},'后端'],
  ['当前平台版本','version',{mono:'v1.4.0'},'后端']
 ]});
side=ROW(main,{gap:14});
sec1=PANEL(side,{n:'SecPanel',t:'安全相关配置',i:'shield-check',meta:'生效中'});
s1=PANEL_BODY(sec1,{pad:[16,18],gap:10});
T(s1,{n:'S',c:'会话超时 120 分钟；登录验证码开启；上传体积上限 50MB。',fs:12,col:C.t2,w:520,lh:1.7});
CALLOUT(s1,{tone:'live',i:'check-circle-2',t:'三项安全配置均为推荐值',c:'若需放宽上传体积，请同步调整网关与对象存储的限额。'});
sec2=PANEL(side,{n:'ChainPanel',t:'区块链相关配置',i:'blocks'});
s2=PANEL_BODY(sec2,{pad:[16,18],gap:10});
KV(s2,{k:'集成开关',v:'true',col:C.acc});
KV(s2,{k:'通道名称',v:'mychannel'});
KV(s2,{k:'链码名称',v:'carbon-chaincode'});
CALLOUT(s2,{tone:'warn',i:'triangle-alert',t:'关闭集成开关将停止自动上链',c:'已通过审核的报告会停留在「待认证」状态，需重新开启后手动补救。'});`,
  },
  // idx 26 -> 27
  {
    name: '27 统计数据',
    body: `
main=SCREEN({name:'27 统计数据',x:PX,y:PY,navItems:NAV_ADM,tab:1,tabs:TABS_ADM,user:U_ADM.user,role:U_ADM.role,initial:U_ADM.initial,live:U_ADM.live,nav:3});
PAGEHEAD(main,{crumbs:['数据管理','统计数据'],title:'统计数据',sub:'全平台用户规模与角色构成大盘，数据每次进入页面实时聚合。',
 actions:[{l:'导出统计快照',i:'download',kind:'ghost'}]});
stats=ROW(main,{gap:12});
STAT(stats,{l:'总用户数',v:'5',u:'个',i:'users',d:'含管理员 1 个'});
STAT(stats,{l:'企业数量',v:'3',u:'个',i:'building-2'});
STAT(stats,{l:'审核员数量',v:'1',u:'个',i:'shield-check'});
STAT(stats,{l:'第三方数量',v:'1',u:'个',i:'scan-eye'});
grid=ROW(main,{gap:14});
donut=PANEL(grid,{n:'DonutPanel',t:'用户类型分布',i:'chart-pie',meta:'按角色口径统计'});
db=PANEL_BODY(donut,{pad:[16,18],gap:14});
PROPBAR(db,{w:560,unit:' 个',segs:[{l:'企业',v:'3',c:C.acc},{l:'审核员',v:'1',c:'$chart2'},{l:'第三方',v:'1',c:'$chart3'}]});
H(db,{n:'D',h:1,col:C.line});
T(db,{n:'Sum',c:'合计 5 个账号；管理员按「总数 − 企业 − 审核员 − 第三方」推算，当前为 0，故不计入分布。',fs:11.5,col:C.t3,w:520,lh:1.6});
side=COL(grid,{w:460,gap:14});
health=PANEL(side,{n:'HealthPanel',t:'平台运行概况',i:'activity',meta:'最近 7 天'});
hb=PANEL_BODY(health,{pad:[16,18],gap:12});
[['接口可用性','99.96%',0.9996],['日均活跃账号','4.2 个',0.84],['报告提交量周环比','+12.4%',0.62],['平均审核时长','4.2 小时',0.58]].forEach(x=>{
 METER(hb,{l:x[0],v:x[1],pct:x[2],w:380});
});
note=PANEL(side,{n:'NotePanel',t:'口径说明',i:'info'});
nb=PANEL_BODY(note,{pad:[16,18],gap:10});
T(nb,{n:'S',c:'管理员数量按「总数 − 企业 − 审核员 − 第三方」推算，仅在大于零时计入分布。',fs:12,col:C.t2,w:380,lh:1.7});
CALLOUT(nb,{tone:'info',i:'info',t:'统计实时聚合，不做缓存',c:'页面刷新即重新查询用户表，因此与账号管理页的数字始终一致。'});`,
  },
  // idx 27 -> 28
  {
    name: '28 证书管理',
    body: `
main=SCREEN({name:'28 证书管理',x:PX,y:PY,navItems:NAV_ADM,tab:2,tabs:TABS_ADM,user:U_ADM.user,role:U_ADM.role,initial:U_ADM.initial,live:U_ADM.live,nav:5});
PAGEHEAD(main,{crumbs:['认证管理','证书管理'],title:'证书管理',sub:'为企业签发准入证书、为审核员签发资格证；吊销后立即生效且不可恢复。',
 actions:[{l:'签发证书',i:'plus',kind:'primary'}]});
stats=ROW(main,{gap:12});
STAT(stats,{l:'准入证书',v:'3',u:'张',i:'file-badge',d:'有效 3 · 已吊销 0'});
STAT(stats,{l:'审核员资格证',v:'1',u:'张',i:'badge-check',d:'有效 1 · 已吊销 0'});
STAT(stats,{l:'本年签发',v:'4',u:'张',i:'file-plus'});
STAT(stats,{l:'本年吊销',v:'0',u:'张',i:'ban'});
TABS(main,{on:0,items:['准入证书','审核员资格证']});
tbl=PANEL(main,{n:'CertPanel',t:'准入证书',i:'file-badge',meta:'共 3 张 · 按签发日期倒序',
 right:[{l:'签发准入证书',kind:'primary',i:'plus'}]});
tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,cols:[{l:'ID',w:70,a:'r'},{l:'企业 ID',w:100,a:'r'},{l:'证书编号',w:170},{l:'签发日期',w:150},{l:'状态',w:110},{l:'创建时间',w:170},{l:'操作',w:100,a:'r'}],
 rows:[
  [{mono:'1'},{mono:'1'},{link:'CERT-2026-001'},{text:'2024-01-15',col:'$textSecondary'},{pill:'有效',tone:'live'},{text:'2026-01-15 10:00',col:'$textSecondary'},{text:'吊销'}],
  [{mono:'2'},{mono:'2'},{link:'CERT-2026-002'},{text:'2026-01-15',col:'$textSecondary'},{pill:'有效',tone:'live'},{text:'2026-01-15 10:05',col:'$textSecondary'},{text:'吊销'}],
  [{mono:'3'},{mono:'3'},{link:'CERT-2026-003'},{text:'2026-02-08',col:'$textSecondary'},{pill:'有效',tone:'live'},{text:'2026-02-08 14:10',col:'$textSecondary'},{text:'吊销'}]
 ]});
side=ROW(main,{gap:14});
issued=PANEL(side,{n:'IssuedPanel',t:'审核员资格证',i:'badge-check',meta:'共 1 张'});
ib=PANEL_BODY(issued,{pad:[16,18],gap:10});
KV(ib,{k:'审核员 ID',v:'1'});
KV(ib,{k:'证书编号',v:'CERT-2026-001'});
KV(ib,{k:'资质类型',v:'碳核算审核员'});
KV(ib,{k:'签发机构',v:'中国碳排放权交易协会'});
KV(ib,{k:'状态',v:'有效',col:C.acc});
warn=PANEL(side,{n:'WarnPanel',t:'签发与吊销须知',i:'triangle-alert'});
wb=PANEL_BODY(warn,{pad:[16,18],gap:10});
CALLOUT(wb,{tone:'danger',i:'circle-alert',t:'吊销操作不可撤销',c:'吊销后企业将立即失去报告提交与交易权限，需重新签发才能恢复。'});
CALLOUT(wb,{tone:'warn',i:'triangle-alert',t:'签发前确认企业资质',c:'准入证书仅可签发给信誉排名前 50 的企业，且每家企业同时只能持有一张有效证书。'});`,
  },
];
