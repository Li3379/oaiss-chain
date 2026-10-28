import { execute } from './pen.mjs';
// id -> correct link text
const MAP = {
  warAB: 'TRX-2026-013', WUSR9: 'TRX-2026-012', m7pK5: 'TRX-2026-011', U9d4A0: 'TRX-2026-010',
  QZHiC: '绿色能源科技有限公司',
  dKYZP: 'CERT-2026-001', FNM14: 'CERT-2026-002', J511XG: 'CERT-2026-003',
};
for (const [id, text] of Object.entries(MAP)) {
  const r = await execute(`Update('${id}',{content:'${text}',fill:'$textSecondary'});`);
  console.log(id, '->', text, r.ok);
}
