import { test, expect, type Page, type TestInfo } from "@playwright/test";
import path from "node:path";
import fs from "node:fs";

/**
 * Ensaio do roteiro de demonstração — docs/08 §4, passos 1 a 12 (FD-5/F9-3).
 *
 * Um único teste `serial`, um `page` do início ao fim (a sessão troca de
 * conta com "Sair" + login de novo, como faria a equipe na apresentação).
 * Navegação interna usa os links do próprio app (`nav()`), nunca `page.goto`
 * no meio do fluxo — o token de acesso mora só em memória (CLAUDE.md §7) e um
 * recarregamento completo depende do refresh via cookie; preferir clique em
 * link evita ficar batendo nesse caminho a cada passo e replica melhor o
 * comportamento de quem apresenta ao vivo (sem apertar F5 a cada tela).
 *
 * Cada passo roda isolado num `try/catch`: uma falha é registrada e reportada
 * no console, mas não interrompe o ensaio (os passos seguintes que dependem
 * do anterior falham em cascata — isso também é sinal para o relatório).
 *
 * Credenciais vêm só de variáveis de ambiente (nunca escritas neste arquivo,
 * D-65): APP_SEED_ADMIN_PASSWORD, APP_DEMO_PASSWORD, APP_DEMO_TEMP_PASSWORD.
 */

const ADMIN_EMAIL = "admin@exemplo.test";
const ADMIN_PASSWORD = process.env.APP_SEED_ADMIN_PASSWORD ?? "";
const SYNDIC_EMAIL = "sindico@exemplo.test";
const DEMO_PASSWORD = process.env.APP_DEMO_PASSWORD ?? "";
const DEMO_TEMP_PASSWORD = process.env.APP_DEMO_TEMP_PASSWORD ?? "";

if (!ADMIN_PASSWORD || !DEMO_PASSWORD || !DEMO_TEMP_PASSWORD) {
  throw new Error(
    "Faltam variáveis de ambiente: APP_SEED_ADMIN_PASSWORD, APP_DEMO_PASSWORD, APP_DEMO_TEMP_PASSWORD (docs/08 §2, D-65).",
  );
}
// DEMO_TEMP_PASSWORD só é exigida como pré-condição do ambiente (docs/08 §2);
// a senha temporária de a-104 usada neste roteiro é gerada na hora (RN-03) e
// capturada do modal no passo 2 — nunca reaproveitamos a de a-102 aqui.
void DEMO_TEMP_PASSWORD;

interface StepResult {
  num: string;
  slug: string;
  status: "ok" | "fail";
  detail?: string;
}

const results: StepResult[] = [];
const consoleErrors: string[] = [];
const pageErrors: string[] = [];

function screenshotPath(testInfo: TestInfo, num: string, slug: string): string {
  const dir = path.join(__dirname, "screenshots", testInfo.project.name);
  fs.mkdirSync(dir, { recursive: true });
  return path.join(dir, `${num}-${slug}.png`);
}

async function shot(page: Page, testInfo: TestInfo, num: string, slug: string) {
  await page.screenshot({ path: screenshotPath(testInfo, num, slug), fullPage: true });
}

async function assertNoHorizontalOverflow(page: Page, testInfo: TestInfo) {
  if (testInfo.project.name !== "mobile-360") return;
  const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
  expect(scrollWidth, "overflow horizontal em 360px").toBeLessThanOrEqual(360);
}

async function runStep(
  page: Page,
  testInfo: TestInfo,
  num: string,
  slug: string,
  fn: () => Promise<void>,
) {
  await test.step(`Passo ${num} — ${slug}`, async () => {
    try {
      await fn();
      await assertNoHorizontalOverflow(page, testInfo);
      await shot(page, testInfo, num, slug);
      results.push({ num, slug, status: "ok" });
    } catch (err) {
      console.error(`[PASSO ${num} FALHOU] ${slug}: ${String(err)}`);
      try {
        await shot(page, testInfo, num, `${slug}-erro`);
      } catch {
        /* melhor esforço */
      }
      results.push({ num, slug, status: "fail", detail: String(err) });
    }
  });
}

async function logout(page: Page) {
  const menuButton = page.getByRole("button", { name: /abrir menu/i });
  if (await menuButton.isVisible().catch(() => false)) {
    await menuButton.click();
  }
  await page.locator("button:visible", { hasText: "Sair" }).first().click();
  await page.waitForURL("**/login", { timeout: 15_000 });
}

/**
 * Só usado para o primeiro carregamento (tela de login não faz parte do SPA
 * autenticado). F9-3 (reensaio): quando um passo anterior falha no meio (ex.:
 * cascata do achado de performance do passo 7), a sessão anterior continua
 * autenticada — `/login` redireciona direto para a home de quem já está
 * logado (`LoginPage`, `if (user) return <Navigate .../>`) e `#login` nunca
 * aparece. Verifica isso antes e desloga para não propagar a cascata.
 */
async function login(page: Page, loginId: string, password: string) {
  await page.goto("/login");
  // O redirecionamento de sessão ativa (`<Navigate>` em `LoginPage`) troca a URL quase
  // instantaneamente — bem mais rápido que o SPA terminar de montar `#login` num carregamento
  // frio. `waitForURL` com timeout curto distingue os dois casos sem depender de visibilidade
  // (que daria falso positivo em um load só lento, não redirecionado).
  const redirected = await page
    .waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 2_000 })
    .then(() => true)
    .catch(() => false);
  if (redirected) {
    console.log("[observação] /login redirecionou (sessão anterior ainda ativa) — deslogando antes de tentar de novo.");
    await logout(page);
    await page.goto("/login");
  }
  await page.locator("#login").fill(loginId);
  await page.locator("#password").fill(password);
  await page.getByRole("button", { name: "Entrar" }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 15_000 });
}

/**
 * Navega por um item do menu (`navItems.ts`) sem recarregar a página: no
 * desktop o link já está visível na barra lateral; no celular abre o menu
 * compacto primeiro. `label` casa por substring (RF-PAG-01 acrescenta uma
 * contagem, ex. "Confirmações (2)").
 */
async function nav(page: Page, label: string) {
  // Item de menu condicional (ex. "Confirmações", RF-PAG-01) pode levar um instante
  // para aparecer depois do login — `isVisible({timeout})` espera por isso em vez de
  // checar o estado instantâneo (que teria caído sempre no ramo mobile por engano).
  // F9-3 (reensaio): 12s às vezes não bastava para o item "Confirmações" aparecer logo
  // após o login em /painel (a home concorre por conexões HTTP com a busca do contador
  // — ver docs/12, achado de performance) — folga maior evita confundir isso com falha
  // real de navegação.
  const desktopLink = page.locator("aside nav a", { hasText: label });
  if (await desktopLink.first().isVisible({ timeout: 20_000 }).catch(() => false)) {
    await desktopLink.first().click();
    return;
  }
  const menuButton = page.getByRole("button", { name: /abrir menu/i });
  if (await menuButton.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await menuButton.click();
  }
  await page.locator("#menu-mobile a", { hasText: label }).first().click();
}

/** Clica no primeiro bloco de horário livre (30 min) do TimeSlotPicker e devolve o rótulo (ex.: "08:00"). */
async function pickFirstAvailableSlot(page: Page): Promise<string> {
  const candidates = await page.getByRole("button", { name: /^\d{2}:\d{2}$/ }).all();
  for (const candidate of candidates) {
    if (await candidate.isEnabled()) {
      const label = (await candidate.textContent())?.trim().split("\n")[0] ?? "";
      await candidate.click();
      return label;
    }
  }
  throw new Error("Nenhum horário livre encontrado na grade.");
}

/** Passos 1→4 do assistente de reserva até a tela de revisão (não confirma). */
async function fillWizardUpToReview(page: Page, dayNumber: number): Promise<{ slot: string }> {
  await page.getByRole("button", { name: String(dayNumber), exact: true }).click();
  await page.getByRole("button", { name: "Continuar" }).click();
  const slot = await pickFirstAvailableSlot(page);
  await page.getByRole("button", { name: "Continuar" }).click();
  await page.locator("#resident").selectOption({ index: 1 });
  await page.locator("#guests").fill("1");
  await page.getByRole("button", { name: "Continuar" }).click();
  return { slot };
}

/** Só o badge de status (span visível) — `getByText` sozinho também casa a `<option>` oculta do select de "Alterar status". */
function statusBadge(page: Page, label: string) {
  return page.locator("span:visible", { hasText: label }).first();
}

/**
 * `/painel` (`GET /dashboard/home`) falhou uma vez no ensaio com 401/500/409
 * em sequência no console — sintoma de corrida entre chamadas concorrentes
 * logo após um login novo, não reproduzida de forma determinística. Um retry
 * único aqui evita que esse soluço isolado derrube o passo inteiro; se
 * persistir, o passo falha do mesmo jeito e fica registrado no relatório.
 */
async function ensureHomeLoaded(page: Page) {
  const retryButton = page.getByRole("button", { name: "Tentar novamente" });
  if (await retryButton.isVisible({ timeout: 3_000 }).catch(() => false)) {
    console.log("[observação] /painel não carregou de primeira — clicando 'Tentar novamente'.");
    await retryButton.click();
  }
}

interface NetworkSample {
  url: string;
  status: number;
  durationMs: number;
}

/**
 * F9-3 (reensaio final): mede quanto tempo o item "Confirmações" leva para
 * aparecer na navegação (desktop: barra lateral; mobile: menu compacto) logo
 * após o login, registrando os requests concluídos nesse intervalo (status e
 * duração via `request.timing()`) — evidência de rede para o Bug 3 de
 * docs/12, caso ainda esteja lento. Devolve o tempo decorrido e a amostra de
 * rede; quem chama decide como reportar.
 */
async function measureConfirmationsNavAppearance(
  page: Page,
  testInfo: TestInfo,
): Promise<{ elapsedMs: number; appeared: boolean; network: NetworkSample[] }> {
  const startedAt = Date.now();
  const network: NetworkSample[] = [];
  const onRequestFinished = async (request: import("@playwright/test").Request) => {
    try {
      const response = await request.response();
      if (!response) return;
      const timing = request.timing();
      const durationMs = timing.responseEnd >= 0 ? Math.round(timing.responseEnd) : -1;
      network.push({ url: request.url(), status: response.status(), durationMs });
    } catch {
      /* melhor esforço — request pode ter sido abortado pelo route guard */
    }
  };
  page.on("requestfinished", onRequestFinished);

  let appeared: boolean;
  if (testInfo.project.name === "desktop") {
    appeared = await page
      .locator("aside nav a", { hasText: "Confirmações" })
      .first()
      .isVisible({ timeout: 60_000 })
      .catch(() => false);
    if (!appeared) {
      // dá mais uma chance com espera ativa, para não subestimar o tempo real
      appeared = await page
        .locator("aside nav a", { hasText: "Confirmações" })
        .first()
        .waitFor({ state: "visible", timeout: 30_000 })
        .then(() => true)
        .catch(() => false);
    }
  } else {
    const menuButton = page.getByRole("button", { name: /abrir menu/i });
    await menuButton.click();
    appeared = await page
      .locator("#menu-mobile a", { hasText: "Confirmações" })
      .first()
      .isVisible({ timeout: 60_000 })
      .catch(() => false);
  }
  const elapsedMs = Date.now() - startedAt;
  page.off("requestfinished", onRequestFinished);

  console.log(
    `[medição][${testInfo.project.name}] item "Confirmações" ${appeared ? "apareceu" : "NÃO apareceu"} em ${elapsedMs} ms após o login.`,
  );
  console.log(`[medição][${testInfo.project.name}] requests concluídos nesse intervalo (${network.length}):`);
  for (const sample of network) {
    console.log(`  [medição][${testInfo.project.name}]   ${sample.status} ${sample.durationMs}ms ${sample.url}`);
  }

  return { elapsedMs, appeared, network };
}

/** Volta ao catálogo de áreas e abre o detalhe de uma área pelo nome do card. */
async function openArea(page: Page, areaName: string) {
  await nav(page, "Áreas");
  await page.waitForURL("**/areas");
  const card = page.locator("li", { hasText: areaName }).first();
  await card.getByRole("link", { name: "Ver detalhes" }).click();
}

test.describe.serial("Roteiro de demonstração — docs/08 §4", () => {
  test("passos 1 a 12", async ({ page }, testInfo) => {
    page.on("console", (msg) => {
      if (msg.type() === "error") {
        const text = `[console.error][${testInfo.project.name}] ${msg.text()}`;
        consoleErrors.push(text);
        console.log(text);
      }
    });
    page.on("pageerror", (err) => {
      const text = `[pageerror][${testInfo.project.name}] ${String(err)}`;
      pageErrors.push(text);
      console.log(text);
    });

    // Nenhuma navegação sai do compose: qualquer host != frontend é abortado
    // (o botão do WhatsApp é só conferido pelo href — RN-26, instrução do passo 6).
    await page.route("**/*", (route) => {
      const url = new URL(route.request().url());
      const allowed = ["frontend", "localhost", "127.0.0.1"];
      if (!allowed.includes(url.hostname)) {
        return route.abort();
      }
      return route.continue();
    });

    let salaoWhatsappHref = "";
    let reservationA104FreeSlot = "";
    let a104Username = "a-104";
    let a104TempPassword = "";

    // ---- Passo 1 — ADMIN: dashboard ----------------------------------------
    await runStep(page, testInfo, "01", "dashboard-admin-6meses", async () => {
      await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
      await nav(page, "Dashboard");
      await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
      await expect(page.getByText("Resumo", { exact: true })).toBeVisible();
      // F9-3: atalho "Últimos 6 meses" — é como a equipe vai apresentar o dashboard.
      await page.getByRole("button", { name: "Últimos 6 meses" }).click();
      await expect(page.getByText("Resumo", { exact: true })).toBeVisible();
      // Marca "Turno" no cabeçalho (header mobile / barra lateral desktop) e mapa de calor.
      const brand =
        testInfo.project.name === "mobile-360"
          ? page.locator("header").getByText("Turno", { exact: true })
          : page.locator("aside").getByText("Turno", { exact: true });
      await expect(brand).toBeVisible();
      await expect(page.getByText("Demanda por dia e horário")).toBeVisible();
    });

    // ---- Passo 2 — ADMIN: cadastrar unidade a-104 --------------------------
    await runStep(page, testInfo, "02", "cadastrar-unidade-a104", async () => {
      await nav(page, "Unidades");
      await page.waitForURL("**/admin/unidades");
      await page.getByRole("link", { name: "+ Nova unidade" }).click();
      await page.waitForURL("**/admin/unidades/nova");
      await page.getByLabel("Bloco").fill("A");
      await page.getByLabel("Número").fill("104");
      await page.locator("#resident-0-name").fill("Morador Exemplo A104");
      await page.locator("#resident-0-phone").fill("5562999990104");
      await page.locator("#resident-0-email").fill("a104@exemplo.test");
      await page.locator("#resident-0-cpf").fill("52998224725");
      await page.getByRole("button", { name: "Cadastrar unidade" }).click();
      await expect(page.getByText("Acesso gerado")).toBeVisible();
      await expect(page.getByText("Usuário")).toBeVisible();
      // RN-03: a senha temporária é gerada na hora — captura do modal.
      const ddTexts = (await page.locator("dl dd").allTextContents()).map((t) => t.trim());
      a104Username = ddTexts[0] ?? a104Username;
      a104TempPassword = ddTexts[1] ?? "";
      expect(a104TempPassword, "senha temporária capturada do modal").not.toBe("");
      const whatsappLink = page.getByRole("link", { name: "Enviar por WhatsApp" });
      await expect(whatsappLink).toHaveAttribute("href", /^https:\/\/wa\.me\//);
      await page.getByRole("button", { name: "Fechar" }).click();
      await page.waitForURL("**/admin/unidades");
      await logout(page);
    });

    // ---- Passo 3 — UNIT a-104: primeiro acesso -----------------------------
    await runStep(page, testInfo, "03", "primeiro-acesso-a104", async () => {
      expect(a104TempPassword, "senha temporária de a-104 (capturada no passo 2)").not.toBe("");
      await login(page, a104Username, a104TempPassword);
      await expect(page.getByText(/senha temporária/i).first()).toBeVisible();
      await page.getByRole("link", { name: "Trocar senha" }).click();
      await page.waitForURL("**/alterar-senha");
      await page.locator("#currentPassword").fill(a104TempPassword);
      await page.locator("#newPassword").fill("NovaSenha123");
      await page.locator("#confirmPassword").fill("NovaSenha123");
      await page.getByRole("button", { name: "Salvar nova senha" }).click();
      await expect(page.getByText("Senha alterada com sucesso.")).toBeVisible();
      await expect(page.getByText(/senha temporária/i)).toHaveCount(0);

      await nav(page, "Minha unidade");
      await page.waitForURL("**/minha-unidade");
      const addButton = page.getByRole("button", { name: /adicionar morador/i });
      if (await addButton.isVisible().catch(() => false)) {
        await addButton.click();
        const newIndex = (await page.locator("fieldset legend").count()) - 1;
        await page.locator(`#resident-${newIndex}-name`).fill("Morador Adicional A104");
        await page.locator(`#resident-${newIndex}-phone`).fill("5562999990105");
        await page.getByRole("button", { name: /salvar/i }).last().click();
      }
    });

    // ---- Passo 4 — UNIT a-104: catálogo + reserva gratuita -----------------
    await runStep(page, testInfo, "04", "reserva-gratuita-churrasqueira2", async () => {
      await nav(page, "Áreas");
      await page.waitForURL("**/areas");
      await expect(page.getByRole("heading", { name: "Áreas comuns" })).toBeVisible();
      const card = page.locator("li", { hasText: "Churrasqueira 2" });
      await expect(card.getByText(/Gratuita/)).toBeVisible();
      await card.getByRole("link", { name: "Ver detalhes" }).click();
      await expect(page.getByRole("heading", { name: /Churrasqueira 2/ })).toBeVisible();
      await page.getByRole("link", { name: "Reservar" }).click();
      await page.waitForURL(/\/areas\/.+\/reservar/);
      const { slot } = await fillWizardUpToReview(page, 11);
      reservationA104FreeSlot = slot;
      await page.getByRole("button", { name: "Confirmar reserva" }).click();
      await expect(page.getByRole("heading", { name: "Reserva confirmada" })).toBeVisible();
    });

    // ---- Passo 5 — UNIT a-104: sobreposição + reserva hoje -----------------
    await runStep(page, testInfo, "05", "sobreposicao-e-reserva-hoje", async () => {
      await openArea(page, "Churrasqueira 2");
      await page.getByRole("link", { name: "Reservar" }).click();
      await page.waitForURL(/\/areas\/.+\/reservar/);
      // Amanhã de novo: o bloco que acabamos de reservar deve estar indisponível
      // (RN-24 — o front nem deixa selecionar, então a demonstração é a grade
      // já mostrando "Indisponível" em vez de um 409 do backend).
      await page.getByRole("button", { name: "11", exact: true }).click();
      await page.getByRole("button", { name: "Continuar" }).click();
      // O nome acessível do botão junta o rótulo com "Indisponível" sem separador
      // (TimeSlotPicker não usa espaço entre os dois nós de texto) — casa só o prefixo.
      const bookedSlot = page.getByRole("button", { name: new RegExp(`^${reservationA104FreeSlot}`) });
      await expect(bookedSlot).toBeDisabled();
      await expect(bookedSlot).toContainText("Indisponível");

      // Hoje (10): dia aberto mas não reservável (RN-20) — clique mostra o motivo.
      await openArea(page, "Churrasqueira 2");
      await page.getByRole("link", { name: "Reservar" }).click();
      await page.waitForURL(/\/areas\/.+\/reservar/);
      // F9-3 (após a correção do Bug 2 do ensaio anterior): o rótulo acessível do dia não
      // reservável agora é "{dia}, não disponível: {motivo}" (sem aria-disabled), então o
      // seletor por nome exato "10" não casa mais — usar o prefixo do dia. O clique volta a
      // ser normal (sem force), já que o botão não carrega mais aria-disabled.
      await page.getByRole("button", { name: /^10, não disponível:/ }).click();
      // .last(): a faixa "Horário simulado" também é role="status" e vem primeiro no DOM.
      await expect(page.getByRole("status").last()).toContainText(/mesmo dia|dia seguinte|antecedência/i);
    });

    // ---- Passo 6 — UNIT a-104: reserva paga (Salão) ------------------------
    await runStep(page, testInfo, "06", "reserva-paga-salao-whatsapp", async () => {
      await openArea(page, "Salão de festas");
      await page.getByRole("link", { name: "Reservar" }).click();
      await page.waitForURL(/\/areas\/.+\/reservar/);
      await fillWizardUpToReview(page, 12);
      await page.getByRole("button", { name: "Confirmar reserva" }).click();
      await expect(page.getByRole("heading", { name: "Reserva pendente de pagamento" })).toBeVisible();
      const whatsappLink = page.getByRole("link", { name: "Pagar via WhatsApp" });
      await expect(whatsappLink).toBeVisible();
      const href = await whatsappLink.getAttribute("href");
      expect(href ?? "").toMatch(/^https:\/\/wa\.me\//);
      salaoWhatsappHref = href ?? "";
      await logout(page);
    });

    // ---- Passo 7 — ADMIN: confirmar pagamento ------------------------------
    await runStep(page, testInfo, "07", "confirmar-pagamento", async () => {
      expect(salaoWhatsappHref, "href do WhatsApp capturado no passo 6").not.toBe("");
      await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
      // F9-3 (reensaio final): mede o tempo até "Confirmações" aparecer e
      // registra os requests concluídos nesse intervalo (Bug 3, docs/12).
      const confirmationsTiming = await measureConfirmationsNavAppearance(page, testInfo);
      expect(confirmationsTiming.appeared, "item 'Confirmações' apareceu na navegação").toBe(true);
      results.push({
        num: "07-medicao",
        slug: "confirmacoes-tempo-ate-aparecer",
        status: confirmationsTiming.elapsedMs > 20_000 ? "fail" : "ok",
        detail: `${confirmationsTiming.elapsedMs} ms (${testInfo.project.name}); ${confirmationsTiming.network.length} requests concluídos nesse intervalo`,
      });
      if (testInfo.project.name === "desktop") {
        await page.locator("aside nav a", { hasText: "Confirmações" }).first().click();
      } else {
        // O menu já está aberto de `measureConfirmationsNavAppearance`; o link ainda está visível.
        await page.locator("#menu-mobile a", { hasText: "Confirmações" }).first().click();
      }
      await page.waitForURL("**/admin/confirmacoes");
      const card = page.locator("li", { hasText: "Salão de festas" }).filter({ hasText: "a-104" });
      await expect(card).toBeVisible();
      await card.getByRole("button", { name: "Confirmar pagamento" }).click();
      await page.getByRole("button", { name: "Confirmar pagamento" }).last().click();
      await expect(page.locator("li", { hasText: "Salão de festas" }).filter({ hasText: "a-104" })).toHaveCount(0);
      await logout(page);

      await login(page, a104Username, "NovaSenha123");
      await nav(page, "Minhas reservas");
      await page.waitForURL("**/minhas-reservas");
      const reservationRow = page.locator("li", { hasText: "Salão de festas" });
      await expect(reservationRow.getByText("Confirmada")).toBeVisible();
      await logout(page);
    });

    // ---- Passo 8 — UNIT a-101: reportar problema ---------------------------
    await runStep(page, testInfo, "08", "reportar-problema-a101", async () => {
      await login(page, "a-101", DEMO_PASSWORD);
      await nav(page, "Minhas reservas");
      await page.waitForURL("**/minhas-reservas");
      await page.getByRole("tab", { name: "Anteriores" }).click();
      const reportButton = page.getByRole("link", { name: "Reportar" }).first();
      await expect(reportButton).toBeVisible();
      await reportButton.click();
      await page.waitForURL(/\/minhas-reservas\/.+\/reportar/);
      await page.locator("#report-category").selectOption("DAMAGE");
      await page.locator("#report-resident").selectOption({ index: 1 });
      await page
        .locator("#report-description")
        .fill("Churrasqueira com grelha danificada, encontrada assim na chegada.");
      await page.getByRole("button", { name: "Enviar report" }).click();
      await page.waitForURL("**/meus-reports");
      await expect(page.getByRole("heading", { name: /meus reports/i })).toBeVisible();
      await logout(page);
    });

    // ---- Passo 9 — SYNDIC: report → Em manutenção + vistoria ---------------
    await runStep(page, testInfo, "09", "sindico-report-manutencao-vistoria", async () => {
      await login(page, SYNDIC_EMAIL, DEMO_PASSWORD);
      await expect(page.getByRole("heading", { name: "Início" })).toBeVisible();
      await ensureHomeLoaded(page);
      await expect(page.getByRole("heading", { name: /Reports abertos/ })).toBeVisible();
      // F9-3: captura dedicada da página inicial do síndico (`/painel`).
      await shot(page, testInfo, "09", "sindico-painel-inicio");

      await nav(page, "Reports");
      await page.waitForURL("**/reports");
      const openReport = page
        .locator("li", { hasText: "Churrasqueira 1" })
        .filter({ hasText: "Aberto" })
        .first();
      await expect(openReport).toBeVisible();
      await openReport.click();

      const whatsappLink = page.getByRole("link", { name: "Falar no WhatsApp" });
      if (await whatsappLink.isVisible().catch(() => false)) {
        await expect(whatsappLink).toHaveAttribute("href", /^https:\/\/wa\.me\//);
      }

      await page.getByRole("button", { name: "Em manutenção" }).click();
      await page.getByRole("button", { name: "Confirmar" }).click();
      await expect(statusBadge(page, "Em manutenção")).toBeVisible();

      // Vistoria + comparador (RF-SIN-04/D-24) na própria Churrasqueira 1.
      await openArea(page, "Churrasqueira 1");
      await page.getByRole("link", { name: "Vistorias" }).click();
      await expect(page.getByRole("heading", { name: "Vistorias" })).toBeVisible();
      await expect(page.locator("li", { hasText: /Vistoriado por/ }).first()).toBeVisible();

      await openArea(page, "Churrasqueira 1");
      await page.getByRole("link", { name: "Comparar" }).click();
      await expect(page.getByRole("heading", { name: "Comparador de fotos" })).toBeVisible();
    });

    // ---- Passo 10 — ADMIN: Quadra → Em manutenção --------------------------
    await runStep(page, testInfo, "10", "area-manutencao-quadra", async () => {
      await logout(page);
      await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
      await openArea(page, "Quadra poliesportiva");
      await page.getByRole("button", { name: "Alterar status" }).click();
      const dialog = page.locator("dialog[open]");
      await expect(dialog).toBeVisible();
      await dialog.getByLabel("Novo status").selectOption("MAINTENANCE");
      await dialog.getByRole("button", { name: "Alterar status" }).click();

      // Espera a resposta do primeiro clique: ou o diálogo já fecha (sem reservas futuras
      // conflitantes) ou aparece a lista de "Reservas futuras afetadas" (RN-16). Um `isVisible`
      // com timeout curto e `.catch(() => false)` mascarava lentidão real da rede como "sem
      // conflito" e pulava o preenchimento da justificativa — troca por uma corrida explícita
      // entre os dois desfechos possíveis, com timeout mais folgado.
      const affectedHeading = dialog.getByText(/Reservas futuras afetadas/);
      const outcome = await Promise.race([
        affectedHeading
          .waitFor({ state: "visible", timeout: 20_000 })
          .then(() => "affected" as const),
        dialog.waitFor({ state: "hidden", timeout: 20_000 }).then(() => "closed" as const),
      ]).catch(() => "timeout" as const);

      if (outcome === "affected") {
        const justification = dialog.getByLabel("Justificativa (mínimo 10 caracteres)");
        await justification.fill("Manutenção emergencial no piso da quadra, reservas futuras canceladas.");
        await expect(justification).toHaveValue(/Manutenção emergencial/);
        await dialog.getByRole("button", { name: "Confirmar cancelamento" }).click();
        const dialogError = dialog.getByRole("alert");
        if (await dialogError.isVisible({ timeout: 3_000 }).catch(() => false)) {
          console.log(`[observação] Falha ao confirmar cancelamento da Quadra: ${await dialogError.textContent()}`);
        }
      }
      await expect(dialog).toBeHidden({ timeout: 15_000 });
      await expect(statusBadge(page, "Em manutenção")).toBeVisible();
    });

    // ---- Passo 11 — ADMIN: motor de regras (limite por unidade) -----------
    await runStep(page, testInfo, "11", "limite-reservas-configuracoes", async () => {
      await nav(page, "Configurações");
      await page.waitForURL("**/admin/configuracoes");
      await expect(page.getByRole("heading", { name: "Configurações" })).toBeVisible();
      const limitField = page.locator("#maxActiveBookingsPerUnit");
      await limitField.fill("4");
      await page.getByRole("button", { name: "Salvar configurações" }).click();
      await expect(page.getByText("Configurações salvas.")).toBeVisible();
      await logout(page);

      await login(page, "b-201", DEMO_PASSWORD);
      await openArea(page, "Espaço gourmet");
      await page.getByRole("link", { name: "Reservar" }).click();
      await page.waitForURL(/\/areas\/.+\/reservar/);
      await fillWizardUpToReview(page, 13);
      await page.getByRole("button", { name: "Confirmar reserva" }).click();
      await expect(page.getByRole("heading", { name: "Reserva confirmada" })).toBeVisible();
      await logout(page);
    });

    // ---- Passo 12 — ADMIN: exportar XLSX -----------------------------------
    await runStep(page, testInfo, "12", "exportar-xlsx", async () => {
      await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
      await nav(page, "Dashboard");
      await page.waitForURL("**/dashboard");
      // F9-3 (reensaio final) — Bug 5 (novo, achado de QA, só no teste): `page.locator("li", {
      // hasText: "Reservas" }).first()` também casava o card de área "Churrasqueira 1 — Bloco A"
      // (a lista `<ul className="... md:hidden">` de DashboardPage.tsx, que tem um <li> por área
      // com "Reservas: N" no `<dl>`) — essa lista fica só com `display:none` em md:hidden, nunca
      // sai do DOM, e o `hasText` do Playwright não filtra por visibilidade. Como essa área vem
      // antes da seção "Exportar relatórios" no JSX, `.first()` sempre resolvia para o card da
      // área (sem nenhum botão "Exportar XLSX" dentro), nunca para a linha de exportação — em
      // QUALQUER viewport, não só desktop (confirmado com diagnóstico: contagem de matches = 1,
      // sempre o card da área). Sem consequência para quem usa o app de verdade (só um dos dois
      // é visível na tela ao mesmo tempo), mas quebra um seletor de teste que não escopava a
      // seção certa. Corrigido escopando a busca à `<section>` que contém o heading "Exportar
      // relatórios".
      const exportSection = page.locator("section", {
        has: page.getByRole("heading", { name: "Exportar relatórios" }),
      });
      const reservationsCard = exportSection.locator("li", { hasText: "Reservas" }).first();
      const exportRequests: NetworkSample[] = [];
      const onExportRequestFinished = async (request: import("@playwright/test").Request) => {
        if (!request.url().includes("/exports/")) return;
        try {
          const response = await request.response();
          if (!response) return;
          const timing = request.timing();
          exportRequests.push({
            url: request.url(),
            status: response.status(),
            durationMs: timing.responseEnd >= 0 ? Math.round(timing.responseEnd) : -1,
          });
        } catch {
          /* melhor esforço */
        }
      };
      page.on("requestfinished", onExportRequestFinished);
      const clickedAt = Date.now();
      const [download] = await Promise.all([
        page.waitForEvent("download", { timeout: 20_000 }),
        reservationsCard.getByRole("button", { name: "Exportar XLSX" }).click(),
      ]);
      const downloadElapsedMs = Date.now() - clickedAt;
      page.off("requestfinished", onExportRequestFinished);
      console.log(`[diagnóstico][${testInfo.project.name}] download disparou em ${downloadElapsedMs} ms após o clique.`);
      for (const sample of exportRequests) {
        console.log(
          `[diagnóstico][${testInfo.project.name}]   /exports: ${sample.status} ${sample.durationMs}ms ${sample.url}`,
        );
      }
      const suggested = download.suggestedFilename();
      expect(suggested).toMatch(/^turno-.*\.xlsx$/);
      const resultsDir = path.join(__dirname, "test-results");
      fs.mkdirSync(resultsDir, { recursive: true });
      const savedPath = path.join(resultsDir, `${testInfo.project.name}-${suggested}`);
      await download.saveAs(savedPath);
      const size = fs.statSync(savedPath).size;
      expect(size, "tamanho do XLSX exportado").toBeGreaterThan(0);
    });

    // ---- Resumo final (stdout) --------------------------------------------
    console.log(`\n===== RESUMO ${testInfo.project.name} =====`);
    for (const r of results) {
      console.log(`  [${r.status.toUpperCase()}] Passo ${r.num} — ${r.slug}${r.detail ? ` :: ${r.detail}` : ""}`);
    }
    console.log(`Erros de console: ${consoleErrors.length}`);
    console.log(`Erros de página (pageerror): ${pageErrors.length}`);
    console.log("=====================================\n");

    const failed = results.filter((r) => r.status === "fail");
    if (failed.length > 0) {
      // Não derruba o teste (queremos o relatório completo para os dois
      // viewports); a falha fica registrada no console e nas capturas.
      test.info().annotations.push({
        type: "roteiro-falhas",
        description: failed.map((f) => `${f.num}:${f.slug}`).join(", "),
      });
    }
  });
});
