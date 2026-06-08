package com.br.face.service;

import static org.bytedeco.opencv.global.opencv_calib3d.estimateAffinePartial2D;
import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_COLOR;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imdecode;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2RGB;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.warpAffine;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Reconhecimento facial com ArcFace (InsightFace MobileFaceNet, w600k_mbf)
 * rodando via ONNX Runtime. Gera, a partir de UMA imagem, um embedding de 512
 * floats robusto a câmera/iluminação/ângulo/idade — sem treino nem múltiplas
 * fotos.
 *
 * <p>
 * Pipeline: detecção do rosto e seus 5 pontos pelo YuNet (OpenCV) → alinhamento
 * por transformação de similaridade para o template canônico do ArcFace
 * (112x112) → inferência ONNX → vetor L2-normalizado. A comparação é por
 * distância do coseno.
 * </p>
 *
 * <p>
 * Concorrência: a sessão ONNX é compartilhada (thread-safe). O detector YuNet
 * não é thread-safe, então mantemos um <em>pool</em> de detectores — várias
 * requisições inferem em paralelo, limitadas pelo tamanho do pool.
 * </p>
 */
@Service
public class FaceRecognitionService {

	private static final Logger log = LoggerFactory.getLogger(FaceRecognitionService.class);

	private static final String ARCFACE_MODEL = "models/w600k_mbf.onnx";
	private static final String YUNET_MODEL = "models/face_detection_yunet_2023mar.onnx";
	private static final String INPUT_NAME = "input.1";
	private static final int FACE_SIZE = 112;
	private static final int EMBEDDING_SIZE = 512;

	/**
	 * Template canônico de 5 pontos do ArcFace (olho esq., olho dir., nariz, canto
	 * esq. da boca, canto dir. da boca) em coordenadas de uma face 112x112.
	 */
	private static final float[][] TEMPLATE = { { 38.2946f, 51.6963f }, { 73.5318f, 51.5014f },
			{ 56.0252f, 71.7366f }, { 41.5493f, 92.3655f }, { 70.7299f, 92.2041f } };

	private final int detectorPoolSize;
	private final int intraOpThreads;
	private final boolean useCuda;

	private OrtEnvironment ortEnv;
	private OrtSession arcSession;
	private BlockingQueue<FaceDetectorYN> detectorPool;
	private Path yunetTempFile;

	public FaceRecognitionService(@Value("${arcface.detector-pool-size:0}") int detectorPoolSize,
			@Value("${arcface.intra-op-threads:0}") int intraOpThreads,
			@Value("${arcface.cuda:false}") boolean useCuda) {
		this.detectorPoolSize = detectorPoolSize > 0 ? detectorPoolSize
				: Runtime.getRuntime().availableProcessors();
		this.intraOpThreads = intraOpThreads;
		this.useCuda = useCuda;
	}

	@PostConstruct
	void init() throws Exception {
		// O detector YuNet exige caminho de arquivo: extrai o modelo do classpath.
		yunetTempFile = Files.createTempFile("yunet", ".onnx");
		try (InputStream in = new ClassPathResource(YUNET_MODEL).getInputStream()) {
			Files.copy(in, yunetTempFile, StandardCopyOption.REPLACE_EXISTING);
		}
		detectorPool = new ArrayBlockingQueue<>(detectorPoolSize);
		for (int i = 0; i < detectorPoolSize; i++) {
			detectorPool.add(newDetector());
		}

		byte[] modelBytes;
		try (InputStream in = new ClassPathResource(ARCFACE_MODEL).getInputStream()) {
			modelBytes = in.readAllBytes();
		}
		ortEnv = OrtEnvironment.getEnvironment();
		arcSession = ortEnv.createSession(modelBytes, buildSessionOptions());
		log.info("ArcFace pronto: poolDetectores={}, intraOpThreads={}, cuda={}", detectorPoolSize, intraOpThreads,
				useCuda);
	}

	private FaceDetectorYN newDetector() {
		// Args: model, config, inputSize, scoreThreshold, nmsThreshold, topK,
		// backendId (0 = default), targetId (0 = CPU).
		return FaceDetectorYN.create(yunetTempFile.toString(), "", new Size(320, 320), 0.6f, 0.3f, 5000, 0, 0);
	}

	private SessionOptions buildSessionOptions() throws Exception {
		SessionOptions options = new SessionOptions();
		options.setOptimizationLevel(OptLevel.ALL_OPT);
		if (intraOpThreads > 0) {
			options.setIntraOpNumThreads(intraOpThreads);
		}
		if (useCuda) {
			try {
				options.addCUDA(0);
				log.info("ONNX Runtime: provider CUDA habilitado.");
			} catch (Exception ex) {
				// O artifact 'onnxruntime' (CPU) não traz o provider CUDA. Para GPU,
				// troque por 'onnxruntime_gpu' no pom. Aqui caímos para CPU.
				log.warn("CUDA solicitada mas indisponível ({}); usando CPU.", ex.getMessage());
			}
		}
		return options;
	}

	@PreDestroy
	void shutdown() throws Exception {
		if (arcSession != null) {
			arcSession.close();
		}
		if (detectorPool != null) {
			for (FaceDetectorYN detector : detectorPool) {
				detector.close();
			}
		}
		if (yunetTempFile != null) {
			Files.deleteIfExists(yunetTempFile);
		}
	}

	/**
	 * Gera o embedding ArcFace (512 floats L2-normalizados) a partir dos bytes de
	 * uma imagem (JPEG/PNG). Detecta o maior rosto, alinha e roda o modelo.
	 *
	 * @param imageBytes Conteúdo da imagem.
	 * @return Vetor de 512 floats, ou null se nenhum rosto for detectado.
	 */
	public float[] embed(byte[] imageBytes) {
		Mat buffer = new Mat(1, imageBytes.length, opencv_core.CV_8U);
		buffer.data().put(imageBytes);
		Mat bgr = imdecode(buffer, IMREAD_COLOR);
		buffer.release();
		if (bgr == null || bgr.empty()) {
			return null;
		}
		try {
			float[] landmarks = detectLandmarks(bgr);
			if (landmarks == null) {
				return null;
			}
			Mat aligned = align(bgr, landmarks);
			try {
				return runArcface(aligned);
			} finally {
				aligned.release();
			}
		} catch (Exception ex) {
			throw new IllegalStateException("Falha ao gerar embedding facial: " + ex.getMessage(), ex);
		} finally {
			bgr.release();
		}
	}

	/**
	 * Distância do coseno (1 - similaridade) entre dois embeddings. 0 = idênticos,
	 * 2 = opostos. Vetores de tamanhos diferentes ou de norma nula retornam a
	 * distância máxima, nunca correspondendo.
	 */
	public static double cosineDistance(float[] a, float[] b) {
		if (a == null || b == null || a.length != b.length || a.length == 0) {
			return 2.0;
		}
		double dot = 0;
		double normA = 0;
		double normB = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			normA += a[i] * a[i];
			normB += b[i] * b[i];
		}
		if (normA == 0 || normB == 0) {
			return 2.0;
		}
		return 1.0 - dot / (Math.sqrt(normA) * Math.sqrt(normB));
	}

	/** Serializa o embedding como bytes (little-endian, 4 bytes por float). */
	public static byte[] toBytes(float[] embedding) {
		if (embedding == null || embedding.length == 0) {
			return null;
		}
		ByteBuffer buffer = ByteBuffer.allocate(embedding.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
		for (float value : embedding) {
			buffer.putFloat(value);
		}
		return buffer.array();
	}

	/** Recupera o embedding persistido (bytes little-endian) como vetor de floats. */
	public static float[] fromBytes(byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			return new float[0];
		}
		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		float[] values = new float[bytes.length / Float.BYTES];
		for (int i = 0; i < values.length; i++) {
			values[i] = buffer.getFloat();
		}
		return values;
	}

	/**
	 * Detecta o rosto de maior score com um detector do pool e retorna seus 5
	 * pontos (x,y) reordenados para a ordem do template do ArcFace.
	 *
	 * @return 10 floats (5 pares x,y) ou null se nenhum rosto detectado.
	 */
	private float[] detectLandmarks(Mat bgr) {
		FaceDetectorYN detector;
		try {
			detector = detectorPool.take();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrompido ao aguardar um detector facial.", ex);
		}
		Mat faces = new Mat();
		try {
			detector.setInputSize(new Size(bgr.cols(), bgr.rows()));
			detector.detect(bgr, faces);
			if (faces.rows() == 0) {
				return null;
			}
			FloatIndexer idx = faces.createIndexer();
			int best = 0;
			float bestScore = -1f;
			for (int r = 0; r < faces.rows(); r++) {
				float score = idx.get(r, 14);
				if (score > bestScore) {
					bestScore = score;
					best = r;
				}
			}
			// YuNet (colunas 4..13): dois olhos, nariz, dois cantos da boca. O template
			// do ArcFace espera [olho esq., olho dir., nariz, boca esq., boca dir.] por
			// POSIÇÃO na imagem (esquerda = menor x), então desambiguamos pelo x em vez
			// de confiar no rótulo anatômico do detector (evita alinhamento espelhado).
			float eye1X = idx.get(best, 4), eye1Y = idx.get(best, 5);
			float eye2X = idx.get(best, 6), eye2Y = idx.get(best, 7);
			float noseX = idx.get(best, 8), noseY = idx.get(best, 9);
			float mouth1X = idx.get(best, 10), mouth1Y = idx.get(best, 11);
			float mouth2X = idx.get(best, 12), mouth2Y = idx.get(best, 13);
			idx.release();

			boolean eye1IsLeft = eye1X <= eye2X;
			float lEyeX = eye1IsLeft ? eye1X : eye2X, lEyeY = eye1IsLeft ? eye1Y : eye2Y;
			float rEyeX = eye1IsLeft ? eye2X : eye1X, rEyeY = eye1IsLeft ? eye2Y : eye1Y;
			boolean mouth1IsLeft = mouth1X <= mouth2X;
			float lMouthX = mouth1IsLeft ? mouth1X : mouth2X, lMouthY = mouth1IsLeft ? mouth1Y : mouth2Y;
			float rMouthX = mouth1IsLeft ? mouth2X : mouth1X, rMouthY = mouth1IsLeft ? mouth2Y : mouth1Y;
			return new float[] { lEyeX, lEyeY, rEyeX, rEyeY, noseX, noseY, lMouthX, lMouthY, rMouthX, rMouthY };
		} finally {
			faces.release();
			detectorPool.add(detector);
		}
	}

	/**
	 * Alinha o rosto para 112x112 estimando a transformação de similaridade dos 5
	 * pontos detectados para o template do ArcFace.
	 */
	private Mat align(Mat bgr, float[] landmarks) {
		Mat src = new Mat(5, 2, opencv_core.CV_32F);
		Mat dst = new Mat(5, 2, opencv_core.CV_32F);
		FloatIndexer srcIdx = src.createIndexer();
		FloatIndexer dstIdx = dst.createIndexer();
		for (int i = 0; i < 5; i++) {
			srcIdx.put(i, 0, landmarks[i * 2]);
			srcIdx.put(i, 1, landmarks[i * 2 + 1]);
			dstIdx.put(i, 0, TEMPLATE[i][0]);
			dstIdx.put(i, 1, TEMPLATE[i][1]);
		}
		srcIdx.release();
		dstIdx.release();

		Mat transform = estimateAffinePartial2D(src, dst);
		src.release();
		dst.release();
		if (transform == null || transform.empty()) {
			throw new IllegalStateException("Não foi possível alinhar o rosto.");
		}
		Mat aligned = new Mat();
		warpAffine(bgr, aligned, transform, new Size(FACE_SIZE, FACE_SIZE));
		transform.release();
		return aligned;
	}

	/**
	 * Roda o modelo ArcFace sobre o rosto alinhado e devolve o embedding
	 * L2-normalizado. Pré-processamento InsightFace: RGB, (x-127.5)/127.5, NCHW.
	 */
	private float[] runArcface(Mat alignedBgr) throws Exception {
		Mat rgb = new Mat();
		cvtColor(alignedBgr, rgb, COLOR_BGR2RGB);
		UByteIndexer pixels = rgb.createIndexer();
		int area = FACE_SIZE * FACE_SIZE;
		float[] data = new float[3 * area];
		for (int y = 0; y < FACE_SIZE; y++) {
			for (int x = 0; x < FACE_SIZE; x++) {
				int p = y * FACE_SIZE + x;
				data[p] = (pixels.get(y, x, 0) - 127.5f) / 127.5f;
				data[area + p] = (pixels.get(y, x, 1) - 127.5f) / 127.5f;
				data[2 * area + p] = (pixels.get(y, x, 2) - 127.5f) / 127.5f;
			}
		}
		pixels.release();
		rgb.release();

		long[] shape = { 1, 3, FACE_SIZE, FACE_SIZE };
		try (OnnxTensor tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(data), shape);
				OrtSession.Result result = arcSession.run(Collections.singletonMap(INPUT_NAME, tensor))) {
			float[][] output = (float[][]) result.get(0).getValue();
			return normalize(output[0]);
		}
	}

	private float[] normalize(float[] vector) {
		double norm = 0;
		for (float value : vector) {
			norm += value * value;
		}
		norm = Math.sqrt(norm);
		if (norm == 0) {
			return vector;
		}
		float[] normalized = new float[EMBEDDING_SIZE];
		for (int i = 0; i < vector.length; i++) {
			normalized[i] = (float) (vector[i] / norm);
		}
		return normalized;
	}

}
