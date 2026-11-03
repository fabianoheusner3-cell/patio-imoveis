# Atividade sobre autenticação e autorização com JWT

## 1. Autenticação e autorização

Autenticação verifica quem é o usuário. Neste sistema, o login confere email e senha e devolve um token JWT assinado.

Autorização verifica quais ações esse usuário pode executar. Por exemplo: um usuário USER autenticado pode alterar o status de um imóvel, mas recebe 403 ao tentar cadastrar um imóvel. Um ADMIN pode fazer as duas operações.

## 2. Stateful e stateless

Em uma aplicação stateful, o servidor mantém uma sessão e o cliente envia um identificador para que ela seja consultada. Em uma aplicação stateless, cada requisição fornece suas próprias credenciais e não depende de uma sessão de login armazenada no servidor.

Aqui, o cliente envia `Authorization: Bearer <token>` em cada chamada protegida. Spring Security usa `SessionCreationPolicy.STATELESS`. Ainda existem dados persistentes no banco: stateless não significa ausência de banco de dados. A validação também consulta o usuário atual para rejeitar tokens de usuários excluídos ou cujo perfil mudou.

## 3. Semântica e sintaxe de JWT

JWT significa JSON Web Token e transporta declarações (claims). No formato assinado compacto usado neste trabalho, sua sintaxe é:

```text
Base64URL(header).Base64URL(payload).Base64URL(signature)
```

O header contém `alg: HS256` e `typ: JWT`. O payload contém `iss` (emissor), `sub` (ID do usuário), `iat` (instante de emissão), `exp` (expiração), `jti` (identificador do token) e a claim da aplicação `role` (`ADMIN` ou `USER`). Os instantes usam segundos desde a época Unix. A assinatura HMAC-SHA256 protege a integridade do header e do payload, usando a chave do servidor. O servidor verifica assinatura, algoritmo, emissor, expiração e usuário antes de autorizar a operação.

Base64URL é uma codificação, não criptografia. O conteúdo é legível: senhas nunca são incluídas no token. As senhas são armazenadas no banco com hash BCrypt e não aparecem nas respostas da API.

## 4. Implementação

| Método e rota | Acesso | Token |
|---|---|---|
| POST `/auth/login` | Público | Não |
| GET `/imoveis` | Público | Não |
| GET `/imoveis/imoveis-page` | Público | Não |
| GET `/imoveis/{id}` | Público | Não |
| POST `/imoveis` | ADMIN | Sim |
| PUT `/imoveis/{id}` | ADMIN | Sim |
| DELETE `/imoveis/{id}` | ADMIN | Sim |
| PATCH `/imoveis/{id}/status` | ADMIN ou USER | Sim |
| GET/POST `/usuarios` | ADMIN | Sim |
| GET/PUT/DELETE `/usuarios/{id}` | ADMIN | Sim |

Também são públicos a interface, a consulta de bairros, tipos e fotos e o formulário de interesse já existente (`POST /imoveis/sabermais/{id}`). Alterações de bairros, tipos e fotos exigem ADMIN. O endpoint legado de email não foi liberado: seu uso não é necessário para a atividade.

Uma rota protegida retorna **401 Unauthorized** quando falta o token ou ele é inválido/expirado. Um token válido de USER em uma operação exclusiva de ADMIN retorna **403 Forbidden**. A rota de status recebe um DTO específico, portanto não altera título, preço nem outros dados do imóvel.

### Executar no PowerShell

Requisitos: JDK 25 ou superior e Maven. Entre nesta cópia do projeto:

```powershell
cd 'C:\Users\Fabiano Heusner\Desktop\Fabiano\WEBIV\BairroPageable'
$env:ADMIN_EMAIL = 'admin@example.com'
$env:ADMIN_PASSWORD = 'AdminTeste123!'
# Gere uma chave aleatória para esta execução; mantenha a mesma chave para preservar tokens entre reinícios.
$bytes = New-Object byte[] 48
$gerador = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$gerador.GetBytes($bytes)
$gerador.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
mvn '-Dmaven.repo.local=../.m2/repository' spring-boot:run
```

O administrador inicial é criado apenas quando não há usuários no banco e as duas variáveis ADMIN estão preenchidas. A senha acima é um exemplo de aula, que pode ser substituído. Sem `JWT_SECRET`, o sistema gera uma chave aleatória em memória; nesse caso, reiniciar invalida os tokens anteriores. Uma chave configurada deve ter no mínimo 32 bytes. O token vale uma hora por padrão, configurável por `JWT_EXPIRATION_SECONDS`.

Abra <http://localhost:8080/login.html>. Entre como ADMIN e cadastre um usuário com perfil USER na seção de gerenciamento. Entre com esse usuário para testar a alteração de status. O login integra as telas existentes usando o token no `sessionStorage` da aba. A checagem de permissões acontece no servidor.

Esta cópia não inclui o banco, as fotos nem as credenciais locais de email da pasta original. Os imóveis podem ser cadastrados pela interface após criar tipo e bairro. O banco novo fica em `data/` nesta cópia.

### Exemplos na API

Login:

```http
POST /auth/login
Content-Type: application/json

{"email":"admin@example.com","senha":"AdminTeste123!"}
```

A resposta contém `token`, `tipo`, `expiraEm`, `perfil` e `nome`. Use o valor de `token` nas requisições seguintes:

```http
POST /usuarios
Authorization: Bearer <token-do-admin>
Content-Type: application/json

{"nome":"Aluno","email":"aluno@example.com","senha":"AlunoTeste123!","perfil":"USER"}
```

Faça login com esse usuário e use seu token:

```http
PATCH /imoveis/1/status
Authorization: Bearer <token-do-user>
Content-Type: application/json

{"status":"Reservado"}
```

O status é um texto não vazio de até 255 caracteres, preservando o modelo da aula. Para atualizar usuários, PUT recebe nome, email e perfil; omitir a senha mantém o hash atual. Excluir um usuário ou alterar seu perfil invalida seus tokens existentes; ele deve fazer login novamente. Trocar somente a senha não revoga tokens já emitidos, que continuam válidos até expirar. Sair da interface remove o token da aba.

### Testes

```powershell
mvn '-Dmaven.repo.local=../.m2/repository' test
```

`JwtAuthorizationApiTest` verifica acesso público, login incorreto, senha com hash, ausência de token, assinatura adulterada, expiração, permissões USER/ADMIN, gerenciamento de usuários e alteração exclusiva do campo status. Os testes existentes de cadastro de imóveis, tipos e fotos agora usam um token ADMIN; a segurança permanece habilitada nos testes.

Referência utilizada: [Spring Security — OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
