# Sistema Integrado de Gestão e Reserva de Áreas de Lazer para Condomínios Residenciais

Projeto acadêmico da disciplina **CIC1060 – Arquitetura e Desenho de Software**. Sistema web de
reserva de áreas comuns (churrasqueira, salão de festas, quadra, piscina...) para condomínios
residenciais de pequeno e médio porte, com regras de negócio configuráveis por condomínio,
histórico transparente de uso e conservação dos espaços, e concorrência de reservas resolvida com
garantia do banco de dados.

> Uso previsto: demonstração conduzida pela própria equipe, com dados fictícios. Não há usuários
> reais nem publicação online.

## Stack

- **Backend:** Java 21, Spring Boot 3, Spring Security, Data JPA, Flyway, springdoc-openapi.
- **Banco:** PostgreSQL 16 (extensão `btree_gist`, usada na constraint anti-sobreposição de
  reservas).
- **Frontend:** React + TypeScript + Vite, React Router, TanStack Query, React Hook Form + Zod,
  Tailwind CSS.
- **Infra local:** Docker Compose (Postgres, storage S3-compatível para fotos, backend, frontend
  com nginx), tudo em `http://localhost`.

## Estrutura do repositório

```
/
├── docker-compose.yml
├── backend/     API REST (Spring Boot) — domínio, regras de negócio e testes
├── frontend/    Interface web (React + TypeScript)
└── scripts/     Scripts de build/verificação e do ambiente de demonstração
```

## Como rodar

Pré-requisito: Docker (Desktop no Windows/macOS, Engine + Compose no Linux). Não precisa de Java
nem Node instalados na máquina.

```bash
docker compose up --build -d       # sobe tudo em http://localhost
docker compose ps                  # os 4 serviços devem ficar "healthy"
docker compose down                # para (acrescente -v para apagar banco e fotos)
```

- Interface: `http://localhost`
- Documentação da API (Swagger): `http://localhost/api/swagger-ui.html`
- Health check: `http://localhost/actuator/health` (via proxy interno do backend)

### Ambiente de demonstração

Um perfil separado (`demo`) sobe com dados fictícios prontos (unidades, áreas, reservas em
diferentes estados, reports, vistorias) e um relógio simulado, para que o roteiro de apresentação
funcione em qualquer horário real:

```bash
# Linux/macOS (Git Bash no Windows)
bash scripts/demo-up.sh
bash scripts/demo-reset.sh   # apaga tudo e recria o cenário do zero

# Windows (PowerShell)
.\scripts\demo-up.ps1
.\scripts\demo-reset.ps1
```

Na primeira execução, o script gera um `.env` local com segredos aleatórios e imprime no console as
senhas das contas fictícias de demonstração.

## Contas de exemplo (perfil padrão `local`)

Ao subir com `docker compose up --build` (sem trocar `SPRING_PROFILES_ACTIVE`), o sistema sobe no
perfil `local`, com um condomínio fictício e uma conta de cada perfil de acesso:

| Perfil | Login | Senha |
|---|---|---|
| **Administração** (`ADMIN`) | `admin@exemplo.test` | `troque-esta-senha` |
| **Síndico** (`SYNDIC`) | `sindico@exemplo.test` | `LocalDev123!` |
| **Morador — unidade A-101** (`UNIT`) | `a-101` | `LocalDev123!` |
| **Morador — unidade B-201** (`UNIT`) | `b-201` | `LocalDev123!` |

Essas são credenciais de desenvolvimento, valem só para o ambiente local com dados fictícios e
podem ser trocadas por variáveis de ambiente (veja `.env.example`).

## Perfis de acesso

- **Administração (`ADMIN`):** cadastra unidades, moradores e áreas; confirma pagamentos;
  configura as regras do condomínio; acessa relatórios completos.
- **Síndico (`SYNDIC`):** agenda, reports, vistorias e comparador de fotos; dashboard só leitura.
- **Morador (`UNIT`):** uma conta por unidade, compartilhada por quem mora lá; reserva áreas,
  acompanha "Minhas reservas" e abre reports.
