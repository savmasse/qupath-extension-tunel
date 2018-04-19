
package qupath.lib.active_learning;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterer;

import qupath.lib.objects.PathObject;

public abstract class AbstractClusterer {
	
	protected List<ClusterableObject> clusterableObjects;
	protected Clusterer<ClusterableObject> clusterer;
	
	public AbstractClusterer (List<PathObject> pathObjects, List<double[]> dataPoints) {
		
		clusterableObjects = new ArrayList<>();
		
		for (int i = 0; i < pathObjects.size(); i++) {
			clusterableObjects.add(new ClusterableObject(pathObjects.get(i), dataPoints.get(i)));
		}
	}
	
	/**
	 * Get the results of the actual clustering.
	 * @return
	 */
	public Cluster<ClusterableObject> getResults () {
		return (Cluster<ClusterableObject>) clusterer.cluster(clusterableObjects);
	}
	
}