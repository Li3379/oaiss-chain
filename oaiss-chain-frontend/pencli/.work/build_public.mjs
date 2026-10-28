// Canonical page builder: all 28 screens, two-column grid, one page per execute call.
import { HELPERS_SRC } from './lib.js';

export const GRID = (i) => ({ x: (i % 2) * 1480, y: Math.floor(i / 2) * 980 });

export const PAGES = [
  // 0
  {
    name: '01 碳核算填报', group: 'enterprise',
    body: `
main=SCREEN({name:'01 碳核算填报',x:PX,y:PY,navItems:NAV_ENT,tab:0,tabs:TABS_ENT,user:U_ENT.user,role:U_ENT.role,initial:U_ENT.initial,live:U_ENT.live,nav:0});
PAGEHEAD(main,{crumbs:['碳中和','碳核算'],title:'碳核算填报',sub:'编制排放报告、附凭证并送审；审核通过后由系统自动写入 Fabric 存证。',
 actions:[{l:'导出报表',i:'download'},{l:'创建报告',i:'plus',kind:'primary'}]});
stats=ROW(main,{gap:12});
STAT(stats,{l:'本年度已报排放',v:'12,480.6',u:'tCO₂e',i:'factory',d:'较上年度 −4.2%',dtone:'up'});
STAT(stats,{l:'待审核报告',v:'3',u:'份',i:'clock',d:'最早提交 2026-05-28'});
STAT(stats,{l:'已上链存证',v:'41',u:'份',i:'blocks',d:'最近存证 2 小时前'});
STAT(stats,{l:'配额余额',v:'38,000',u:'tCO₂e',i:'wallet',d:'使用率 24.00%'});
filt=F(main,{n:'Filters',dir:'horizontal',w:'fill_container',gap:10,align:'end'});
FIELD(filt,{l:'报告标题',ph:'按标题检索',w:240,i:'search'});
FIELD(filt,{l:'核算周期',ph:'如 2026-Q1',w:170});
FIELD(filt,{l:'状态',ph:'全部状态',w:150,i:'chevron-down'});
BTN(filt,{l:'查询',kind:'primary'});
BTN(filt,{l:'重置',kind:'ghost'});
tbl=PANEL(main,{n:'ReportsPanel',t:'报告列表',i:'files',meta:'共 8 份 · 按创建时间倒序',right:[{l:'批量送审',kind:'ghost',i:'send'}]});
tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,cols:[
 {l:'报告编号',w:140},{l:'报告标题'},{l:'核算周期',w:104},{l:'总排放量',w:140,a:'r'},{l:'状态',w:104},{l:'签名',w:92},{l:'审核人',w:86},{l:'创建时间',w:126},{l:'操作',w:92,a:'r'}],
 rows:[
  ['RPT-2026-010','2026 年第一季度排放报告','2026-Q1',{mono:'370.5'},{pill:'审核中',tone:'warn'},{pill:'已签名',tone:'live'},'王审核','2026-05-31 08:12',{text:'查看详情'}],
  ['RPT-2026-009','2026 年 3 月月度核算',{text:'2026-03',col:'$textSecondary'},{mono:'128.4'},{pill:'审核通过',tone:'info'},{pill:'已签名',tone:'live'},'王审核','2026-05-28 16:40',{text:'查看详情'}],
  ['RPT-2026-008','2026 年 2 月月度核算',{text:'2026-02',col:'$textSecondary'},{mono:'141.2'},{pill:'已上链',tone:'live'},{pill:'已签名',tone:'live'},'李审核','2026-05-20 09:05',{text:'查看详情'}],
  ['RPT-2026-007','2026 年第一季度补充核算',{text:'2026-Q1',col:'$textSecondary'},{mono:'96.8'},{pill:'审核拒绝',tone:'danger'},{pill:'已签名',tone:'live'},'王审核','2026-05-18 11:22',{text:'查看详情'}],
  ['RPT-2026-006','2026 年 1 月月度核算',{text:'2026-01',col:'$textSecondary'},{mono:'134.5'},{pill:'已上链',tone:'live'},{pill:'已签名',tone:'live'},'李审核','2026-05-10 14:31',{text:'查看详情'}],
  ['RPT-2026-005','2025 年度排放报告',{text:'2025-Y',col:'$textSecondary'},{mono:'3,162.0'},{pill:'已上链',tone:'live'},{pill:'已签名',tone:'live'},'王审核','2026-04-02 10:00',{text:'查看详情'}]
 ]});
PAGER(tb,{count:'共 8 条 · 每页 10 条'});`,
  },
  // 1
  {
    name: '02 官方网站首页', group: 'public',
    body: `
P=Insert(document,{type:'frame',name:'02 官方网站首页',x:PX,y:PY,width:1440,height:900,fill:'$bg',layout:'vertical'});
nav=F(P,{n:'Nav',dir:'horizontal',w:'fill_container',h:64,pad:[0,40],align:'center',gap:28,fill:'$shellTop',stroke:C.line,sw:{bottom:1}});
brand=F(nav,{n:'Brand',dir:'horizontal',gap:10,align:'center',w:250});
mark=F(brand,{n:'Mark',dir:'horizontal',w:26,h:26,just:'center',align:'center',rad:2,fill:C.acc});
I(mark,{n:'Glyph',i:'blocks',s:16,col:'$onAccent'});
T(brand,{n:'Word',c:'双碳链动系统',fs:14,fw:'600',col:C.t1});
T(brand,{n:'En',c:'OAISS',fs:9,mono:true,col:C.t3,ls:1.2});
links=F(nav,{n:'Links',dir:'horizontal',gap:26,align:'center',w:400});
['能力','流程','区块链','数据','关于'].forEach((l,i)=>T(links,{n:'L',c:l,fs:13,col:i===0?C.t1:C.t2}));
F(nav,{n:'Spacer',dir:'horizontal',w:'fill_container',h:1,fill:'#00000000'});
BTN(nav,{l:'监管入口',kind:'ghost'});
BTN(nav,{l:'进入系统',kind:'primary',i:'arrow-right'});
hero=F(P,{n:'Hero',dir:'horizontal',w:'fill_container',h:436,pad:[0,40],gap:60,align:'center',fill:'$bg'});
hl=F(hero,{n:'HeroL',dir:'vertical',w:600,gap:20});
kick=F(hl,{n:'Kick',dir:'horizontal',gap:8,align:'center',w:340});
E(kick,{n:'Led',w:6,h:6,fill:C.acc});
T(kick,{n:'L',c:'Fabric 联盟链 · 已存证 1,284 个区块',fs:11.5,mono:true,col:C.t2,w:325});
T(hl,{n:'H1',c:'把每一吨碳，写成不可篡改的一条记录',fs:42,fw:'700',col:C.t1,lh:1.24,w:560});
T(hl,{n:'Sub',c:'面向重点排放企业的碳核算、配额交易与中和存证平台。填报有据、审核留痕、上链可溯。',fs:14,col:C.t2,lh:1.8,w:540});
cta=F(hl,{n:'CTA',dir:'horizontal',gap:12,align:'center',w:360});
BTN(cta,{l:'进入系统',kind:'primary',i:'arrow-right',h:42,pad:20});
BTN(cta,{l:'查看区块链浏览器',h:42,pad:18});
met=F(hl,{n:'HeroMetrics',dir:'horizontal',gap:0,align:'center',w:420});
[['1,284','已存证区块'],['41','已上链报告'],['4','角色协同']].forEach((m,i)=>{
  if(i>0)V(met,{w:1,h:30,col:C.line});
  c=F(met,{n:'M',dir:'vertical',gap:3,pad:[0,i===0?0:18],w:120});
  T(c,{n:'V',c:m[0],fs:21,fw:'500',mono:true,col:C.t1});
  T(c,{n:'L',c:m[1],fs:11,col:C.t3});
});
hr=F(hero,{n:'HeroR',dir:'vertical',w:600,h:340,gap:0,fill:'$surface',stroke:C.line,sw:1,rad:3});
hrh=F(hr,{n:'Head',dir:'horizontal',w:'fill_container',h:44,pad:[0,16],align:'center',just:'space_between',fill:'$surfaceSunken',stroke:C.line,sw:{bottom:1}});
hl2=F(hrh,{n:'L',dir:'horizontal',gap:8,align:'center',w:330});
E(hl2,{n:'Led',w:6,h:6,fill:C.acc});
T(hl2,{n:'T',c:'通道 mychannel · 实时区块流',fs:11,mono:true,col:C.t2,w:300});
PILL(hrh,{l:'运行中',tone:'live'});
rows=F(hr,{n:'Rows',dir:'vertical',w:'fill_container',gap:0});
[['#1,284','RPT-2026-009 · 审核通过','2 分钟前',C.acc],
 ['#1,283','RPT-2026-008 · 已上链','18 分钟前',C.acc],
 ['#1,282','TRX-2026-117 · 配额转让','41 分钟前',C.t2],
 ['#1,281','RPT-2026-007 · 审核拒绝','1 小时前',C.red],
 ['#1,280','PRJ-2026-004 · 监测数据更新','2 小时前',C.t2]].forEach((r,i)=>{
  g=F(rows,{n:'Ev',dir:'horizontal',w:'fill_container',h:52,pad:[0,16],align:'center',gap:12,stroke:C.lineSoft,sw:{bottom:1}});
  T(g,{n:'H',c:r[0],fs:12,mono:true,col:C.t3,w:64});
  E(g,{n:'Dot',w:5,h:5,fill:r[3]});
  T(g,{n:'D',c:r[1],fs:12.5,col:C.t1,w:360});
  T(g,{n:'T',c:r[2],fs:11,mono:true,col:C.t3,w:90});
});
caps=F(P,{n:'Capabilities',dir:'vertical',w:'fill_container',pad:[26,40],gap:20,fill:'$shell'});
ch=F(caps,{n:'CapHead',dir:'horizontal',w:'fill_container',just:'space_between',align:'end'});
chl=F(ch,{n:'L',dir:'vertical',gap:6,w:700});
T(chl,{n:'H2',c:'四个角色，一条闭环',fs:21,fw:'600',col:C.t1,w:260});
T(chl,{n:'S',c:'企业填报、审核核证、监管穿透、管理配置，全部落在同一条链上。',fs:12.5,col:C.t2,w:560});
capgrid=F(caps,{n:'Grid',dir:'horizontal',w:'fill_container',gap:0,align:'start'});
[['企业端','file-pen-line','填排放、传凭证、签报告','RSA-2048 报告签名'],
 ['审核端','shield-check','验签、核证、驳回扣分','AI 预审辅助'],
 ['监管端','scan-eye','辖区报告穿透与统计','只读监督'],
 ['管理端','settings-2','用户、证书、链上补救','批量上链']].forEach((c,i)=>{
  card=F(capgrid,{n:'Cap',dir:'vertical',w:330,pad:[0,i===0?0:22],gap:9,stroke:C.line,sw:{left:i>0?1:0}});
  I(card,{n:'Ico',i:c[1],s:19,col:C.acc});
  T(card,{n:'T',c:c[0],fs:14,fw:'500',col:C.t1,w:120});
  T(card,{n:'D',c:c[2],fs:12,col:C.t2,lh:1.6,w:290});
  CAPS(card,{c:c[3],fs:9.5,col:C.t3,w:220});
});
foot=F(P,{n:'Foot',dir:'horizontal',w:'fill_container',h:'fill_container',pad:[0,40],align:'center',just:'space_between',stroke:C.line,sw:{top:1}});
T(foot,{n:'C',c:'© 2026 双碳链动系统 · 中国碳排放权注册登记机构',fs:11,col:C.t3,w:420});
fr=F(foot,{n:'FR',dir:'horizontal',gap:22,align:'center',w:280});
['服务协议','隐私政策','联系方式'].forEach(l=>T(fr,{n:'L',c:l,fs:11,col:C.t3,w:56}));`,
  },
  // 2
  {
    name: '03 登录', group: 'public',
    body: `
P=Insert(document,{type:'frame',name:'03 登录',x:PX,y:PY,width:1440,height:900,fill:'$bg',layout:'horizontal'});
left=F(P,{n:'Aside',dir:'vertical',w:760,h:900,pad:[44,52],gap:0,just:'space_between',fill:'$shell',stroke:C.line,sw:{right:1}});
lb=F(left,{n:'Brand',dir:'horizontal',gap:11,align:'center',w:300});
lm=F(lb,{n:'Mark',dir:'horizontal',w:28,h:28,just:'center',align:'center',rad:2,fill:C.acc});
I(lm,{n:'G',i:'blocks',s:17,col:'$onAccent'});
lc=F(lb,{n:'Col',dir:'vertical',gap:1,w:200});
T(lc,{n:'W',c:'双碳链动系统',fs:14,fw:'600',col:C.t1,w:130});
T(lc,{n:'S',c:'OAISS CHAIN PLATFORM',fs:8.5,mono:true,col:C.t3,ls:1.4,w:190});
mid=F(left,{n:'Mid',dir:'vertical',w:'fill_container',gap:18,just:'center'});
T(mid,{n:'H',c:'每一次提交，都留下可验证的痕迹。',fs:32,fw:'600',col:C.t1,lh:1.34,w:560});
T(mid,{n:'P',c:'报告经企业私钥签名后进入审核，核证通过即自动写入联盟链。任何一次改动都会留下新版本，旧版本不会被覆盖。',fs:13,col:C.t2,lh:1.85,w:520});
proof=F(mid,{n:'Proof',dir:'vertical',w:520,gap:0,fill:'$surfaceSunken',stroke:C.line,sw:1,rad:2});
ph=F(proof,{n:'PrHead',dir:'horizontal',w:'fill_container',h:38,pad:[0,14],align:'center',just:'space_between',stroke:C.line,sw:{bottom:1}});
CAPS(ph,{c:'最近一次存证',fs:9.5,w:110});
T(ph,{n:'T',c:'区块 #1,284 · 2 分钟前',fs:10.5,mono:true,col:C.t3,w:230});
pb=F(proof,{n:'PrBody',dir:'vertical',w:'fill_container',pad:[13,14],gap:7});
T(pb,{n:'K',c:'交易哈希',fs:10,col:C.t3,w:90});
T(pb,{n:'V',c:'9f2c7d51a8e34b06c1d9f7e2b5a04c8d13e6f92a7b4c0d58e1f3a6b9c2d7e40f',fs:11,mono:true,col:C.acc,w:490,lh:1.75});
foot=F(left,{n:'Foot',dir:'horizontal',w:'fill_container',just:'space_between',align:'center'});
T(foot,{n:'C',c:'© 2026 双碳链动 · 中国碳排放权注册登记机构',fs:11,col:C.t3,w:420});
T(foot,{n:'V',c:'v1.4.0',fs:10.5,mono:true,col:C.t3,w:50});
right=F(P,{n:'FormCol',dir:'vertical',w:'fill_container',h:900,pad:[0,140],gap:0,just:'center',align:'center'});
form=F(right,{n:'Card',dir:'vertical',w:392,gap:16});
T(form,{n:'H',c:'登录',fs:26,fw:'600',col:C.t1,w:120});
T(form,{n:'S',c:'使用企业账号或监管机构账号进入系统。',fs:12.5,col:C.t2,w:340});
FIELD(form,{l:'用户名',v:'enterprise001',i:'user-round'});
FIELD(form,{l:'密码',v:'••••••••••',i:'lock'});
capt=F(form,{n:'Captcha',dir:'horizontal',w:'fill_container',gap:10,align:'end'});
FIELD(capt,{l:'验证码',ph:'请输入右侧四位',w:230,i:'shield-question'});
capbox=F(capt,{n:'CapImg',dir:'horizontal',w:112,h:36,just:'center',align:'center',fill:'$surfaceSunken',stroke:C.line,sw:1,rad:2});
T(capbox,{n:'C',c:'65NH',fs:15,mono:true,col:C.acc,fw:'500',ls:4,w:70});
opt=F(form,{n:'Opts',dir:'horizontal',w:'fill_container',just:'space_between',align:'center'});
cob=F(opt,{n:'Remember',dir:'horizontal',gap:7,align:'center',w:130});
R(cob,{n:'Box',w:13,h:13,fill:C.acc,rad:1});
I(cob,{n:'Tick',i:'check',s:10,col:'$onAccent'});
T(cob,{n:'L',c:'记住账号',fs:12,col:C.t2,w:70});
T(opt,{n:'Forgot',c:'忘记密码？',fs:12,col:C.t3,w:80});
BTN(form,{l:'进入系统',kind:'primary',h:42,w:'fill_container'});
hint=F(form,{n:'Hint',dir:'horizontal',w:'fill_container',pad:[10,12],gap:8,align:'start',fill:'$noteBg',stroke:'$noteBd',sw:1,rad:2});
I(hint,{n:'I',i:'info',s:13,col:C.acc});
T(hint,{n:'L',c:'首次登录请使用管理员分配的初始账号；登录后前往「个人中心」生成 RSA 密钥对，用于报告签名。',fs:11.5,col:C.t2,w:330,lh:1.6});`,
  },
];
