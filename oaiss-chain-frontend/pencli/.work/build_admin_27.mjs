// Page 27 only, rebuilt after the denominator fix.
export const INDEX_OFFSET = 26;
export const PAGES = [
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
];
