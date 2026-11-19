# ThermoCity — API de Cadastro (Java / Spring Boot)

> **ThermoCity** é um gêmeo digital de risco de calor para **Presidente Prudente-SP**. O objetivo é
> encontrar e contatar idosos vulneráveis (60+) **antes** que uma onda de calor vire emergência de saúde.
>
> Projeto da Global Solution 2026/2 — FIAP, 2TDS (tema *CitizenTech*).

## Integrantes

| Nome | RM |
| --- | --- |
| Arthur Graciani | RM561728 |
| Gustavo Oliveira | RM566358 |
| João Pedro Scarpin | RM565421 |
| Lucas Hideki | RM565355 |
| Wesley Andrade | RM563593 |

O sistema é dividido em três camadas, cada uma respondendo uma pergunta:

| Camada | Pergunta | Responsável |
|---|---|---|
| **ONDE** | Quais setores da cidade são mais vulneráveis? | base territorial: setores censitários, IPVS, UBS e refúgios climáticos |
| **QUANDO** | Quando o calor fica perigoso? | `thermocity-weather-api` (.NET): coleta climática e níveis de calor NC1–NC5 |
| **QUEM** | Quem precisa ser contatado e por quem? | **esta API**: cadastro das pessoas acompanhadas, usuários, contatos da busca ativa e cobertura |

Fluxo resumido:

1. Um **cidadão** cadastra um familiar idoso, com consentimento LGPD.
2. A API descobre o **setor censitário** e a **UBS de referência** a partir da coordenada do endereço.
3. Em dias de calor, o **agente de saúde** da UBS recebe a lista de busca ativa, liga para as pessoas e
   registra o resultado de cada contato.
4. O **gestor** acompanha a **cobertura do dia**: quantos foram contatados e quem precisa de ajuda.

O banco compartilhado (PostgreSQL no Azure) já contém **5.000 pessoas sintéticas** (`sintetico = true`),
geradas a partir do Censo, com telefones fictícios. Elas permitem demonstrar o sistema sem expor dados reais.

---

## Sumário

- [Tecnologias](#tecnologias)
- [Como rodar](#como-rodar)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Partes do projeto](#partes-do-projeto)
- [Endpoints](#endpoints)
- [Formato de erro](#formato-de-erro)
- [Testes](#testes)
- [LGPD](#lgpd)
- [Próximos passos](#próximos-passos)

---

## Tecnologias

- **Java 21** e **Spring Boot 4.1**: Web MVC, Data JPA, Validation, Security
- **PostgreSQL** (Azure) em produção; **H2** em memória nos testes
- **JWT RS256** (`com.auth0:java-jwt`): a API Java assina o token com a chave privada, e a API .NET
  valida só com a chave pública
- **JTS Topology Suite**: verifica se uma coordenada cai dentro de um setor censitário (o banco não tem
  PostGIS)
- **springdoc-openapi**: Swagger UI
- **Gradle** (wrapper incluso)
- CI/CD com **GitHub Actions** → **Azure Web App** (`thermocity-cadastro-api`)

---

## Como rodar

### 1. Pré-requisitos

- JDK 21
- Acesso a um PostgreSQL com as tabelas do ThermoCity (o banco compartilhado do projeto)
- Um par de chaves RSA para o JWT (passo 3)

### 2. Preparar o banco

As tabelas territoriais (`tmc_setor`, `tmc_servico`) e as pessoas sintéticas
(`tmc_pessoa_sintetica`) já existem no banco compartilhado. O script abaixo cria o que esta API
acrescenta:

```
scripts/script-bd.sql
```

O script é **idempotente** (pode rodar mais de uma vez) e roda numa transação única. Ele:

- cria `tmc_usuario`;
- acrescenta em `tmc_pessoa_sintetica` as colunas de titular, data de nascimento, endereço, CEP e
  `atualizado_em`;
- remaneja para a UBS válida mais próxima as pessoas que estavam ligadas a unidades que não atendem a
  população: penitenciária, centro de ressocialização e triagem COVID;
- cria as tabelas da busca ativa: `tmc_lista_busca_ativa`, `tmc_lista_item`, `tmc_contato` e
  `tmc_evento_processado`.

A aplicação roda com `spring.jpa.hibernate.ddl-auto=none`, ou seja, **não altera o schema do banco**.

### 3. Chaves do JWT

Gere um par de chaves RSA (fora do repositório ou em `src/main/resources/keys/`, que está no
`.gitignore`):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private_key.pem
openssl rsa -in private_key.pem -pubout -out public_key.pem
```

> A chave **privada** nunca vai para o Git. Só a pública é compartilhada com a API .NET.

### 4. Configuração — `application.properties`

O `src/main/resources/application.properties` **não contém nenhum segredo**. Todo valor sensível ou
que muda por ambiente é lido de **variáveis de ambiente** pela sintaxe `${VARIAVEL:padrão}`:

```properties
# Banco de dados
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=${DDL_AUTO:none}

# JWT RS256
api.security.token.private-key=${JWT_PRIVATE_KEY:classpath:keys/private_key.pem}
api.security.token.public-key=${JWT_PUBLIC_KEY:classpath:keys/public_key.pem}
api.security.token.issuer=${JWT_ISSUER:thermocity}
api.security.token.expiration-hours=${JWT_EXPIRATION_HOURS:2}

# CORS (origens do front separadas por vírgula)
cors.allowed-origins=${CORS_ALLOWED_ORIGINS:*}

# Regras do ThermoCity
thermocity.ubs.excluidas=CNES-8016305,CNES-8016291,CNES-118796
thermocity.refugios.revisoes=manter,potencial
# Pesos da lista priorizada (os demais estão no arquivo; ver "Regra de prioridade v1")
thermocity.prioridade.pesos.mora-sozinho=3

# RabbitMQ (evento de nível de calor publicado pela API .NET)
spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${RABBITMQ_PORT:5672}
spring.rabbitmq.username=${RABBITMQ_USERNAME:guest}
spring.rabbitmq.password=${RABBITMQ_PASSWORD:}
spring.rabbitmq.virtual-host=${RABBITMQ_VHOST:/}
thermocity.rabbitmq.consumidor-ativo=${RABBITMQ_CONSUMIDOR_ATIVO:true}
```

| Variável | Obrigatória | Para que serve | Exemplo |
|---|---|---|---|
| `DB_URL` | sim | URL JDBC do PostgreSQL | `jdbc:postgresql://<servidor>.postgres.database.azure.com:5432/<banco>?sslmode=require` |
| `DB_USERNAME` | sim | usuário do banco | `<usuario>` |
| `DB_PASSWORD` | sim | senha do banco | `<senha>` |
| `JWT_PRIVATE_KEY` | não* | local da chave privada | `file:C:/chaves/private_key.pem` |
| `JWT_PUBLIC_KEY` | não* | local da chave pública | `file:C:/chaves/public_key.pem` |
| `JWT_ISSUER` | não | emissor do token (deve ser o mesmo na API .NET) | `thermocity` |
| `JWT_EXPIRATION_HOURS` | não | validade do token em horas | `2` |
| `CORS_ALLOWED_ORIGINS` | não | origens liberadas para o front | `https://meu-front.vercel.app` |
| `DDL_AUTO` | não | estratégia do Hibernate (mantenha `none`) | `none` |
| `RABBITMQ_HOST` | sim** | servidor do RabbitMQ | `<nome>.canadacentral.azurecontainer.io` |
| `RABBITMQ_PORT` | não | porta AMQP | `5672` |
| `RABBITMQ_USERNAME` | sim** | usuário do RabbitMQ | `<usuario>` |
| `RABBITMQ_PASSWORD` | sim** | senha do RabbitMQ | `<senha>` |
| `RABBITMQ_VHOST` | não | virtual host | `/` |
| `RABBITMQ_CONSUMIDOR_ATIVO` | não | `false` desliga o consumo da fila (a API sobe sem RabbitMQ) | `true` |

\* Sem a variável, a aplicação procura a chave em `classpath:keys/`.
\** Para receber o evento da API de clima. Sem RabbitMQ a API funciona, e a lista pode ser gerada pela
simulação do gestor (`POST /simulacoes/nivel-calor`).

`thermocity.ubs.excluidas` não é segredo e por isso fica fixo no arquivo. São os ids das três
unidades cadastradas como UBS que **não atendem a população**; elas nunca são escolhidas como UBS de
referência.

**Primeiro gestor.** Ninguém se cadastra como gestor pelo endpoint público, e a API não cria usuários
na subida. O primeiro GESTOR é inserido direto no banco, uma única vez; depois disso, os outros agentes
e gestores são criados por `POST /usuarios` com o token dele. A senha fica em hash BCrypt (com a
extensão `pgcrypto`, o próprio PostgreSQL gera o hash):

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO tmc_usuario (nome, email, senha, telefone, cpf, cep, papel)
VALUES ('<nome>', '<email em minúsculas>', crypt('<senha forte>', gen_salt('bf', 10)),
        '<telefone só dígitos>', '<cpf só dígitos>', '<cep só dígitos>', 'GESTOR');
```

Use um CPF válido e que não esteja em uso, porque `cpf` e `email` são únicos. Não versione esse script
com a senha preenchida.


### 5. Subir a aplicação

**PowerShell (Windows):**

```powershell
$env:DB_URL = "jdbc:postgresql://<servidor>:5432/<banco>?sslmode=require"
$env:DB_USERNAME = "<usuario>"
$env:DB_PASSWORD = "<senha>"
$env:JWT_PRIVATE_KEY = "file:C:/chaves/private_key.pem"
$env:JWT_PUBLIC_KEY = "file:C:/chaves/public_key.pem"
$env:RABBITMQ_HOST = "<servidor do RabbitMQ>"
$env:RABBITMQ_USERNAME = "<usuario>"
$env:RABBITMQ_PASSWORD = "<senha>"
.\gradlew.bat bootRun
```

**Bash (Linux/macOS/Git Bash):**

```bash
export DB_URL="jdbc:postgresql://<servidor>:5432/<banco>?sslmode=require"
export DB_USERNAME="<usuario>"
export DB_PASSWORD="<senha>"
export JWT_PRIVATE_KEY="file:/home/eu/chaves/private_key.pem"
export JWT_PUBLIC_KEY="file:/home/eu/chaves/public_key.pem"
export RABBITMQ_HOST="<servidor do RabbitMQ>"
export RABBITMQ_USERNAME="<usuario>"
export RABBITMQ_PASSWORD="<senha>"
./gradlew bootRun
```

No **IntelliJ**, as mesmas variáveis vão em *Run/Debug Configurations → Environment variables*.
No **Azure Web App**, vão em *Configuração → Configurações de aplicativo*.

A API sobe em `http://localhost:8080`, com a documentação em
**`http://localhost:8080/swagger-ui.html`**. No Swagger, faça login em `/auth/login`, copie o `token` e
clique em **Authorize**.

### 6. Roteiro rápido de teste manual

1. `POST /auth/login` com o primeiro gestor → token do GESTOR.
2. `POST /usuarios` (GESTOR) → cria um AGENTE vinculado a uma UBS.
3. `GET /pessoas?size=20` (GESTOR) → lista as pessoas sintéticas do banco.
4. `POST /auth/cadastro` → cria um CIDADAO; faça login com ele.
5. `POST /pessoas` (CIDADAO) → cadastra um familiar; a resposta mostra o setor e a UBS calculados.
6. `POST /simulacoes/nivel-calor` (GESTOR) com `{"nivelNovo": 4}` → gera as listas do dia.
7. Login como o AGENTE → `GET /listas/hoje` → pessoas da UBS dele em ordem de prioridade.
8. `POST /contatos` (AGENTE) para a primeira da lista → o status dela muda na lista e na
   `GET /cobertura/hoje` (GESTOR).
9. `POST /simulacoes/nivel-calor` com `{"nivelNovo": 2}` → as listas do dia são encerradas
   (`GET /listas/hoje` passa a responder 204).

---

## Estrutura do projeto

```
api-java-cadastro/
├── build.gradle                     dependências e toolchain Java 21
├── scripts/
│   └── script-bd.sql                DDL das tabelas desta API (idempotente)
├── .github/workflows/               pipeline de build e deploy no Azure
└── src/
    ├── main/
    │   ├── java/br/com/fiap/ThermoCity/
    │   │   ├── ThermoCityApplication.java
    │   │   ├── common/              utilitários compartilhados (paginação, distância)
    │   │   ├── config/              OpenAPI/Swagger e relógio no fuso de Brasília
    │   │   ├── exception/           exceções de negócio e handler global de erros
    │   │   ├── security/            login, cadastro público, JWT e regras de acesso
    │   │   ├── user/                usuários, papéis, /usuarios e /usuarios/me
    │   │   ├── geolocalizacao/      CEP → coordenadas (AwesomeAPI / BrasilAPI)
    │   │   ├── territorio/          setores censitários, UBS, refúgios e GeoService
    │   │   ├── pessoa/              CRUD /pessoas (pessoas acompanhadas)
    │   │   ├── contato/             /contatos e /cobertura/hoje
    │   │   └── busca/               lista de busca ativa do dia e seus itens
    │   └── resources/
    │       ├── application.properties
    │       └── keys/                chaves JWT locais (no .gitignore)
    └── test/
        ├── java/br/com/fiap/ThermoCity/
        │   ├── support/ApiTestBase.java   base dos testes de API (território e usuários)
        │   ├── pessoa/  contato/  user/  security/  geolocalizacao/  config/
        └── resources/
            ├── application.properties     H2 em memória
            └── keys-test/                 par de chaves usado só nos testes
```

Cada módulo de negócio segue a mesma organização em camadas:

```
modulo/
├── controller/   recebe a requisição HTTP, valida o DTO e devolve o status correto
├── dto/          formatos de entrada (RequestDTO) e saída (ResponseDTO), records Java
├── service/      regras de negócio e verificação de permissão
├── repository/   acesso ao banco (Spring Data JPA)
└── model/        entidades JPA mapeadas nas tabelas tmc_*
```

As entidades **nunca** saem direto na resposta: sempre passam por um DTO. Isso evita expor campos
internos, como a senha, e evita erros de carregamento preguiçoso (*lazy loading*) do JPA.

---

## Partes do projeto

### `common` — utilitários compartilhados

| Classe | O que faz |
|---|---|
| `Paginacao` | Valida o `page` (≥ 0) e o `size` (1 a 100) vindos da URL e monta o `PageRequest` com uma ordenação fixa definida pelo service. Fora desses limites, responde 400. O limite de 100 impede alguém de pedir o banco inteiro numa só chamada. Toda ordenação termina pelo `id` como desempate, para que nenhum registro se repita ou suma ao trocar de página. |
| `PaginaResponse<T>` | Formato único de resposta paginada (`conteudo`, `pagina`, `tamanho`, `totalElementos`, `totalPaginas`, `ultima`). O método `de(page, conversor)` recebe a página de entidades e uma função que converte cada uma em DTO. Assim `/pessoas` e `/contatos` respondem no mesmo formato. |
| `Distancia` | Distância em metros entre duas coordenadas pela fórmula de **Haversine**. É a mesma fórmula do `script-bd.sql`, então a API e o banco escolhem a mesma UBS mais próxima. |

```java
// PessoaService: monta a página com ordenação por nome
Page<Pessoa> pagina = pessoaRepository.buscar(titularId, ubsId, setor, nome,
        Paginacao.de(page, size, Sort.by("nome", "id")));

// PessoaController: converte as entidades em DTO no formato padrão
return PaginaResponse.de(pagina, p -> PessoaResponseDTO.from(p, hoje));
```

### `territorio` — onde a pessoa mora

- `Setor` e `Servico` mapeiam `tmc_setor` (420 setores censitários com polígono em WKT e grupo IPVS)
  e `tmc_servico` (UBS e refúgios climáticos). São `@Immutable`, porque esta API só lê essas tabelas.
- **`GeoService.localizar(lat, lon)`** recebe uma coordenada e devolve o setor que a contém, a UBS
  válida mais próxima e a distância até ela. Como o banco não tem PostGIS, o cálculo é feito em memória:
  1. No primeiro uso, carrega os 420 polígonos, converte o texto WKT em geometria (JTS `WKTReader`) e os
     guarda num índice espacial **STRtree**.
  2. Para cada coordenada, o índice devolve só os poucos setores cujo retângulo envolvente contém o
     ponto, sem percorrer os 420.
  3. Entre esses candidatos, testa `covers(ponto)` no polígono real (`PreparedGeometry` deixa o teste
     rápido).
  4. Escolhe a UBS mais próxima com `Distancia.metros`, ignorando as unidades de
     `thermocity.ubs.excluidas`.
  5. Se nenhum setor contém o ponto, o resultado vem vazio, e o cadastro responde
     **"Endereço fora da área atendida"**.
- Atenção à ordem no WKT: os polígonos estão em `POLYGON ((lon lat, ...))`, ou seja, x = longitude.
- O índice é carregado uma vez (*lazy*, com `synchronized`). `recarregar()` o descarta; os testes usam
  isso depois de inserir os próprios setores.

### `pessoa` — CRUD de pessoas acompanhadas

Opera sobre a tabela `tmc_pessoa_sintetica`, a mesma das pessoas sintéticas. Os cadastros reais
ficam com `sintetico = false` e um `titular_id`, que é o cidadão que fez o cadastro.

Regras aplicadas no `PessoaService`:

- **Consentimento LGPD** obrigatório; a data/hora do consentimento é gravada.
- **60 anos ou mais**, calculado pela data de nascimento no fuso de Brasília.
- **Setor e UBS calculados pela coordenada** (`GeoService`), nunca enviados pelo cliente. Se a
  coordenada muda no `PUT`, os dois são recalculados.
- **Quem vê o quê**: o CIDADAO vê só os próprios cadastros, o AGENTE só os da sua UBS e o GESTOR todos.
- Só o **titular** altera ou exclui o cadastro.
- **Exclusão** é o direito de eliminação da LGPD (ver [LGPD](#lgpd)).

### `contato` — busca ativa e cobertura

- `POST /contatos`: o agente registra o resultado de uma ligação: `CONTATADO_BEM`, `PRECISA_AJUDA`,
  `NAO_ATENDEU` ou `DADOS_INCORRETOS`. A pessoa precisa ser da UBS do agente. Se ela está na lista
  aberta de hoje, a situação do item na lista também é atualizada.
- O resultado aceita variações como `"nao atendeu"` ou `"Precisa ajuda"`. Um valor desconhecido
  responde **422** com os valores válidos em `detalhes`.
- `PRECISA_AJUDA` vem com `destaque: true`, e o gestor encontra esses casos com
  `GET /contatos?resultado=PRECISA_AJUDA`.
- `GET /cobertura/hoje` (`CoberturaService`) soma os itens da lista do dia por UBS e por setor.
  **Contatado** = `CONTATADO_BEM` + `PRECISA_AJUDA`, porque nos dois casos alguém falou com a pessoa.
  `NAO_ATENDEU` continua pendente, e `DADOS_INCORRETOS` aparece à parte. As UBS com menor cobertura
  aparecem primeiro, porque são as que precisam de reforço.

### `busca` — lista priorizada de busca ativa

Quando a API de clima (.NET) classifica um dia (hoje ou um dos próximos) como **NC3 ou mais**, esta API
monta, para **cada UBS**, a lista das pessoas que os agentes daquela UBS devem contatar primeiro. Cada
pessoa traz **posição, pontuação e motivos**, como "mora sozinha · 78 anos · setor nota 5".

**Fluxo:**

```
API .NET ──evento NIVEL_CALOR_ALTERADO──▶ RabbitMQ ──▶ NivelCalorListener ──▶ EventoNivelCalorHandler
                                                                                  │ valida o evento
GESTOR ── POST /simulacoes/nivel-calor ─────────────────────────────────────────▶ GeracaoListaService
                                                                                  │ PrioridadeService (regra v1)
AGENTE / GESTOR ── GET /listas/hoje ──▶ ListaConsultaService ◀── tmc_lista_busca_ativa / tmc_lista_item
```

#### Regra de prioridade v1 (`PrioridadeService`)

**Alcance:** quem entra na lista depende do nível de calor do dia.

| Nível | Quem entra |
|---|---|
| NC1, NC2 | ninguém (não há busca ativa; listas abertas do dia são encerradas) |
| NC3 | 70+ em setores IPVS 5–6 |
| NC4 | 60+ em setores IPVS 4–6 |
| NC5 | todos |

**Pontuação:** cada critério soma pontos. Esses "pesos" ficam em `thermocity.prioridade.pesos.*`, para
a equipe de saúde ajustar sem mudar código.

| Critério | Pontos | Origem do dado |
|---|---|---|
| mora sozinho | 3 | cadastro da pessoa |
| mobilidade reduzida | 2 | cadastro da pessoa |
| condição crônica | 2 | cadastro da pessoa |
| 70 anos ou mais | 2 | data de nascimento (ou idade/faixa das pessoas sintéticas) |
| nota de calor do setor | 0 a 4 (nota − 1) | `tmc_calor_setor.classe_calor` (1 = mais fresco, 5 = mais quente) |
| setor IPVS 5–6 / IPVS 4 | 2 / 1 | `tmc_setor.ipvs_grupo` |
| refúgio a mais de 800 m | 1 | refúgio mais próximo com `revisao` = `manter` ou `potencial` (101 locais) |

O máximo é 16 pontos. Empates são resolvidos pela idade (mais velho primeiro) e depois pelo id.

#### Geração (`GeracaoListaService`)

- **NC3 ou mais:** uma lista por UBS para o dia, mesmo que fique vazia.
- **Novo evento para o mesmo dia** (ex.: NC3 → NC4): a lista existente é **ampliada** com quem passou a
  entrar no alcance. O status dos contatos já feitos é preservado, e a ordem é recalculada pela
  pontuação. Se o nível cair, mas continuar em NC3 ou mais, ninguém sai da lista.
- **Abaixo de NC3:** as listas abertas do dia são **encerradas**. Se o calor voltar no mesmo dia, elas
  são reabertas com o andamento dos contatos.
- **Pessoa sem setor ou sem UBS válida:** fica fora e é registrada no log para correção do cadastro. O
  total aparece em `pessoasSemCadastroCompleto`.
- **Idempotência:** o `idEvento` é gravado em `tmc_evento_processado`. Um evento repetido não gera nada.
- **Volume:** os itens são gravados em lote via JDBC (no NC5 são cerca de 5.000 pessoas).

#### Priorização no banco (procedure e trigger)

A regra de prioridade v1 roda no PostgreSQL (`scripts/script-priorizacao.sql`, rodar depois do `script-bd.sql`):

| Objeto | O que faz |
|---|---|
| `tmc_prc_gerar_lista_busca_ativa(data, nivel, evento_id, ...)` | Monta ou atualiza a lista de cada UBS para o dia: alcance do nível, pontuação, motivos e posição. Preserva o status dos contatos já feitos |
| `tmc_trg_pessoa_reorganiza_lista` | `AFTER INSERT OR UPDATE` em `tmc_pessoa_sintetica`, por comando: se há lista aberta hoje, chama a procedure e a lista se reorganiza |

A API chama a procedure ao receber um evento de NC3 ou mais (`thermocity.busca.priorizacao=banco`, padrão). A idempotência do evento e o encerramento das listas (abaixo de NC3) continuam na API. Nos testes (H2, sem PL/pgSQL), `thermocity.busca.priorizacao=java` usa a mesma regra em Java.

#### Evento e RabbitMQ (contrato com a API .NET)

A API Java declara a topologia no broker na primeira conexão:

| Elemento | Nome | Tipo |
|---|---|---|
| exchange | `thermocity.clima` | topic, durável |
| routing key | `nivel-calor.alterado` | |
| fila | `thermocity.cadastro.nivel-calor` | durável, com DLX |
| exchange de erro | `thermocity.clima.dlx` | direct |
| fila de erro (DLQ) | `thermocity.cadastro.nivel-calor.dlq` | durável |

A API .NET publica na exchange `thermocity.clima` com a routing key `nivel-calor.alterado` e
`content-type: application/json`:

```json
{
  "idEvento": "6f1c2a9e-...",
  "tipo": "NIVEL_CALOR_ALTERADO",
  "dataLocal": "2026-11-16",
  "nivelAnterior": 2,
  "nivelNovo": 3,
  "icMaxC": 38.4,
  "origem": "PREVISTO",
  "ocorridoEm": "2026-11-15T06:00:00-03:00"
}
```

- **Obrigatórios:** `idEvento` (até 64 caracteres, único por evento), `dataLocal` e `nivelNovo` (1 a 5).
  Os demais campos são informativos, e campos extras são ignorados.
- **Mensagem inválida** (JSON ruim, sem nível, nível fora de 1 a 5): vai para a **DLQ**, com o motivo
  no log.
- **Falha inesperada** (ex.: banco fora do ar): 3 tentativas e depois a DLQ.

### `security` — autenticação e autorização

- `POST /auth/cadastro` é público e cria sempre um **CIDADAO**. Um `papel`
  enviado no corpo é ignorado. O CEP é convertido em coordenadas pelo módulo `geolocalizacao`.
- `POST /auth/login` devolve um **JWT RS256** com o papel do usuário e a data de expiração.
- O `SecurityFilter` lê o header `Authorization: Bearer <token>` em toda requisição. As permissões por
  papel ficam nos controllers (`@PreAuthorize`) e as regras finas, como "só pessoas da sua UBS", nos
  services.

Papéis:

| Papel | Quem é | O que faz |
|---|---|---|
| `CIDADAO` | familiar ou vizinho | cadastra e mantém pessoas sob sua responsabilidade |
| `AGENTE` | agente comunitário de saúde, vinculado a uma UBS | consulta as pessoas da sua UBS e registra contatos |
| `GESTOR` | gestão da Secretaria de Saúde | vê tudo, acompanha a cobertura e cria usuários |
| `COLETOR` | integração de sistemas | reservado para a API de clima (.NET) |

### `user` — usuários

- `User` mapeia `tmc_usuario`. Um AGENTE é obrigado a ter `ubsId`, e o banco também garante isso por
  constraint.
- `POST /usuarios` (GESTOR) cria AGENTE, GESTOR ou COLETOR.
- `GET /usuarios` e `GET /usuarios/{id}` (GESTOR) listam e detalham todos os cadastros, com filtro por papel e UBS.
- `GET /usuarios/me`, `PUT /usuarios/me` e `PUT /usuarios/me/senha` (qualquer papel) mostram e alteram
  **só o cadastro do dono do token**. CPF, papel e UBS não mudam por aí; trocar o e-mail exige novo login.
- O primeiro GESTOR é criado direto no banco por script SQL (ver "Primeiro gestor").

### `geolocalizacao` — CEP → coordenadas

`CepGeocodingService` consulta a **AwesomeAPI** e, se ela falhar, a **BrasilAPI** (ambas gratuitas). Se
as duas estiverem fora do ar, o cadastro do usuário continua sem coordenadas. Se o CEP não existe,
responde 400.

### `exception` — tratamento de erros

O `GlobalExceptionHandler` converte as exceções no [formato de erro](#formato-de-erro) padrão:

| Exceção | Status |
|---|---|
| validação do DTO (`@Valid`) | 400, com um item por campo em `detalhes` |
| `RegraNegocioException` | 400 |
| id ou parâmetro com tipo errado (`/pessoas/abc`) | 400 |
| `AcessoNegadoException` | 403, com mensagem própria |
| `RecursoNaoEncontradoException` | 404 |
| `RecursoDuplicadoException` | 409 |
| `ValorNaoProcessavelException` | 422 |

### `config`

- `OpenApiConfig`: título, descrição e esquema de segurança Bearer do Swagger.
- `ClockConfig`: `Clock` no fuso `America/Sao_Paulo`. "Hoje" (lista do dia, idade) é sempre o dia em
  Presidente Prudente, mesmo com o servidor do Azure em UTC.

---

## Endpoints

| Método | Rota | Papel | Descrição |
|---|---|---|---|
| POST | `/auth/cadastro` | público | cadastro de cidadão |
| POST | `/auth/login` | público | login, devolve JWT |
| POST | `/usuarios` | GESTOR | cria AGENTE, GESTOR ou COLETOR |
| GET | `/usuarios?papel=&ubs=&page=&size=` | GESTOR | lista todos os usuários, ordenados por nome |
| GET | `/usuarios/{id}` | GESTOR | detalha um usuário |
| GET | `/usuarios/me` | autenticado | mostra o próprio cadastro |
| PUT | `/usuarios/me` | autenticado | altera nome, e-mail, telefone e CEP do próprio cadastro |
| PUT | `/usuarios/me/senha` | autenticado | troca a própria senha (exige a atual) |
| POST | `/pessoas` | CIDADAO | cadastra pessoa acompanhada (201 + `Location`) |
| GET | `/pessoas?ubs=&setor=&nome=&page=0&size=10` | CIDADAO, AGENTE, GESTOR | lista paginada, ordenada por nome |
| GET | `/pessoas/{id}` | CIDADAO, AGENTE, GESTOR | detalhe |
| PUT | `/pessoas/{id}` | titular | atualiza |
| DELETE | `/pessoas/{id}` | titular | exclui (204) |
| POST | `/contatos` | AGENTE | registra resultado de contato |
| GET | `/contatos?pessoa=&resultado=&page=0&size=10` | AGENTE, GESTOR | histórico, do mais recente para o mais antigo |
| GET | `/contatos/{id}` | AGENTE, GESTOR | detalhe |
| GET | `/cobertura/hoje` | GESTOR | cobertura da lista do dia (204 se não houver lista) |
| GET | `/listas/hoje?ubs=&page=0&size=20` | AGENTE (sua UBS), GESTOR | lista priorizada do dia, por posição (204 se não houver busca ativa) |
| GET | `/listas?data=AAAA-MM-DD&ubs=&page=0&size=20` | AGENTE (sua UBS), GESTOR | lista de outra data (ex.: amanhã, pela previsão) |
| POST | `/simulacoes/nivel-calor?viaFila=false` | GESTOR | simula o evento de nível de calor (demonstração) |

### Exemplos

**Cadastrar pessoa** — `POST /pessoas` com `Authorization: Bearer <token do cidadão>`

```json
{
  "nome": "Dona Aparecida Souza",
  "dataNascimento": "1948-03-15",
  "sexo": "F",
  "telefone": "(18) 99999-1234",
  "endereco": "Rua Siqueira Campos, 1200 - Centro",
  "cep": "19010-000",
  "latitude": -22.1228739,
  "longitude": -51.3897085,
  "moraSozinho": true,
  "mobilidadeReduzida": false,
  "condicaoCronica": true,
  "contatoReferenciaNome": "Carlos Souza (filho)",
  "contatoReferenciaTelefone": "(18) 98888-7777",
  "consentimentoLgpd": true
}
```

Resposta `201 Created` (resumida), com `Location: /pessoas/20052`:

```json
{
  "id": 20052,
  "nome": "Dona Aparecida Souza",
  "idade": 78,
  "faixaEtaria": "70+",
  "telefone": "18999991234",
  "setorCodigo": "354140605000009",
  "setorBairro": "CENTRO",
  "setorIpvsGrupo": 5,
  "ubsId": "CNES-2755114",
  "ubsNome": "UBS CENTRO AUX ENF GUSTAVO LOURENCO",
  "consentimentoLgpd": true,
  "consentimentoEm": "2026-11-14T21:10:00-03:00",
  "sintetico": false,
  "titularId": 7
}
```

**Listar** — `GET /pessoas?page=0&size=10`

```json
{
  "conteudo": [ { "id": 20052, "nome": "Dona Aparecida Souza", "...": "..." } ],
  "pagina": 0,
  "tamanho": 10,
  "totalElementos": 12,
  "totalPaginas": 2,
  "ultima": false
}
```

**Registrar contato** — `POST /contatos` com o token do agente

```json
{
  "pessoaId": 20052,
  "resultado": "PRECISA_AJUDA",
  "observacao": "Tontura e sem água gelada em casa. Encaminhada ao refúgio."
}
```

Resposta `201` (resumida):

```json
{
  "id": 1,
  "pessoaId": 20052,
  "resultado": "PRECISA_AJUDA",
  "resultadoDescricao": "Contatado e precisa de ajuda (encaminhado à UBS ou ao refúgio)",
  "destaque": true,
  "ubsId": "CNES-2755114",
  "realizadoEm": "2026-11-15T09:40:00-03:00",
  "listaItemId": 120
}
```

**Criar agente** — `POST /usuarios` com o token do gestor

```json
{
  "nome": "Joana Agente",
  "email": "joana@saude.gov.br",
  "senha": "senhaForte123",
  "telefone": "(18) 99999-1234",
  "cpf": "935.411.347-80",
  "cep": "19010-000",
  "papel": "AGENTE",
  "ubsId": "CNES-2755114"
}
```

---

## Formato de erro

Todos os endpoints respondem erros no mesmo formato:

```json
{
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "Dados inválidos",
  "detalhes": ["dataNascimento: A data de nascimento é obrigatória", "telefone: Telefone inválido. Use DDD + número, ex.: (18) 99999-1234"],
  "timestamp": "2026-11-14T21:10:00-03:00"
}
```

Mensagens de regra de negócio mais comuns:

| Situação | Status | Mensagem |
|---|---|---|
| cadastro sem consentimento | 400 | É necessário o consentimento para o cadastro |
| pessoa com menos de 60 anos | 400 | O acompanhamento é para pessoas com 60 anos ou mais |
| coordenada fora de Presidente Prudente | 400 | Endereço fora da área atendida |
| `size` fora de 1..100 | 400 | O parâmetro size deve estar entre 1 e 100 |
| sem token ou token inválido | 401 | — |
| outro usuário tenta alterar o cadastro | 403 | Somente o titular do cadastro pode alterá-lo ou excluí-lo |
| agente consulta outra UBS | 403 | O agente só consulta pessoas da sua UBS |
| agente registra contato de outra UBS | 403 | A pessoa não pertence à sua UBS |
| id inexistente | 404 | Pessoa não encontrada |
| e-mail ou CPF repetido | 409 | E-mail já cadastrado / CPF já cadastrado |
| resultado de contato desconhecido | 422 | Resultado de contato inválido (valores válidos em `detalhes`) |
| agente pede a lista de outra UBS | 403 | O agente só consulta a lista da sua UBS |
| sem busca ativa no dia (NC1/NC2) | 204 | — |

---

## Testes

```bash
./gradlew test            # Linux/macOS/Git Bash
.\gradlew.bat test        # Windows
```

Os testes **não acessam o banco do Azure nem a internet**:

- usam **H2 em memória** (`src/test/resources/application.properties`), com o schema criado pelo
  Hibernate;
- usam um **par de chaves exclusivo** em `src/test/resources/keys-test/`;
- substituem a consulta de CEP por um mock (`@MockitoBean`);
- não conectam no RabbitMQ (`thermocity.rabbitmq.consumidor-ativo=false`): o tratamento do evento é
  testado chamando o `EventoNivelCalorHandler` direto.

Por isso rodam em qualquer máquina e no CI sem nenhuma variável de ambiente. O relatório fica em
`build/reports/tests/test/index.html`.

### `ApiTestBase`

Classe base dos testes de API (`@SpringBootTest` + `MockMvc`). Antes de cada teste ela:

1. limpa as tabelas na ordem das chaves estrangeiras;
2. cria um **território mínimo**:
   - **setor A** (IPVS 5) e **setor B** (IPVS 3), dois retângulos vizinhos;
   - **UBS A** e **UBS B**;
   - nota de calor 5 no setor A e 2 no setor B;
   - um refúgio válido (`manter`) no setor A e um marcado para `remover` no setor B, que deve ser
     ignorado;
   - uma unidade excluída (`CNES-PRISAO`) posicionada **exatamente** no ponto padrão de cadastro, para
     provar que ela nunca é escolhida;
3. cria um usuário de cada papel (`cidadao`, `outroCidadao`, `agenteA`, `agenteB`, `gestor`) e gera os
   tokens com `bearer(usuario)`.

Também oferece atalhos como `pessoaJson("Nome")` (78 anos, consentimento, coordenada no setor A) e
`cadastrarPessoa(titular, json)`, que cadastra pelo endpoint e devolve o id lido do header `Location`.
O atalho `inserirPessoaSintetica(...)` grava direto no banco uma pessoa como as geradas do Censo.

### Exemplo de teste

```java
@Test
void contatoRegistradoAtualizaASituacaoNaListaDoDia() throws Exception {
    // cenário: pessoa cadastrada e incluída como PENDENTE na lista de hoje da UBS A
    long pessoa = cadastrarPessoa(cidadao, pessoaJson("Dona Aparecida"));
    incluirNaLista(criarLista(hoje(), UBS_A), pessoa, 1, SETOR_A, "PENDENTE");

    // ação: o agente da UBS A registra o contato
    mockMvc.perform(post("/contatos").header("Authorization", bearer(agenteA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contatoJson(pessoa, "CONTATADO_BEM")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resultado").value("CONTATADO_BEM"))
            .andExpect(jsonPath("$.destaque").value(false))
            .andExpect(jsonPath("$.listaItemId").isNumber());

    // efeito colateral: o item da lista também mudou
    assertThat(statusNaLista(pessoa)).isEqualTo("CONTATADO_BEM");
}
```

### O que está coberto (75 testes)

| Classe | Cenários |
|---|---|
| `ListaControllerTest` (14) | NC3 gera uma lista por UBS em ordem de pontuação, com motivos e refúgio mais próximo; alcance NC3 → NC4 → NC5 (empate resolvido pela idade); refúgio "remover" ignorado; NC2 encerra, e a volta do calor reabre preservando o status do contato; contato registrado aparece na lista; lista de amanhã por `?data=`; 204 sem lista; 403 para outra UBS e para CIDADAO; gestor vê todas as UBS ou uma; paginação; simulação só para GESTOR e com nível 1–5; evento da fila gera lista e evento repetido é ignorado; nível baixo sem lista não faz nada; mensagem inválida vai para a DLQ |
| `PessoaControllerTest` (16) | cadastro calcula setor, UBS, titular e consentimento; sem consentimento, menor de 60 ou fora da cidade → 400 sem gravar nada; agente não cadastra; listagem de 12 itens em 2 páginas; agente vê só a sua UBS e gestor filtra por setor; paginação inválida; 404; cidadão não vê nem altera cadastro de outro; `PUT` com nova coordenada recalcula setor e UBS; exclusão anonimiza contatos e itens de lista; id não numérico |
| `ContatoControllerTest` (12) | contato atualiza a lista do dia; contato fora de lista aberta é gravado sem item; `PRECISA_AJUDA` destacado; 422 com valores válidos; 403 para outra UBS e para papéis sem permissão; 404; histórico paginado; cobertura por UBS e setor (5 de 8 = 62,5%); 204 sem lista |
| `UsuarioControllerTest` (15) | gestor cria agente; agente sem UBS ou com unidade excluída → 400; CIDADAO não é criado por esse endpoint; só GESTOR cria usuários; `/auth/cadastro`; gestor lista, filtra e detalha usuários (com CPF, sem senha); outros papéis → 403; cada um vê e altera só o próprio cadastro, sem trocar papel ou UBS; e-mail de outro → 409; troca de senha exige a atual |
| `AuthenticationControllerTest` (10) | cadastro com coordenadas do CEP; papel enviado é ignorado; geocodificação indisponível; CEP inexistente; e-mail repetido (409); validação; login, senha errada e token inválido |
| `CepGeocodingServiceTest` (5) | AwesomeAPI; fallback para BrasilAPI; CEP inexistente nas duas; CEP sem coordenadas; as duas fora do ar |
| `OpenApiDocumentationTest` (2) | Swagger e `/v3/api-docs` publicados |
| `ThermoCityApplicationTests` (1) | o contexto Spring sobe |

> Como os testes usam H2, as consultas JPQL devem ser confirmadas também contra o PostgreSQL real
> (roteiro de [teste manual](#6-roteiro-rápido-de-teste-manual)).

---

## LGPD

- O cadastro só é aceito com **consentimento explícito**, e a data/hora do consentimento é gravada.
- O cidadão vê **apenas** as pessoas que ele cadastrou; o agente, apenas as da sua UBS.
- `senha` nunca aparece nas respostas e é guardada com BCrypt; o `cpf` é devolvido nas respostas de usuário.
- **Direito de eliminação**: `DELETE /pessoas/{id}` apaga o registro e as orientações de contato da
  pessoa. Os contatos e os itens de lista são preservados para a estatística de cobertura, mas
  **anonimizados**: perdem o vínculo com a pessoa e a observação em texto livre.
- Os dados de demonstração são **sintéticos** (`sintetico = true`), sem pessoas reais.
- Nenhuma senha ou chave fica no código ou no Git; tudo vem de variáveis de ambiente.

---

## Próximos passos

- API .NET: publicar o evento `NIVEL_CALOR_ALTERADO` no formato acima (o lado Java já consome).
- Orientações personalizadas por pessoa (`tmc_orientacao_contato`) com IA generativa.

---

