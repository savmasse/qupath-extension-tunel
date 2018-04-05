
package qupath.lib.active_learning;

import org.nd4j.linalg.api.ndarray.INDArray;

import javafx.scene.control.*;
import qupath.lib.gui.helpers.dialogs.ParameterPanelFX;
import qupath.lib.plugins.parameters.ParameterList;

public abstract class AbstractExtractionPanel <T> {
	
	private ParameterList params;
	private ParameterPanelFX paramFX;
	private T featureExtractor;
	
	public AbstractExtractionPanel (final T featureExtractor) {		
		this.featureExtractor = featureExtractor;
	}
	
	public abstract void setParams ();
	
	public ParameterList getParams () {
		return params;
	}
	
	/**
	 * Extract the features from all pathObjects in the hierarchy, regardless
	 * of the class of the objects.
	 * 
	 * @return
	 */
	public abstract INDArray extractFeatures ();
	
	/**
	 * Return the titled pane with the parameters for the feature extraction.
	 * 
	 * @return
	 */
	public TitledPane getPane () {
		return new TitledPane("Feature extraction", paramFX.getPane());
	}
}
