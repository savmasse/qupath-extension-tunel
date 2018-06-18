package qupath.lib.scripting;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.PathClassificationLabellingHelper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.commands.scriptable.MergeSelectedAnnotationsCommand;
import qupath.lib.gui.panels.classify.PathClassifierPanel;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.PathROIToolsAwt.CombineOp;
import qupath.lib.roi.interfaces.PathShape;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;

public class CreateParentAnnotation implements PathCommand {
	
	private static final Logger logger = LoggerFactory.getLogger(CreateParentAnnotation.class);
	
	private QuPathGUI qupath;
	private PathObjectHierarchy hierarchy;
	private Set <PathObject> selected; // Will not contain duplicates
	
	// Constructor
	public CreateParentAnnotation(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		
		// Get the hierarchy from the image
		hierarchy = qupath.getImageData().getHierarchy();
		
		// TEST
//		QuPathGUI qupath = QuPathGUI.getInstance();
//		PathClass pc = PathClassFactory.getPathClass("NEWCLASS");
//		qupath.getAvailablePathClasses().add(pc);
		
		// Get the selected PathObjects
		selected = hierarchy.getSelectionModel().getSelectedObjects();
		
		// Check if set is empty
		if (selected.isEmpty()) {
			logger.info("No objects selected! Could not create annotation.");
			return;
		}
		
//		// Add the set to the new annotation
		PathAnnotationObject annotation = new PathAnnotationObject();
//		annotation.addPathObjects(selected);
		
		// Add the new annotation to hierarchy at highest level
		hierarchy.addPathObject(annotation, true);
		
//		// Loop through the selected cells
//		for (PathObject p : selected) {
//
//			// Create new annotation
//			ROI roi = p.getROI();
//			PathClass pathClass = p.getPathClass();
//			PathAnnotationObject cellAnnotation = new PathAnnotationObject(roi, pathClass);
//			cellAnnotation.addPathObject(p);
//			
//			// Add this annotation to the larger annotation
////			annotation.addPathObject(cellAnnotation);
//			hierarchy.addPathObject(cellAnnotation, true);
//		}
		
		// TEST :: Create a large annotation from the ROIs of selected cells
		ROI totalROI = null;
		for (PathObject p : selected) {
			ROI roi = p.getROI();
			if (totalROI != null) 
				totalROI = PathROIToolsAwt.combineROIs((PathShape) totalROI, (PathShape) roi, CombineOp.ADD);
			else 
				totalROI = roi;
			annotation.addPathObject(p);
		}
		annotation.setROI(totalROI);
		hierarchy.addPathObject(annotation, true);
		
		// Log what was done
		logger.info("Selected " + selected.size() + " cells.");
	}
	
}