package qupath.lib.algorithms;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import qupath.lib.ij_opencv.ImagePlusToMatConverter;
import qupath.lib.ij_opencv.MatToImagePlusConverter;

/**
 * Helper class for the Fast Radial Symmetry algorithm.
 * This code is based on a C++ implementation of the algorithms 
 * described by Loy, G. & Zelinsky, A. (2002).
 * 
 * The original C++ algorithm can be found at:
 * https://github.com/Xonxt/frst
 */
public class FastRadialSymmetry {
	
	private static final Logger logger = LoggerFactory.getLogger(FastRadialSymmetry.class);
	
	public static enum Mode {
		BRIGHT, DARK, BOTH;
	}
	
	/** Empty constructor */
	public FastRadialSymmetry () {}

	/**
	Applies Fast radial symmetry transform to image
	Check paper Loy, G., & Zelinsky, A. (2002). A fast radial symmetry transform for
	detecting points of interest. Computer Vision, ECCV 2002.

	@param inputImage The input grayscale image (8-bit ByteProcessor)
	@param radii Gaussian kernel radius
	@param alpha Strictness of radial symmetry
	@param beta Threshold to filter small gradient magnitudes
	@param mode Transform mode (BRIGHT, DARK or BOTH)
	@throws Exception 
	*/
	public static ImageProcessor doTransform (ImageProcessor inputImage, int [] radii, double alpha, double beta, Mode mode) throws Exception {
		
		Mat mat = ImagePlusToMatConverter.toMat(inputImage);
		mat = doTransform(mat, radii, alpha, beta, mode);
		ImageProcessor bp = MatToImagePlusConverter.toImageProcessor(mat);
		
		// Release the matrix (is this necessary?)
		mat.release();
		
		return bp;
	}
	
	/**
	Applies Fast radial symmetry transform to image
	Check paper Loy, G., & Zelinsky, A. (2002). A fast radial symmetry transform for
	detecting points of interest. Computer Vision, ECCV 2002.

	@param inputImage The input grayscale image (8-bit OpenCV Mat)
	@param radii Gaussian kernel radius
	@param alpha Strictness of radial symmetry
	@param beta Threshold to filter small gradient magnitudes
	@param mode Transform mode (BRIGHT, DARK or BOTH)
	@throws Exception 

	*/
	public static Mat doTransform (Mat inputImage, int [] radii, double alpha, double beta, Mode mode) throws Exception {
	
		// Get matrix dimensions
		int cols = inputImage.cols();
		int rows = inputImage.rows();
		boolean dark = false, bright = false;
		
		if (mode.equals(Mode.BRIGHT)) {
			bright = true;
		}
		else if (mode.equals(Mode.DARK)) {
			dark = true;
		}
		else {
			dark = true;
			bright = true;
		}
		
		// Get the gradients (x and y direction)
		Mat gx = gradX(inputImage); // SobelX
		Mat gy = gradY(inputImage); // SobelY
		
		// Calculate magnitude of this gradient
		Mat mag = new Mat();
		Core.add(gx.mul(gx), gy.mul(gy), mag);
		Core.sqrt(mag, mag);
		
		// Add small number to avoid dividing by zero
		Core.add(mag, new Scalar(0.1), mag);
		
		// Normalize the gradients
		Core.divide(gx, mag, gx);
		Core.divide(gy, mag, gy);
		
		// We only want the magnitude
		Mat S = new Mat(rows, cols, CvType.CV_64FC1);
		Mat So = new Mat(rows, cols, CvType.CV_64FC1);
		
		for (int n : radii) {
			
			if (n == 0)
				continue;
			
			Mat M = Mat.zeros(rows, cols, CvType.CV_64FC1);
			Mat O = Mat.zeros(rows, cols, CvType.CV_64FC1);
			
			for (int x = 0; x < cols; x++) {
				for (int y = 0; y < rows; y++) {
					
					if (mag.get(y, x)[0] > beta) {
						
						// Get the pixel values
						double px = gx.get(y,x)[0];
						double py = gy.get(y,x)[0];
												
						int posX = 0, posY = 0;
						int negX = 0, negY = 0;
						
						if (bright) {
							
							// Get coords for positively affected pixels
							posX = (int) (x + Math.round( n * px ));
							posY = (int) (y + Math.round( n * py ));
							// Keep within bounds
							if (posX < 1)
								posX = 1;
							else if (posX > (cols-1))
								posX = cols-1;
							
							if (posY < 1)
								posY = 1;
							else if (posY > (rows-1))
								posY = rows-1;
							

							// Get values
							double o = O.get(posY, posX)[0];
							double m = M.get(posY, posX)[0];
							
							o += 1;
							m += mag.get(y, x)[0];
							
//							double op = O.get(posY, posX)[0] + 1;
//							double mp = M.get(posY, posX)[0] + mag.get(y, x)[0];
							
							// Put values in matrix
							O.put(posY, posX, o);
							M.put(posY, posX, m);
						}
						
						if (dark) {
							// Get coords for negatively affected pixels
							negX = (int) (x - Math.round( n * px ));
							negY = (int) (y - Math.round( n * py ));
							// Keep within bounds
							if (negX < 1)
								negX = 1;
							else if (negX > (cols-1))
								negX = cols-1;
							
							if (negY < 1)
								negY = 1;
							else if (negY > (rows-1))
								negY = rows-1;
							
							// Get values
							double o = O.get(negY, negX)[0];
							double m = M.get(negY, negX)[0];
							
							o -= 1;
							m -= mag.get(y, x)[0];
							
//							double on = O.get(negY, negX)[0] - 1;
//							double mn = M.get(negY, negX)[0] - mag.get(y, x)[0];

							// Put values in matrix
							O.put(negY, negX, o);
							M.put(negY, negX, m);
						}
						
					}
				}
			}
			
			for (int x = 0; x < cols; x++) {
				for (int y = 0; y < rows; y++) {
					
					// Get the new values after the processing
					double o = O.get(y, x)[0];
					double m = M.get(y, x)[0];

					// Do normalization
					double kappa = 9.9;
					if (n == 1) 
						kappa = 8.0;		
					
					if (o > kappa)
						o = kappa;
					else if (o < -kappa)
						o = -kappa;
					
					double f = (m/kappa) * Math.pow (Math.abs(o)/kappa, alpha);
					double fo = Math.signum(o) * Math.pow (Math.abs(o)/kappa, alpha);
					
					// Now we can place the values back into the matrix
					O.put(y, x, fo);
					M.put(y, x, f);
				}
			}
			
			// Smooth and spread the symmetry measure with a gaussian
			Mat tempM = new Mat();
			Mat tempO = new Mat();
			Imgproc.GaussianBlur(M, tempM, new Size(5,5), 0.25*n);
			tempM.mul(tempM, n);
			Imgproc.GaussianBlur(O, tempO, new Size(5,5), 0.25*n);
			tempO.mul(tempO, n);
			
			// Add this filtered image to the total
			Core.add(S, tempM, S);
			Core.add(So, tempO, So);
			
		}
		
		// Average out across all radii
		Core.divide(S, new Scalar (radii.length), S);
		Core.divide(So, new Scalar (radii.length), So);
		
		return S;
	}
				
	
	private static Mat gradX (Mat input) {
		
		Mat output = new Mat();
		Imgproc.Sobel(input, output, CvType.CV_64FC1, 1, 0);
		
		return output;
	}
	
	private static Mat gradY (Mat input) {
		
		Mat output = new Mat();
		Imgproc.Sobel(input, output, CvType.CV_64FC1, 0, 1);

		return output;
	}
	

}
