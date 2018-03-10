package qupath.lib.algorithms;

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import qupath.lib.geom.Point2;
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
	@param stdFactor Standard deviation factor
	@param mode Transform mode (BRIGHT, DARK or BOTH)
	@throws Exception 
	*/
	public static ByteProcessor doTransform (ByteProcessor inputImage, int radii, double alpha, double stdFactor, Mode mode) throws Exception {
		
		Mat mat = ImagePlusToMatConverter.toMat(inputImage);
		mat = doTransform(mat, radii, alpha, stdFactor, mode);
		ByteProcessor bp = (ByteProcessor) MatToImagePlusConverter.toImageProcessor(mat);
		
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
	@param stdFactor Standard deviation factor
	@param mode Transform mode (BRIGHT, DARK or BOTH)
	@throws Exception 

	*/
	public static Mat doTransform (Mat inputImage, int radii, double alpha, double stdFactor, Mode mode) throws Exception {
		
		// Get matrix dimensions
		int width = inputImage.cols();
		int height = inputImage.rows();
		
		// Get the gradients (x and y direction)
		Mat gx = gradX(inputImage);
		Mat gy = gradY(inputImage);
		
		// Set the mode
		boolean dark = false, 
				bright = false;
		
		if (mode == Mode.DARK) {
			dark = true;
		}
		else if (mode == Mode.BRIGHT) {
			bright = true;
		}
		else if (mode == Mode.BOTH) {
			dark = true;
			bright = true;
		}
		else 
			throw new Exception("Invalid mode in the FSR transform.");
		
		// Create an empty image 
		Mat outputImage = Mat.zeros(inputImage.size(), CvType.CV_64FC1);
		
		Mat S = Mat.zeros(inputImage.rows() + 2 * radii, inputImage.cols() + 2 * radii, outputImage.type());
		
		Mat O_n = Mat.zeros(S.size(), CvType.CV_64FC1);
		Mat M_n = Mat.zeros(S.size(), CvType.CV_64FC1);
		
		/*
		// Go through the pixels
		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				
				Point p = new Point(i, j);
				
				Vector2D v = new Vector2D(gx.get(i, j)[0], gy.get(i, j)[0]);
				double vx = v.getX();
				double vy = v.getY();
				double norm = v.getNorm();
				
				if (norm > 0) {
					
					double gpx = (int) Math.round( (vx / norm) * radii);
					double gpy = (int) Math.round( (vy / norm) * radii);
					
					if (bright) {
						Point ppve = new Point (p.x + gpx + radii, p.y + gpy + radii);
						
					}
					
					if (dark) {
						Point p1 = new Point(1,2);
						
						
					}
				}
			}
		}
		*/
		
		// Get all pixel values into an array
		double [] pixels = new double[height * width];
		double [] pixelsX = new double[gx.rows() * gx.cols()];
		double [] pixelsY = new double[gy.rows() * gy.cols()];
		inputImage.get(0, 0, pixels);
		gx.get(0, 0, pixelsX);
		gy.get(0, 0, pixelsY);
		
		// Now go through the array
		
		for (int i = 0; i < pixels.length; i++) {
						
			Vector2D v = new Vector2D()
		}
		
		
		return outputImage;
	}
	
	private static Mat gradX (Mat input) {
		
		Mat output = new Mat(input.size(), CvType.CV_64FC1);
		Imgproc.Sobel(input, output, CvType.CV_64FC1, 1, 0);
		
		return output;
	}
	
	private static Mat gradY (Mat input) {
		
		Mat output = new Mat(input.size(), CvType.CV_64FC1);
		Imgproc.Sobel(input, output, CvType.CV_64FC1, 0, 1);

		return output;
	}
	

}
