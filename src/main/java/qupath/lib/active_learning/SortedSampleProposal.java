
package qupath.lib.active_learning;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import qupath.lib.objects.PathObject;

/**
 * Propose samples by sorting them by probability and proposing them in order of lowest to highest.
 * 
 * @author Sam Vanmassenhove
 *
 */
public class SortedSampleProposal extends AbstractSampleProposal {
	
	protected Iterator<PathObject> iter;
	
	public SortedSampleProposal(List<PathObject> pathObjects) {
		super(pathObjects);
		
		Collections.sort(this.pathObjects, new Comparator<PathObject>() {
		    @Override
		    public int compare(PathObject lhs, PathObject rhs) {
		        // -1 - less than, 1 - greater than, 0 - equal, all inversed for descending
		        return Double.compare(lhs.getClassProbability(), rhs.getClassProbability()); // Use double compare to safely handle NaN and Infinity
		    }
		});
		
		iter = this.pathObjects.iterator();
		currentObject = this.pathObjects.get(0); // Set the first manually
	}
	
	/**
	 * We may want to sort again after a certain amount of samples have been added and the classification 
	 * has changed.
	 */
	public void sort () {
		Collections.sort(this.pathObjects, new Comparator<PathObject>() {
		    @Override
		    public int compare(PathObject lhs, PathObject rhs) {
		        // -1 - less than, 1 - greater than, 0 - equal, all inversed for descending
		        return Double.compare(lhs.getClassProbability(), rhs.getClassProbability()); // Use double compare to safely handle NaN and Infinity
		    }
		});
		
		iter = pathObjects.iterator();
	}
	
	@Override
	public PathObject serveObject() {
		if (iter.hasNext()) {
			currentObject = iter.next();
		}
		return currentObject;
	}
	
	@Override
	public String getName () {
		return "Sorted";
	}

	@Override
	protected void reset() {
		sort();
	}
	
}