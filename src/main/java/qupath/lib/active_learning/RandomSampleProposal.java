package qupath.lib.active_learning;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import qupath.lib.objects.PathObject;

/**
 * Completely random proposal of new samples. The only things that happens here
 * is that previous samples are remembered so the same if never proposed multiple times.
 * 
 * @author Sam Vanmassenhove
 *
 */
public class RandomSampleProposal extends AbstractSampleProposal {
	
	protected Random random;
	protected Set <PathObject> previouslyProposed;
	
	public RandomSampleProposal (final List<PathObject> pathObjects, final Random random) {
		super(pathObjects);
		this.random = random;
		previouslyProposed = new HashSet<>();
	}
	
	@Override
	public PathObject serveObject() {
		
		// Get random samples until we find one we haven't seen before
		int index = random.nextInt(pathObjects.size());
		currentObject = pathObjects.get(index);
		
		while (previouslyProposed.contains(currentObject) && previouslyProposed.size() < pathObjects.size()) {
			index = random.nextInt(pathObjects.size());
			currentObject = pathObjects.get(index);
		}
		
		// Add to the set so we know not to pick this one next time
		previouslyProposed.add(currentObject);
		
		return currentObject;
	}
}