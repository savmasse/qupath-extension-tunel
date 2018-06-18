package qupath.lib.algorithms;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.AutoThresholder;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import qupath.imagej.helpers.IJTools;
import qupath.imagej.objects.PathImagePlus;
import qupath.imagej.objects.ROIConverterIJ;
import qupath.imagej.processing.SimpleThresholding;
import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.color.ColorDeconvMatrix3x3;
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.StainVector;
import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.ij_opencv.ImagePlusToMatConverter;
import qupath.lib.ij_opencv.MatToImagePlusConverter;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.plugins.AbstractTileableDetectionPlugin;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.experimental.ShapeSimplifier;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.processing.OpenCVTools;
import qupath.opencv.processing.ProcessingCV;

public class WatershedDetectionFRS extends AbstractTileableDetectionPlugin <BufferedImage> {
	private static final Logger logger = LoggerFactory.getLogger(WatershedDetectionFRS.class);
			
	transient static OpenCvDetector detector;
	
	static class OpenCvDetector implements ObjectDetector<BufferedImage> {
		private ROI pathROI;
		private List <PathObject> pathObjects = new ArrayList<>();
		private boolean nucleiClassified = false;
		
		static String ADAPTIVE_GAUSSIAN = "Gaussian";
		static String ADAPTIVE_MEAN = "Mean";

		@Override
		public Collection<PathObject> runDetection(ImageData<BufferedImage> imageData, ParameterList params, ROI pathROI) {
			
			// Clear any previous objects
			pathObjects.clear();
			
			// Set downsampling rate
			double downsample = imageData.getServer().hasPixelSizeMicrons() ? getPreferredPixelSizeMicrons(imageData, params) / imageData.getServer().getAveragedPixelSizeMicrons() : 1;
			downsample = Math.max(downsample, 1);
			
			Rectangle bounds = AwtTools.getBounds(pathROI);
			double x = bounds.getX();
			double y = bounds.getY();
						
			// Get the image data
			ImageServer<BufferedImage> server = imageData.getServer();
			BufferedImage img = server.readBufferedImage(RegionRequest.createInstance(server.getPath(), downsample, pathROI));
			
			// Set the values of parameters given by the user
			int medianRadius, openingRadius;
			double gaussianSigma, minArea, threshold, adaptiveBlockSize, kernelSize;
			boolean splitShape, adaptiveThreshold, simplifyShapes;
			String thresholder;
			
			// Check if pixel size is known
			if (server.hasPixelSizeMicrons()) {
				double pixelSize = 0.5 * downsample * (server.getPixelHeightMicrons() + server.getPixelWidthMicrons());
				medianRadius = (int)(params.getDoubleParameterValue("medianRadius") / pixelSize + .5);
				gaussianSigma = params.getDoubleParameterValue("gaussianSigma") / pixelSize;
				openingRadius = (int)(params.getDoubleParameterValue("openingRadius") / pixelSize + .5);
				minArea = params.getDoubleParameterValue("minArea") / (pixelSize * pixelSize);
				logger.trace(String.format("Sizes: %d, %.2f, %d, %.2f", medianRadius, gaussianSigma, openingRadius, minArea));
			} else {
				medianRadius = (int)(params.getDoubleParameterValue("medianRadius") + .5);
				gaussianSigma = params.getDoubleParameterValue("gaussianSigma");
				openingRadius = (int)(params.getDoubleParameterValue("openingRadius") + .5);
				minArea = params.getDoubleParameterValue("minArea");
			}
			
			// Other parameters
			splitShape = params.getBooleanParameterValue("splitShape");
			simplifyShapes = params.getBooleanParameterValue("simplifyShapes");
			adaptiveThreshold = params.getBooleanParameterValue("adaptiveThreshold");
			adaptiveBlockSize = params.getIntParameterValue("adaptiveBlockSize");
			
			// Set threshold regardless of size
			threshold = params.getDoubleParameterValue("threshold");
			
			// Get thresholding method
			thresholder = (String) params.getChoiceParameterValue("thresholder");
			
			// Set the detection channel 
			int detectionChannel = params.getIntParameterValue("detectionChannel");
			// If set to zero or to a non-existing channel, set to 1 as default
			if (detectionChannel == 0 || detectionChannel > server.nChannels()) {
				detectionChannel = 1;
				logger.info("Detection channel input does not exist. Set to 1 by default.");
			}
			
			// Get image data with intent to separate channels
			PathImage<ImagePlus> pathImage = PathImagePlus.createPathImage(server, pathROI, ServerTools.getDownsampleFactor(server, getPreferredPixelSizeMicrons(imageData, params), true));

			ImageProcessor ip = pathImage.getImage().getProcessor();
			
			// Detect the bit depth of the image
			int bitDepth = ip.getBitDepth();
			int MAX_PIXEL_VAL = (int) Math.pow(bitDepth, 2);
			// TODO :: Handle bit depths other than 16bit
			MAX_PIXEL_VAL = 65535;
			
			// Create map for channel data
			Map<String, FloatProcessor> channels = new LinkedHashMap<>();
			ImagePlus imp = pathImage.getImage();
			for (int c = 1; c <= imp.getNChannels(); c++) {
				channels.put("Channel " + c, imp.getStack().getProcessor(imp.getStackIndex(c, 0, 0)).convertToFloatProcessor());
			}
			
			// Get the processor for selected channel
	        FloatProcessor fp = channels.get("Channel " + detectionChannel);
	        
			// Remove all data outside the ROI of the annotation by setting a mask
//			Roi mask = ROIConverterIJ.convertToIJRoi(pathROI, pathImage);
//			fp.setRoi(mask);
			
			// Get with and height
	        final int w = fp.getWidth();
	        final int h = fp.getHeight();
	        
	        // Load pixel data into a new Mat object
	        float[] pix = (float[]) fp.getPixels();
	       	Mat mat = new Mat (h,w, CvType.CV_32FC1);
			mat.put(0, 0, pix);
			
/*************************
/ Processing starts here /
*************************/
			
	        // Start off with some simple preprocessing and a closing
			Mat matBackground = new Mat();

			Imgproc.medianBlur(mat, mat, 3);
			Imgproc.GaussianBlur(mat, mat, new Size(5, 5), gaussianSigma);
			Imgproc.morphologyEx(mat, matBackground, Imgproc.MORPH_CLOSE, OpenCVTools.getCircularStructuringElement(1));
			ProcessingCV.morphologicalReconstruction(mat, matBackground);
			
			// Apply opening by reconstruction & subtraction to reduce background
			Imgproc.morphologyEx(mat, matBackground, Imgproc.MORPH_OPEN, OpenCVTools.getCircularStructuringElement(openingRadius));
			ProcessingCV.morphologicalReconstruction(matBackground, mat);
			Core.subtract(mat, matBackground, mat);
			
			// Apply Gaussian filter
			int gaussianWidth = (int)(Math.ceil(gaussianSigma * 3) * 2 + 1);
			Imgproc.GaussianBlur(mat, mat, new Size(gaussianWidth, gaussianWidth), gaussianSigma);
			
			// Attempt FRS transform
			int [] radii = {6};
			Mat frs = new Mat();
			try {
				frs = FastRadialSymmetry.doTransform(mat, radii, 2, 1, FastRadialSymmetry.Mode.BRIGHT);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			Core.normalize(frs, frs, 0, 255, Core.NORM_MINMAX);
			frs.convertTo(frs, CvType.CV_8U);
//			Imgproc.threshold(frs, frs, 0, 255, Imgproc.THRESH_OTSU | Imgproc.THRESH_BINARY);
			
			// Apply Laplacian filter
			Mat matLoG = matBackground;
			Imgproc.Laplacian(mat, matLoG, mat.depth(), 1, -1, 0);
			
			// Threshold
			Mat matBinaryLoG = new Mat();
			Core.compare(matLoG, new Scalar(0), matBinaryLoG, Core.CMP_GT);
			
			// Do a watershed
			OpenCVTools.watershedIntensitySplit(matBinaryLoG, frs, 0, 1);
			
			IJTools.quickShowImage("FRS", MatToImagePlusConverter.toImageProcessor(matBinaryLoG));
					
			Mat binary = matBinaryLoG;
			
			// Split using distance transform, if necessary
			if (splitShape) {
//				OpenCVTools.watershedDistanceTransformSplit(binary, openingRadius/4);
			}

			// Use OpenCV to find simple contours
			List <MatOfPoint> contours = new ArrayList<>();
			Imgproc.findContours( binary , contours, new Mat (), Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
			
			Mat matBinary = frs.clone();
			
			// Create a labelled image for each contour
			Mat matLabels = new Mat(matBinary.size(), CvType.CV_32F, new Scalar(0));
			List<RunningStatistics> statsList = new ArrayList<>();
			List<MatOfPoint> tempContourList = new ArrayList<>(1);
			int label = 0;
			for (MatOfPoint contour : contours) {
				label++;
				tempContourList.clear();
				tempContourList.add(contour);
				Imgproc.drawContours(matLabels, tempContourList, 0, new Scalar(label), -1);
				statsList.add(new RunningStatistics());
			}
			
			// Compute mean for each contour, keep those that are sufficiently intense
			float[] labels = new float[(int)matLabels.total()];
			matLabels.get(0, 0, labels);
			computeRunningStatistics(pix, labels, statsList);
			int ind = 0;
			Scalar color = new Scalar(255);
			matBinary.setTo(new Scalar(0));
			for (RunningStatistics stats : statsList) {
				if (stats.getMean() > threshold) {
					tempContourList.clear();
					tempContourList.add(contours.get(ind));
					Imgproc.drawContours(matBinary, tempContourList, 0, color, -1);				
				}
				ind++;
			}
			
			// Dilate binary image & extract remaining contours
			Imgproc.dilate(matBinary, matBinary, Imgproc.getStructuringElement(Imgproc.CV_SHAPE_RECT, new Size(3, 3)));
			Core.min(matBinary, frs, matBinary);
			
			OpenCVTools.fillSmallHoles(matBinary, minArea*4);
			
			// Create path objects from contours		
			contours = new ArrayList<>();
			Mat hierarchy = new Mat();
			Imgproc.findContours(matBinary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
			ArrayList<Point2> points = new ArrayList<>();
			
			// Create label image
			matLabels.setTo(new Scalar(0));
			
			// Update the labels to correspond with the contours, and compute statistics
			label = 0;
			List<RunningStatistics> statsHematoxylinList = new ArrayList<>(contours.size());
			List<RunningStatistics> statsDABList = new ArrayList<>(contours.size());
			for (MatOfPoint contour : contours){
				
				// Discard single pixels / lines
				if (contour.size().height <= 2)
					continue;
				
				// Simplify the contour slightly
				MatOfPoint2f contour2f = new MatOfPoint2f();
				contour2f.fromArray(contour.toArray());
				MatOfPoint2f contourApprox = new MatOfPoint2f();
				Imgproc.approxPolyDP(contour2f, contourApprox, 0.5, true);
				contour2f = contourApprox;
				
				// Create a polygon ROI
		        points.clear();
		        for (org.opencv.core.Point p : contour2f.toArray())
		        	points.add(new Point2(p.x * downsample + x, p.y * downsample + y));
		        	        
		        // Add new polygon if it is contained within the ROI & measurable
		        PolygonROI pathPolygon = new PolygonROI(points);
		        if (!(pathPolygon.getArea() >= minArea)) {
		        	// Don't do a simpler < because we also want to discard the region if the area couldn't be measured (although this is unlikely)
		        	continue;
		        }
		        
	//	        logger.info("Area comparison: " + Imgproc.contourArea(contour) + ",\t" + (pathPolygon.getArea() / downsample / downsample));
	//	        Mat matSmall = new Mat();
		        if (pathROI instanceof RectangleROI || PathObjectTools.containsROI(pathROI, pathPolygon)) {
		        	MeasurementList measurementList = MeasurementListFactory.createMeasurementList(20, MeasurementList.TYPE.FLOAT);
		        	PathObject pathObject = new PathDetectionObject(pathPolygon, null, measurementList);
		        	
		        	measurementList.addMeasurement("Area", pathPolygon.getArea());
		        	measurementList.addMeasurement("Perimeter", pathPolygon.getPerimeter());
		        	measurementList.addMeasurement("Circularity", pathPolygon.getCircularity());
		        	measurementList.addMeasurement("Solidity", pathPolygon.getSolidity());
		        	
		        	// I am making an assumption regarding square pixels here...
		        	RotatedRect rrect = Imgproc.minAreaRect(contour2f);
		        	measurementList.addMeasurement("Min axis", Math.min(rrect.size.width, rrect.size.height) * downsample);
		        	measurementList.addMeasurement("Max axis", Math.max(rrect.size.width, rrect.size.height) * downsample);
		        		        	
		        	// Store the object
		        	pathObjects.add(pathObject);
		        	
		        	// Create a statistics object & paint a label in preparation for intensity stat computations later
		        	label++;
		        	statsHematoxylinList.add(new RunningStatistics());

		        	tempContourList.clear();
		        	tempContourList.add(contour);
		        	Imgproc.drawContours(matLabels, tempContourList, 0, new Scalar(label), -1);
		        	
		        	IJTools.quickShowImage("image", MatToImagePlusConverter.toImageProcessor(matLabels));
		        }
			}
			
			logger.info("Found " + pathObjects.size() + " contours");
						
			return pathObjects;
		}

		@Override
		public String getLastResultsDescription() {
			if (pathObjects == null)
				return null;
			int nDetections = pathObjects.size();
			if (nDetections == 1)
				return "1 nucleus detected";
			String s = String.format("%d nuclei detected", nDetections);
			if (nucleiClassified) {
				int nPositive = PathObjectTools.countObjectsWithClass(pathObjects, PathClassFactory.getPathClass(PathClassFactory.getPositiveClassName()), false);
				int nNegative = PathObjectTools.countObjectsWithClass(pathObjects, PathClassFactory.getPathClass(PathClassFactory.getNegativeClassName()), false);
				return String.format("%s (%.3f%% positive)", s, ((double)nPositive * 100.0 / (nPositive + nNegative)));			
			} else
				return s;
		}
		
		public static double getPreferredPixelSizeMicrons(ImageData<BufferedImage> imageData, ParameterList params) {
			if (imageData.getServer().hasPixelSizeMicrons())
				return Math.max(params.getDoubleParameterValue("preferredMicrons"), imageData.getServer().getAveragedPixelSizeMicrons());
			return Double.NaN;
		}
		
	}

	@Override
	public String getName() {
		return "OpenCV Cell Detection Test";
	}

	@Override
	public String getDescription() {
		return "Test of OpenCV methods for cell detection.";
	}

	@Override
	public String getLastResultsDescription() {
		return detector == null ? "" : detector.getLastResultsDescription();
	}

	@Override
	protected double getPreferredPixelSizeMicrons(ImageData<BufferedImage> imageData, ParameterList params) {
		return OpenCvDetector.getPreferredPixelSizeMicrons(imageData, params);
	}

	@Override
	protected ObjectDetector<BufferedImage> createDetector(ImageData<BufferedImage> imageData, ParameterList params) {
		return new OpenCvDetector();
	}

	@Override
	protected int getTileOverlap(ImageData<BufferedImage> imageData, ParameterList params) {
		double pxSize = imageData.getServer().getAveragedPixelSizeMicrons();
		if (Double.isNaN(pxSize))
			return params.getDoubleParameterValue("cellExpansion") > 0 ? 25 : 10;
		double cellExpansion = params.getDoubleParameterValue("cellExpansionMicrons") / pxSize;
		int overlap = cellExpansion > 0 ? (int)(cellExpansion + 10) : 10;
//		System.out.println("Tile overlap: " + overlap + " pixels");
		return overlap;
	}

	@Override
	public ParameterList getDefaultParameterList(ImageData<BufferedImage> imageData) {
		
		// Get the classes that currently exist and display them as choices
		List <String> classList = new ArrayList<>();
		classList.add("Gaussian");
		classList.add("Mean");		
		
		if (imageData.isFluorescence()) {
			logger.info("FLUORESCENCE IMAGE");
		}
						
		ParameterList params = new ParameterList();
		params.addIntParameter("detectionChannel", "Detection channel", 1).
			addDoubleParameter("preferredMicrons", "Preferred pixel size", 0.5, GeneralTools.micrometerSymbol());
		
		if (imageData.getServer().hasPixelSizeMicrons()) {
			String um = GeneralTools.micrometerSymbol();
			params.addDoubleParameter("medianRadius", "Median radius", 1, um).
				addDoubleParameter("gaussianSigma", "Gaussian sigma", 1.5, um).
				addDoubleParameter("openingRadius", "Opening radius", 8, um).
				addDoubleParameter("threshold", "Threshold", 0.1, null, 0, 1.0).
				addDoubleParameter("minArea", "Minimum area", 25, um+"^2");
		} else {
			params.setHiddenParameters(true, "preferredMicrons");
			params.addDoubleParameter("medianRadius", "Median radius", 1, "px").
					addDoubleParameter("gaussianSigma", "Gaussian sigma", 2, "px").
					addDoubleParameter("openingRadius", "Opening radius", 20, "px").
					addDoubleParameter("threshold", "Threshold", 0.1, null, 0, 1.0).
					addDoubleParameter("minArea", "Minimum area", 100, "px^2");
		}
		params.addBooleanParameter("splitShape", "Split by shape", true);		
		
		// Extra parameters
		params.addBooleanParameter("simplifyShapes", "Simplify object contours", true);
		params.addBooleanParameter("adaptiveThreshold", "Adaptive thresholding", true);		
		params.addIntParameter("adaptiveBlockSize", "Adaptive threshold block size", 35);
		
		return params;
	}
	
	public RunningStatistics computeRunningStatistics(float[] pxIntensities, byte[] pxMask, int width, Rect bounds) {
		RunningStatistics stats = new RunningStatistics();
		for (int i = 0; i < pxMask.length; i++) {
			if (pxMask[i] == 0)
				continue;
			// Compute the image index
			int x = i % bounds.width + bounds.x;
			int y = i % bounds.width + bounds.y;
			// Add the value
			stats.addValue(pxIntensities[y * width + x]);
		}
		return stats;
	}
	
	private static void computeRunningStatistics(float[] pxIntensities, float[] pxLabels, List<RunningStatistics> statsList) {
		float lastLabel = Float.NaN;
		int nLabels = statsList.size();
		RunningStatistics stats = null;
		for (int i = 0; i < pxIntensities.length; i++) {
			float label = pxLabels[i];
			if (label == 0 || label > nLabels)
				continue;
			// Get a new statistics object if necessary
			if (label != lastLabel) {
				stats = statsList.get((int)label-1);
				lastLabel = label;
			}
			// Add the value
			stats.addValue(pxIntensities[i]);
		}
	}
	
	// TODO: If this ever becomes important, switch to using the QuPathCore implementation instead of this one
	@Deprecated
	public static float[][] colorDeconvolve(BufferedImage img, double[] stain1, double[] stain2, double[] stain3, int nStains) {
		// TODO: Precompute the default matrix inversion
		if (stain3 == null)
			stain3 = StainVector.cross3(stain1, stain2);
		double[][] stainMat = new double[][]{stain1, stain2, stain3};
		ColorDeconvMatrix3x3 mat3x3 = new ColorDeconvMatrix3x3(stainMat);
		double[][] matInv = mat3x3.inverse();
		double[] stain1Inv = matInv[0];
		double[] stain2Inv = matInv[1];
		double[] stain3Inv = matInv[2];
	
		// Extract the buffered image pixels
		int[] buf = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
		// Preallocate the output
		float[][] output = new float[nStains][buf.length];
		
		// Apply color deconvolution
		double[] od_lut = ColorDeconvolutionHelper.makeODLUT(255, 256);
		for (int i = 0; i < buf.length; i++) {
			int c = buf[i];
			// Extract RGB values & convert to optical densities using a lookup table
			double r = od_lut[(c & 0xff0000) >> 16];
			double g = od_lut[(c & 0xff00) >> 8];
			double b = od_lut[c & 0xff];
			// Apply deconvolution & store the results
			for (int s = 0; s < nStains; s++) {
				output[s][i] = (float)(r * stain1Inv[s] + g * stain2Inv[s] + b * stain3Inv[s]);
			}
		}
		return output;
	}
}
