# Comece aqui — Projeto Condomínio

## 1. Pré-requisitos (Windows)
- Git for Windows (o Claude Code usa o Git Bash no Windows)
- Claude Code instalado e logado (`claude --version`)
- Docker Desktop, Java 21 (JDK) e Node.js LTS — necessários a partir da Fase 0

## 2. Primeira vez
Abra o PowerShell:
```powershell
cd "D:\Projeto Condominio"
git init
git add .
git commit -m "chore: documentação, agentes e skills do MVP"
claude
```
- Aceite o aviso de confiança da pasta (workspace trust). Sem isso, hooks de projeto não rodam.
- O cabeçalho deve mostrar `@orchestrator` (definido em `.claude/settings.json`).

## 3. Primeiro prompt sugerido
```
Leia o CLAUDE.md e todos os arquivos de docs/. Depois execute a Fase 0 do docs/04-plano-mvp.md,
tarefa por tarefa, delegando aos subagentes conforme o template e o roteamento de skills do
docs/06-skills.md. Antes da F0-8, pare e me mostre a proposta de PRODUCT.md e DESIGN.md.
```

## 4. Como conferir que está funcionando
- `/tasks` durante uma delegação: cada subagente deve aparecer rodando em Sonnet.
- O relatório de cada subagente termina com `Skills usadas: ...`.
- Se um agente ou skill não carregar: feche e abra com `claude --debug`.

## 5. Entre sessões
- `/handoff` gera um resumo para continuar numa sessão nova.
- O status das tarefas fica marcado em `docs/04-plano-mvp.md`.

## 6. Atualizar as skills
```powershell
.\scripts\install-skills.ps1
```
(Se o PowerShell bloquear scripts: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.)

## 7. Rodar e demonstrar o sistema
- **Desenvolvimento:** `docker compose up --build` → `http://localhost` (só precisa do Docker Desktop).
- **Apresentação:** `.\scripts\demo-reset.ps1` e depois `.\scripts\demo-up.ps1` (dados fictícios e relógio simulado). Roteiro e contas em `docs/08-roteiro-demo.md`.
- **Plateia no celular:** mesma rede → `http://<IP-do-PC>`; outra rede → `.\scripts\demo-up.ps1 -Tunnel`.
- Não haverá usuários reais nem publicação online nesta fase (D-31).
