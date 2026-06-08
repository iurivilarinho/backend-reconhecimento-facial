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

## Stack

- Java 17, Spring Boot 3.4, Spring Data JPA, Bean Validation, springdoc-openapi.
- ONNX Runtime (ArcFace) + JavaCV/OpenCV (YuNet + alinhamento).
- SQL Server.

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
