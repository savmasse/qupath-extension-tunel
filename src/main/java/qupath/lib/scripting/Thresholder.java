package qupath.lib.scripting;

import java.awt.image.BufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.ImageData;
import qupath.lib.plugins.AbstractTileableDetectionPlugin;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.parameters.ParameterList;

public class Thresholder extends AbstractTileableDetectionPlugin<BufferedImage>{
	// Always add a logger
	private static final Logger logger = LoggerFactory.getLogger(Thresholder.class);
	ParameterList params;
	CellDetector detector;
	
	public Thresholder() {
		// create a small menu
		params = new ParameterList();
		params.addTitleParameter("Threshold");
		params.addIntParameter("thresholdValue", "Set threshold value", 1, "Set the threshold for the image.");
		
	}
	
	static class CellDetector {

		public String getLastResultsDescription() {
			return null;
		}
		
	}

	@Override
	public String getName() {
		return "Thresholder";
	}

	@Override
	public String getDescription() {
		return "Simple thresholding implementation.";
	}

	@Override
	public String getLastResultsDescription() {
		return detector == null ? "" : detector.getLastResultsDescription();
	}

	public double getPreferredPixelSizeMicrons(ImageData<BufferedImage> imageData, ParameterList params) {
		if (imageData.getServer().hasPixelSizeMicrons())
			return Math.max(params.getDoubleParameterValue("requestedPixelSizeMicrons"), imageData.getServer().getAveragedPixelSizeMicrons());
		return Double.NaN;
	}

	@Override
	protected ObjectDetector<BufferedImage> createDetector(ImageData<BufferedImage> imageData, ParameterList params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected int getTileOverlap(ImageData<BufferedImage> imageData, ParameterList params) {
		double pxSize = imageData.getServer().getAveragedPixelSizeMicrons();
		if (Double.isNaN(pxSize))
			return params.getDoubleParameterValue("cellExpansion") > 0 ? 25 : 10;
		double cellExpansion = params.getDoubleParameterValue("cellExpansionMicrons") / pxSize;
		int overlap = cellExpansion > 0 ? (int)(cellExpansion + 10) : 10;
//		System.out.println("Tile overlap: " + overlap + " pixels");
		return overlap;
	}

	@Override
	public ParameterList getDefaultParameterList(ImageData<BufferedImage> imageData) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
}
