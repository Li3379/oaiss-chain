// Page 14 only, rebuilt after the hash-truncation fix.
export const INDEX_OFFSET = 13;
export const PAGES = [
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
];
