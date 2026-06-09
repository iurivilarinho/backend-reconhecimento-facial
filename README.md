# API de Reconhecimento Facial (ArcFace)

API Spring Boot que reconhece pessoas a partir de **uma única foto**, usando
reconhecimento facial moderno por _embedding_. Substitui a abordagem clássica
(OpenCV `EigenFaceRecognizer`, que exigia ~10 fotos de treino por pessoa e
re-treino a cada cadastro).

## Como funciona (o que cada peça faz)

O reconhecimento tem dois cérebros distintos:

1. **ONNX Runtime — o reconhecimento.** Roda o modelo **ArcFace** (InsightFace
   MobileFaceNet, `w600k_mbf`) que transforma um rosto em um **embedding de 512
   números**. Rostos da mesma pessoa geram vetores próximos; de pessoas
   diferentes, vetores distantes. É robusto a câmera, luz, ângulo e idade — uma
   foto antiga reconhece.
2. **OpenCV / JavaCV — achar e alinhar o rosto.** O ArcFace precisa receber o
   rosto recortado e alinhado. Quem faz isso é o detector **YuNet** do OpenCV
   (via JavaCV): encontra o rosto e 5 pontos (olhos, nariz, cantos da boca). Em
   seguida estimamos uma transformação de similaridade e _warpamos_ o rosto para
   o template canônico 112×112 do ArcFace. **O OpenCV não reconhece nada** — só
   localiza e alinha.

Fluxo completo (`FaceRecognitionService`):

```
imagem → YuNet detecta rosto + 5 pontos → alinhamento (112×112)
       → ArcFace (ONNX) → embedding 512d L2-normalizado
```

A comparação é por **distância do coseno** (0 = idêntico). Cadastrar = salvar o
embedding do usuário; reconhecer = achar o usuário de menor distância abaixo do
limite (`arcface.threshold`).

## Endpoints

| Método | Rota | Descrição |
| --- | --- | --- |
| `POST` | `/users` | Cria um usuário (sem biometria). |
| `GET` | `/users` | Lista usuários. |
| `GET` | `/users/{id}` | Busca usuário por ID. |
| `POST` | `/face/{userId}/biometrics` | Cadastra a biometria do usuário a partir de **uma** foto (`multipart/form-data`, campo `file`). |
| `POST` | `/face/recognize` | Identifica (1:N) o usuário da foto enviada. |

Documentação interativa em `/swagger-ui.html`.

## Autenticação

Todos os endpoints exigem o header **`X-API-Key`** (exceto Swagger/OpenAPI e
`/actuator/health`). Há dois níveis de chave:

- **Bootstrap (admin):** configuradas em `api.keys` / variável de ambiente
  **`API_KEYS`** (lista separada por vírgula). São as chaves mestras — as únicas
  que podem **gerenciar chaves** em `/api-keys`. Sem nenhuma, a API fica fechada
  (fail-closed).
- **Geradas (banco):** criadas via `POST /api-keys`. São persistidas
  **hasheadas** (SHA-256) com nome, descrição e validade; a chave em texto plano
  aparece **uma única vez** na criação. Dão acesso aos endpoints normais
  (usuários/reconhecimento), mas não a `/api-keys`.

```bash
# 1) sobe com uma chave de bootstrap
API_KEYS="admin-master-key" ./mvnw spring-boot:run

# 2) gera uma chave de uso (validade opcional)
curl -X POST http://localhost:8080/api-keys -H "X-API-Key: admin-master-key" \
  -H "Content-Type: application/json" \
  -d '{"name":"Terminal portao 1","expiresAt":"2027-01-01T00:00:00"}'
# -> { "apiKey": "fk_....", ... }  (guarde: só aparece agora)

# 3) usa a chave gerada
curl -H "X-API-Key: fk_...." http://localhost:8080/users
```

### Endpoints de chaves (admin)

| Método | Rota | Descrição |
| --- | --- | --- |
| `POST` | `/api-keys` | Gera uma chave (retorna o texto plano uma vez). |
| `GET` | `/api-keys` | Lista chaves (metadados, sem o segredo). |
| `GET` | `/api-keys/{id}` | Metadados de uma chave. |
| `DELETE` | `/api-keys/{id}` | Revoga a chave. |

## Stack

- Java 21 (virtual threads), Spring Boot 3.4, Spring Data JPA, Bean Validation, springdoc-openapi.
- ONNX Runtime (ArcFace) + JavaCV/OpenCV (YuNet + alinhamento).
- SQL Server.

## Performance e concorrência

- **Índice em memória** (`EmbeddingIndex`): os embeddings são carregados uma vez
  na subida e atualizados a cada cadastro. O reconhecimento 1:N não toca no banco
  nem desserializa nada por requisição — só compara 512 floats por usuário.
- **Embedding binário**: persistido como `byte[]` (little-endian), não CSV.
- **Pool de detectores**: o YuNet não é thread-safe, então mantemos um pool; a
  sessão ONNX é compartilhada (thread-safe). Várias requisições inferem em
  paralelo, sem `synchronized` global.
- **Sessão ONNX afinada**: otimização de grafo `ALL_OPT` e threads configuráveis.

Parâmetros (em `application.properties`):

| Propriedade | Padrão | O que faz |
| --- | --- | --- |
| `arcface.threshold` | `0.5` | Distância coseno máxima aceita. |
| `arcface.detector-pool-size` | `0` (= nº de CPUs) | Concorrência máxima da inferência. |
| `arcface.intra-op-threads` | `0` (= padrão ORT) | Threads por inferência; 1–2 sob alta concorrência. |
| `arcface.cuda` | `false` | Usa GPU (exige o artifact `onnxruntime_gpu` + CUDA). |

> Para muitos milhares de usuários, o passo seguinte é trocar a varredura linear
> por um índice ANN (HNSW/pgvector) — a interface do `EmbeddingIndex` já isola
> isso.

## Modelos

Em `src/main/resources/models/`:

- `w600k_mbf.onnx` — ArcFace (reconhecimento, 512d).
- `face_detection_yunet_2023mar.onnx` — YuNet (detecção + 5 pontos).

## Rodar

```bash
./mvnw spring-boot:run         # local (precisa do SQL Server configurado)
./mvnw test                    # testes (provam o reconhecimento com fotos reais)
docker build -t face .         # imagem de produção
```

> Os testes baixam o JavaCV completo (todas as plataformas). Para acelerar
> localmente, use `-Djavacpp.platform=windows-x86_64` (ou `linux-x86_64`).
