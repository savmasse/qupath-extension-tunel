package qupath.lib.active_learning;

import java.util.List;

import org.apache.commons.math3.ml.clustering.Clusterable;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;

/**
 * @author Pete Bankhead
 */
public class ClusterableObject implements Clusterable {
	
	private PathObject pathObject;
	private double[] point; // Features for the clustering
	
	public ClusterableObject(final PathObject pathObject, final List<String> measurements) {
		this.pathObject = pathObject;
		point = new double[measurements.size()];
		MeasurementList ml = pathObject.getMeasurementList();
		for (int i = 0; i < measurements.size(); i++) {
			point[i] = ml.getMeasurementValue(measurements.get(i));
		}
	}
	
	public ClusterableObject(final PathObject pathObject, final double[] features) {
		this.pathObject = pathObject;
		point = features.clone();
	}
	
	public PathObject getPathObject() {
		return pathObject;
	}

	@Override
	public double[] getPoint() {
		return point;
	}

}
