package qupath.lib.active_learning;

import java.util.List;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;

import qupath.lib.objects.PathObject;

public class KMeansClusterer extends AbstractClusterer {
	
	protected KMeansPlusPlusClusterer<Clusterable> clusterer;
	
	public KMeansClusterer(List<PathObject> pathObjects, List<double[]> dataPoints, int clusterCount) {
		super(pathObjects, dataPoints);
		
		clusterer = new KMeansPlusPlusClusterer<>(clusterCount);
	}
	
	@Override
	public Cluster<ClusterableObject> getResults() {
		return (CentroidCluster<ClusterableObject>) super.getResults();
	}

}
