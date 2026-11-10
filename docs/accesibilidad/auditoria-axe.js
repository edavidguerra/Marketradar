// Auditoría automática con axe-core sobre todas las pantallas de Marketradar.
// Uso (con la web en http://localhost:8080 y datos_demo.sql cargado):
//   npm install playwright axe-core@4.10.2 && npx playwright install chromium
//   node auditoria-axe.js resultado.json
// El resultado lista, por pantalla, las reglas que pasaron y las que fallaron.
const { chromium } = require('playwright');
const fs = require('fs');
const axe = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const B = 'http://localhost:8080';
async function run(page, nombre, out) {
  await page.addScriptTag({ content: axe });
  const r = await page.evaluate(async () => await axe.run(document, { runOnly: ['wcag2a','wcag2aa','wcag21a','wcag21aa','best-practice'] }));
  out.push({ nombre, violaciones: r.violations.map(v => ({ id: v.id, impacto: v.impact, ayuda: v.help, nodos: v.nodes.length, ejemplo: v.nodes[0].target.join(' '), resumen: v.nodes[0].failureSummary })), aprobadas: r.passes.length });
}
(async () => {
  const b = await chromium.launch(); const out = [];
  const ctx = await b.newContext({ bypassCSP: true, viewport: { width: 1280, height: 900 } }); const page = await ctx.newPage();
  await page.goto(B + '/'); await page.waitForTimeout(800); await run(page, 'Ingreso', out);
  await page.click('#pestana-registro'); await page.waitForTimeout(300); await run(page, 'Registro', out);
  await page.click('#contraste'); await page.waitForTimeout(300); await run(page, 'Ingreso (alto contraste)', out); await page.click('#contraste');
  await page.click('#pestana-ingreso');
  await page.fill('#ingreso-correo', 'rosa.demo@marketradar.co'); await page.fill('#ingreso-clave', 'Demo2026*');
  await page.click('#formulario-ingreso button[type=submit]'); await page.waitForURL(/usuario/, { timeout: 10000 }); await page.waitForTimeout(1000);
  for (const s of ['inicio','buscar','historial','perfil','pqr','tutorial']) { await page.goto(B + '/usuario.html#' + s); await page.waitForTimeout(900); await run(page, 'Usuario: ' + s, out); }
  await page.goto(B + '/usuario.html#buscar'); await page.waitForTimeout(700);
  const campo = await page.$('#seccion-buscar input'); if (campo) { await campo.fill('olla arrocera'); await campo.press('Enter'); await page.waitForTimeout(3500); await run(page, 'Usuario: resultado de búsqueda', out); const h = await page.$('text=Ver reseñas'); if (h) { await h.click(); await page.waitForTimeout(1500); await run(page, 'Usuario: reseñas de una oferta', out); } }
  const ctx2 = await b.newContext({ bypassCSP: true }); const p2 = await ctx2.newPage();
  await p2.goto(B + '/'); await p2.fill('#ingreso-correo', 'admin.demo@marketradar.co'); await p2.fill('#ingreso-clave', 'Demo2026*');
  await p2.click('#formulario-ingreso button[type=submit]'); await p2.waitForURL(/admin/, { timeout: 10000 }); await p2.waitForTimeout(1200); await run(p2, 'Administrador: lista de PQR', out);
  const fila = await p2.$('#seccion-lista button, #seccion-lista a'); if (fila) { await fila.click(); await p2.waitForTimeout(1000); await run(p2, 'Administrador: detalle de PQR', out); }
  await page.goto(B + '/usuario.html#inicio'); await page.setViewportSize({ width: 390, height: 844 }); await page.waitForTimeout(600); await run(page, 'Usuario: inicio en celular', out);
  fs.writeFileSync(process.argv[2], JSON.stringify(out, null, 1)); await b.close();
})().catch(e => { console.error(e); process.exit(1); });
