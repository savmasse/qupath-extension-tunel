
package qupath.lib.classification;

import java.util.Map;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.objects.classes.PathClass;

/**
 * Class to help with calculation of classifier statistics.
 * 
 * @author Sam Vanmassenhove
 *
 */
public class ClassifierStatisticsHelper {
	
	private final static Logger logger = LoggerFactory.getLogger(ClassifierStatisticsHelper.class);
	
	private byte [] groundTruth;
	private byte [] predictions;
	private int [] [] confusionMatrix;
	private int nClasses;
	private Map <Integer, PathClass> labelMap;
	
	/**
	 * Constructor.
	 * @param groundTruth matrix of labels, size = (rows, 1)
	 * @param predictions matrix of labels, size = (rows, 1)
	 */
	public ClassifierStatisticsHelper (final Mat groundTruth, final Mat predictions, final int nClasses, final Map <Integer, PathClass> labelMap) {
		
		this.labelMap = labelMap;
		
		// Convert the matrices to the correct type
		groundTruth.convertTo(groundTruth, CvType.CV_8U);
		predictions.convertTo(predictions, CvType.CV_8U);
		
		// Fill the arrays
		this.groundTruth = new byte [groundTruth.rows()];
		this.predictions = new byte [predictions.rows()];
		this.nClasses = nClasses;
		groundTruth.get(0, 0, this.groundTruth);
		predictions.get(0, 0, this.predictions);
		
		// From these values, calculate the confusion matrix
		calculateConfusionMatrix();
	}
	
	/**
	 * Calculate the confusion matrix.
	 */
	private void calculateConfusionMatrix () {
		
		confusionMatrix = new int [nClasses][nClasses];
		
		for (int i = 0; i < groundTruth.length; i++) {
			confusionMatrix[groundTruth[i]][predictions[i]] = confusionMatrix[groundTruth[i]][predictions[i]] + 1;
		}
	}
	
	/**
	 * Print some statistics about the classifier.
	 */
	public void evaluate () {

		// Print the confusion matrix
		logger.info("Confusion matrix : ");
		String matrix = "\t\t";
		for (PathClass c : labelMap.values()) {
			matrix += c + "\t\t\t";
		}
		for (int i = 0; i < confusionMatrix.length; i++) {
			String row = labelMap.get(i) + "\t\t|\t\t";
			for (int j : confusionMatrix[i]) {
				row += j + "\t\t|\t\t";
			}
			matrix += "\n" + row;
		}
		logger.info(matrix);
		
		// Print some statistics
		logger.info("Accuracy:\t" + getAccuracy());
		logger.info("Precision:\t" + getPrecision());
		logger.info("Recall:\t\t" + getRecall());
		
	}
	
	public double getRecall () {
		return getTruePositive() / (getTruePositive() + getFalseNegative());
	}
	
	public double getPrecision () {
		return getTruePositive() / (getTruePositive() + getFalsePositive());
	}
	
	public double getAccuracy () {
		return (getTruePositive() + getTrueNegative()) / (getTruePositive() + getTrueNegative() + getFalsePositive() + getFalseNegative());
	}
	
	private double getTruePositive () {
		return confusionMatrix[1][1];
	}
	
	private double getFalsePositive () {
		return confusionMatrix[1][0];
	}
	
	private double getTrueNegative () {
		return confusionMatrix[0][0];
	}
	
	private double getFalseNegative () {
		return confusionMatrix[0][1];
	}
}