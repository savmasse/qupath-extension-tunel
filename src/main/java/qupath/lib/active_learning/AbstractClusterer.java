
package qupath.lib.active_learning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.ml.clustering.Clusterer;

import qupath.lib.objects.PathObject;

public abstract class AbstractClusterer {
	
	protected List<ClusterableObject> clusterableObjects;
	protected Clusterer<ClusterableObject> clusterer;
	protected Map <Integer, List<ClusterableObject>> clusteredMap;
	
	public AbstractClusterer (List<PathObject> pathObjects, List<double[]> dataPoints) {
		
		clusterableObjects = new ArrayList<>();
		clusteredMap = new HashMap<>();
		
		for (int i = 0; i < pathObjects.size(); i++) {
			clusterableObjects.add(new ClusterableObject(pathObjects.get(i), dataPoints.get(i)));
		}
	}
	
	/**
	 * Do the actual clustering and put create a map of clusters.
	 * @return
	 */
	public abstract void cluster ();
	
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