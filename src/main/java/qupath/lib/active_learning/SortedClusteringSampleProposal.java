package qupath.lib.active_learning;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import qupath.lib.objects.PathObject;

/**
 * Propose samples by clustering with a certain clustering algorithm.
 * 
 * @author Sam Vanmassenhove
 *
 */
public class SortedClusteringSampleProposal extends AbstractSampleProposal {
	
	private AbstractClusterer clusterer;
	private Map <Integer, Iterator<ClusterableObject>> iteratorMap;
	private Map <Integer, List<ClusterableObject>> clusterMap;
	private int currentCluster, clusterCount;
	
	public SortedClusteringSampleProposal(final List<PathObject> pathObjects, final List<String> featureNames, final int clusterCount) {
		super(pathObjects);
		clusterer = new KMeansClusterer(pathObjects, featureNames, clusterCount);
		this.clusterCount = clusterCount;
		iteratorMap = new HashMap<>();
		
		initialize();
	}
	
	private void initialize () {
		
		// Get the clusters
		iteratorMap = new HashMap<>();
		clusterer.cluster();
		clusterMap = clusterer.getClusterMap();
		currentCluster = 0;
		
		// Sort each cluster in order of increasing probability
		for (List<ClusterableObject> clusterableList : clusterMap.values()) {
			sort(clusterableList);
		}
		
		for (Integer i : clusterMap.keySet()) {
			Iterator<ClusterableObject> it = clusterMap.get(i).iterator();
			iteratorMap.put(i, it);
		}
	}
	
	@Override
	public PathObject serveObject() {		
		
		Iterator<ClusterableObject> it;
		
		int attempts = 0;
		while ( !(it = iteratorMap.get(currentCluster)).hasNext() && attempts < iteratorMap.size()) {
			currentCluster++;
			if (currentCluster >= clusterCount)
				currentCluster = 0;
			attempts++;
		}
		
		// We must recluster if all iterators are at an end.
		if (attempts >= iteratorMap.size()) {
			logger.info("End of training samples: reclustering");
			reset();
			return serveObject();
		}
		
		// Increment the current cluster
		currentCluster++;
		if (currentCluster >= clusterCount)
			currentCluster = 0;
		
		if (it != null)
			return it.next().getPathObject();
		else return currentObject;
	}

	@Override
	public String getName() {
		return "Sorted_Clusters";
	}
	
	private void sort (List<ClusterableObject> objects) {
		Collections.sort(objects, new Comparator<ClusterableObject>() {
		    @Override
		    public int compare(ClusterableObject lhs, ClusterableObject rhs) {
		        // -1 - less than, 1 - greater than, 0 - equal, all inversed for descending
		        return Double.compare(lhs.getPathObject().getClassProbability(), rhs.getPathObject().getClassProbability()); // Use double compare to safely handle NaN and Infinity
		    }
		});
	}

	@Override
	protected void reset() {
		initialize();
	}
	
}