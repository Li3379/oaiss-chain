// Auditor pages 19-21 + regulator 23 (grid indices 18..21).
export const INDEX_OFFSET = 18;
export const PAGES = [
  // idx 18 -> 19
  {
    name: '19 待审报告',
    body: `
main=SCREEN({name:'19 待审报告',x:PX,y:PY,navItems:NAV_REV,tab:0,tabs:['审核材料','项目核证','上链补救'],user:U_REV.user,role:U_REV.role,initial:U_REV.initial,live:U_REV.live,nav:0});
PAGEHEAD(main,{crumbs:['审核管理','碳排放数据'],title:'碳排放数据',sub:'待审碳报告排队；初审通过或驳回时须同时核验企业数字签名。',
 actions:[{l:'刷新队列',i:'refresh-cw',kind:'ghost'}]});
top=ROW(main,{gap:14});
qual=PANEL(top,{n:'QualPanel',t:'我的资格证',i:'badge-check',meta:'中国碳排放权交易协会签发'});
qb=PANEL_BODY(qual,{pad:[14,18]});
qh=ROW(qb,{w:'fill_container',just:'space_between',align:'center'});
PILL(qh,{l:'有效',tone:'live'});
T(qh,{n:'N',c:'碳核算审核员',fs:12.5,fw:'500',col:C.t1,w:110});
H(qb,{n:'D',h:1,col:C.line});
KV(qb,{k:'审核员',v:'王审核'});
KV(qb,{k:'所属组织',v:'中碳认证中心'});
KV(qb,{k:'证书编号',v:'REV20260001'});
perf=PANEL(top,{n:'PerfPanel',t:'审核绩效',i:'chart-column',meta:'生涯累计'});
pb=PANEL_BODY(perf,{pad:[14,18]});
pg=ROW(pb,{gap:0,w:'fill_container',align:'center'});
[['总审核数','156'],['通过','132'],['驳回','24'],['通过率','84.6%']].forEach((x,i)=>{
 if(i>0)V(pg,{w:1,h:42,col:C.line});
 c=COL(pg,{w:'fill_container',gap:4,pad:[0,i===0?0:14],align:'start'});
 T(c,{n:'V',c:x[1],fs:24,fw:'500',mono:true,col:i===3?C.acc:C.t1});
 T(c,{n:'L',c:x[0],fs:10.5,col:C.t3,w:80});});
TABS(main,{on:0,items:['待审核报告','全部报告']});
tbl=PANEL(main,{n:'QueuePanel',t:'待审核报告',i:'clipboard-check',meta:'共 3 份 · 按提交时间正序',
 right:[{l:'批量审核',kind:'ghost',i:'check-check'}]});
tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,cols:[{l:'报告编号',w:140},{l:'企业名称',w:230},{l:'报告标题'},{l:'总排放量',w:130,a:'r'},{l:'状态',w:104},{l:'验签',w:104},{l:'创建时间',w:150},{l:'操作',w:90,a:'r'}],
 rows:[
  ['RPT-2026-010','绿色能源科技有限公司','2026 年第一季度排放报告',{mono:'78.0'},{pill:'已提交',tone:'warn'},{pill:'已签名',tone:'live'},{text:'2026-05-31 08:12',col:'$textSecondary'},{text:'审核'}],
  ['RPT-2026-011','低碳制造股份有限公司','2026 年 4 月月度核算',{mono:'86.4'},{pill:'审核中',tone:'info'},{pill:'验签通过',tone:'live'},{text:'2026-05-30 15:40',col:'$textSecondary'},{text:'审核'}],
  ['RPT-2026-012','华北建材有限公司','2026-Q1 补充核算',{mono:'142.6'},{pill:'已提交',tone:'warn'},{pill:'验签失败',tone:'danger'},{text:'2026-05-29 11:05',col:'$textSecondary'},{text:'审核'}]
 ]});
detail=F(main,{n:'Detail',dir:'horizontal',w:'fill_container',gap:14});
em=PANEL(detail,{n:'EmissionPanel',t:'排放数据明细',i:'factory',meta:'RPT-2026-010 · 只读'});
eb=PANEL_BODY(em,{pad:[14,18]});
TABLE(eb,{cols:[{l:'范围',w:150},{l:'活动数据'},{l:'排放因子',w:140,a:'r'},{l:'排放量 (tCO₂e)',w:160,a:'r'}],
 rows:[['范围一 燃煤燃烧',{mono:'30.0 t'},{mono:'2.53'},{mono:'75.9'}],
 ['范围二 净购电',{mono:'4.2 MWh'},{mono:'0.5810'},{mono:'2.1'}]]});
ai=PANEL(detail,{n:'AIPanel',t:'AI 智能辅助预审',i:'sparkles',meta:'模型置信度 94%'});
ab=PANEL_BODY(ai,{pad:[14,18],gap:10});
PILL(ab,{l:'AI 建议通过',tone:'live'});
T(ab,{n:'S',c:'排放总量与产量、能耗台账基本吻合；参数方差处于同行业合理范围（σ = 0.032）。',fs:12,col:C.t2,w:520,lh:1.7});
MINI(ab,{l:'活动数据方差',v:'0.032'});
MINI(ab,{l:'排放因子偏差',v:'−1.8%'});
BTN(ab,{l:'采用 AI 推荐意见',kind:'ghost',i:'sparkles'});
CALLOUT(ab,{tone:'warn',i:'shield-alert',t:'RPT-2026-012 验签失败',c:'该报告数字签名校验未通过，数据可能已被篡改，建议驳回并要求企业重新签名后提交。'});`,
  },
  // idx 19 -> 20
  {
    name: '20 项目核证',
    body: `
main=SCREEN({name:'20 项目核证',x:PX,y:PY,navItems:NAV_REV,tab:0,tabs:['审核材料','项目核证','上链补救'],user:U_REV.user,role:U_REV.role,initial:U_REV.initial,live:U_REV.live,nav:2});
PAGEHEAD(main,{crumbs:['审核管理','项目核证'],title:'项目核证',sub:'对碳中和项目做现场核证并出具核证减排量；违规项目可联动扣减企业信用分。',
 actions:[{l:'导出核证明细',i:'download',kind:'ghost'}]});
NOTE(main,{i:'clipboard-check',c:'待核证项目：已进入核证流程并分配给当前审核员的项目。有新项目提交后会自动出现在这里。'});
tbl=PANEL(main,{n:'QueuePanel',t:'待核证项目',i:'folder-check',meta:'共 2 个 · 按创建时间正序'});
tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,rh:48,cols:[{l:'项目名称'},{l:'企业名称',w:230},{l:'项目类型',w:120},{l:'状态',w:104},{l:'核证状态',w:120},{l:'预期减排量',w:130,a:'r'},{l:'创建时间',w:150},{l:'操作',w:150,a:'r'}],
 rows:[
  ['林业碳汇造林一期','清洁能源发展有限公司',{text:'碳汇造林',col:'$textSecondary'},{pill:'实施中',tone:'live'},{pill:'核证中',tone:'info'},{mono:'12,000 吨'},{text:'2026-03-12',col:'$textSecondary'},{text:'核证 / 扣分'}],
  ['余热回收二期','绿色能源科技有限公司',{text:'节能改造',col:'$textSecondary'},{pill:'已完成',tone:'live'},{pill:'未核证',tone:'warn'},{mono:'5,000 吨'},{text:'2026-02-28',col:'$textSecondary'},{text:'核证'}]
 ]});
grid=ROW(main,{gap:14});
form=PANEL(grid,{n:'FormPanel',t:'核证减排量登记',i:'clipboard-check',meta:'以现场测量报告为准'});
fb=PANEL_BODY(form,{pad:[16,18],gap:12});
fr=ROW(fb,{gap:12});
FIELD(fr,{l:'项目',v:'林业碳汇造林一期',w:'fill_container',i:'chevron-down'});
FIELD(fr,{l:'核证减排量（吨）',v:'10,400',w:'fill_container'});
FIELD(fb,{l:'核证报告',v:'已上传：碳汇计量核算报告(2026).pdf',i:'paperclip'});
FIELD(fb,{l:'监测数据',v:'已上传：样地监测数据.csv',i:'paperclip'});
FIELD(fb,{l:'备注',v:'样地成活率 92%，符合《碳汇造林项目核证规程》要求。'});
H(fb,{n:'D',h:1,col:C.line});
act=ROW(fb,{gap:10});
BTN(act,{l:'提交核证结果',kind:'primary',i:'check'});
BTN(act,{l:'核证未通过',kind:'ghost',i:'x-circle'});
side=COL(grid,{w:480,gap:14});
summ=PANEL(side,{n:'SumPanel',t:'核证摘要',i:'sigma',meta:'提交前确认'});
sb=PANEL_BODY(summ,{pad:[16,18]});
KV(sb,{k:'预期减排量',v:'12,000 吨'});
KV(sb,{k:'核证减排量',v:'10,400 吨',col:C.acc});
KV(sb,{k:'核证差异率',v:'−13.3%',col:'$stateWarn'});
KV(sb,{k:'核证状态',v:'核证中'});
deduct=PANEL(side,{n:'DeductPanel',t:'违规扣分',i:'gavel',meta:'联动企业信用分'});
db=PANEL_BODY(deduct,{pad:[16,18],gap:12});
CALLOUT(db,{tone:'danger',i:'triangle-alert',t:'扣分将立即生效',c:'信用分变化会写入企业评分流水，影响其交易权限与配额额度，操作不可撤销。'});
FIELD(db,{l:'事件类型',v:'违规',i:'chevron-down'});
FIELD(db,{l:'扣分描述',v:'核证减排量与监测数据偏差超过 10%，未按要求补充说明。'});
BTN(db,{l:'确认扣分',kind:'ghost',w:'fill_container',i:'gavel'});`,
  },
  // idx 20 -> 21
  {
    name: '21 审核历史',
    body: `
main=SCREEN({name:'21 审核历史',x:PX,y:PY,navItems:NAV_REV,tab:0,tabs:['审核材料','项目核证','上链补救'],user:U_REV.user,role:U_REV.role,initial:U_REV.initial,live:U_REV.live,nav:1});
PAGEHEAD(main,{crumbs:['审核管理','审核历史'],title:'审核历史',sub:'已办结案件的归档追溯：审核结果、意见与时间完整留存，只读不可修改。',
 actions:[{l:'导出归档记录',i:'download',kind:'ghost'}]});
stats=ROW(main,{gap:12});
STAT(stats,{l:'累计办结',v:'156',u:'件',i:'archive'});
STAT(stats,{l:'通过',v:'132',u:'件',i:'check-check',d:'占比 84.6%'});
STAT(stats,{l:'驳回',v:'24',u:'件',i:'x-circle',d:'占比 15.4%'});
STAT(stats,{l:'平均办结时长',v:'4.2',u:'小时',i:'clock',d:'较上月 −0.6 小时',dtone:'up'});
NOTE(main,{i:'info',c:'本页仅为归档视图。如需查看报告正文或原始凭证，请前往「碳排放数据」按报告编号检索。'});
tbl=PANEL(main,{n:'HistPanel',t:'审核历史',i:'history',meta:'共 156 条 · 按审核时间倒序'});
tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,rh:48,cols:[{l:'报告编号',w:140},{l:'企业名称',w:230},{l:'报告标题'},{l:'审核结果',w:110},{l:'审核意见'},{l:'审核时间',w:180}],
 rows:[
  ['RPT-2026-009','低碳制造股份有限公司','2026 年 3 月月度核算',{pill:'通过',tone:'live'},'排放数据与能源台账一致，予以通过。','2026-05-28 16:40:00'],
  ['RPT-2026-007','绿色能源科技有限公司','2026 年第一季度补充核算',{pill:'驳回',tone:'danger'},'缺少能源台账发票，活动数据凭证不符。','2026-05-18 11:22:00'],
  ['RPT-2026-006','绿色能源科技有限公司','2026 年 1 月月度核算',{pill:'通过',tone:'live'},'参数选用符合国标，排放因子来源可溯。','2026-05-10 14:31:00'],
  ['RPT-2026-005','绿色能源科技有限公司','2025 年度排放报告',{pill:'通过',tone:'live'},'年度报告经第三方核查一致，予以通过。','2026-04-02 10:00:00'],
  ['RPT-2026-004','华北建材有限公司','2026 年 2 月月度核算',{pill:'驳回',tone:'danger'},'核算周期与台账冲突，请核对后重新提交。','2026-03-26 09:14:00']
 ]});
PAGER(tb,{count:'共 156 条 · 每页 10 条'});`,
  },
  // idx 21 -> 22
  {
    name: '22 上链补救',
    body: `
main=SCREEN({name:'22 上链补救',x:PX,y:PY,navItems:NAV_REV,tab:2,tabs:['审核材料','项目核证','上链补救'],user:U_REV.user,role:U_REV.role,initial:U_REV.initial,live:U_REV.live,nav:3});
PAGEHEAD(main,{crumbs:['审核管理','上链补救'],title:'上链补救',sub:'审核通过后由系统自动上链；此页处理自动上链失败的重试与审核后驳回。',
 actions:[{l:'批量上链补救',i:'blocks',kind:'primary'}]});
stats=ROW(main,{gap:12});
STAT(stats,{l:'待上链补救',v:'1',u:'份',i:'clock',d:'最早通过 2026-05-31'});
STAT(stats,{l:'已上链报告',v:'41',u:'份',i:'blocks',d:'最近 2 小时前'});
STAT(stats,{l:'已驳回报告',v:'6',u:'份',i:'x-circle'});
chk=F(stats,{n:'ChainStat',dir:'vertical',w:'fill_container',h:94,pad:[13,15],gap:6,just:'space_between',fill:C.surf,stroke:C.line,sw:1,rad:2});
CAPS(chk,{c:'区块链状态',fs:10.5,w:110});
r=ROW(chk,{gap:8,align:'center'});
PILL(r,{l:'正常 · healthy',tone:'live'});
T(chk,{n:'D',c:'通道 mychannel · 链码 carbon-chaincode',fs:11,col:C.t3,w:300});
filt=ROW(main,{gap:10,align:'end'});
FIELD(filt,{l:'状态',v:'待认证',w:160,i:'chevron-down'});
FIELD(filt,{l:'关键词',ph:'报告编号 / 企业名称',w:260,i:'search'});
BTN(filt,{l:'查询',kind:'primary'});
BTN(filt,{l:'重置',kind:'ghost'});
tbl=PANEL(main,{n:'ChainPanel',t:'报告列表',i:'blocks',meta:'共 48 份 · 仅「待认证」可选可补救'});
tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,cols:[{l:'序号',w:52},{l:'报告编号',w:140},{l:'企业名称',w:230},{l:'核算周期',w:110},{l:'总排放量',w:130,a:'r'},{l:'状态',w:110},{l:'提交时间',w:170},{l:'操作',w:190,a:'r'}],
 rows:[
  [{mono:'1'},'RPT-2026-010','绿色能源科技有限公司',{text:'2026-Q1',col:'$textSecondary'},{mono:'100.0'},{pill:'待认证',tone:'warn'},{text:'2026-05-31 08:00',col:'$textSecondary'},{text:'查看 / 上链补救 / 驳回'}],
  [{mono:'2'},'RPT-2026-009','低碳制造股份有限公司',{text:'2026-03',col:'$textSecondary'},{mono:'128.4'},{pill:'已上链',tone:'live'},{text:'2026-05-28 16:40',col:'$textSecondary'},{text:'查看'}],
  [{mono:'3'},'RPT-2026-007','绿色能源科技有限公司',{text:'2026-Q1',col:'$textSecondary'},{mono:'96.8'},{pill:'已驳回',tone:'danger'},{text:'2026-05-18 11:22',col:'$textSecondary'},{text:'查看'}]
 ]});
proof=F(main,{n:'Proof',dir:'horizontal',w:'fill_container',pad:[14,16],gap:16,align:'center',fill:'$noteBg',stroke:'$noteBd',sw:1,rad:2});
I(proof,{n:'Ico',i:'blocks',s:18,col:C.acc});
pc=COL(proof,{w:'fill_container',gap:4});
T(pc,{n:'T',c:'存证目标：通道 mychannel · 链码 carbon-chaincode',fs:12.5,fw:'500',col:C.t1,w:520});
T(pc,{n:'D',c:'每次补救都会生成新的交易哈希；若原交易实际已成功，重复提交将被链码幂等规则拒绝，不会产生重复存证。',fs:11.5,col:C.t2,w:640,lh:1.6});
T(proof,{n:'M',c:'最近一次补救：2 小时前 · 成功',fs:11,col:C.t3,w:240});`,
  },
  // idx 22 -> 23
  {
    name: '23 监管面板',
    body: `
main=SCREEN({name:'23 监管面板',x:PX,y:PY,navItems:NAV_GOV,tab:0,tabs:['监管中心'],user:U_GOV.user,role:U_GOV.role,initial:U_GOV.initial,live:U_GOV.live,nav:0});
PAGEHEAD(main,{crumbs:['监管中心','第三方监管中心'],title:'监管面板',sub:'查看辖区碳排放审核报告统计，并按企业、状态穿透检索全部报告。',
 actions:[{l:'导出监管报表',i:'download',kind:'ghost'}]});
grid=ROW(main,{gap:14});
org=PANEL(grid,{n:'OrgPanel',t:'机构信息',i:'landmark',meta:'只读监督机构'});
ob=PANEL_BODY(org,{pad:[16,18],gap:12});
KV(ob,{k:'机构名称',v:'中国碳排放权注册登记机构'});
KV(ob,{k:'权限级别',v:'3 · 辖区监管'});
KV(ob,{k:'地址',v:'北京市西城区金融大街 15 号'});
H(ob,{n:'D',h:1,col:C.line});
or1=ROW(ob,{gap:12});
FIELD(or1,{l:'联系人',v:'赵监管',w:'fill_container'});
FIELD(or1,{l:'联系电话',v:'138 0013 8004',w:'fill_container'});
BTN(ob,{l:'保存联系人',kind:'primary',i:'check',w:180});
statCol=COL(grid,{gap:12});
s1=ROW(statCol,{gap:12});
STAT(s1,{l:'总报告数',v:'1,284',u:'份',i:'files',h:88});
STAT(s1,{l:'待审核',v:'37',u:'份',i:'clock',h:88});
STAT(s1,{l:'已通过',v:'986',u:'份',i:'check-check',tone:'live',h:88});
s2=ROW(statCol,{gap:12});
STAT(s2,{l:'已拒绝',v:'124',u:'份',i:'x-circle',tone:'danger',h:88});
STAT(s2,{l:'审核通过率',v:'76.8',u:'%',i:'activity',h:88});
gauge=PANEL(statCol,{n:'GaugePanel',t:'辖区关键比率',i:'gauge',meta:'实时计算'});
gb=PANEL_BODY(gauge,{pad:[16,18],gap:16});
gb1=F(gb,{n:'BulletRow',dir:'horizontal',w:'fill_container',gap:26,align:'center'});
bl=COL(gb1,{w:'fill_container',gap:14});
GAUGE(bl,{w:300,v:'76.8%',l:'审核通过率',sub:'986 / 1,284 份',pct:0.768});
GAUGE(bl,{w:300,v:'2.9%',l:'待审核占比',sub:'37 / 1,284 份',pct:0.029,c:C.amber});
V(gb1,{w:1,h:56,col:C.line});
gc=COL(gb1,{w:'fill_container',gap:10});
LEGEND(gc,{items:[{l:'已通过',v:'986 份',c:C.chart},{l:'已拒绝',v:'124 份',c:C.red},{l:'待审核',v:'37 份',c:C.amber},{l:'已上链',v:'41 份',c:C.cyan}]});
filt=ROW(main,{gap:10,align:'end'});
FIELD(filt,{l:'企业 ID',ph:'如 1',w:150});
FIELD(filt,{l:'关键词',ph:'报告编号 / 企业名称',w:260,i:'search'});
FIELD(filt,{l:'状态',v:'全部',w:150,i:'chevron-down'});
BTN(filt,{l:'查询',kind:'primary'});
BTN(filt,{l:'重置',kind:'ghost'});
tbl=PANEL(main,{n:'ReportPanel',t:'辖区报告列表',i:'files',meta:'共 1,284 份 · 只读穿透查询'});
tb=PANEL_BODY(tbl);
TABLE(tb,{zebra:true,cols:[{l:'序号',w:52},{l:'报告编号',w:140},{l:'企业名称',w:230},{l:'年份',w:90},{l:'碳排放总量（吨）',w:150,a:'r'},{l:'状态',w:110},{l:'提交时间',w:180}],
 rows:[
  [{mono:'1'},'RPT-2026-010','绿色能源科技有限公司',{text:'2026',col:'$textSecondary'},{mono:'370.5'},{pill:'待审核',tone:'warn'},{text:'2026-05-31 08:12',col:'$textSecondary'}],
  [{mono:'2'},'RPT-2026-009','低碳制造股份有限公司',{text:'2026',col:'$textSecondary'},{mono:'128.4'},{pill:'已通过',tone:'live'},{text:'2026-05-28 16:40',col:'$textSecondary'}],
  [{mono:'3'},'RPT-2026-007','绿色能源科技有限公司',{text:'2026',col:'$textSecondary'},{mono:'96.8'},{pill:'已拒绝',tone:'danger'},{text:'2026-05-18 11:22',col:'$textSecondary'}],
  [{mono:'4'},'RPT-2024-001','绿色能源科技有限公司',{text:'2024',col:'$textSecondary'},{mono:'1,700.0'},{pill:'已通过',tone:'live'},{text:'2026-04-08 09:20',col:'$textSecondary'}]
 ]});
PAGER(tb,{count:'共 1,284 条 · 每页 10 条'});`,
  },
];
