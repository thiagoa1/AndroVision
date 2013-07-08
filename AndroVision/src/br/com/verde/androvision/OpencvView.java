package br.com.verde.androvision;

import static com.googlecode.javacv.cpp.opencv_core.IPL_DEPTH_8U;
import static com.googlecode.javacv.cpp.opencv_core.cvClearMemStorage;
import static com.googlecode.javacv.cpp.opencv_core.cvGetSeqElem;
import static com.googlecode.javacv.cpp.opencv_core.cvLoad;
import static com.googlecode.javacv.cpp.opencv_imgproc.CV_GAUSSIAN;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvLaplace;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvSmooth;
import static com.googlecode.javacv.cpp.opencv_objdetect.CV_HAAR_DO_CANNY_PRUNING;
import static com.googlecode.javacv.cpp.opencv_objdetect.cvHaarDetectObjects;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.view.View;

import com.googlecode.javacpp.Loader;
import com.googlecode.javacv.cpp.opencv_core.CvMemStorage;
import com.googlecode.javacv.cpp.opencv_core.CvRect;
import com.googlecode.javacv.cpp.opencv_core.CvSeq;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.cpp.opencv_objdetect;
import com.googlecode.javacv.cpp.opencv_objdetect.CvHaarClassifierCascade;

public class OpencvView extends View implements Camera.PreviewCallback {
	public static final int SUBSAMPLING_FACTOR = 4;

	private IplImage grayImage;
	private CvHaarClassifierCascade classifier;
	private CvMemStorage storage;
	private CvSeq faces;
	private Paint paint;

	private Bitmap bitmap;
	private Bitmap toDraw;
	private int height = 0;
	private int width = 0;

	public OpencvView(Context context) throws IOException {
		super(context);

		// Carregue o arquivo do classificador do Java Resources.
		File classifierFile = Loader.extractResource(getClass(), "/br/com/verde/androvision/haarcascade_frontalface_alt.xml",
		        context.getCacheDir(), "classifier", ".xml");
		if (classifierFile == null || classifierFile.length() <= 0) {
			throw new IOException("Could not extract the classifier file from Java resource.");
		}

		// Pré-carregue o módulo opencv_objdetect -> "Gambiarra" pra corrigir um bug
		Loader.load(opencv_objdetect.class);
		classifier = new CvHaarClassifierCascade(cvLoad(classifierFile.getAbsolutePath()));
		if (classifier.isNull()) {
			throw new IOException("Could not load the classifier file.");
		}
		storage = CvMemStorage.create();
	}

	public void onPreviewFrame(final byte[] data, final Camera camera) {
		try {
			init(camera);

			findFace(data);

			// IplImage image = getIplImageFromData(data);
			// IplImage outputImage = processImage(image);

			// if (outputImage == null) {
			// toDraw = null;
			// } else {
			// IplToBitmap(outputImage, toDraw);
			// }
			invalidate();

			camera.addCallbackBuffer(data);
		} catch (RuntimeException e) {
			// A câmera deve ter sido liberada.
			e.printStackTrace();
		}
	}

	private void IplToBitmap(IplImage src, Bitmap dst) {
		dst.copyPixelsFromBuffer(src.getIntBuffer());
	}

	private IplImage getIplImageFromData(byte[] data) {
		int[] temp = new int[width * height];
		IplImage image = IplImage.create(width, height, IPL_DEPTH_8U, 4);
		if (image != null) {
			decodeYUV420SP(temp, data, width, height); // convert to rgb
			image.getIntBuffer().put(temp);
		}
		return image;
	}

	private void init(Camera camera) {
		if (width == 0 || height == 0) {
			// SWITCHED PARAMETERS (rotation 90)
			width = camera.getParameters().getPreviewSize().width;
			height = camera.getParameters().getPreviewSize().height;
		}

		if (bitmap == null)
			bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

		toDraw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
	}

	// private IplImage processImage(IplImage image) {
	// // return blurImage(image);
	// // return laplace(image);
	// }

	private IplImage blurImage(IplImage image) {
		IplImage blurred = IplImage.create(width, height, IPL_DEPTH_8U, 4);
		cvSmooth(image, blurred, CV_GAUSSIAN, 3);
		cvSmooth(blurred, blurred, CV_GAUSSIAN, 3);
		cvSmooth(blurred, blurred, CV_GAUSSIAN, 3);
		cvSmooth(blurred, blurred, CV_GAUSSIAN, 3);
		cvSmooth(blurred, blurred, CV_GAUSSIAN, 3);

		return blurred;
	}

	private IplImage laplace(IplImage image) {
		IplImage laplace = IplImage.create(width, height, IPL_DEPTH_8U, 4);
		cvLaplace(image, laplace, 3);
		return laplace;
	}

	private void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {
		int frameSize = width * height;
		for (int j = 0, yp = 0; j < height; j++) {
			int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
			for (int i = 0; i < width; i++, yp++) {
				int y = (0xff & ((int) yuv420sp[yp])) - 16;
				if (y < 0)
					y = 0;
				if ((i & 1) == 0) {
					v = (0xff & yuv420sp[uvp++]) - 128;
					u = (0xff & yuv420sp[uvp++]) - 128;
				}
				int y1192 = 1192 * y;

				int r = (y1192 + 1634 * v);
				int g = (y1192 - 833 * v - 400 * u);
				int b = (y1192 + 2066 * u);

				if (r < 0)
					r = 0;
				else if (r > 262143)
					r = 262143;
				if (g < 0)
					g = 0;
				else if (g > 262143)
					g = 262143;
				if (b < 0)
					b = 0;
				else if (b > 262143)
					b = 262143;

				rgb[yp] = 0xff000000 | ((b << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((r >> 10) & 0xff);
			}
		}
	}

	private void findFace(byte[] data) {
		// Criando uma sub-amostragem e convertendo em escala de cinza.
		int f = SUBSAMPLING_FACTOR;
		int sampledWidth = width / f;
		int sampledHeight = height / f;
		if (grayImage == null || grayImage.width() != sampledWidth || grayImage.height() != sampledHeight) {
			grayImage = IplImage.create(sampledWidth, sampledHeight, IPL_DEPTH_8U, 1);
		}
		int imageWidth = sampledWidth;
		int imageHeight = sampledHeight;
		int dataStride = f * width;
		int imageStride = grayImage.widthStep();
		ByteBuffer imageBuffer = grayImage.getByteBuffer();
		for (int y = 0; y < imageHeight; y++) {
			int dataLine = y * dataStride;
			int imageLine = y * imageStride;
			for (int x = 0; x < imageWidth; x++) {
				imageBuffer.put(imageLine + x, data[dataLine + f * x]);
			}
		}

		cvClearMemStorage(storage);
		faces = cvHaarDetectObjects(grayImage, classifier, storage, 1.1, 3, CV_HAAR_DO_CANNY_PRUNING);
	}

	private Paint getPaint() {
		if (paint == null) {
			paint = new Paint();

			// Atributos da fonte
			paint.setColor(Color.RED);
			paint.setTextSize(20);

			// Atributos da linha
			paint.setStrokeWidth(2);
			paint.setStyle(Paint.Style.STROKE);
		}
		return paint;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		// if (toDraw != null) {
		// canvas.drawBitmap(Bitmap.createScaledBitmap(toDraw, canvas.getWidth(), canvas.getHeight(), false), 0, 0, null);
		// }

		Paint paint = getPaint();
		if (faces != null) {
			float scaleX = (float) getWidth() / grayImage.width();
			float scaleY = (float) getHeight() / grayImage.height();

			int total = faces.total();

			CvRect rect;
			for (int i = 0; i < total; i++) {
				rect = new CvRect(cvGetSeqElem(faces, i));
				int x = rect.x(), y = rect.y(), w = rect.width(), h = rect.height();
				canvas.drawRect(x * scaleX, y * scaleY, (x + w) * scaleX, (y + h) * scaleY, paint);
			}
		}
	}
}