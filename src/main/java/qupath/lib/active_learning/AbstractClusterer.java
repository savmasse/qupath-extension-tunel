
package qupath.lib.active_learning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.ml.clustering.Clusterer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;

public abstract class AbstractClusterer {
	
	protected static final Logger logger = LoggerFactory.getLogger(AbstractClusterer.class);
	protected List<ClusterableObject> clusterableObjects;
	protected Clusterer<ClusterableObject> clusterer;
	protected Map <Integer, List<ClusterableObject>> clusteredMap;
	
	public AbstractClusterer (final List<PathObject> pathObjects, final List<double[]> dataPoints) {
		
		clusterableObjects = new ArrayList<>();
		clusteredMap = new HashMap<>();
		
		for (int i = 0; i < pathObjects.size(); i++) {
			clusterableObjects.add(new ClusterableObject(pathObjects.get(i), dataPoints.get(i)));
		}
	}
	
	/**
	 * Constructor which calculates the datapoints so we don't have calculate them beforehand.
	 * @param pathObjects
	 * @param featureNames
	 */
	public AbstractClusterer (final List<PathObject> pathObjects, final Collection <String> featureNames) {
		
		clusterableObjects = new ArrayList<>();
		clusteredMap = new HashMap<>();
		
		for (PathObject p : pathObjects) {
			MeasurementList ml = p.getMeasurementList();
			List<Double> featureList = new ArrayList<>();
			
			for (String feature : featureNames) {
				if (ml.containsNamedMeasurement(feature) ) {
					featureList.add (ml.getMeasurementValue(feature));
				}
			}
			
			// Convert to primitive
			double [] d = new double [featureList.size()];
			for (int i = 0; i < featureList.size(); i++) {
				d[i] = featureList.get(i);
			}
			
			// Add a new clusterable object
			clusterableObjects.add((new ClusterableObject(p, d)));
		}
	}
	
	/**
	 * Do the actual clustering and create a map of clusters.
	 */
	public abstract void cluster ();
	
	/**
	 * Return the map of clusters.
	 * @return
	 */
	public Map<Integer, List<ClusterableObject>> getClusterMap () {
		return clusteredMap;
	}
	
	/**
	 * Return a string containing the results of the clustering: how many elements in how many clusters.
	 */
	public String resultToString () {
		StringBuilder sb = new StringBuilder();
		sb.append("Clustering using " + getName() + " finished: \n");
		
		if (!clusteredMap.isEmpty()) {	
			for (Integer key : clusteredMap.keySet()) {
				sb.append("\tCluster " + key + ": " + clusteredMap.get(key).size() + "\n");
			}
		}
		else sb.append("Clustering was not completed.");
		
		return sb.toString();
	}
	
	/**
	 * Get the name of the clustering technique.
	 * @return
	 */
	protected abstract String getName ();
}