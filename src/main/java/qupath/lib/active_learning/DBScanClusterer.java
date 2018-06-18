package qupath.lib.active_learning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;

import qupath.lib.objects.PathObject;

public class DBScanClusterer extends AbstractClusterer {
	
	public  DBScanClusterer(List<PathObject> pathObjects, List <double[]> data, final double eps, final int minPts) {
		super(pathObjects, data);
		clusterer = new DBSCANClusterer<>(eps, minPts);
	}

	public DBScanClusterer(List<PathObject> pathObjects, Collection<String> featureNames, final double eps, final int minPts) {
		super(pathObjects, featureNames);
		clusterer = new DBSCANClusterer<>(eps, minPts);
	}

	@Override
	public void cluster() {
		clusteredMap.clear();
		
		List<Cluster<ClusterableObject>> results = (List<Cluster<ClusterableObject>>) clusterer.cluster(clusterableObjects);
		
		int i = 0;
		for (Cluster<ClusterableObject> cen : results) {
			Integer k = Integer.valueOf(i);
			List <ClusterableObject> objects = new ArrayList<>();
			for (ClusterableObject c: cen.getPoints()) {
				objects.add(c);
			}
			clusteredMap.put(k, objects);
			i++;
		}
	}

	@Override
	protected String getName() {
		return "DBScan";
	}
	
}