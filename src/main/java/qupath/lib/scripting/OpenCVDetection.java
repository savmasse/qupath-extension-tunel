package qupath.lib.scripting;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.color.ColorDeconvMatrix3x3;
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.StainVector;
import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
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
import qupath.lib.roi.interfaces.ROI;

public class OpenCVDetection extends AbstractTileableDetectionPlugin <BufferedImage> {
	private static final Logger logger = LoggerFactory.getLogger(OpenCVDetection.class);
			
	transient static OpenCvDetector detector;
	
	static class OpenCvDetector implements ObjectDetector<BufferedImage> {
		private ROI pathROI;
		private List <PathObject> pathObjects = new ArrayList<>();
		private boolean nucleiClassified = false;

		@Override
		public Collection<PathObject> runDetection(ImageData<BufferedImage> imageData, ParameterList params, ROI pathROI) {
			
			// clear
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
			double gaussianSigma, minArea, threshold;
			
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
			
			// Set threshold regardless of size
			threshold = params.getDoubleParameterValue("threshold");
			int MAX_PIXEL_VAL = 65535; // We assume 16bit image for now...
			
			// Read the actual pixel data from the BufferedImage
			short[] pixels = ((DataBufferUShort) img.getRaster().getDataBuffer()).getData();

			// Copy to array of doubles
			double[] doubles = new double[pixels.length];
			for(int i=0; i<pixels.length; i++) {
			    doubles[i] = pixels[i];
			}
			
			// Convert to OpenCV Mat
			int width = img.getWidth();
			int height = img.getHeight();
			Mat mat = new Mat(height, width, CvType.CV_32FC1);
						
			// It seems OpenCV doesn't use the array directly, so no need to copy...
			mat.put(0, 0, doubles);
			
			// Convert to 16bit and write to disk to see if it works
			Mat write = new Mat();
			mat.convertTo(write, CvType.CV_16U);
			Imgcodecs.imwrite("C:\\Users\\SamVa\\Desktop\\Thesis\\data\\saved\\test.png", write);
			
//			String string = "ARRAY: " + mat.get(0, 0)[0];
//			logger.info(string);
			
			// Create binary of the image
			Mat matBinary = new Mat();
//			Core.compare(mat, new Scalar(100), matBinary, Core.CMP_GT);
			Imgproc.threshold(mat, matBinary, threshold*MAX_PIXEL_VAL, MAX_PIXEL_VAL, Imgproc.THRESH_BINARY);
			
			// Convert to 16bit and print
			matBinary.convertTo(write, CvType.CV_16U);
			Imgcodecs.imwrite("C:\\Users\\SamVa\\Desktop\\Thesis\\data\\saved\\Binary.png", write);
			
			matBinary.convertTo(matBinary, CvType.CV_32SC1);
			
			// Use OpenCV to find simple contours
			List <MatOfPoint> contours = new ArrayList<>();
			Imgproc.findContours( matBinary, contours, new Mat (), Imgproc.RETR_FLOODFILL,Imgproc.CHAIN_APPROX_SIMPLE);
			ArrayList<Point2> points = new ArrayList<>();
			
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
				
				// Don't save polygon if smaller than minimum allowed area
				double area = pathPolygon.getArea();
				if (!(area >= minArea)) {
					continue;
				}
				
				// Create measurements
	        	MeasurementList measurementList = MeasurementListFactory.createMeasurementList(20, MeasurementList.TYPE.FLOAT);
	        	measurementList.addMeasurement("Area", area);
				
				// Create a simple object	        	
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
		ParameterList params = new ParameterList();
		params.addDoubleParameter("preferredMicrons", "Preferred pixel size", 0.5, GeneralTools.micrometerSymbol());
//				addIntParameter("downsampleFactor", "Downsample factor", 2, "", 1, 4);
		
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
		
		return params;
	}
}
