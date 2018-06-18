
package qupath.lib.visualization;

import java.util.List;

import org.nd4j.linalg.factory.Nd4j;

/**
 * Abstract parent class for the different dimensionanlty reduction algortithms that could 
 * be used for visualization.
 * 
 * @author Sam Vanmassenhove
 *
 * @param <T> Class of the data type used: will probably be either INDArray or OpenCV Mat.
 */
public abstract class DimensionalityReduction <T>{
	
	protected T data;
	protected T result;
	protected int dimensions;
	
	public DimensionalityReduction (final T data, int dimensions) {
		this.data = data;
		this.dimensions = dimensions;
	}
	
	public abstract void doReduction ();
	
	public T getResult () {
		return result;
	}
	
	public abstract List<double[]> convertToDoubleList ();
	
	
}