// Build a style-comparison board: the same "碳核算填报" screen rendered 4× with different themes.
import { HELPERS_SRC } from './lib.js';
import { THEMES } from './themes.mjs';
import { execute } from './pen.mjs';

const CARD_W = 1440, CARD_H = 900, GAP = 60;

// One screen body, parameterised by theme tokens injected as variables.
const screen = (t, x, y) => `

const UI='${t.font}', MONO='${t.mono}';
const BG='${t.bg}', SURF='${t.surface}', RAISED='${t.raised}', LINE='${t.border}';
const T1='${t.text}', T2='${t.muted}', T3='${t.faint}';
const ACC='${t.accent}', ONACC='${t.onAccent}', CHART='${t.chart}';
const POSBG='${t.posBg}', POSINK='${t.posInk}', NEGBG='${t.negBg}', NEGINK='${t.negInk}';
const WARNBG='${t.warnBg}', WARNINK='${t.warnInk}', INFOBG='${t.infoBg}', INFOINK='${t.infoInk}';
const RAD=${t.radius};

function FR(p,o){const n={type:'frame',name:o.n||'F',layout:o.dir||'vertical'};
 if(o.w!=null)n.width=o.w; if(o.h!=null)n.height=o.h;
 if(o.x!=null)n.x=o.x; if(o.y!=null)n.y=o.y;
 if(o.fill!==undefined)n.fill=o.fill;
 if(o.stroke!==undefined)n.stroke=o.stroke; if(o.sw!=null)n.strokeWidth=o.sw;
 if(o.pad!=null)n.padding=o.pad; if(o.gap!=null)n.gap=o.gap;
 if(o.just)n.justifyContent=o.just; if(o.align)n.alignItems=o.align;
 if(o.rad!=null)n.cornerRadius=o.rad;
 return Insert(p,n);}
function TX(p,o){const n={type:'text',name:o.n||'T',content:String(o.c),fontFamily:o.mono?MONO:UI,
 fontSize:o.fs||13,fill:o.col||T1}; if(o.w!=null){n.textGrowth='fixed-width';n.width=o.w;}
 if(o.fw)n.fontWeight=String(o.fw); if(o.lh)n.lineHeight=o.lh; return Insert(p,n);}
function RC(p,o){const n={type:'rectangle',name:o.n||'R',width:o.w,height:o.h,fill:o.fill||LINE};
 if(o.rad!=null)n.cornerRadius=o.rad; return Insert(p,n);}
function SL(p,o){return FR(p,{n:o.n||'line',dir:'horizontal',w:o.w||'fill_container',h:o.h||1,fill:o.fill||LINE});}
function CHIP(p,o){
 const f=FR(p,{n:'Chip',dir:'horizontal',w:'fit_content',h:22,pad:[3,8],gap:5,align:'center',fill:o.bg,rad:RAD-2});
 RC(f,{n:'dot',w:5,h:5,fill:o.ink,rad:3});
 TX(f,{n:'l',c:o.l,fs:11,col:o.ink,fw:'500'});
 return f;}
function BT(p,o){
 const primary=o.kind==='primary';
 const f=FR(p,{n:'Btn',dir:'horizontal',w:o.w||'fit_content',h:34,pad:[0,14],gap:6,just:'center',align:'center',rad:RAD-2,
  fill:primary?ACC:RAISED,stroke:primary?'#00000000':LINE,sw:1});
 TX(f,{n:'l',c:o.l,fs:13,fw:primary?'500':'400',col:primary?ONACC:T1,w:String(o.l).length*13+6});
 return f;}
function FLD(p,o){
 const f=FR(p,{n:'Field',dir:'vertical',w:o.w||'fill_container',gap:6});
 TX(f,{n:'lb',c:o.l,fs:11,col:T3});
 const b=FR(f,{n:'box',dir:'horizontal',w:'fill_container',h:34,pad:[0,10],align:'center',fill:SURF,stroke:LINE,sw:1,rad:RAD-2});
 TX(b,{n:'v',c:o.v||o.ph||'',fs:13,col:o.v?T1:T3,w:'fill_container'});
 return f;}
function KPI(p,o){
 const f=FR(p,{n:'KPI',dir:'vertical',w:'fill_container',h:88,pad:[12,14],gap:5,just:'space_between',fill:SURF,stroke:LINE,sw:1,rad:RAD});
 TX(f,{n:'l',c:o.l,fs:11,col:T3,w:'fill_container'});
 const r=FR(f,{n:'r',dir:'horizontal',w:'fill_container',gap:5,align:'end'});
 TX(r,{n:'v',c:o.v,fs:25,fw:'500',mono:true,col:o.col||T1});
 if(o.u)TX(r,{n:'u',c:o.u,fs:11,col:T3});
 if(o.d)TX(f,{n:'d',c:o.d,fs:11,col:o.dc||T3,w:'fill_container'});
 return f;}
function PNL(p,o){
 const f=FR(p,{n:o.n||'Panel',dir:'vertical',w:o.w||'fill_container',gap:0,fill:SURF,stroke:LINE,sw:1,rad:RAD});
 const hd=FR(f,{n:'head',dir:'horizontal',w:'fill_container',h:42,pad:[0,14],just:'space_between',align:'center',stroke:LINE,sw:{bottom:1}});
 TX(hd,{n:'t',c:o.t,fs:12,col:T2,fw:'500',w:String(o.t).length*12+20});
 if(o.meta)TX(hd,{n:'m',c:o.meta,fs:11,col:T3,w:String(o.meta).length*11+16});
 return f;}
function ROWT(p,o){
 const hd=FR(p,{n:'th',dir:'horizontal',w:'fill_container',h:34,pad:[0,10],align:'center',fill:RAISED});
 o.cols.forEach(c=>{
  const cell=FR(hd,{n:'c',dir:'horizontal',w:c.w||'fill_container',just:c.r?'end':'start',align:'center',pad:[0,8]});
  TX(cell,{n:'l',c:c.l,fs:11,col:T3,fw:'500'});
 });
 (o.rows||[]).forEach((r,ri)=>{
  const row=FR(p,{n:'tr',dir:'horizontal',w:'fill_container',h:42,pad:[0,10],align:'center',stroke:LINE,sw:{bottom:1}});
  r.forEach((v,ci)=>{
   const c=o.cols[ci];
   const cell=FR(row,{n:'td',dir:'horizontal',w:c.w||'fill_container',just:c.r?'end':'start',align:'center',pad:[0,8]});
   if(v&&v.chip)CHIP(cell,{l:v.chip,bg:v.bg,ink:v.ink});
   else if(v&&v.mono!==undefined)TX(cell,{n:'v',c:v.mono,fs:12.5,mono:true,col:v.col||T1});
   else if(v&&typeof v==='object'&&v.text!==undefined)TX(cell,{n:'v',c:v.text,fs:13,col:T2,w:'fill_container'});
   else if(v!=null&&v!=='')TX(cell,{n:'v',c:String(v),fs:13,col:c.dim?T2:T1,w:'fill_container'});
  });
 });
 return p;}
function BARS(p,o){
 const f=FR(p,{n:'bars',dir:'horizontal',w:'fill_container',h:o.h||120,gap:8,align:'end'});
 const mx=Math.max(...o.data)||1;
 o.data.forEach((d,i)=>{
  const c=FR(f,{n:'col',dir:'vertical',w:'fill_container',h:'fill_container',just:'end',align:'center',gap:6});
  RC(c,{n:'b',w:18,h:Math.max(4,Math.round(d/mx*((o.h||120)-24))),fill:i===o.hi?CHART:RAISED,rad:2});
  TX(c,{n:'x',c:(o.labels||[])[i]||'',fs:9.5,mono:true,col:T3});
 });
 return f;}

// ---------- the screen ----------
P=Insert(document,{type:'frame',name:'主题 ${t.key} · ${t.name}',x:${x},y:${y},width:${CARD_W},height:${CARD_H},fill:BG,layout:'vertical',clip:false});

NAV=FR(P,{n:'Nav',dir:'horizontal',w:'fill_container',h:56,pad:[0,20],align:'center',gap:22,fill:SURF,stroke:LINE,sw:{bottom:1}});
BR=FR(NAV,{n:'Brand',dir:'horizontal',gap:9,align:'center',w:200});
MK=FR(BR,{n:'Mark',dir:'horizontal',w:24,h:24,just:'center',align:'center',rad:RAD-3,fill:ACC});
TX(MK,{n:'g',c:'碳',fs:12,fw:'700',col:ONACC});
TX(BR,{n:'w',c:'双碳链动系统',fs:13,fw:'500',col:T1,w:98});
TB=FR(NAV,{n:'Tabs',dir:'horizontal',gap:20,align:'center',w:'fit_content'});
['碳核算','交易市场','企业资产','数据智能'].forEach((s,i)=>{
 const g=FR(TB,{n:'Tab',dir:'vertical',h:56,just:'end',w:'fit_content'});
 const inn=FR(g,{n:'in',dir:'horizontal',h:36,align:'center',w:'fit_content'});
 TX(inn,{n:'l',c:s,fs:13,col:i===0?T1:T2,fw:i===0?'500':'400'});
 RC(g,{n:'u',w:'fill_container',h:2,fill:i===0?ACC:'#00000000',rad:1});
});
FR(NAV,{n:'sp',dir:'horizontal',w:'fill_container',h:1,fill:'#00000000'});
LV=FR(NAV,{n:'Live',dir:'horizontal',gap:6,align:'center',w:'fit_content'});
RC(LV,{n:'led',w:6,h:6,fill:'${t.pos}',rad:3});
TX(LV,{n:'l',c:'链上同步 · 1,284',fs:11,mono:true,col:T2});
AV=FR(NAV,{n:'Avatar',dir:'horizontal',w:26,h:26,just:'center',align:'center',rad:RAD-3,fill:RAISED});
TX(AV,{n:'i',c:'李',fs:11,fw:'500',col:T2});

BODY=FR(P,{n:'Body',dir:'horizontal',w:'fill_container',h:'fill_container'});
RAIL=FR(BODY,{n:'Rail',dir:'vertical',w:64,fill:BG,pad:[12,0],gap:2,align:'center',stroke:LINE,sw:{right:1}});
const ICONS={'填报':'file-pen-line','订单':'receipt-text','拍卖':'gavel','P2P':'arrow-left-right','看板':'layout-dashboard','信誉':'shield-check','碳币':'coins','链':'blocks'};
[['填报',1],['订单',0],['拍卖',0],['P2P',0],['看板',0],['信誉',0],['碳币',0],['链',0]].forEach(([s,on])=>{
 const b=FR(RAIL,{n:'nav',dir:'vertical',w:56,h:52,gap:3,align:'center',just:'center',rad:RAD-2,fill:on?RAISED:'#00000000'});
 if(on)RC(b,{n:'mk',w:2,h:18,x:0,y:17,fill:ACC,rad:1});
 Insert(b,{type:'icon',name:'ic',library:'lucide',icon:ICONS[s]||'circle',width:15,height:15,fill:on?ACC:T3});
 TX(b,{n:'l',c:s,fs:9.5,col:on?T1:T3});
});
MAIN=FR(BODY,{n:'Main',dir:'vertical',w:'fill_container',h:'fill_container',pad:[20,24],gap:14});

CR=FR(MAIN,{n:'Crumb',dir:'horizontal',gap:6,align:'center',w:'fit_content'});
TX(CR,{n:'c',c:'碳中和 / 碳核算',fs:11,col:T3});
HD=FR(MAIN,{n:'Head',dir:'horizontal',w:'fill_container',just:'space_between',align:'end'});
HL=FR(HD,{n:'HL',dir:'vertical',gap:6,w:'fill_container'});
TX(HL,{n:'h1',c:'碳核算填报',fs:24,fw:'500',col:T1});
TX(HL,{n:'s',c:'编制排放报告、附凭证并送审；审核通过后由系统自动写入 Fabric 存证。',fs:12.5,col:T2,w:620});
HA=FR(HD,{n:'HA',dir:'horizontal',gap:8,align:'center',w:'fit_content'});
BT(HA,{l:'导出报表'});
BT(HA,{l:'创建报告',kind:'primary'});

KS=FR(MAIN,{n:'KPIs',dir:'horizontal',w:'fill_container',gap:10});
KPI(KS,{l:'本年度已报排放',v:'12,480.6',u:'tCO₂e',d:'较上年度 −4.2%',dc:'${t.pos}'});
KPI(KS,{l:'待审核报告',v:'3',u:'份',d:'最早提交 2026-05-28'});
KPI(KS,{l:'已上链存证',v:'41',u:'份',d:'最近存证 2 小时前'});
KPI(KS,{l:'配额余额',v:'38,000',u:'tCO₂e',d:'使用率 24.00%'});

FT=FR(MAIN,{n:'Filters',dir:'horizontal',w:'fill_container',gap:10,align:'end'});
FLD(FT,{l:'报告标题',ph:'按标题检索',w:230});
FLD(FT,{l:'核算周期',ph:'如 2026-Q1',w:160});
FLD(FT,{l:'状态',ph:'全部状态',w:150});
BT(FT,{l:'查询',kind:'primary'});

TB2=PNL(MAIN,{n:'Table',t:'报告列表',meta:'共 8 份 · 按创建时间倒序'});
ROWT(TB2,{cols:[{l:'报告编号',w:140},{l:'报告标题'},{l:'核算周期',w:104},{l:'总排放量',w:130,r:1},{l:'状态',w:110},{l:'审核人',w:88},{l:'创建时间',w:126}],
 rows:[
  ['RPT-2026-010','2026 年第一季度排放报告','2026-Q1',{mono:'370.5'},{chip:'审核中',bg:WARNBG,ink:WARNINK},'王审核','2026-05-31 08:12'],
  ['RPT-2026-009','2026 年 3 月月度核算',{text:'2026-03'},{mono:'128.4'},{chip:'审核通过',bg:INFOBG,ink:INFOINK},'王审核','2026-05-28 16:40'],
  ['RPT-2026-008','2026 年 2 月月度核算',{text:'2026-02'},{mono:'141.2'},{chip:'已上链',bg:POSBG,ink:POSINK},'李审核','2026-05-20 09:05'],
  ['RPT-2026-007','2026 年第一季度补充核算',{text:'2026-Q1'},{mono:'96.8'},{chip:'审核拒绝',bg:NEGBG,ink:NEGINK},'王审核','2026-05-18 11:22']
 ]});

// Side-by-side detail row
DT=FR(MAIN,{n:'Detail',dir:'horizontal',w:'fill_container',gap:14});
P1=PNL(DT,{n:'Trend',t:'排放趋势',meta:'近 6 个月',w:'fill_container'});
PB=FR(P1,{n:'body',dir:'vertical',w:'fill_container',pad:[14,16],gap:10});
BARS(PB,{h:110,data:[32,29,34,31,28,26],labels:['1月','2月','3月','4月','5月','6月'],hi:5});
P2=PNL(DT,{n:'Status',t:'状态分布',meta:'本期',w:420});
SB=FR(P2,{n:'body',dir:'vertical',w:'fill_container',pad:[14,16],gap:9});
[['已上链',41,POSBG,POSINK],['审核通过',12,INFOBG,INFOINK],['审核中',3,WARNBG,WARNINK],['已拒绝',6,NEGBG,NEGINK]].forEach(([l,n,bg,ink])=>{
 const r=FR(SB,{n:'st',dir:'horizontal',w:'fill_container',just:'space_between',align:'center'});
 CHIP(r,{l,bg,ink});
 TX(r,{n:'n',c:String(n)+' 份',fs:12,mono:true,col:T1});
});
`;

const jobs = THEMES.map((t, i) => ({
  theme: t,
  code: screen(t, (i % 2) * (CARD_W + GAP), Math.floor(i / 2) * (CARD_H + 140)),
}));

for (const j of jobs) {
  const res = await execute(j.code);
  console.log(`${res.ok ? 'OK  ' : 'FAIL'} ${j.theme.key} ${j.theme.name}`);
  if (!res.ok) console.log('   ', res.text.replace(/\s+/g, ' ').slice(0, 500));
}
