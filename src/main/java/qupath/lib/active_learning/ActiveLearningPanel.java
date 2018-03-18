package qupath.lib.active_learning;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.layout.Pane;
import jfxtras.scene.layout.GridPane;
import jfxtras.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.dialogs.ParameterPanelFX;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent.HierarchyEventType;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.parameters.ParameterChangeListener;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.PathROIToolsAwt.CombineOp;
import qupath.lib.roi.interfaces.PathShape;

public class ActiveLearningPanel implements PathObjectHierarchyListener, ImageDataChangeListener<BufferedImage>, ParameterChangeListener {
	
	private static final Logger logger = LoggerFactory.getLogger(ActiveLearningPanel.class);
	
	private GridPane pane;
	private ParameterList params;
	private ParameterPanelFX paramPanel;
	
	private ParameterList channelParams;
	private ParameterPanelFX classPanel;
	
	private Button btnChangeClass;
	private Button btnConfirmClass;
	private HBox classPane;
	private Label classLabel;
	
	private QuPathGUI qupath;
	private PathObjectHierarchy hierarchy;
	private ImageData<BufferedImage> imageData;
	
	private ALPathObjectServer pathObjectServer;
	private PathObject currentObject;
	private Map <PathClass, PathAnnotationObject> annotationMap;
	
	private QuPathViewer pathViewer;
	
	
	public ActiveLearningPanel (final QuPathGUI qupath) {
		
		// -- Set the listeners and image data -- //
		this.qupath = qupath;
		if (qupath == null)
			this.qupath = QuPathGUI.getInstance();
		
		this.qupath.addImageDataChangeListener(this);
		this.imageData = qupath.getImageData();
		
		if (imageData != null) {
			hierarchy = imageData.getHierarchy();
			hierarchy.addPathObjectListener(this);
		}
		pathObjectServer = new ALPathObjectServer(hierarchy);
		annotationMap = new HashMap();
		
		// -- Create a new pane -- //
		pane = new GridPane();
		pane.setMinSize(350, 300);
		pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		// -- Create the image viewer based on the main viewer -- //
		pathViewer = new QuPathViewer(imageData, qupath.getViewer().getImageRegionStore(), qupath.getViewer().getOverlayOptions());
		pathViewer.centerImage();
		pathViewer.setImageData(imageData);
		pathViewer.setMagnification(20);
		// Center on the first object
//		currentObject = pathObjectServer.serveNext();
//		pathViewer.setCenterPixelLocation(currentObject.getROI().getCentroidX(), currentObject.getROI().getCentroidY());
//		pathViewer.repaint();
		
		logger.info("Available channels: ");
		for (int i = 0; i < pathViewer.getImageDisplay().getSelectedChannels().size(); i++) 
			logger.info(pathViewer.getImageDisplay().getSelectedChannels().get(i).toString());	

		Pane viewPane = pathViewer.getView();
		viewPane.setMinSize(250, 250);
		
		// -- Set the parameters -- //
		params = new ParameterList()
				.addTitleParameter("Image settings")
				.addBooleanParameter("cropROI", "Crop ROI", true, "Set everything outside the ROI to zero")
				.addBooleanParameter("showROI", "Show ROI", true, "Set to false to hide the ROI")
				.addDoubleParameter("magnification", "Set magnification", 50, "x", 10, 400, "Set the magnification of the image.");
		
		// -- Create the channel buttons -- //
		channelParams = new ParameterList();
		// Add an option for each channel
		for (int i = 0; i < pathViewer.getImageDisplay().getAvailableChannels().size(); i++) {
			channelParams.addBooleanParameter("channel"+(i+1), "Channel " + (i+1), true);
		}
	
		// -- Create the parameter panel -- //
		params.addParameters(channelParams);
		paramPanel = new ParameterPanelFX(params);
		paramPanel.addParameterChangeListener(this);
		Pane paramPane = paramPanel.getPane();
		paramPane.setPadding(new javafx.geometry.Insets(0, 10, 10, 10));
		
		// -- Create buttons -- //
		btnChangeClass = new Button("Set class");
		btnChangeClass.setTooltip(new Tooltip("Change class to the class selected in the list.") );
		btnConfirmClass = new Button ("Skip");
		btnConfirmClass.setTooltip(new Tooltip ("Keep the current classification.") );
		
		// -- Handle button clicks -- //
		btnChangeClass.setOnAction(e -> {
			clickChangeClass();
		});
		btnConfirmClass.setOnAction(e -> {
			clickConfirmClass();
		});
		classPane = new HBox(10);
		classPane.add(btnConfirmClass);
		classLabel = new Label("Class : ");
		classLabel.setPadding(new Insets (5, 0, 0, 10));
		classLabel.setTooltip(new Tooltip ("The class this object is currently classified as. Probability between brackets."));
		classPane.add(classLabel);
		classPane.setPadding(new Insets (10, 0, 0, 0));
		
		// -- Create list of classes -- //
		List <PathClass> pathClassChoices = qupath.getAvailablePathClasses();
		ParameterList classParameterList = new ParameterList()
				.addChoiceParameter("classChoice", "Choose class", pathClassChoices.get(1), pathClassChoices)
				.addBooleanParameter("addTrain", "Add to training set", true, "Add the newly classified item to the training set. Also added when the current class is kept.");
		classPanel = new ParameterPanelFX(classParameterList);
		Pane classChoicePane = classPanel.getPane();
		HBox classBox = new HBox(classChoicePane, btnChangeClass);
		classBox.setPadding(new Insets (10, 10, 10, 10));
		
		// -- Add everything to the Panel -- //
		pane.add(paramPane, 1, 0);
		pane.add(viewPane, 0, 0);
		pane.add(classPane, 0, 1);
		pane.add(classBox, 1, 1);
		
		// -- Load the first object -- //
		currentObject = setupNext();
	}
	
	private void clickChangeClass () {
		
		// Serve the next PathObject and set all UI 
		PathObject next = setupNext();
		
		// Set the class of the currentObject
		currentObject.setPathClass( (PathClass) classPanel.getParameters().getChoiceParameterValue("classChoice") ); 
		
		// Add current object to training set if required
		if (classPanel.getParameters().getBooleanParameterValue("addTrain"))
			addTotTraining(currentObject);
		
		// Update currentObject
		currentObject = next;
		
	}
	
	private PathObject setupNext () {
		
		PathObject next = pathObjectServer.serveNext();		
		
		pathViewer.setCenterPixelLocation(next.getROI().getCentroidX(), next.getROI().getCentroidY());
		// Repaint the image after a change
		pathViewer.repaintEntireImage();
		
		// Set the current class label
		classLabel.setText("Class: " + next.getPathClass());
		if (next.getClassProbability() != Double.NaN) {
			double rounded = (int) (next.getClassProbability() * 1000) / 1000.0;
			classLabel.setText( classLabel.getText() + " (" + rounded + ")");
		}
		
		return next;
	}
	
	private void clickConfirmClass () {
		
		// Serve the next PathObject and set all UI 
		currentObject = setupNext();
		
		// Add to training set if required
		if (classPanel.getParameters().getBooleanParameterValue("addTrain"))
			addTotTraining(currentObject);
	}
	
	private void addTotTraining (PathObject p) {
		List <PathObject> annotationList = new ArrayList<>();
		
		// Check if annotation already exists
		if (!annotationMap.containsKey(p.getPathClass())) {
			PathAnnotationObject annotation = new PathAnnotationObject();
			
			// Check if an object already exists from a previous active learning session
			annotationList = hierarchy.getDescendantObjects(hierarchy.getRootObject(), annotationList, PathAnnotationObject.class);
			for (PathObject a : annotationList) {
				if (! (a instanceof PathAnnotationObject) )
					continue;
				
				if (a.getDisplayedName().equals("Active Learning") && a.getPathClass().equals(p.getPathClass())) {
					annotation = (PathAnnotationObject) a;
					logger.info("Discovered previous active learning annotation.");
				}
			}
			
			//annotation.addPathObject(p);
//			annotation.setPathClass(p.getPathClass());
//			annotation.setROI(p.getROI());
//			annotation.setName("Active Learning");
//			annotationMap.put(p.getPathClass(), annotation);
			
			// Briefly turn off listener so event is not called
			//hierarchy.removePathObjectListener(this);
			//hierarchy.addPathObject(annotation, true);
			//hierarchy.addPathObjectListener(this);
			
			annotation.setROI(p.getROI());
			annotation.addPathObject(p);
			annotation.setName("Active Learning");
			annotation.setPathClass(p.getPathClass());
			annotationMap.put(p.getPathClass(), annotation);
			
			// Now add to the hierarchy without triggering a hierarchy event
			//hierarchy.removePathObjectListener(this);
			hierarchy.addPathObject(annotation, true);
			//hierarchy.addPathObjectListener(this);
		}
		else {
		
			PathAnnotationObject annotationObject = new PathAnnotationObject(annotationMap.get(p.getPathClass()).getROI());
			
			annotationObject.setPathClass(p.getPathClass());
			annotationObject.setROI(PathROIToolsAwt.combineROIs((PathShape) annotationObject.getROI(), (PathShape) p.getROI(), CombineOp.ADD));
			annotationObject.addPathObject(p);
			annotationObject.setName("Active Learning");
			
			// Force a hierarchy update with the new data
			//hierarchy.removePathObjectListener(this);
			hierarchy.removeObject(annotationMap.get(p.getPathClass()), true);
			hierarchy.addPathObject(annotationObject, true);
			//hierarchy.addPathObjectListener(this);
			
			// Force update of the map
			annotationMap.put(p.getPathClass(), annotationObject);

//			// The following is really not pretty; but I really don't know how to add PathObjects to training set in another way...
//			PathAnnotationObject annotation = annotationMap.get(p.getPathClass());
//			
//			annotationMap.get(p.getPathClass()).addPathObject(p);
//			// Combine ROIs to force the annotation to know that the Objects are actually inside...
//			if (annotationMap.get(p.getPathClass()).getROI() != null)
//				annotationMap.get(p.getPathClass()).setROI(PathROIToolsAwt.combineROIs((PathShape) annotationMap.get(p.getPathClass()).getROI(), (PathShape) p.getROI(), CombineOp.ADD));
//			//annotationMap.get(p.getPathClass()).setPathClass(p.getPathClass());
//			
//			// Force a hierarchy update with the new data
//			hierarchy.removePathObjectListener(this);
//			hierarchy.removeObject(annotation, false);
//			hierarchy.addPathObject(annotationMap.get(p.getPathClass()), true);
//			hierarchy.addPathObjectListener(this);
		}
		
		logger.info("PathObject added to class " + p.getPathClass() + ".");
	}
	
	public GridPane getPane () {
		return pane;
	}
	
	private void updateViewer () {
		pathViewer.setImageData(imageData);
		pathViewer.repaint();
	}
		
	@Override
	public void imageDataChanged(ImageDataWrapper<BufferedImage> source, ImageData<BufferedImage> imageDataOld,
			ImageData<BufferedImage> imageDataNew) {
		
		this.setImageData(imageDataOld, imageDataNew);
		this.updateViewer();
		
		// Update the channel checkboxes when image changes
	}
	
	public void setImageData(final ImageData<BufferedImage> imageDataOld, final ImageData<BufferedImage> imageDataNew) {
		
		if (imageDataOld == imageDataNew)
			return;
		if (imageDataOld != null)
			imageDataOld.getHierarchy().removePathObjectListener(this);
		if (imageDataNew != null)
			imageDataNew.getHierarchy().addPathObjectListener(this);
		
		this.imageData = imageDataNew;
	}
	
	/**
	 * Handle the hierarchy change
	 */
	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		this.hierarchy = event.getHierarchy();
		
		// Update the object list and recluster
		//pathObjectServer = new ALPathObjectServer(hierarchy);
		//pathObjectServer.clusterPathObjects();
	}

	@Override
	public void parameterChanged(ParameterList parameterList, String key, boolean isAdjusting) {
		
		int channelCount = pathViewer.getImageDisplay().getAvailableChannels().size();
		
		if (key.equals("showROI")) {
			//pathViewer.getOverlayOptions().setShowAnnotations(params.getBooleanParameterValue(key));
			// Lazy solution to have changes in the main viewer not affect this viewer: just turn off the whole overlay
			pathViewer.setShowMainOverlay(params.getBooleanParameterValue(key));
			
			// If there's an element selected; remove this as well
			pathViewer.setSelectedObject(null);
		}
		
		// Handle the channel checkboxes
		for (int i = 0; i < channelCount; i++) {
			if ( key.equals("channel" + (i+1))) {
				pathViewer.getImageDisplay().setChannelSelected(pathViewer.getImageDisplay().getAvailableChannels().get(i), params.getBooleanParameterValue(key));
				logger.info ("Set channel " + (i+1) + " to " + params.getBooleanParameterValue(key));
				// Update the viewer
				pathViewer.repaintEntireImage();
			}
		}
		
		// Set the magnification
		if (key.equals("magnification") ) {
			pathViewer.setMagnification(params.getDoubleParameterValue(key));
		}
	}
	
	
	/**
	 * Helper class for the serving of PathObjects to be displayed by the Viewer in 
	 * the Active Learning panel. This class will perform a clustering on the classified 
	 * PathObjects in the current hierarchy and alternate between clusters when
	 * serving objects.
	 * 
	 * @author Sam Vanmassenhove
	 *
	 */
	static class ALPathObjectServer {
		
		private final Logger logger = LoggerFactory.getLogger(ALPathObjectServer.class);
		
		private List <PathObject> pathObjects;
		private Map <Integer, List <PathObject>> clusteredMap;
		
		private int currentCluster = -1;
		private PathObject currentObject;
		private Iterator<PathObject> it;
		
		
		public ALPathObjectServer (PathObjectHierarchy hierarchy) {
			// Init 
			pathObjects = new ArrayList<>();
			clusteredMap = new HashMap<>();
			
			// Create a list of all objects in the current hierarchy
			List <PathObject> tempList = hierarchy.getFlattenedObjectList(null);
			for (PathObject p : tempList) {
				
				// Filter out annotations; we only want detections in the list
				if (p instanceof PathAnnotationObject) 
					continue;
				
				// Filter out unclassified objects
				if (p.getPathClass() == null || p.getPathClass().equals(PathClassFactory.getPathClass("Image")) )
					continue;
				
				// Add to the actual list
				pathObjects.add(p);
			}
			
			// Create an iterator
			it = pathObjects.iterator();
		} 
		
		public List <PathObject> getObjects () {
			return pathObjects;
		}
		
		public void clusterPathObjects () {
			
		}
		
		public PathObject serveNext () {
			
			if (it.hasNext()) {
				return it.next();
			}
			
			return currentObject;
		}
		
	}
}