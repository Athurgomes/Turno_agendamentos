# Guia de Contribuição e Fluxo de Trabalho

Este repositório possui regras estritas de proteção na branch `main` para garantir a estabilidade do projeto. **Commits diretos na `main` estão bloqueados**. Todo código novo deve ser desenvolvido em uma branch separada, enviado via Pull Request (PR) e validado obrigatoriamente pela nossa esteira de testes automatizados (CI).

Abaixo estão as instruções para contribuir, aprovar PRs e acompanhar a automação.

---

## 1. Fluxo de Trabalho (Ordem dos Commits)

Sempre inicie qualquer alteração garantindo que você está partindo da versão mais atualizada do código.

**Passo 1: Atualize sua branch principal local**
`bash
git checkout main
git pull origin main
`

**Passo 2: Crie uma nova branch para a sua tarefa**
Use nomes descritivos indicando o contexto da alteração (ex: `feature/nova-tela`, `fix/erro-login`, `docs/readme`).
`bash
git checkout -b nome-da-sua-branch
`

**Passo 3: Codifique e adicione suas alterações**
Após finalizar o trabalho, adicione os arquivos modificados.
`bash
git add .
`

**Passo 4: Registre o Commit**
Use uma mensagem clara e objetiva sobre o que foi feito na alteração.
`bash
git commit -m "Sua mensagem clara sobre o que foi alterado"
`

**Passo 5: Envie sua branch para o repositório remoto (GitHub)**
Como a branch ainda não existe no GitHub, você precisa usar a flag `-u` na primeira vez.
`bash
git push -u origin nome-da-sua-branch
`

---

## 2. Pull Request (PR) e Testes Automatizados (CI)

Após enviar sua branch, solicite a integração do código à `main` abrindo um Pull Request.

1. Acesse o repositório no GitHub.
2. Clique no botão verde **Compare & pull request** (que aparecerá automaticamente no topo).
3. Adicione um título, descreva rapidamente o que foi feito e clique em **Create pull request**.

### Pipeline de Integração Contínua (Testes)

Assim que o PR for aberto, o GitHub Actions assumirá o controle e rodará em paralelo nossa pipeline configurada:

- **Backend (Java):** Executa a compilação e todos os testes (incluindo validações de integração com Testcontainers via Docker) usando o Maven. Também gera e publica relatórios de cobertura via JaCoCo.
- **Frontend (Node):** Executa a instalação limpa das dependências, validação de estilo de código (Lint), testes unitários e simulação de build da aplicação.

**⚠️ Regra de Bloqueio de Segurança:** Se qualquer um dos testes falhar (Backend ou Frontend), a aprovação do PR será **estritamente bloqueada**. Ninguém poderá fazer o merge. Para resolver, corrija o erro no seu computador, faça um novo `git commit` e dê um novo `git push` na mesma branch. O GitHub rodará os testes novamente de forma automática.

---

## 3. Aprovação Automática (Auto-merge)

Você não precisa aguardar a conclusão de todos os testes para concluir sua tarefa. O repositório está configurado para aprovar e mesclar PRs sozinho caso os testes validem o código com sucesso.

- Logo após abrir o Pull Request, localize o botão azul **Enable auto-merge** (logo abaixo do status dos testes) e clique nele.
- **Se os testes passarem (✅):** O GitHub fará o merge automaticamente do seu código na `main` e, em seguida, deletará sua branch de trabalho para manter o repositório limpo.
- **Se os testes falharem (❌):** O processo de auto-merge será cancelado instantaneamente. O PR ficará bloqueado, aguardando que você faça os ajustes necessários.

---

## 4. Notificações de Falha

Se uma automação falhar ou o *auto-merge* for cancelado pelos testes, os alertas serão disparados automaticamente. 
Dependendo de como o projeto está integrado, você poderá verificar o erro através de:
- **E-mail:** Pela conta vinculada ao seu perfil do GitHub.
- **Discord / Slack:** Caso os Webhooks de notificação do repositório estejam ativos para o canal da equipe.
- **Direto no PR:** Clicando em "Details" ao lado do job que falhou para ler os logs exatos do erro.