package qupath.lib.scripting;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.PathROIToolsAwt.CombineOp;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.interfaces.PathShape;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;


/*
 * This command will create a PathObject from a selected annotation.
 */
public class CreateCellFromAnnotation implements PathCommand {
	
	private static final Logger logger = LoggerFactory.getLogger(CreateParentAnnotation.class);
	
	private QuPathGUI qupath;
	private PathObjectHierarchy hierarchy;
	private Set <PathObject> selected; // Will not contain duplicates
	
	// Constructor
	public CreateCellFromAnnotation (final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		
		// Get the hierarchy from the image
		hierarchy = qupath.getImageData().getHierarchy();
		
		// Get the selected PathObjects
		selected = hierarchy.getSelectionModel().getSelectedObjects();
		
		// Check if set is empty
		if (selected.isEmpty()) {
			logger.info("No objects selected! Could not create annotation.");
			return;
		}
		
		// Get the annotation
		for (PathObject p : selected) {
			
			// Only apply this to selected annotations.
			if (!(p instanceof PathAnnotationObject)) {
					logger.info("One of the selected PathObjects was not an annotation and was skipped.");
					continue;
			}
			
			// Get properties
			ROI roi = p.getROI();
			PolygonROI pathPolygon = new PolygonROI(roi.getPolygonPoints());
					
			// Get measurements
			MeasurementList measurementList = MeasurementListFactory.createMeasurementList(20, MeasurementList.TYPE.FLOAT);
        	measurementList.addMeasurement("Nucleus: Area", pathPolygon.getArea());
        	measurementList.addMeasurement("Nucleus: Perimeter", pathPolygon.getPerimeter());
        	measurementList.addMeasurement("Nucleus: Circularity", pathPolygon.getCircularity());
        	measurementList.addMeasurement("Nucleus: Solidity", pathPolygon.getSolidity());
			
			// Apply properties to new PathObject
			PathObject newObject = new PathDetectionObject(roi, null, measurementList);
			p.addPathObject(newObject);
		}
		
		
	}
	
}