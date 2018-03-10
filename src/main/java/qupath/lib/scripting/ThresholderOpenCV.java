package qupath.lib.scripting;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.plaf.multi.MultiTableHeaderUI;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import groovy.transform.AutoCloneStyle;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.AutoThresholder;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.process.StackStatistics;
import ij.process.AutoThresholder.Method;
import qupath.imagej.helpers.IJTools;
import qupath.imagej.objects.PathImagePlus;
import qupath.imagej.objects.ROIConverterIJ;
import qupath.imagej.processing.SimpleThresholding;
import qupath.lib.analysis.algorithms.Watershed;
import qupath.lib.analysis.stats.StatisticsHelper;
import qupath.lib.awt.common.AwtTools;
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
import qupath.lib.plugins.objects.ShapeFeaturesPlugin;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.experimental.ShapeSimplifier;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.processing.OpenCVTools;
import qupath.opencv.processing.ProcessingCV;

public class ThresholderOpenCV extends AbstractTileableDetectionPlugin <BufferedImage> {
	private static final Logger logger = LoggerFactory.getLogger(OpenCVDetection.class);
			
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
			adaptiveBlockSize = params.getDoubleParameterValue("adaptiveBlockSize");
			kernelSize = params.getDoubleParameterValue("kernelSize");
			kernelSize = (int) kernelSize;
			final int ADAPTIVE_METHOD = params.getChoiceParameterValue("adaptiveMethod").equals(ADAPTIVE_GAUSSIAN) ? Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C : Imgproc.ADAPTIVE_THRESH_MEAN_C;
			
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
	        final float[] pix = (float[]) fp.getPixels();
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
			
//			mat.convertTo(write, CvType.CV_16U);
//			Imgcodecs.imwrite("C:\\Users\\SamVa\\Desktop\\Thesis\\data\\saved\\before.png", write);
//			
			ProcessingCV.morphologicalReconstruction(matBackground, mat);
			Core.subtract(mat, matBackground, mat);
//			
//			// Write
//			mat.convertTo(write, CvType.CV_16U);
//			Imgcodecs.imwrite("C:\\Users\\SamVa\\Desktop\\Thesis\\data\\saved\\after.png", write);
			
			// Write
			matBackground.convertTo(matBackground, CvType.CV_16U);
			Imgcodecs.imwrite("C:\\Users\\SamVa\\Desktop\\Thesis\\data\\saved\\background.png", matBackground);
			
			// Apply Gaussian filter
//			int gaussianWidth = (int)(Math.ceil(gaussianSigma * 3) * 2 + 1);
//			Imgproc.GaussianBlur(mat, mat, new Size(gaussianWidth, gaussianWidth), gaussianSigma);
			
			// Apply morphological transformations
			//Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(kernelSize,kernelSize)));
			//Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_DILATE, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(kernelSize,kernelSize)));

			
			// Threshold
			Mat binary = new Mat();
			if (!adaptiveThreshold) {
				//Imgproc.threshold(mat, binary, threshold*MAX_PIXEL_VAL, MAX_PIXEL_VAL, Imgproc.THRESH_BINARY);
//				// Convert the binary image to 8bit
				//binary.convertTo(binary, CvType.CV_8U, 0.00390625);
				
// -- NEW : Try AutoThresholding in the ImageJ library
				
				// Set the thresholding method
				AutoThresholder.Method m;
				logger.info(thresholder);
				switch (thresholder) {
					case "Huang": 
						m = AutoThresholder.Method.Huang;
						break;
					case "Intermodes": 
						m = AutoThresholder.Method.Intermodes;
						logger.info("Intermodes");
						break;
					case "IsoData": 
						m = AutoThresholder.Method.IsoData;
						break;
					case "Li": 
						m = AutoThresholder.Method.Li;
						break;
					case "MaxEntropy": 
						m = AutoThresholder.Method.MaxEntropy;
						logger.info("MaxEntropy");
						break;
					case "Mean": 
						m = AutoThresholder.Method.Mean;
						break;
					case "MinError":
						m = AutoThresholder.Method.MinError;
						break;
					case "Minimum":
						m = AutoThresholder.Method.Minimum;
						break;
					case "Moments": 
						m = AutoThresholder.Method.Moments;
						break;
					case "Otsu":
						m = AutoThresholder.Method.Otsu;
						break;
					case "Percentile":
						m = AutoThresholder.Method.Percentile;
						break;
					case "RenyiEntropy": 
						m = AutoThresholder.Method.RenyiEntropy;
						break;
					case "Shanbhag": 
						m = AutoThresholder.Method.Shanbhag;
						break;
					case "Triangle":
						m = AutoThresholder.Method.Triangle;
						break;
					case "Yen": 
						m = AutoThresholder.Method.Yen;
						break;
					default: 
						m = AutoThresholder.Method.Default;
				}
				
				// Convert Mat to IP
				ImageProcessor ipMat = MatToImagePlusConverter.toImageProcessor(mat);
				ipMat = (FloatProcessor) ipMat;
				
				// Thresholding
				AutoThresholder at = new AutoThresholder();
				int t = at.getThreshold(m, ipMat.getHistogram(256))*255;
				ByteProcessor bp = SimpleThresholding.thresholdAbove(ipMat, t);
				
				// Convert back to openCV
				binary = ImagePlusToMatConverter.toMat(bp);
				//binary = ImagePlusToMatConverter.toMat(ipMat);
				//binary.convertTo(binary, CvType.CV_8U, 0.00390625);
								
				Imgcodecs.imwrite("C:\\Users\\SamVa\\Desktop\\Thesis\\data\\saved\\binary.png", binary);
// -- END NEW
				
			}
			else {
				mat.convertTo(binary, CvType.CV_8U, 0.00390625);
				Imgproc.adaptiveThreshold(binary, binary, MAX_PIXEL_VAL, ADAPTIVE_METHOD, Imgproc.THRESH_BINARY, (int) adaptiveBlockSize, 0);
			}
						
			
			// Split using distance transform, if necessary
			if (splitShape) {
//				OpenCVTools.watershedDistanceTransformSplit(binary, openingRadius/4);
				Imgproc.dilate(binary, binary, Imgproc.getStructuringElement(Imgproc.CV_SHAPE_ELLIPSE, new Size(kernelSize, kernelSize)));
				Imgproc.erode(binary, binary, Imgproc.getStructuringElement(Imgproc.CV_SHAPE_ELLIPSE, new Size(kernelSize, kernelSize)));
			}


			// Use OpenCV to find simple contours
			List <MatOfPoint> contours = new ArrayList<>();
			Imgproc.findContours( binary , contours, new Mat (), Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
			ArrayList<Point2> points = new ArrayList<>();
//--------
			
			// Go through contours
			for (MatOfPoint contour : contours) {
				
				if (contour.size().height <= 2) {
					continue;
				}
				
				// Create polygon 
				points.clear();
				for (org.opencv.core.Point p : contour.toArray())
		        	points.add(new Point2(p.x * downsample + x, p.y * downsample + y));
				
				PolygonROI pathPolygon = new PolygonROI(points);
				
				// Only go on if the polygon is inside the ROI
				if ( !(pathROI instanceof RectangleROI) && !(PathObjectTools.containsROI(pathROI, pathPolygon)) ) {
					continue;
				}
								
				// TODO :: Make simplifications before the measurement ?
	        	PolygonROI polygonROI;
	        	if (simplifyShapes) {
					// Simplify the shape of the ROI (adapted from WatershedCellDetection)
					Calibration cal = pathImage.getImage().getCalibration();
		        	PolygonRoi rOrig = (PolygonRoi) ROIConverterIJ.convertToIJRoi(pathPolygon, pathImage);
		        	PolygonRoi polygonRoi = new PolygonRoi(rOrig.getInterpolatedPolygon(Math.min(2.5, rOrig.getNCoordinates()*0.1), true), Roi.POLYGON);
		        	
		        	// We're not dealing with z-stacks/t-stacks so ignore this for now...
		        	int z = 0, t = 0;
					polygonROI = ROIConverterIJ.convertToPolygonROI(polygonRoi, cal, pathImage.getDownsampleFactor(), 0, z, t);
		        	polygonROI = ShapeSimplifier.simplifyPolygon(polygonROI, pathImage.getDownsampleFactor()/4.0);
		        	
		        	pathPolygon = polygonROI;
	        	}
				
				// Don't save polygon if smaller than minimum allowed area
				double area = pathPolygon.getArea();
				if (!(area >= minArea)) {
					continue;
				}
				
				// Create measurements
	        	MeasurementList measurementList = MeasurementListFactory.createMeasurementList(20, MeasurementList.TYPE.FLOAT);
	        	measurementList.addMeasurement("Nucleus: Area", area);
	        	measurementList.addMeasurement("Nucleus: Perimeter", pathPolygon.getPerimeter());
	        	measurementList.addMeasurement("Nucleus: Circularity", pathPolygon.getCircularity());
	        	measurementList.addMeasurement("Nucleus: Solidity", pathPolygon.getSolidity()); 
	        	
	        	// Calculate extent
	        	double extent = area / (pathPolygon.getBoundsHeight()*pathPolygon.getBoundsWidth());
				measurementList.addMeasurement("Extent", extent);
				
				// Calculate aspect ratio
				double aspectRatio = pathPolygon.getBoundsHeight() / pathPolygon.getBoundsWidth();
				measurementList.addMeasurement("Aspect ratio", aspectRatio);
				
				// Create a simple PathDetectionObject    	
				PathObject pathObject = new PathDetectionObject (pathPolygon, null, measurementList);

				// Add PathObject to the list
				pathObjects.add(pathObject);
			}
			

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
		params.addDoubleParameter("adaptiveBlockSize", "Adaptive threshold block size", 35);
		params.addChoiceParameter("adaptiveMethod", "Adaptive thresholding method", classList.get(0), classList, "Choose the method used for the adaptive thresholding.");
		params.addDoubleParameter("kernelSize", "Adaptive threshold kernel size", 3);
		
		// Create the thresholder choice
		String [] sList = {"Default", "Huang", "Intermodes", "IsoData", "Li", "Minimum",
							"MaxEntropy", "Mean", "MinError", "Minimum", "Moments", "Otsu", "Percentile",
							"RenyiEntropy", "Shanbhag", "Triangle", "Yen"};
		List <String> thresholderList = new ArrayList<>(Arrays.asList(sList));
		
		params.addChoiceParameter("thresholder", "Thresholding method", "Otsu", thresholderList);
		
		return params;
	}
}
