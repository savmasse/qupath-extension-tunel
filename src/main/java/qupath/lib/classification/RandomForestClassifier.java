
package qupath.lib.classification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.TermCriteria;
import org.opencv.ml.Ml;
import org.opencv.ml.RTrees;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

/**
 * Bare-bones wrapper for the Random Forest classifier to be used in scripts that require more
 * options such as using multiple classifiers in series. This classifier will not set PathClasses
 * or update the probabilities; this is up to the user.
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
	private Mat classProbabilities; // Probabilities; this is only used in a binary classification problem
	private RTrees rTrees;
	
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
		
		// Set standard values
		//rTrees.setMaxDepth(25);
		//rTrees.setMinSampleCount(10);
		rTrees.setUse1SERule(true);
		
		TermCriteria tc = new TermCriteria(TermCriteria.COUNT, 50, 0);
		rTrees.setTermCriteria(tc);
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
	 * Return the predicted probabilities. Must run a prediction first before calling this. Cannot use this if
	 * the problem is not a binary classification problem.
	 * @return
	 */
	public Mat getProbabilities () {
		if (pathClasses.size() == 2)
			return classProbabilities;
		else {
			logger.info("Could not calculate probabilities - not a binary classifier !");
			return null;
		}
	}
	
	/**
	 * Set the feature matrix for the training objects. Use this when manually setting features that
	 * cannot be found in the measurement list of the detection objects. The user is responsible for 
	 * the matching of the feature rows to the correct objects in the training matrix.
	 * @param featureMatrix
	 */
	public void setFeatureMatrix (final Mat featureMatrix, final boolean recalculateLabels) {
		this.featureMatrix = featureMatrix;
		if (recalculateLabels)
			labels = createLabelMatrix(trainingSet);
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
				if (pc.isDerivedFrom(c) || pc.toString().contains(c.toString())) {
//					logger.info(pc.toString());
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
	 * Add a single new training sample and recalculate the feature and label matrix.
	 * @param sample
	 */
	public void addTrainingSample (final PathObject sample) {
		trainingSet.add(sample);
		featureMatrix = createFeatureMatrix(trainingSet);
		labels = createLabelMatrix(trainingSet);
	}
	
	/**
	 * Train the classifier on the current dataset
	 */
	public void train () {
		rTrees.train(featureMatrix, Ml.ROW_SAMPLE, labels);
//		logger.info("Classifier was trained on " + featureMatrix.rows() + " samples.");
	}
	
	/**
	 * Perform a prediction on a list of testing samples; also update the probabilities of
	 * the objects.
	 */
	public List <PathClass> predict (List <PathObject> testSet) {
		
		List <PathClass> results = new ArrayList<>();
		Mat testFeatureMatrix = createFeatureMatrix(testSet);		
		Mat predictions = new Mat();
		classProbabilities = new Mat(testFeatureMatrix.rows(), 1, CvType.CV_32F);
		
		for (int i = 0; i < testFeatureMatrix.rows(); i++) {
			int label = (int) rTrees.predict(testFeatureMatrix.row(i));

			double prediction = rTrees.predict(testFeatureMatrix.row(i), predictions, RTrees.PREDICT_SUM) / rTrees.getTermCriteria().maxCount;
			results.add(pathClasses.get(label));
			
			// Get the probabilities if binary problem
			if (pathClasses.size() == 2) {
				if ((int) Math.round(prediction) == 0)
					prediction = 1 - prediction;
				
				classProbabilities.put(i, 0, prediction);
			}
		}
		
		return results;
	}
	
	/**
	 * Perform a prediction on a list of testing samples. Give custom feature matrix; this in case
	 * other features than those available in the measurement list are being used.
	 */
	public List <PathClass> predict (final Mat testFeatureMatrix) {
		
		List <PathClass> results = new ArrayList<>();		
		Mat predictions = new Mat();
		classProbabilities = new Mat(testFeatureMatrix.rows(), 1, CvType.CV_32F);

		for (int i = 0; i < testFeatureMatrix.rows(); i++) {
			int label = (int) rTrees.predict(testFeatureMatrix.row(i));

			double prediction = rTrees.predict(testFeatureMatrix.row(i), predictions, RTrees.PREDICT_SUM) / rTrees.getTermCriteria().maxCount;
			results.add(pathClasses.get(label));
			
			// Get the probabilities if binary problem
			if (pathClasses.size() == 2) {
				if ((int) Math.round(prediction) == 0)
					prediction = 1 - prediction;
				
				classProbabilities.put(i, 0, prediction);
			}
		}
		
		return results;
	}
	
	/**
	 * Perform a prediction on the trainingSet
	 * @return
	 */
	public List<PathClass> predictTraining () {
		return predict(trainingSet);
	}
	
	public List<PathObject> getTrainingSet () {
		return trainingSet;
	}
}