export const HELPERS_SRC = String.raw`
const UI="$fontUi", MONO="$fontMono";
const C={bg:"$bg",surf:"$surface",raised:"$surfaceRaised",line:"$hairline",lineSoft:"$hairline",
 t1:"$textPrimary",t2:"$textSecondary",t3:"$textTertiary",acc:"$accent",red:"$stateDanger",
 cyan:"$stateInfo",amber:"$stateWarn",mute:"$stateNeutral",chart:"$chart1",chart2:"$chart2",chart3:"$chart3",chart4:"$chart4"};
const TONE={
 live:{fg:"$pillPosInk",bg:"$pillPosBg",bd:"#B7E7C6"},
 info:{fg:"$pillInfoInk",bg:"$pillInfoBg",bd:"#C3D6F2"},
 warn:{fg:"$pillWarnInk",bg:"$pillWarnBg",bd:"#F0DCB5"},
 danger:{fg:"$pillNegInk",bg:"$pillNegBg",bd:"#F5C9C4"},
 neutral:{fg:"$pillNeuInk",bg:"$pillNeuBg",bd:"#DDDDDD"}
};
function F(p,o){const n={type:"frame",name:o.n||"Frame",layout:o.dir||"vertical"};
 if(o.w!=null)n.width=o.w; if(o.h!=null)n.height=o.h;
 if(o.x!=null)n.x=o.x; if(o.y!=null)n.y=o.y;
 if(o.fill!==undefined)n.fill=o.fill;
 if(o.stroke!==undefined)n.stroke=o.stroke; if(o.sw!=null)n.strokeWidth=o.sw;
 if(o.pad!=null)n.padding=o.pad; if(o.gap!=null)n.gap=o.gap;
 if(o.just)n.justifyContent=o.just; if(o.align)n.alignItems=o.align;
 if(o.rad!=null)n.cornerRadius=o.rad; if(o.clip)n.clip=true;
 if(o.op!=null)n.opacity=o.op; if(o.abs)n.layoutPosition="absolute";
 if(o.reusable)n.reusable=true;
 if(o.effect)n.effect=o.effect;
 return Insert(p,n);}
function T(p,o){const n={type:"text",name:o.n||"Text",content:String(o.c==null?"":o.c),fontFamily:o.mono?MONO:UI,
 fontSize:o.fs||13,fill:o.col||C.t1,fontWeight:String(o.fw||"400")};
 if(o.lh!=null)n.lineHeight=o.lh;
 if(o.ls!=null)n.letterSpacing=o.ls;
 const w=o.w!=null?o.w:(o.growW?"fill_container":null);
 if(w!=null){n.textGrowth=o.h!=null?"fixed-width-height":"fixed-width";n.width=w;}
 if(o.h!=null)n.height=o.h;
 if(o.ta)n.textAlign=o.ta;
 if(o.op!=null)n.opacity=o.op;
 return Insert(p,n);}
function R(p,o){const n={type:"rectangle",name:o.n||"Rect",width:o.w,height:o.h};
 if(o.x!=null)n.x=o.x; if(o.y!=null)n.y=o.y;
 if(o.fill!==undefined)n.fill=o.fill;
 if(o.stroke!==undefined)n.stroke=o.stroke; if(o.sw!=null)n.strokeWidth=o.sw;
 if(o.rad!=null)n.cornerRadius=o.rad; if(o.op!=null)n.opacity=o.op;
 if(o.abs)n.layoutPosition="absolute"; return Insert(p,n);}
function E(p,o){const n={type:"ellipse",name:o.n||"Ellipse",width:o.w,height:o.h};
 if(o.fill!==undefined)n.fill=o.fill; if(o.stroke!==undefined)n.stroke=o.stroke;
 if(o.sw!=null)n.strokeWidth=o.sw;
 if(o.inner!=null)n.innerRadius=o.inner; if(o.sta!=null)n.startAngle=o.sta;
 if(o.swp!=null)n.sweepAngle=o.swp; if(o.op!=null)n.opacity=o.op;
 return Insert(p,n);}
function I(p,o){const n={type:"icon",name:o.n||"Icon",library:"lucide",icon:o.i,width:o.s||16,height:o.s||16,fill:o.col||C.t2};
 return Insert(p,n);}
function V(p,o){return F(p,{n:o.n||"VLine",dir:"vertical",w:o.w||1,h:o.h,fill:o.col||C.line});}
function H(p,o){return F(p,{n:o.n||"HLine",dir:"horizontal",w:o.w||"fill_container",h:o.h||1,fill:o.col||C.line});}
function CAPS(p,o){return T(p,{n:o.n||"Caps",c:o.c,fs:o.fs||10,fw:"500",col:o.col||C.t3,ls:1.4,w:o.w,lh:1.2});}
function PILL(p,o){const t=TONE[o.tone||"neutral"];
 const f=F(p,{n:"Pill",dir:"horizontal",w:"fit_content",h:o.h||23,pad:o.pad||[3,8],gap:6,align:"center",fill:t.bg,stroke:t.bd,sw:1,rad:2});
 E(f,{n:"Dot",w:5,h:5,fill:t.fg});
 T(f,{n:"Label",c:o.l,fs:11,fw:"500",col:t.fg,ls:0.2,w:(String(o.l).length*11+6)});
 return f;}
function BTN(p,o){const primary=o.kind==="primary",ghost=o.kind==="ghost";
 const f=F(p,{n:"Btn",dir:"horizontal",w:o.w||"fit_content",h:o.h||34,pad:[0,o.pad||14],gap:7,align:"center",just:"center",rad:2,
  fill:primary?C.acc:(ghost?"#00000000":C.raised),stroke:(primary?"#00000000":C.line),sw:1});
 if(o.i)I(f,{n:"Ico",i:o.i,s:14,col:primary?"$onAccent":C.t2});
 T(f,{n:"Label",c:o.l,fs:13,fw:primary?"500":"400",col:primary?"$onAccent":C.t1,w:(String(o.l).length*13+4)});
 return f;}
function FIELD(p,o){const f=F(p,{n:"Field",dir:"vertical",w:o.w||"fill_container",gap:6});
 CAPS(f,{c:o.l,n:"Label",fs:10.5});
 const box=F(f,{n:"Input",dir:"horizontal",w:"fill_container",h:o.h||36,pad:[0,11],align:"center",gap:8,fill:"$surfaceSunken",stroke:C.line,sw:1,rad:2});
 const has=o.v!==undefined&&o.v!=="";
 T(box,{n:"Value",c:has?o.v:(o.ph||""),fs:13,col:has?C.t1:C.t3,w:"fill_container"});
 if(o.i)I(box,{n:"Ico",i:o.i,s:14,col:C.t3});
 return f;}
function STAT(p,o){const f=F(p,{n:"Stat",dir:"vertical",w:o.w||"fill_container",h:o.h||94,pad:[13,15],gap:4,just:"space_between",fill:C.surf,stroke:C.line,sw:1,rad:2});
 const top=F(f,{n:"Top",dir:"horizontal",w:"fill_container",just:"space_between",align:"center"});
 CAPS(top,{c:o.l,n:"Label",fs:10.5});
 if(o.i)I(top,{n:"Ico",i:o.i,s:15,col:C.t3});
 const row=F(f,{n:"ValRow",dir:"horizontal",w:"fill_container",gap:6,align:"end"});
 T(row,{n:"Val",c:o.v,fs:o.fs||25,fw:"500",mono:true,col:o.tone?TONE[o.tone].fg:C.t1,lh:1.1});
 if(o.u)T(row,{n:"Unit",c:o.u,fs:11,col:C.t3});
 if(o.d)T(f,{n:"Delta",c:o.d,fs:11,col:o.dtone==="up"?"$stateSuccess":(o.dtone==="down"?C.red:C.t3),w:"fill_container",lh:1.3});
 return f;}
function PANEL(p,o){const f=F(p,{n:o.n||"Panel",dir:"vertical",w:o.w||"fill_container",h:o.h,gap:0,fill:o.fill||C.surf,stroke:C.line,sw:1,rad:2,clip:true});
 const hd=F(f,{n:"Head",dir:"horizontal",w:"fill_container",h:o.hh||44,pad:[0,o.hp||16],just:"space_between",align:"center",stroke:C.line,sw:{bottom:1}});
 const hl=F(hd,{n:"HeadL",dir:"horizontal",gap:9,align:"center",w:"fill_container"});
 if(o.i)I(hl,{n:"Ico",i:o.i,s:15,col:C.t3});
 CAPS(hl,{c:o.t,n:"Title",fs:10.5,col:C.t2});
 if(o.meta)T(hl,{n:"Meta",c:o.meta,fs:10.5,col:C.t3,ls:0.4});
 if(o.right&&o.right.length){const hr=F(hd,{n:"HeadR",dir:"horizontal",gap:8,align:"center",w:"fit_content"});o.right.forEach(b=>BTN(hr,b));}
 if(o.stat===false)Update(hd,{stroke:"#00000000"});
 return f;}
function SECTION(p,o){const f=F(p,{n:"Section",dir:"horizontal",w:"fill_container",gap:12,align:"center"});
 T(f,{n:"Title",c:o.t,fs:14,fw:"500",col:C.t1});
 H(f,{n:"Rule",w:"fill_container",h:1,col:C.line});
 if(o.meta)CAPS(f,{c:o.meta,n:"Meta"});
 return f;}
function TABLE(p,o){
 const wrap=F(p,{n:"Table",dir:"vertical",w:"fill_container",gap:0});
 const hd=F(wrap,{n:"Head",dir:"horizontal",w:"fill_container",h:34,pad:[0,8],align:"center",fill:"$surfaceSunken",stroke:C.line,sw:{bottom:1}});
 o.cols.forEach(c=>{const cell=F(hd,{n:"Th",dir:"horizontal",w:c.w||"fill_container",just:c.a==="r"?"end":"start",align:"center",pad:[0,8]});
  CAPS(cell,{c:c.l,fs:10});});
 hd2=hd;
 (o.rows||[]).forEach((r,ri)=>{
  const row=F(wrap,{n:"Row",dir:"horizontal",w:"fill_container",h:o.rh||44,pad:[0,8],align:"center",stroke:C.line,sw:{bottom:1},fill:(o.zebra&&ri%2===1)?"$surfaceSunken":"#00000000"});
  r.forEach((v,ci)=>{const c=o.cols[ci];const cell=F(row,{n:"Td",dir:"horizontal",w:c.w||"fill_container",just:c.a==="r"?"end":"start",align:"center",gap:6,pad:[0,8]});
   if(v&&typeof v.pill==='string'){PILL(cell,{l:v.pill,tone:v.tone});}
   else if(v&&v.mono!==undefined){T(cell,{n:"V",c:v.mono,fs:12.5,mono:true,col:v.col||C.t1});}
   else if(v&&v.ico){I(cell,{n:"Ico",i:v.ico,s:13,col:v.col||C.t3});T(cell,{n:"V",c:v.t||"",fs:13,col:v.col||C.t1});}
   else if(v&&Object.prototype.hasOwnProperty.call(v,'link')){T(cell,{n:"V",c:String(v.link),fs:12.5,mono:true,col:C.t2});}
   else if(v&&typeof v==='object'&&v.text!==undefined){T(cell,{n:"V",c:v.text,fs:13,col:v.col||C.t1});}
   else if(v!=null&&v!==""){T(cell,{n:"V",c:String(v),fs:13,col:c.dim?C.t2:C.t1,mono:!!c.mono});}
  });});
 return wrap;}
function PAGER(p,o){const f=F(p,{n:"Pager",dir:"horizontal",w:"fill_container",h:46,pad:[0,16],just:"space_between",align:"center",stroke:C.line,sw:{top:1}});
 T(f,{n:"Count",c:o.count||"共 24 条",fs:12,col:C.t3});
 const r=F(f,{n:"Pages",dir:"horizontal",gap:4,align:"center"});
 ["‹","1","2","3","›"].forEach((n,i)=>{const b=F(r,{n:"Pg",dir:"horizontal",w:26,h:26,just:"center",align:"center",rad:2,
  fill:i===1?C.raised:"#00000000",stroke:i===1?C.line:"#00000000",sw:1});
  T(b,{n:"L",c:n,fs:11.5,mono:true,col:i===1?C.t1:C.t3});});
 return f;}
function RAIL(p,o){
 const rail=F(p,{n:"Rail",dir:"vertical",w:72,h:"fill_container",fill:"$shell",pad:[14,0],gap:3,align:"center",stroke:C.line,sw:{right:1}});
 (o.items||[]).forEach((it,i)=>{
  const on=i===o.active;
  const b=F(rail,{n:"Nav",dir:"vertical",w:64,h:56,gap:5,align:"center",just:"center",rad:2,fill:on?"$navActive":"#00000000"});
  if(on)R(b,{n:"Marker",w:2,h:20,x:0,y:18,fill:C.chart,abs:true,rad:1});
  I(b,{n:"Ico",i:it.i,s:18,col:on?C.t1:C.t3});
  T(b,{n:"L",c:it.l,fs:9.5,col:on?C.t1:C.t3,fw:on?"500":"400"});
 });
 return rail;}
function TOPBAR(p,o){
 const bar=F(p,{n:"TopBar",dir:"horizontal",w:"fill_container",h:56,pad:[0,18],align:"center",gap:16,fill:"$shellTop",stroke:C.line,sw:{bottom:1}});
 const brand=F(bar,{n:"Brand",dir:"horizontal",gap:10,align:"center",w:290});
 const mark=F(brand,{n:"Mark",dir:"horizontal",w:26,h:26,just:"center",align:"center",rad:2,fill:"$brandMark"});
 I(mark,{n:"Glyph",i:"blocks",s:16,col:"$onAccent"});
 const bc=F(brand,{n:"BrandCol",dir:"vertical",gap:0,w:120});
 T(bc,{n:"Word",c:"双碳链动系统",fs:13.5,fw:"600",col:C.t1,ls:0.2});
 T(bc,{n:"Sub",c:"OAISS CHAIN",fs:9,mono:true,col:C.t3,ls:1.2});
 V(brand,{w:1,h:20,col:C.line});
 const tabs=F(bar,{n:"Tabs",dir:"horizontal",gap:20,align:"center",w:320});
 (o.tabs||[]).forEach((t,i)=>{
  const on=i===o.tab;
  const g=F(tabs,{n:"Tab",dir:"vertical",h:56,just:"end",gap:0,w:64});
  const inner=F(g,{n:"Inner",dir:"horizontal",h:38,align:"center",w:"fill_container",just:"center"});
  T(inner,{n:"L",c:t,fs:13,col:on?C.t1:C.t3,fw:on?"500":"400"});
  R(g,{n:"Under",w:"fill_container",h:2,fill:on?C.chart:"#00000000",rad:1});
 });
 F(bar,{n:"Spacer",dir:"horizontal",w:"fill_container",h:1,fill:"#00000000"});
 const live=F(bar,{n:"LiveState",dir:"horizontal",gap:7,align:"center",w:250});
 E(live,{n:"Led",w:6,h:6,fill:"$stateSuccess"});
 T(live,{n:"L",c:o.live||"链上同步 · 区块 1,284",fs:10.5,mono:true,col:C.t2});
 V(live,{w:1,h:16,col:C.line});
 const right=F(bar,{n:"Right",dir:"horizontal",gap:13,align:"center",w:330});
 I(right,{n:"Search",i:"search",s:15,col:C.t3});
 I(right,{n:"Bell",i:"bell",s:15,col:C.t3});
 V(right,{w:1,h:18,col:C.line});
 const usr=F(right,{n:"User",dir:"horizontal",gap:8,align:"center",w:196});
 const av=F(usr,{n:"Avatar",dir:"horizontal",w:26,h:26,just:"center",align:"center",rad:2,fill:C.raised,stroke:C.line,sw:1});
 T(av,{n:"Initial",c:o.initial||"李",fs:11,fw:"600",col:C.t2});
 const ucol=F(usr,{n:"UCol",dir:"vertical",gap:1,w:200});
 T(ucol,{n:"Name",c:o.user||"李四",fs:12,col:C.t1,fw:"500"});
 T(ucol,{n:"Role",c:o.role||"企业碳管理员",fs:9.5,col:C.t3,w:200});
 I(usr,{n:"Caret",i:"chevron-down",s:12,col:C.t3});
 return bar;}
function PAGEHEAD(p,o){
 const f=F(p,{n:"PageHead",dir:"horizontal",w:"fill_container",just:"space_between",align:"end",gap:24});
 const l=F(f,{n:"HeadL",dir:"vertical",gap:8,w:"fill_container"});
 const crumbs=F(l,{n:"Crumbs",dir:"horizontal",gap:7,align:"center",w:"fit_content"});
 (o.crumbs||[]).forEach((c,i)=>{if(i>0)T(crumbs,{n:"Sep",c:"/",fs:10.5,col:C.t3});
  T(crumbs,{n:"C",c,fs:10.5,col:i===(o.crumbs.length-1)?C.t2:C.t3});});
 T(l,{n:"H1",c:o.title,fs:24,fw:"500",col:C.t1,lh:1.2});
 if(o.sub)T(l,{n:"Sub",c:o.sub,fs:12.5,col:C.t2,lh:1.4});
 const r=F(f,{n:"HeadR",dir:"horizontal",gap:8,align:"center",w:"fit_content",just:"end"});
 (o.actions||[]).forEach(a=>BTN(r,a));
 return f;}
function NOTE(p,o){
 const f=F(p,{n:"Note",dir:"horizontal",w:"fill_container",pad:[9,12],gap:9,align:"start",fill:"$noteBg",stroke:"$noteBd",sw:1,rad:2});
 I(f,{n:"Ico",i:o.i||"info",s:14,col:C.acc});
 T(f,{n:"L",c:o.c,fs:12,col:C.t2,w:"fill_container",lh:1.55});
 return f;}
function BARS(p,o){
 const h=o.h||160;
 const f=F(p,{n:"Bars",dir:"horizontal",w:"fill_container",h:h,gap:o.gap||8,align:"end",just:"space_between"});
 let mx=0;o.data.forEach(d=>{if(d>mx)mx=d;});mx=mx||1;
 o.data.forEach((d,i)=>{
  const col=F(f,{n:"Col",dir:"vertical",w:"fill_container",h:"fill_container",just:"end",align:"center",gap:7});
  const bh=Math.max(3,Math.round((d/mx)*(h-26)));
  R(col,{n:"Bar",w:o.bw||16,h:bh,rad:1,fill:(o.hi!==undefined&&i===o.hi)?C.chart:"$chartTrack"});
  T(col,{n:"X",c:(o.labels||[])[i]||"",fs:9,mono:true,col:C.t3});
 });
 return f;}
function DONUT(p,o){
 const size=o.size||130;
 const f=F(p,{n:"Donut",dir:"horizontal",w:size,h:size,just:"center",align:"center"});
 let ang=90;
 (o.segs||[]).forEach((s,i)=>{
  const sweep=(s.v/o.total)*360;
  E(f,{n:"Seg",w:size,h:size,inner:o.inner||0.64,sta:ang,swp:-sweep,fill:s.c});
  ang-=sweep;});
 return f;}
function LEGEND(p,o){
 const f=F(p,{n:"Legend",dir:"vertical",w:"fill_container",gap:9});
 (o.items||[]).forEach(it=>{
  const r=F(f,{n:"Lg",dir:"horizontal",w:"fill_container",just:"space_between",align:"center",gap:8});
  const l=F(r,{n:"LgL",dir:"horizontal",gap:7,align:"center",w:"fit_content"});
  R(l,{n:"Swatch",w:8,h:8,fill:it.c,rad:1});
  T(l,{n:"T",c:it.l,fs:11.5,col:C.t2});
  T(r,{n:"V",c:it.v,fs:11.5,mono:true,col:C.t1});});
 return f;}
function CHART(p,o){const h=o.h||158;
 const wrap=F(p,{n:"Chart",dir:"vertical",w:"fill_container",gap:8});
 const body=F(wrap,{n:"Body",dir:"horizontal",w:"fill_container",gap:8});
 const ay=F(body,{n:"AxisY",dir:"vertical",w:32,h:h,just:"space_between",align:"end"});
 (o.yLabels||[]).forEach(l=>T(ay,{n:"Y",c:l,fs:9,mono:true,col:C.t3}));
 const plot=F(body,{n:"Plot",dir:"vertical",w:"fill_container",h:h,just:"end",stroke:C.lineSoft,sw:{bottom:1,left:1}});
 o.draw(plot);
 const xr=F(wrap,{n:"XLabels",dir:"horizontal",w:"fill_container",just:"space_between",pad:[0,0,0,40]});
 (o.xLabels||[]).forEach(l=>T(xr,{n:"X",c:l,fs:9,mono:true,col:C.t3}));
 return wrap;}
function PANEL_BODY(panel,o){
 o=o||{};
 return F(panel,{n:"PBody",dir:"vertical",w:"fill_container",h:o.h,gap:o.gap||0,pad:o.pad||[0,0]});
}
function MiniStat(p,o){
 const f=F(p,{n:"MiniStat",dir:"vertical",w:"fill_container",gap:4,pad:[12,14],fill:"$surfaceSunken",stroke:C.line,sw:1,rad:2});
 CAPS(f,{c:o.l,fs:10});
 const r=F(f,{n:"R",dir:"horizontal",w:"fill_container",gap:5,align:"end"});
 T(r,{n:"V",c:o.v,fs:o.fs||19,fw:"500",mono:true,col:o.col||C.t1});
 if(o.u)T(r,{n:"U",c:o.u,fs:10.5,col:C.t3});
 return f;}
function Toggle(p,o){
 const on=o.on!==false;
 const f=F(p,{n:"Toggle",dir:"horizontal",w:34,h:19,pad:[3,3],align:"center",
  just:on?"end":"start",fill:on?"$chart":"$track",stroke:on?"#00000000":C.line,sw:1,rad:10});
 E(f,{n:"Knob",w:13,h:13,fill:on?"#07120A":"#5F736B"});
 return f;}
function SLIDER(p,o){
 const f=F(p,{n:"Slider",dir:"vertical",w:"fill_container",gap:7});
 const hr=F(f,{n:"H",dir:"horizontal",w:"fill_container",just:"space_between",align:"center"});
 T(hr,{n:"L",c:o.l,fs:12,col:C.t2});
 T(hr,{n:"V",c:o.v,fs:12,mono:true,col:C.acc});
 const track=F(f,{n:"Track",dir:"horizontal",w:"fill_container",h:4,pad:[0,6],fill:"$track",rad:2,align:"center"});
 R(track,{n:"Fill",w:Math.max(2,(o.pct||50)-6),h:4,fill:C.accDim,rad:2});
 E(track,{n:"Knob",w:11,h:11,fill:C.acc});
 return f;}
function SCREEN(o){
 const root=Insert(document,{type:"frame",name:o.name,x:(typeof o.x==="number"?o.x:0),y:(typeof o.y==="number"?o.y:0),width:o.w||1440,height:o.h||900,fill:"$bg",layout:"vertical",clip:false});
 TOPBAR(root,{tab:o.tab||0,tabs:o.tabs,user:o.user,role:o.role,initial:o.initial,live:o.live});
 const body=F(root,{n:"Body",dir:"horizontal",w:"fill_container",h:"fill_container",gap:0});
 if(o.rail!==false)RAIL(body,{active:o.nav||0,items:o.navItems});
 const main=F(body,{n:"Main",dir:"vertical",w:"fill_container",h:"fill_container",pad:o.pad||[22,26],gap:o.gap||18});
 return main;}
function ROW(p,o){return F(p,{n:o.n||"Row",dir:"horizontal",w:o.w||"fill_container",h:o.h,gap:o.gap!=null?o.gap:12,align:o.align||"start",just:o.just});}
function COL(p,o){return F(p,{n:o.n||"Col",dir:"vertical",w:o.w||"fill_container",h:o.h,gap:o.gap!=null?o.gap:12,just:o.just,align:o.align});}
function KV(p,o){
 const r=F(p,{n:"KV",dir:"horizontal",w:"fill_container",just:"space_between",align:"center",gap:12,pad:[o.py!=null?o.py:7,0]});
 T(r,{n:"K",c:o.k,fs:12,col:C.t3});
 T(r,{n:"V",c:o.v,fs:12.5,col:o.col||C.t1,mono:o.mono!==false});
 return r;}
function TABS(p,o){
 const f=F(p,{n:"Tabs",dir:"horizontal",w:"fill_container",gap:24,align:"center",stroke:C.line,sw:{bottom:1}});
 (o.items||[]).forEach((t,i)=>{
  const on=i===o.on;
  const g=F(f,{n:"Tab",dir:"vertical",w:"fit_content",just:"end",gap:0});
  T(g,{n:"L",c:t,fs:13,col:on?C.t1:C.t3,fw:on?"500":"400"});
  R(g,{n:"U",w:"fill_container",h:2,fill:on?C.chart:"#00000000",rad:1});
 });
 return f;}
function STEPPER(p,o){
 const f=F(p,{n:"Stepper",dir:"horizontal",w:"fill_container",gap:0,align:"center"});
 (o.steps||[]).forEach((st,i)=>{
  if(i>0)R(f,{n:"Conn",w:"fill_container",h:1,fill:i<=o.at?C.acc:C.line});
  const c=F(f,{n:"Step",dir:"horizontal",gap:8,align:"center",w:"fit_content"});
  const dot=F(c,{n:"Dot",dir:"horizontal",w:20,h:20,just:"center",align:"center",rad:10,
   fill:i<o.at?C.acc:(i===o.at?C.chart:"$track"),stroke:i<=o.at?"#00000000":C.line,sw:1});
  T(dot,{n:"N",c:i<o.at?"✓":String(i+1),fs:9.5,mono:true,col:i<=o.at?"#07120A":C.t3});
  T(c,{n:"L",c:st,fs:11.5,col:i===o.at?C.t1:(i<o.at?C.t2:C.t3),fw:i===o.at?"500":"400"});
 });
 return f;}
function BADGEROW(p,o){
 const f=F(p,{n:"Badges",dir:"horizontal",w:"fill_container",gap:8,align:"center"});
 (o.items||[]).forEach(it=>PILL(f,it));
 return f;}
function CALLOUT(p,o){
 const t=TONE[o.tone||"warn"];
 const f=F(p,{n:"Callout",dir:"horizontal",w:"fill_container",pad:[11,13],gap:10,align:"start",fill:t.bg,stroke:t.bd,sw:1,rad:2});
 I(f,{n:"Ico",i:o.i||"triangle-alert",s:15,col:t.fg});
 const c=F(f,{n:"Body",dir:"vertical",w:"fill_container",gap:3});
 T(c,{n:"T",c:o.t,fs:12.5,fw:"500",col:t.fg,w:"fill_container"});
 if(o.c)T(c,{n:"C",c:o.c,fs:11.5,col:C.t2,w:"fill_container",lh:1.5});
 return f;}
function SPARK(p,o){
 const w=o.w||120,h=o.h||34;
 const f=F(p,{n:"Spark",dir:"horizontal",w:w,h:h});
 (o.paths||[]).forEach(pth=>{
  const n=Insert(f,{type:"path",name:"Line",geometry:pth.d,width:pth.w||w,height:pth.h||h,stroke:pth.c||C.acc,strokeWidth:pth.s||1.5});
 });
 return f;}
function GAUGE(p,o){
 const f=F(p,{n:"Bullet",dir:"vertical",w:o.w||"fill_container",gap:6});
 const hd=F(f,{n:"Top",dir:"horizontal",w:"fill_container",just:"space_between",align:"end",alignItems:"end"});
 const lw=F(hd,{n:"LW",dir:"vertical",gap:1,w:'fill_container'});
 if(o.l)T(lw,{n:"L",c:o.l,fs:11.5,col:C.t2,w:'fill_container'});
 if(o.sub)T(lw,{n:"S",c:o.sub,fs:10,col:C.t3,w:'fill_container'});
 T(hd,{n:"V",c:o.v,fs:o.fs||20,fw:"500",mono:true,col:C.t1});
 const tr=F(f,{n:"Track",dir:"horizontal",w:"fill_container",h:8,fill:"$track",rad:2});
 R(tr,{n:"Fill",w:Math.max(3,Math.round((o.pct||0)*(o.w||260))),h:8,fill:o.c||C.chart,rad:2});
 return f;}

function PANEL_BODY(panel,o){
 o=o||{};
 return F(panel,{n:"PBody",dir:"vertical",w:"fill_container",h:o.h,gap:o.gap||0,pad:o.pad||[0,0]});
}
function MiniStat(p,o){
 const f=F(p,{n:"MiniStat",dir:"vertical",w:"fill_container",gap:4,pad:[12,14],fill:"$surfaceSunken",stroke:C.line,sw:1,rad:2});
 CAPS(f,{c:o.l,fs:10});
 const r=F(f,{n:"R",dir:"horizontal",w:"fill_container",gap:5,align:"end"});
 T(r,{n:"V",c:o.v,fs:o.fs||19,fw:"500",mono:true,col:o.col||C.t1});
 if(o.u)T(r,{n:"U",c:o.u,fs:10.5,col:C.t3});
 return f;}
function Toggle(p,o){
 const on=o.on!==false;
 const f=F(p,{n:"Toggle",dir:"horizontal",w:34,h:19,pad:[3,3],align:"center",
  just:on?"end":"start",fill:on?"$chart":"$track",stroke:on?"#00000000":C.line,sw:1,rad:10});
 E(f,{n:"Knob",w:13,h:13,fill:on?"#07120A":"#5F736B"});
 return f;}
function INPUTROW(p,o){
 const f=F(p,{n:"InputRow",dir:"horizontal",w:"fill_container",gap:0,align:"center",fill:"$surfaceSunken",stroke:C.line,sw:1,rad:2});
 T(f,{n:"V",c:o.v||"",fs:13,col:o.v?C.t1:C.t3,w:"fill_container",pad:[0,11]});
 if(o.suffix)T(f,{n:"S",c:o.suffix,fs:11.5,col:C.t3,w:46});
 return f;}
function SLIDER(p,o){
 const f=F(p,{n:"Slider",dir:"vertical",w:"fill_container",gap:7});
 const hr=F(f,{n:"H",dir:"horizontal",w:"fill_container",just:"space_between",align:"center"});
 T(hr,{n:"L",c:o.l,fs:12,col:C.t2});
 T(hr,{n:"V",c:o.v,fs:12,mono:true,col:C.acc});
 const track=F(f,{n:"Track",dir:"horizontal",w:"fill_container",h:4,pad:[0,6],fill:"$track",rad:2,align:"center"});
 R(track,{n:"Fill",w:Math.max(2,(o.pct||50)-6),h:4,fill:C.accDim,rad:2});
 E(track,{n:"Knob",w:11,h:11,fill:C.acc});
 return f;}

const NAV_ENT=[{l:"填报",i:"file-pen-line"},{l:"订单",i:"receipt-text"},{l:"拍卖",i:"gavel"},{l:"P2P",i:"arrow-left-right"},{l:"看板",i:"layout-dashboard"},{l:"信誉",i:"shield-check"},{l:"碳币",i:"coins"},{l:"链",i:"blocks"},{l:"中和",i:"leaf"},{l:"AI",i:"sparkles"},{l:"我的",i:"user-round"}];
const NAV_REV=[{l:"待审",i:"clipboard-check"},{l:"历史",i:"history"},{l:"项目",i:"folder-check"},{l:"补救",i:"blocks"}];
const NAV_GOV=[{l:"监管",i:"scan-eye"}];
const NAV_ADM=[{l:"用户",i:"users"},{l:"核算",i:"calculator"},{l:"配置",i:"settings-2"},{l:"统计",i:"chart-pie"},{l:"认证",i:"badge-check"},{l:"证书",i:"file-badge"}];
const TABS_ENT=["碳核算","交易市场","企业资产","数据智能"];
const TABS_ADM=["系统管理","数据管理","认证管理"];
const U_ENT={user:"李四",role:"企业碳管理员 · 绿色能源科技",initial:"李",live:"链上同步 · 区块高度 1,284"};
const U_REV={user:"王审核",role:"碳核算审核员 · 中碳认证",initial:"王",live:"链上同步 · 区块高度 1,284"};
const U_GOV={user:"赵监管",role:"监管员 · 中国碳排放权注册登记机构",initial:"赵",live:"只读监督 · 区块高度 1,284"};
const U_ADM={user:"admin",role:"系统管理员",initial:"A",live:"链上状态 正常"};

function MINI(p,o){
 const f=F(p,{n:"Mini",dir:"horizontal",w:"fill_container",just:"space_between",align:"center",pad:[7,0]});
 T(f,{n:"K",c:o.l,fs:11.5,col:C.t3});
 T(f,{n:"V",c:o.v,fs:11.5,mono:true,col:C.t1});
 return f;}

function PROPBAR(p,o){
 const f=F(p,{n:"PropBar",dir:"vertical",w:"fill_container",gap:12});
 const track=F(f,{n:"Track",dir:"horizontal",w:"fill_container",h:26,gap:2,align:"center"});
 let tot=0;(o.segs||[]).forEach(s=>tot+=parseFloat(s.v)||0);tot=tot||1;
 (o.segs||[]).forEach(s=>{
  const frac=(parseFloat(s.v)||0)/tot;
  const seg=F(track,{n:"Seg",dir:"horizontal",h:26,just:"center",align:"center",
   w:Math.max(6,Math.round(frac*(o.w||640))),fill:s.c,rad:1});
  if(frac>0.10)T(seg,{n:"Pct",c:Math.round(frac*100)+"%",fs:9.5,mono:true,col:C.t1});
 });
 const leg=F(f,{n:"Legend",dir:"vertical",w:"fill_container",gap:8});
 (o.segs||[]).forEach(it=>{
  const r=F(leg,{n:"Lg",dir:"horizontal",w:"fill_container",just:"space_between",align:"center",gap:8});
  const l=F(r,{n:"LgL",dir:"horizontal",gap:8,align:"center",w:"fit_content"});
  R(l,{n:"Swatch",w:9,h:9,fill:it.c,rad:1});
  T(l,{n:"T",c:it.l,fs:11.5,col:C.t2});
  T(r,{n:"V",c:it.v+(o.unit||""),fs:11.5,mono:true,col:C.t1});});
 return f;}
function METER(p,o){
 const f=F(p,{n:"Meter",dir:"vertical",w:"fill_container",gap:6});
 const hr=F(f,{n:"H",dir:"horizontal",w:"fill_container",just:"space_between",align:"center"});
 T(hr,{n:"L",c:o.l,fs:11.5,col:C.t2});
 T(hr,{n:"V",c:o.v,fs:11.5,mono:true,col:o.col||C.acc});
 const tr=F(f,{n:"Track",dir:"horizontal",w:"fill_container",h:6,fill:"$track",rad:1});
 R(tr,{n:"Fill",w:Math.round((o.pct||0)*(o.w||600)),h:6,fill:o.col||C.acc,rad:1});
 return f;}
`;
