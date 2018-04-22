package qupath.lib.active_learning;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.objects.PathObject;

/**
 * Abstract parent class for the different sample proposal methods used in the active 
 * learning algorithm.
 * 
 * TODO: Consider adding functionality here to remember previous proposals since this is
 * used in every child class anyway...
 * 
 * @author Sam Vanmassenhove
 *
 */
public abstract class AbstractSampleProposal {
	
	protected static final Logger logger = LoggerFactory.getLogger(AbstractSampleProposal.class);
	protected List<PathObject> pathObjects;
	protected PathObject currentObject;
	
	public AbstractSampleProposal(final List<PathObject> pathObjects) {
		this.pathObjects = pathObjects;
	}
	
	public PathObject serveObject () {
		return currentObject;
	}
}