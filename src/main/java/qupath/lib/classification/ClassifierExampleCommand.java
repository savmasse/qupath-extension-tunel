
package qupath.lib.classification;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.opencv.core.Algorithm;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.ml.Ml;
import org.opencv.ml.SVM;
import org.opencv.ml.StatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.PathClassificationLabellingHelper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.images.ImageData;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.PointsROI;

public class ClassifierExampleCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(ClassifierExampleCommand.class);
	
	private QuPathGUI qupath;
	private PathObjectHierarchy hierarchy;
	private ImageData<BufferedImage> imageData;
	
	private List <PathClass> classList;
	private boolean binaryClassifier;
	private Map <PathClass, List<PathObject>> trainingMap;
	
	private SVM classifier;
	private int nSamples = 0;
	private List <String> featureList;
	private Map <Integer, PathClass> labelMap;
	
	public ClassifierExampleCommand(final QuPathGUI qupath, final List<PathClass> classList, final List<String> featureList) {
		this.qupath = qupath;
		this.classList = classList;
		this.featureList = featureList;
		
		if (classList.size() != 2) 
			binaryClassifier = false;
		else 
			binaryClassifier = true;
	}
	
	@Override
	public void run() {
		
		// Init
		if (qupath == null)
			qupath = QuPathGUI.getInstance();
		imageData = qupath.getImageData();
		if (imageData == null) {
			logger.info("No image data available. Please open an image before trying to use the classifier.");
			return;
		}
		hierarchy = imageData.getHierarchy();
		trainingMap = new HashMap<>();
		labelMap = new HashMap<>();
		nSamples = 0; // Always reset the training set counter
		
		// Get the training objects for the classes in the classList
		// TODO Remove duplicates: all pointObjects in the training set are currently counted twice: once as point and once as the underlying object...
		logger.info("==== Training samples for classifier ====");
		int k = 0;
		for (PathClass c : classList) {
			List <PathObject> objectList = PathClassificationLabellingHelper.getLabelledObjectsForClass(hierarchy, c);
			nSamples += objectList.size(); // Count the total samples
			
			logger.info("" + c + " : " + objectList.size());
			
			trainingMap.put(c, objectList);
			
			// Create map for decoding of PathClasses from the assigned labels
			labelMap.put(k, c);
			k++;
		}		
		logger.info("============================");
		
		// Get a complete feature matrix for the classifier
		Mat featureMatrix = getFeatureMatrix();
		
		// Get training labels for the classifier
		Mat trainingLabels = getTrainingLabels();
		
		// Create classifier
		classifier = SVM.create();
		classifier.setKernel(SVM.LINEAR);
		classifier.setType(SVM.C_SVC);
		
		// Train classifier
		classifier.train(featureMatrix, Ml.ROW_SAMPLE, trainingLabels);
		
		// Apply classifier to rest of pathObjects in the image
		propagateClassification();
	}
	
	/**
	 * Create a matrix containing the features from every training object. Each sample
	 * is placed in a row of the feature matrix.
	 * 
	 * @return {@link Mat}
	 */
	private Mat getFeatureMatrix () {
		
		Mat featureMatrix = new Mat(nSamples, featureList.size(), CvType.CV_32F);
		
		int i = 0;
		for (List<PathObject> objects : trainingMap.values()) {
			for (PathObject p : objects) {

				List <Double> row = new ArrayList<>();
				MeasurementList ml = p.getMeasurementList();
				
				for (int j = 0; j < ml.size(); j++) {
					
					// If the feature is in the list then add it to the feature row
					if (featureList.contains(ml.getMeasurementName(j))) {
						row.add(ml.getMeasurementValue(j));
					}
				}
				
				// Put the row into the feature matrix
				Double [] r = new Double [row.size()];
				row.toArray(r);
				featureMatrix.put(i, 0, ArrayUtils.toPrimitive(r));
				
				i++; // Increment row counter
			}
		}
		
		return featureMatrix;
	}
	
	/**
	 * Create a matrix containing the features from every object in the supplies list
	 * of detection objects. 
	 * 
	 * @param List <PathObject> pathObjects
	 * @return {@link Mat}
	 */
	private Mat getFeatureMatrix (List <PathObject> pathObjects) {
		
		Mat featureMatrix = new Mat(pathObjects.size(), featureList.size(), CvType.CV_32F);
		
		int i = 0;
		for (PathObject p : pathObjects) {

			List <Double> row = new ArrayList<>();
			MeasurementList ml = p.getMeasurementList();
			
			for (int j = 0; j < ml.size(); j++) {
				
				// If the feature is in the list then add it to the feature row
				if (featureList.contains(ml.getMeasurementName(j))) {
					row.add(ml.getMeasurementValue(j));
				}
			}
			
			// Put the row into the feature matrix
			Double [] r = new Double [row.size()];
			row.toArray(r);
			featureMatrix.put(i, 0, ArrayUtils.toPrimitive(r));
			
			i++; // Increment row counter
		}
		
		return featureMatrix;
	}
	
	/**
	 * Go through the objects per class and set the labels.
	 * 
	 * @return {@link Mat}
	 */
	private Mat getTrainingLabels () {
		
		Mat label = new Mat(nSamples, 1, CvType.CV_32SC1);

		int i = 0, j = 0;
		for (PathClass c : classList) {
			for (PathObject p : trainingMap.get(c)) {
				label.put(j, 0, i);
				j++; // Count objects
			}
			
			i++; // Count class label
		}
		
		return label;
	}
	
	/**
	 * Propagate the classification for all detection objects in the image.
	 */
	private void propagateClassification() {
		
		// Get all detection objects in the whole image
		List <PathObject> pathObjects = new ArrayList<>();
		
		// Filter all objects we don't need
		for (PathObject p : hierarchy.getFlattenedObjectList(null)) {
			
			if (p instanceof PathAnnotationObject || p instanceof PathRootObject)
				continue;
				
			if (p.getROI() instanceof PointsROI)
				continue;
			
			// Add to the list
			pathObjects.add(p);
		}
		
		// Now classify these objects
		Mat featureMatrix = getFeatureMatrix(pathObjects);
		
		Mat predictions = new Mat();
		classifier.predict(featureMatrix, predictions, 0);
		
		// Decode the predicted labels back into PathClasses
		float [] labels = new float [predictions.rows()];
		predictions.get(0, 0, labels);
		
		// Update the classes of the objects
		for (int i = 0; i < pathObjects.size(); i++) {
			pathObjects.get(i).setPathClass( labelMap.get( Integer.valueOf( (int) labels[i]) ) );
		}
		
		// Update the hierarchy
		hierarchy.fireObjectClassificationsChangedEvent(this, pathObjects);
		logger.info("Classified " + pathObjects.size() + " objects.");
		
		// Evaluate the model by testing on the training set
		classifier.predict(getFeatureMatrix(), predictions, 0);
		ClassifierStatisticsHelper stats = new ClassifierStatisticsHelper(getTrainingLabels(), predictions, classList.size(), labelMap);
		stats.evaluate();
	}
	
}