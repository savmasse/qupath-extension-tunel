
package qupath.lib.classification;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.ml.Ml;
import org.opencv.ml.RTrees;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

/**
 * Bare-bones wrapper for the Random Forest classifier without any UI. Should be useful for scripting
 * purposes. This classifier will not update any properties of the objects in the image.
 * 
 * @author Sam Vanmassenhove
 *
 */
public class RandomForestClassifier {
	
	private static final Logger logger = LoggerFactory.getLogger(RandomForestClassifier.class);
	
	private List <PathClass> pathClasses;
	private List <PathObject> trainingSet;
	private List <String> featureList;
	
	private Mat labels; // Training labels
	private Mat featureMatrix; // Feature matrix of training samples
	private RTrees rTrees;
	
	private int maxTrees;
	
	public RandomForestClassifier (	final List <String> featureList, 
									final List <PathClass> pathClasses, 
									final List <PathObject> trainingSet) {
		// Initialize
		this.pathClasses = pathClasses;
		this.trainingSet = trainingSet;
		this.featureList = featureList;
		
		// Create the classifier
		rTrees = RTrees.create();
		
		// Convert the features of objects in the classifier
		featureMatrix = createFeatureMatrix(trainingSet);
		labels = createLabelMatrix(trainingSet);
	}
	
	public RandomForestClassifier ( final List <String> featureList,
									final List <PathClass> pathClasses,
									final List <PathObject> trainingSet,
									final int maxDepth,
									final int minSampleCount) {
		this(featureList, pathClasses, trainingSet);
		rTrees.setMaxDepth(maxDepth);
		rTrees.setMinSampleCount(minSampleCount);	
	}
	
	/**
	 * Create a feature matrix from the names of the measurements that should be included.
	 */
	private Mat createFeatureMatrix (List <PathObject> pathObjects) {
		
		Mat res = new Mat(pathObjects.size(), featureList.size(), CvType.CV_32F);
		
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
			res.put(i, 0, ArrayUtils.toPrimitive(r));
			
			i++; // Increment row counter
		}
		
		return res;
	}
	
	/**
	 * Set the labels of the objects for their class.
	 * 
	 * @param pathObjects
	 * @return
	 */
	private Mat createLabelMatrix (List <PathObject> pathObjects) {

		Mat res = new Mat(pathObjects.size(), 1, CvType.CV_32F);
		
		// Go through the samples and label them for their class
		int i = 0;
		for (PathObject p : pathObjects) {
			PathClass pc = p.getPathClass();
			
			// The label is the index in the class list
//			int label = pathClasses.indexOf(pc);
			
			for (PathClass c : pathClasses) {
				if (pc.isDerivedFrom(c)) {
					int label = pathClasses.indexOf(c);
					res.put(i, 0, label);
					break; // Exit loop once label is set
				}
			}
			
			i++;
		}
		
		return res;
	}
	
	/**
	 * Add new training samples. Must also recalculate the labels and features.
	 * 
	 * @param newSamples
	 */
	public void addTrainingSamples (List <PathObject> newSamples) {
		trainingSet.addAll(newSamples);
		featureMatrix = createFeatureMatrix(trainingSet);
		labels = createLabelMatrix(trainingSet);
	}
	
	/**
	 * Train the classifier on the current dataset
	 */
	public void train () {
		rTrees.train(featureMatrix, Ml.ROW_SAMPLE, labels);
		logger.info("Classifier was trained on " + featureMatrix.rows() + " samples.");
	}
	
	/**
	 * Perform a prediction on a list of testing samples; also update the probabilities of
	 * the objects.
	 */
	public List <Integer> predict (List <PathObject> testSet) {
		
		List <Integer> results = new ArrayList<>();
		Mat testFeatureMatrix = createFeatureMatrix(testSet);		
		Mat predictions = new Mat();
		
		for (int i = 0; i < testFeatureMatrix.rows(); i++) {
			int label = (int) Math.round( rTrees.predict(testFeatureMatrix.row(i)) );
			double prediction = rTrees.predict(testFeatureMatrix.row(i), predictions, RTrees.PREDICT_SUM) / rTrees.getTermCriteria().maxCount;
			results.add(label);
			
			// Get the probabilities if binary problem
			if (pathClasses.size() == 2) {
				if (label == 0)
					logger.info("" + (1-prediction));
				else 
					logger.info("" + prediction);
				
				// Set the PathClass of the objects in the test set; also set the probability
				for (PathObject p : testSet) {
					p.setPathClass(pathClasses.get(label), prediction);
				}
			}
			else {
				// Set the PathClass of the objects in the test set
				for (PathObject p : testSet) {
					p.setPathClass(pathClasses.get(label));
				}
			}
		}
		
		return results;
	}
}