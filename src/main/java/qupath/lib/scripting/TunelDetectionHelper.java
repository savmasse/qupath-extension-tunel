
package qupath.lib.scripting;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.plugin.tool.RoiRotationTool;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.AreaROI;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.interfaces.PathShape;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.WatershedCellDetection2.CellDetector;
import qupath.lib.scripting.WatershedNucleusDetection.WatershedNucleusDetector;

/**
 * Simple helper class for the detection of objects in two channels without overwriting detections
 * in different channels. All that happens here is the detection in the two channels and then a comparison
 * of the objects' ROI to see if the detections are actually the same object.
 * 
 * @author Sam Vanmassenhove
 *
 */
public class TunelDetectionHelper {
	
	private static final Logger logger = LoggerFactory.getLogger(TunelDetectionHelper.class);
	
	private ImageData<BufferedImage> imageData;
	private List<String> args;
	
	private boolean showUI = false;
	
	private Map<Integer, List<PathObject>> pathObjectMap;
	private List<PathObject> pathObjects;
	private double pixelSize;
	
	public TunelDetectionHelper(final List<String> args, final boolean showUI) {

		this.imageData = QuPathGUI.getInstance().getImageData();
		this.pixelSize = imageData.getServer().getAveragedPixelSizeMicrons();
		this.args = args;
		
		this.pathObjectMap = new HashMap<>();
		this.pathObjects = new ArrayList<>();
	}

	public void runDetection () {
		
		// Run for all the channels in the argList
		for (String arg : args) {
			
			// Get the channel number
			Map<String, String> map = GeneralTools.parseArgStringValues(arg);
			
			// Detect in the channel
			List<PathObject> tempList = (ArrayList<PathObject>) detectChannel(arg);
			
			// Add to map and display
			pathObjects.addAll(tempList);
			
			pathObjectMap.put( Integer.parseInt( map.get("detectionImageFluorescence") ), tempList );
			logger.info("Channel " + map.get("detectionImageFluorescence") + " : " + tempList.size());	
		}
		
		// Check for overlapping elements
		handleOverlappingObjects();
	}
	
	/**
	 * Checks whether the objects that were detected accross multiple channels were actually the same object.
	 * 
	 * TODO This is a very quick implementation; do this in a better way...
	 * 
	 */
	private void handleOverlappingObjects() {
		
		// Create new list
		List <PathObject> tempList = new ArrayList<>();
		
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
						
						// If no overlap, add to list
						if (!significantOverlap(p1, p2)) {	
							
							// Should only keep the largest object
							if (p1.getMeasurementList().getMeasurementValue("Nucleus: Area") > p2.getMeasurementList().getMeasurementValue("Nucleus: Area")) {
								if (!tempList.contains(p1)) {
									tempList.add(p1);
								}
								
								// In case of more than two channels, we have to remove the previous largest to make
								// room for the new largest objects.
//								if (tempList.contains(p2)) 
//									tempList.remove(p2);
							}
							else {
								if (!tempList.contains(p2)) {
									tempList.add(p2);
								}
							}							
						}
						else {
							// Add p2 anyway
							if (!tempList.contains(p2))
								tempList.add(p2);
						}
					}
				}
			}
		}
		
		logger.info("Total before removing overlapping objects : " + pathObjects.size());
		pathObjects = tempList;
		logger.info("Total objects after removing overlapping objects : " + pathObjects.size()); 
	}
	
	private boolean significantOverlap (PathObject p1, PathObject p2) {
		
		// Get the ROIs
		ROI r1 = p1.getROI();
		ROI r2 = p2.getROI();
		
		// Check if they overlap
		if (PathROIToolsAwt.containsShape((PathShape) r1, (PathShape) r2) || PathROIToolsAwt.containsShape((PathShape) r2, (PathShape) r1)) {
//		if (PathROIToolsAwt.getArea(r1).getBounds().intersects(new Rectangle(PathROIToolsAwt.getArea(r2).getBounds()))) {
//			logger.info("Overlap found: (" + r1.getCentroidX()*pixelSize + ", " + r1.getCentroidY()*pixelSize + ")");
			return true;
		}		
				
		return false;
	}
	
	private Collection<PathObject> detectChannel (String arg) {
		
		// Update the parameters
		ParameterList params = parseArgument(arg);
		
//		WatershedNucleusDetection watershed = new WatershedNucleusDetection();
//		WatershedNucleusDetection.WatershedNucleusDetector detector = (WatershedNucleusDetector) watershed.createDetector(imageData, params); // Params as argument
//		
//		ROI roi = imageData.getHierarchy().getSelectionModel().getSelectedObject().getROI();
//		Collection <PathObject> pathObjects = detector.runDetection(imageData, params, roi);
		
		WatershedCellDetection2 w = new WatershedCellDetection2();
		WatershedCellDetection2.CellDetector cd = (CellDetector) w.createDetector(imageData, params);
		
		ROI roi = imageData.getHierarchy().getSelectionModel().getSelectedObject().getROI();
		
		Collection <PathObject> pathObjects = cd.runDetection(imageData, params, roi);

		return pathObjects;
	}
	
	
	private ParameterList parseArgument (String arg) {
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
}