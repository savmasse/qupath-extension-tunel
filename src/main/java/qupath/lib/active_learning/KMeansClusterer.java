package qupath.lib.active_learning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;

import qupath.lib.objects.PathObject;

public class KMeansClusterer extends AbstractClusterer {
	
	protected KMeansPlusPlusClusterer<ClusterableObject> clusterer;
	
	public KMeansClusterer(List<PathObject> pathObjects, List<double[]> dataPoints, int clusterCount) {
		super(pathObjects, dataPoints);
		
		clusterer = new KMeansPlusPlusClusterer<>(clusterCount);
	}
	
	public KMeansClusterer(List<PathObject> pathObjects, Collection<String> featureNames, int clusterCount) {
		super (pathObjects, featureNames);
		
		clusterer = new KMeansPlusPlusClusterer<>(clusterCount);
	}
	
	/**
	 * Do the actual clustering and place the clusters into a map of clusterable objects. Return the
	 * clusterable objects instead of regular PathObjects because we'll want access to the features
	 * for plotting.
	 */
	@Override
	public void cluster() {
		
		// Reset the map so it is emptied if we are reclustering
		clusteredMap.clear();
		
		List<CentroidCluster<ClusterableObject>> results = clusterer.cluster(clusterableObjects);
		
		int i = 0;
		for (CentroidCluster<ClusterableObject> cen : results) {
			Integer k = Integer.valueOf(i);
			List <ClusterableObject> objects = new ArrayList<>();
			for (ClusterableObject c: cen.getPoints()) {
				objects.add(c);
			}
			clusteredMap.put(k, objects);
			i++;
		}
		
//		logger.info ("Performed clustering");
	}
	
	@Override
	protected String getName() {
		return "KMeans";
	}
}
