
package qupath.lib.scripting;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import javafx.concurrent.*;
import javafx.scene.Cursor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.plugins.PluginRunnerFX;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.PathShape;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.WatershedCellDetection2.CellDetector;

/**
 * Simple helper class for the detection of objects in two channels without overwriting detections
 * in different channels. All that happens here is the detection in multiple channels and then a comparison
 * of the objects' ROI to see if the detections are actually the same object.
 * 
 * @author Sam Vanmassenhove
 *
 */
public class TunelDetectionHelper {
	
	private static final Logger logger = LoggerFactory.getLogger(TunelDetectionHelper.class);
	
	private ImageData<BufferedImage> imageData;
	private List<String> args;
	
	private Map<Integer, List<PathObject>> pathObjectMap;
	private List<PathObject> pathObjects;
	private List<List<PathObject>> pathObjectListList;
	private double pixelWidth, pixelHeight;
	private boolean showResults;
	private int maxDistance;
	private int overlappedCount;
	private double overlap;
	
	/**
	 * 
	 * @param args
	 * @param showResults
	 */
	public TunelDetectionHelper(final List<String> args, final boolean showResults) {

		this.imageData = QuPathGUI.getInstance().getImageData();
		this.pixelWidth = imageData.getServer().getPixelWidthMicrons();
		this.pixelHeight = imageData.getServer().getPixelHeightMicrons();
		
		this.args = args;
		
		this.pathObjectMap = new HashMap<>();
		this.pathObjects = new ArrayList<>();
		this.showResults = showResults;
	}
	
	public TunelDetectionHelper(final ImageData<BufferedImage> imageData, final List<String> args, List<PathObject> pathObjects, List<List<PathObject>> pathObjectListList, final int maxDistance, final double overlap) {
		this.imageData = imageData;
		this.pixelWidth = imageData.getServer().getPixelWidthMicrons();
		this.pixelHeight = imageData.getServer().getPixelHeightMicrons();
		if (pixelWidth == 0.0 && pixelHeight == 0.0) {
			pixelWidth = imageData.getServer().getAveragedPixelSizeMicrons();
			pixelHeight = pixelWidth;
		}
		else if (pixelWidth == 0.0) {
			pixelWidth = pixelHeight;
		}
		else if (pixelWidth == 0.0) {
			pixelHeight = pixelWidth;
		}
		logger.info("H: " + pixelHeight);
		logger.info("W: " + pixelWidth);
		this.args = args;
		
		this.pathObjectListList = pathObjectListList;
		this.pathObjects = pathObjects;
		if (pathObjects == null) {
			this.pathObjects = new ArrayList<>();
			for (List<PathObject> list : pathObjectListList) {
				this.pathObjects.addAll(list);
			}
		}
		
		this.overlap = overlap;
		this.maxDistance = maxDistance;
		this.overlappedCount = 0;
	}
	
	/**
	 * Run the detection in all required channels and handle the overlapping objects.
	 */
//	public void runDetection () {
//		
//		// Run for all the channels in the argList
//		for (String arg : args) {
//			
//			// Get the channel number
//			Map<String, String> map = GeneralTools.parseArgStringValues(arg);
//			
//			// Detect in the channel
//			List<PathObject> tempList = (ArrayList<PathObject>) detectChannel(arg);
//			
//			// Add to map and display
//			pathObjects.addAll(tempList);
//			
//			pathObjectMap.put( Integer.parseInt( map.get("detectionImageFluorescence") ), tempList );
//			logger.info("Channel " + map.get("detectionImageFluorescence") + " : " + tempList.size());	
//		}
//		
//		// Check for overlapping elements
//		handleOverlappingObjects();
//		
//		// Display the results if the user requires it
//		if (showResults) 
//			imageData.getHierarchy().addPathObjects(pathObjects, true);
//	}
	
	public void runDetection () {
		
		List <PluginTask> taskList = new ArrayList<>();
		ExecutorService pool = Executors.newFixedThreadPool(Math.min(Runtime.getRuntime().availableProcessors(), args.size()));
		QuPathGUI qupath = QuPathGUI.getInstance();
		
		for (String arg : args) {
			PluginTask p = new PluginTask(arg);
			taskList.add(p);
			pool.submit(p);
//			QuPathGUI.getInstance().runPlugin(new WatershedCellDetection2(), arg, false);
		}
		
		pool.shutdown(); // Don't take any more submissions
		logger.info("Pool was shut down.");
	}
	
	/**
	 * Run the multiple detections in the different channels, each in a different thread. At the end a 
	 * complete list of detections is compiled and overlapping detections are removed.
	 */
	public void runDetection1 () {
		
		List <DetectionTask> taskList = new ArrayList<>();
		
		QuPathGUI qupath = QuPathGUI.getInstance();
		for (String arg : args) {
			
			// Decode the args so we can get the channel number
			Map<String, String> map = GeneralTools.parseArgStringValues(arg);

			DetectionTask detectionTask = new DetectionTask(arg, imageData, Integer.parseInt(map.get("detectionImageFluorescence")));
						
			// Start a new thread for this task
//			Thread t = new Thread(detectionTask, "Detection channel " + Integer.parseInt(map.get("detectionImageFluorescence") ));
//			t.setDaemon(true);
//			t.start();
//			threadList.add(t);
			taskList.add(detectionTask);
		}
		
		// Create a pool to run the tasks
		ExecutorService pool = Executors.newFixedThreadPool(Math.min(Runtime.getRuntime().availableProcessors(), taskList.size()));
		logger.info("Available thread pool size : " + Math.min(Runtime.getRuntime().availableProcessors(), taskList.size()));
		try {
			pool.invokeAll(taskList);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		logger.info("Shutting down executor.");
		pool.shutdown(); // Stop allowing tasks
		
		// Remove overlapping detections
		int size = pathObjects.size();
		handleOverlappingObjects();
		logger.info(size - pathObjects.size() + " objects overlapping detections removed.");
		
		// Draw on the image
		if (showResults)
			imageData.getHierarchy().addPathObjects(pathObjects, false);
		
		// Let user know what happened
		logger.info("Detection finished : " + pathObjects.size() + " objects detected.");
	}
	
	/**
	 * Checks whether the objects that were detected accross multiple channels were actually the same object.
	 */
	public void handleOverlappingObjects() {
		
		// Create new list
		Set <PathObject> tempList = new HashSet<>(pathObjects);
		
		// Compare all objects in the map
		for (int i = 0; i < pathObjectListList.size()-1; i++ ) {
			List<PathObject> o1 = pathObjectListList.get(i);
			
			for (int j = (i+1); j < pathObjectListList.size(); j++) {
				List<PathObject> o2 = pathObjectListList.get(j);
								
				// Go through the objects in the list
				for (PathObject p1 : o1) {
					for (PathObject p2 : o2) {
						
						if (!(tempList.contains(p1) && tempList.contains(p2)))
							continue;
						
						// If the detections are very far apart we don't have to bother...
						ROI r1 = p1.getROI();
						ROI r2 = p2.getROI();
						if ( (r2.getCentroidX() - r1.getCentroidX())*(r2.getCentroidX() - r1.getCentroidX()) + (r2.getCentroidY() - r1.getCentroidY())*(r2.getCentroidY() - r1.getCentroidY()) > maxDistance*maxDistance) {
							continue;
						}
						
						// If overlap, remove
						if (significantOverlap(r1, r2)) {	
							
							// Should only keep the largest object
							if (p1.getMeasurementList().getMeasurementValue("Nucleus: Area") > p2.getMeasurementList().getMeasurementValue("Nucleus: Area")) {
								tempList.remove(p2);
							}
							else {
								tempList.remove(p1);
							}
							overlappedCount++;
						}
					}
				}
			}
		}
		
		// Set the new list
		pathObjects = new ArrayList<>(tempList);
	}
	
	/**
	 * Getter for the list of detections.
	 * @return
	 */
	public List<PathObject> getPathObjects () {
		return pathObjects;
	}
	
	public int getOverlappedCount () {
		return overlappedCount;
	}
	
	/**
	 * Checks whether the objects that were detected accross multiple channels were actually the same object.
	 * 
	 * TODO Make sure each channel is only compared against another once, this is not the case in the
	 * current implementation...
	 * 
	 */
	public void handleOverlappingObjects1() {
		
		// Create new list
		Set <PathObject> tempList = new HashSet<>(pathObjects);
		
		// Compare all objects in the map
		for (Integer i : pathObjectMap.keySet()) {
			List<PathObject> o1 = pathObjectMap.get(i);
			for (Integer j : pathObjectMap.keySet()) {
				List<PathObject> o2 = pathObjectMap.get(j);
				
				// We don't want to check channel against itself...
				if (i.equals(j)) 
					continue;
				
				// Go through the objects in the list
				for (PathObject p1 : o1) {
					for (PathObject p2 : o2) {
						
						if (!(tempList.contains(p1) && tempList.contains(p2)))
							continue;
						
						// If the detections are very far apart we don't have to bother...
						ROI r1 = p1.getROI();
						ROI r2 = p2.getROI();
						if ( (r2.getCentroidX() - r1.getCentroidX())*(r2.getCentroidX() - r1.getCentroidX()) + (r2.getCentroidY() - r1.getCentroidY())*(r2.getCentroidY() - r1.getCentroidY()) > 10000 ) {
//							tempList.add(p2);
							continue;
						}
						
						// If no overlap, add to list
						if (significantOverlap(r1, r2)) {	
							
							// Should only keep the largest object
							if (p1.getMeasurementList().getMeasurementValue("Nucleus: Area") > p2.getMeasurementList().getMeasurementValue("Nucleus: Area")) {
								tempList.remove(p2);
								
								// In case of more than two channels, we have to remove the previous largest to make
								// room for the new largest objects.
//								if (tempList.contains(p2)) 
//									tempList.remove(p2);
							}
						}
					}
				}
			}
		}
		
		logger.info("Total before removing overlapping objects : " + pathObjects.size());
		pathObjects = new ArrayList<>(tempList);
		logger.info("Total objects after removing overlapping objects : " + pathObjects.size()); 
	}
	
	/**
	 * Decide whether there is significant overlap between two ROIs.
	 * @param r1
	 * @param r2
	 * @return
	 */
	private boolean significantOverlap (ROI r1, ROI r2) {
		
//		// Check if they overlap
//		if (PathROIToolsAwt.containsShape((PathShape) r1, (PathShape) r2) || PathROIToolsAwt.containsShape((PathShape) r2, (PathShape) r1)) {
////		if (PathROIToolsAwt.getArea(r1).getBounds().intersects(new Rectangle(PathROIToolsAwt.getArea(r2).getBounds()))) {
////			logger.info("Overlap found: (" + r1.getCentroidX()*pixelSize + ", " + r1.getCentroidY()*pixelSize + ")");
//			return true;
//		}		
				
		double a1 = ((PathArea) r1).getScaledArea(pixelWidth, pixelHeight);
		double a2 = ((PathArea) r2).getScaledArea(pixelWidth, pixelHeight);
		double a3 = ((PathArea) PathROIToolsAwt.combineROIs((PathShape) r1, (PathShape) r2, PathROIToolsAwt.CombineOp.INTERSECT)).getScaledArea(pixelWidth, pixelHeight);
		
		if (a3 != (a1 + a2) && a3 > overlap*(Math.min(a1, a2))) {
//			logger.info (a3 + " > " + overlap*(Math.min(a1, a2)) + "; Min(" + a1 + ", " + a2 + ")");
			return true;
		}
		
		return false;
	}
	
	private ParameterList parseArgument (String arg, final ImageData<BufferedImage> imageData) {
		ParameterList params = new WatershedCellDetection2().getDefaultParameterList(imageData);
		
		if (arg != null) {
			logger.trace("Updating parameters with arg: {}", arg);
			// Parse JSON-style arguments
			Map<String, String> map = GeneralTools.parseArgStringValues(arg);
			// Use US locale for standardization, and use of decimal points (not commas)
			ParameterList.updateParameterList(params, map, Locale.US);
		}
		return params;
	}
	
//	class DetectionTask extends Task<Void> implements Callable<Void> {
	class DetectionTask implements Callable<Void> {
		private String arg;
		private ImageData<BufferedImage> imageData;
		private List<PathObject> objects;
		private int channel;
		
		public DetectionTask(final String arg, final ImageData<BufferedImage> imageData, final int channel) {
			this.arg = arg;
			this.imageData = imageData;
			this.channel = channel;
		}

		@Override
		public Void call() throws Exception {
			
			// Do the detection
			objects = (ArrayList<PathObject>) detectChannel(arg);
			
			// Add the detections to the list
			pathObjects.addAll(objects);
			pathObjectMap.put(channel, objects);
			
			logger.info("Channel " + channel + " : " + objects.size() + " detections.");
			
			return null;
		} 
		
//		@Override
		public void done() {
			if (Platform.isFxApplicationThread()) {
				logger.info("Channel " + channel + " : " + objects.size() + " detections.");
			}
			else
				Platform.runLater(() -> done());
		}
		
		/**
		 * Do a watershed nucleus detection in a certain channel.
		 * @param arg
		 * @return
		 */
		private Collection<PathObject> detectChannel (String arg) {
			
			// Update the parameters
			ParameterList params = parseArgument(arg, imageData);
			
			WatershedCellDetection2 w = new WatershedCellDetection2();
			WatershedCellDetection2.CellDetector cd = (CellDetector) w.createDetector(imageData, params);
			
			ROI roi = imageData.getHierarchy().getSelectionModel().getSelectedObject().getROI();
			
			Collection <PathObject> pathObjects = cd.runDetection(imageData, params, roi);
			return pathObjects;
		}
		
		
	}
	
	class PluginTask implements Callable<Void> {
		
		private String arg;
		
		public PluginTask(String arg) {
			this.arg = arg;
		}

		@Override
		public Void call() throws Exception {

			WatershedCellDetection2 w = new WatershedCellDetection2();
			w.runPlugin(new PluginRunnerFX(QuPathGUI.getInstance(), true), arg);
			QuPathGUI.getInstance().getImageData().getHierarchy().getObjects(pathObjects, PathDetectionObject.class);
			
			logger.info("Object lists size : " + pathObjects.size());
			
			return null;
		}
		
	}
}