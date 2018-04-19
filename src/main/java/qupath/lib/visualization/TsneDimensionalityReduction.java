
package qupath.lib.visualization;

import java.util.ArrayList;
import java.util.List;

import org.deeplearning4j.plot.Tsne;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;

public class TsneDimensionalityReduction extends DimensionalityReduction<INDArray> {
	
	protected Tsne tsne;
	protected int perplexity;
	
	public TsneDimensionalityReduction(INDArray data, int dimensions, int nIter, int perplexity) {
		super(data, dimensions);
		
		// Create the tsne object
		Tsne tsne = new Tsne.Builder()
				.setMaxIter(nIter)
				.perplexity(perplexity)
				.normalize(true)
				.build();
		
		this.perplexity = perplexity;
	}

	/**
	 * Perform the dimensionality reduction using TSNE.
	 */
	@Override
	public void doReduction() {
		result = tsne.calculate(data, dimensions, perplexity);
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
		
		for (int i = 0; i < result.length(); i++) {
			
			DataBuffer buf = result.getRow(i).dup().data();
			double [] dataPoint = buf.asDouble();
			
			list.add(dataPoint);
		}
		
		return list;
	}
	
}