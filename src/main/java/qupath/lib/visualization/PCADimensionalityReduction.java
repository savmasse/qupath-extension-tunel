
package qupath.lib.visualization;

import java.util.ArrayList;
import java.util.List;

import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dimensionalityreduction.PCA;

public class PCADimensionalityReduction extends DimensionalityReduction <INDArray> {
	private boolean normalize;
	
	public PCADimensionalityReduction(final INDArray data, final int dimensions, final boolean normalize) {
		super(data, dimensions);
		this.normalize = normalize;
	}
	
	/**
	 * Perform the dimensionality reduction using PCA.
	 */
	@Override
	public void doReduction() {
		result = PCA.pca(data, dimensions, normalize);
	}

	/**
	 * Convert the result of the reduction to a list of double arrays. Each array should be a 
	 * data point containing an x and a y value.
	 * 
	 * TODO Consider creating a class with static methods to do this...
	 */
	@Override
	public List<double[]> convertToDoubleList () {
		List <double []> list = new ArrayList<>();
		
		for (int i = 0; i < result.rows(); i++) {
			
			DataBuffer buf = result.getRow(i).dup().data();
			double [] dataPoint = buf.asDouble();
			
			list.add(dataPoint);
		}
		
		return list;
	}
	
}