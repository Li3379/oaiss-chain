export const pages=[{name:"x",code:String.raw`
Wait=0;
const RAILITEMS=[{l:"填报",i:"file-pen-line"},{l:"订单",i:"receipt-text"},{l:"拍卖",i:"gavel"},{l:"P2P",i:"arrow-left-right"},{l:"看板",i:"layout-dashboard"},{l:"信誉",i:"shield-check"},{l:"碳币",i:"coins"},{l:"链",i:"blocks"},{l:"中和",i:"leaf"},{l:"AI",i:"sparkles"},{l:"我的",i:"user-round"}];
const pos=FindEmptySpace({width:1440,height:900,padding:120});
S=Insert(document,{type:"frame",name:"01 碳核算填报",x:pos.x,y:pos.y,width:1440,height:900,fill:"$bg",layout:"vertical",clip:true});
TOPBAR(S,{tab:0,tabs:["碳核算","交易市场","企业资产","数据智能"],user:"李四",role:"企业碳管理员 · 绿色能源科技",initial:"李",live:"链上同步 · 区块高度 1,284"});
const body=F(S,{n:"Body",dir:"horizontal",w:"fill_container",h:"fill_container",gap:0});
RAIL(body,{active:0,items:RAILITEMS});
const main=F(body,{n:"Main",dir:"vertical",w:"fill_container",h:"fill_container",pad:[22,26],gap:18});
PAGEHEAD(main,{crumbs:["碳中和","碳核算"],title:"碳核算填报",sub:"编制排放报告、附凭证并送审；审核通过后由系统自动写入 Fabric 存证。",
 actions:[{l:"导出报表",i:"download"},{l:"创建报告",i:"plus",kind:"primary"}]});
const stats=F(main,{n:"Stats",dir:"horizontal",w:"fill_container",gap:12});
STAT(stats,{l:"本年度已报排放",v:"12,480.6",u:"tCO₂e",i:"factory",d:"较上年度 −4.2%",dtone:"up"});
STAT(stats,{l:"待审核报告",v:"3",u:"份",i:"clock",d:"最早提交 2026-05-28"});
STAT(stats,{l:"已上链存证",v:"41",u:"份",i:"blocks",d:"最近存证 2 小时前"});
STAT(stats,{l:"配额余额",v:"38,000",u:"tCO₂e",i:"wallet",d:"使用率 24.00%"});
const filt=F(main,{n:"Filters",dir:"horizontal",w:"fill_container",gap:10,align:"end",pad:[0,0,0,0]});
FIELD(filt,{l:"报告标题",ph:"按标题检索",w:230,i:"search"});
FIELD(filt,{l:"核算周期",ph:"如 2026-Q1",w:160});
FIELD(filt,{l:"状态",ph:"全部状态",w:150,i:"chevron-down"});
BTN(filt,{l:"查询",kind:"primary"});
BTN(filt,{l:"重置",kind:"ghost"});
const tbl=PANEL(main,{n:"ReportsPanel",t:"报告列表",i:"files",meta:"共 8 份 · 按创建时间倒序",hh:44,right:[{l:"批量送审",kind:"ghost",i:"send"}]});
const body2=F(tbl,{n:"TblBody",dir:"vertical",w:"fill_container",pad:[0,0],gap:0});
TABLE(body2,{zebra:true,cols:[
 {l:"报告编号",w:140},{l:"报告标题"},{l:"核算周期",w:110},{l:"总排放量",w:150,a:"r"},{l:"状态",w:110},{l:"签名",w:96},{l:"审核人",w:92},{l:"创建时间",w:130},{l:"操作",w:120,a:"r"}],
 rows:[
  ["RPT-2026-010","2026 年第一季度排放报告","2026-Q1",{mono:"370.5"},{pill:"审核中",tone:"warn"},{pill:"已签名",tone:"live"},"王审核","2026-05-31 08:12",{text:"查看详情"}],
  ["RPT-2026-009","2026 年 3 月月度核算",{text:"2026-03",col:"$textSecondary"},{mono:"128.4"},{pill:"审核通过",tone:"info"},{pill:"已签名",tone:"live"},"王审核","2026-05-28 16:40",{text:"查看详情"}],
  ["RPT-2026-008","2026 年 2 月月度核算",{text:"2026-02",col:"$textSecondary"},{mono:"141.2"},{pill:"已上链",tone:"live"},{pill:"已签名",tone:"live"},"李审核","2026-05-20 09:05",{text:"查看详情"}],
  ["RPT-2026-007","2026 年第一季度补充核算",{text:"2026-Q1",col:"$textSecondary"},{mono:"96.8"},{pill:"审核拒绝",tone:"danger"},{pill:"已签名",tone:"live"},"王审核","2026-05-18 11:22",{text:"查看详情"}],
  ["RPT-2026-006","2026 年 1 月月度核算",{text:"2026-01",col:"$textSecondary"},{mono:"134.5"},{pill:"已上链",tone:"live"},{pill:"已签名",tone:"live"},"李审核","2026-05-10 14:31",{text:"查看详情"}],
  ["RPT-2026-005","2025 年度排放报告",{text:"2025-Y",col:"$textSecondary"},{mono:"3,162.0"},{pill:"已上链",tone:"live"},{pill:"已签名",tone:"live"},"王审核","2026-04-02 10:00",{text:"查看详情"}],
  ["RPT-2026-011","2026 年第二季度排放报告",{text:"2026-Q2",col:"$textSecondary"},{mono:"—"},{pill:"草稿",tone:"neutral"},{pill:"未签名",tone:"neutral"},"—","2026-06-01 09:30",{text:"继续编制"}]
 ]});
`}];