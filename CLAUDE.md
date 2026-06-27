# CLAUDE.md — device-management

Microsserviço de **cadastro e gestão de sensores** do projeto AlgaSensors. Expõe o CRUD de
sensores e orquestra o monitoramento delegando ao serviço `temperature-monitoring`.

## Stack

- Java 25 (toolchain Gradle) · Spring Boot 4.1.0 · Gradle (use o wrapper `./gradlew`)
- Spring Web (MVC) · Spring Data JPA · H2 (arquivo em `~/algasensors-device-management-db`)
- Lombok · hypersistence-tsid (IDs no formato TSID)
- **Jackson 3** (vem com o Spring Boot 4 — ver Convenções)

## Comandos

```bash
./gradlew bootRun   # sobe a aplicação na porta 8080
./gradlew test      # roda os testes
./gradlew build     # compila + testa + empacota
```

H2 console: `http://localhost:8080/h2-console` (URL/credenciais em `src/main/resources/application.yml`).

## Arquitetura

- Porta **8080**. Pacote base: `com.algaworks.algasensors.device.management`.
- Camadas: `api` (controller/model/config) · `domain` (model/repository) · `common`.
- `SensorController` (`/api/sensors`): CRUD + `PUT|DELETE /{id}/enable` para ligar/desligar o
  monitoramento, e `GET /{id}/detail` que agrega dados do monitoramento.
- **Comunicação entre serviços**: chama o `temperature-monitoring` (porta 8082) via
  `SensorMonitoringClient`, uma HTTP Interface criada em `RestClientConfig` a partir do
  `RestClient` montado em `RestClientFactory` (timeouts + handler que lança
  `SensorMonitoringClientBadGatewayException` em erro → 502/504).

## Convenções

- **IDs são TSID** (`io.hypersistence.tsid.TSID`), persistidos como `Long`. A conversão é feita
  por peças dedicadas, reutilize-as ao criar novas entidades/endpoints:
  - Web (path variable `String`→TSID): `api/config/web/StringToTSIDWebConverter`
  - JSON: `api/config/jackson/StringToTSIDDeserializer` + `TSIDToStringSerializer`
  - JPA: `api/config/jpa/TSIDToLongJPAAttributeConverter`
  - Value object de identidade: `domain/model/SensorId`; geração via `common/IdGenerator`.
- **Jackson 3**: o pacote é `tools.jackson.*` (não `com.fasterxml.jackson.*`, exceto as
  anotações). `JsonDeserializer`→`ValueDeserializer`, `JsonSerializer`→`ValueSerializer`,
  `parser.getText()`→`getString()`, e as exceções Jackson agora são unchecked.
- Lombok em uso: `@RequiredArgsConstructor` (injeção por construtor), `@Builder`, `@Getter/@Setter`.
- Erros de domínio via `ResponseStatusException` + `ApiExceptionHandler`.

## Testes

- Padrão e2e: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestTestClient` apontando para
  `http://localhost:{port}`. Cada teste limpa o repositório no `@BeforeEach`.
- `src/test/resources/application.yml` sobrescreve o datasource para **H2 em memória**
  (`create-drop`), isolando os testes do banco em arquivo.
- Dependências externas (ex.: `SensorMonitoringClient`) são substituídas com **`@MockitoBean`**
  (`org.springframework.test.context.bean.override.mockito.MockitoBean`) — `@MockBean` foi
  removido no Spring Boot 4. Sem o mock, os endpoints de enable/disable/delete fazem chamada
  HTTP real e falham com 504.

## Pegadinhas (migração Spring Boot 4)

- O bean `RestClient.Builder` **não** vem mais no starter web. É preciso a dependência
  `org.springframework.boot:spring-boot-starter-restclient` (já presente no `build.gradle`);
  sem ela o `RestClientFactory` quebra todo o contexto.

## Contexto do repositório

Este módulo é um submódulo Git do meta-repo `ems-algasensors-meta`. Para a ordem de commit/push
entre submódulos e meta-repo, ver o `README.md` da raiz.
