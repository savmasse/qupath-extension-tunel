package qupath.lib.save_detections;

import java.awt.ImageCapabilities;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import javafx.stage.Stage;
import jfxtras.scene.layout.GridPane;
import javafx.scene.Scene;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import qupath.imagej.objects.PathImagePlus;
import qupath.imagej.objects.ROIConverterIJ;
import qupath.lib.geom.Point2;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.dialogs.ParameterPanelFX;
import qupath.lib.ij_opencv.ImagePlusToMatConverter;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.interfaces.ROI;

/**
 * Command for the saving of detection objects to the disk as PNG or JPEG.
 * 
 * TODO :: Maybe implement listeners to make sure the displayed data in the
 * menu is always up to date.
 * 
 * @author Sam Vanmassenhove
 *
 */
public class SaveDetectionImagesCommand implements PathCommand {
	
	private static final Logger logger = LoggerFactory.getLogger(SaveDetectionImagesCommand.class);
	
	private QuPathGUI qupath; 
	private Stage dialog;
	private SaveDetectionImagesPanel panel;
	
	public SaveDetectionImagesCommand (final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run () {
		
		if (dialog == null) {
			dialog = new Stage();
			if (qupath != null) 
				dialog.initOwner(qupath.getStage());
			dialog.setTitle("Save detection images");
			panel = new SaveDetectionImagesPanel(qupath);
			BorderPane pane = new BorderPane();
			//pane.setCenter(panel.getPane());
			pane.setTop(panel.getPane());
			
			ScrollPane scrollPane = new ScrollPane(pane);
			scrollPane.setFitToWidth(true);
			scrollPane.setFitToHeight(true);
			dialog.setScene(new Scene(scrollPane));
			
			// Handle closing of dialog
			dialog.setOnCloseRequest(e -> {
				resetPanel();
				return;
			});
			
			dialog.show();
			dialog.setMinWidth(dialog.getWidth());

			if (dialog.getHeight() < javafx.stage.Screen.getPrimary().getVisualBounds().getHeight()) {
				dialog.setMinHeight(dialog.getHeight()/2);
			}
		}
	}

	/**
	 * Handle cleanup whenever a dialog should be closed (and forgotten)
	 */
	private void resetPanel () {
		if (panel == null)
			return;
		qupath.removeImageDataChangeListener(panel);
		if (dialog != null) 
			dialog.setOnCloseRequest(null);
		dialog = null;
		panel = null;
	}
	
	private static class SaveDetectionImagesPanel implements PathObjectHierarchyListener, ImageDataChangeListener<BufferedImage>{

		private final static Logger logger = LoggerFactory.getLogger(SaveDetectionImagesPanel.class);

		private QuPathGUI qupath;
		
		// Panels and panes
		private GridPane pane;
		private TitledPane titledPane;
		private ParameterPanelFX paramPanel;
		
		// Buttons
		private Button btnSave;
		
		// File formats
		private static enum FileFormat {
			JPEG,
			PNG
		}
		private String extension;
		private boolean currentAnnotationOnly;
		private boolean saveByClass;
		private boolean cropROI;
		
		// Parameters
		private List <FileFormat> formatList = new ArrayList <> ();
		private ParameterList params;
		
		// Detection objects
		private List <PathObject> pathObjects;
		
		// hierarchy and imagedata should change with listeners
		private PathObjectHierarchy hierarchy;
		private ImageData<BufferedImage> imageData;
		
		public SaveDetectionImagesPanel(final QuPathGUI qupath) {
			
			// Add listeners so we will be aware of changes in the image while the window is opened
			this.qupath = qupath;
			
			if (qupath == null)
				this.qupath = QuPathGUI.getInstance();
			
			this.qupath.addImageDataChangeListener(this);
			this.imageData = qupath.getImageData();
			
			if (this.imageData != null) {
				this.hierarchy = imageData.getHierarchy();
				this.hierarchy.addPathObjectListener(this);
			}
			
			//this.pane = new TitledPane("Test", null);
			this.pane = new GridPane();
			this.pane.setMinSize(400, 200);
			this.pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
			
			// Set the parameters
			formatList.add(FileFormat.PNG);
			formatList.add(FileFormat.JPEG);
			params = new ParameterList()
						.addBooleanParameter("currentAnnotation", "Current annotation only", true, "Set to true to only save only the currently selected annotation. Otherwise all annotations will be saved.")
						.addBooleanParameter("saveByClass", "Save by class", false, "Save elements of each class in a different subfolder of the selected path.")
						.addBooleanParameter("cropROI", "Crop ROI", true, "Crop the ROI to set all values of the bounding box outside the ROI to zero (black)")
						.addChoiceParameter("fileFormat", "File format", FileFormat.PNG, formatList, "JPEG=8bit, PNG=16bit (if your image supports this)");
						
			
			this.paramPanel = new ParameterPanelFX(params);
			titledPane = new TitledPane("Image save options", paramPanel.getPane());
			
			// Create the button
			btnSave = new Button ("Save");
			btnSave.setTooltip(new Tooltip("This button will open a filechooser to select the save folder."));
			btnSave.setOnAction(e -> {
				logger.info("Save button clicked !");
				clickSaveButton();
			});
			
			// Add all created components to the GridPane			
			this.pane.add(titledPane,0,0);
			this.pane.add(btnSave,0,1);
			this.pane.setCenterShape(true);
			GridPane.setHgrow(titledPane, Priority.ALWAYS);
			GridPane.setHgrow(btnSave, Priority.ALWAYS);
			
			/*
			FileInputStream input = null;
			try {
				input = new FileInputStream("C:\\\\Users\\\\SamVa\\\\Desktop\\\\Thesis\\\\data\\\\saved\\\\labelA\\\\print_8.png");
			} catch (FileNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			javafx.scene.image.Image image = new javafx.scene.image.Image(input);
			
			// We want to resample the image because ImageViewer does some weird interpolation/smoothing effect ...
			final int W = (int) image.getWidth();
		    final int H = (int) image.getHeight();
		    final int S = 50;

		    WritableImage output = new WritableImage(
		      W * S,
		      H * S
		    );
		    
			PixelReader pr = image.getPixelReader();
			PixelWriter pw = output.getPixelWriter();
		
		    for (int y = 0; y < H; y++) {
			      for (int x = 0; x < W; x++) {
			    	  final int argb = pr.getArgb(x, y);
			    	  for (int dy = 0; dy < S; dy++) {
			    		  for (int dx = 0; dx < S; dx++) {
			    			  pw.setArgb(x * S + dx, y * S + dy, argb);
			    		  }
			    	  }
			      }
			}
			
			javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(output);
			this.pane.add(imageView);
			*/
		}
		
		private void clickSaveButton () {
			
			currentAnnotationOnly = params.getBooleanParameterValue("currentAnnotation");

			// Check if image is opened
			if (imageData == null) {
				logger.error("No image data available. Please open an image before trying to save detections.");
				return;
			}

			// Get selected items
			PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
			
			// If current annotation only, then first check whether such an annotation exists!
			if (currentAnnotationOnly) {
				if (selected == null) {
					logger.error("Nothing was selected.");
					return;
				}
				if ( ! (selected instanceof PathAnnotationObject) ) {
					logger.error("Current selection is not an annotation.");
					return;
				}
			}
			
			// Get the file extension
			if (params.getChoiceParameterValue("fileFormat").equals(FileFormat.PNG)) {
				extension = ".png";
			}
			else if (params.getChoiceParameterValue("fileFormat").equals(FileFormat.JPEG)) {
				extension = ".jpg";
			}
			else 
				extension = null;
			
			// Get all detection objects from the hierarchy
			if(currentAnnotationOnly) {
				pathObjects = (List<PathObject>) selected.getChildObjects();
			}
			else {
				pathObjects = hierarchy.getFlattenedObjectList(null);
			}
			
			// Check if list is empty
			if (pathObjects == null || pathObjects.size() == 0) {
				logger.error("There were no objects in the image or selected annotation.");
				return;
			}
			
			// Get the save folder location
			File outputFolder = qupath.getDialogHelper().promptForDirectory(new File("..\\"));
			if (outputFolder == null) {
				logger.error("No folder selected");
				return;
			}
			
			// Now save these PathObjects
			saveObjects(pathObjects, outputFolder);
		}
		
		private void saveObjects (List <PathObject> pathObjects, File outputFolder) {
			
			saveByClass = params.getBooleanParameterValue("saveByClass");
			cropROI = params.getBooleanParameterValue("cropROI");
			
			Map <PathClass, Integer> classMap = new HashMap<PathClass, Integer>();
			int counter = 0;
			
			// Get access to the actual image data
			File f = new File ("C:\\users\\SamVa\\Desktop\\test.png");
			ImageServer<BufferedImage> server = imageData.getServer();
			double downSampleFactor = ServerTools.getDownsampleFactor(server, 0, true);
//			BufferedImage img = server.readBufferedImage(RegionRequest.createInstance(server.getPath(), downSampleFactor, );
//			try {
//				ImageIO.write(img, "PNG", f);
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			double pixelSize = imageData.getServer().getAveragedPixelSizeMicrons();
			PathImage<ImagePlus> pathImage = PathImagePlus.createPathImage(server, pathObjects.get(0).getROI(), ServerTools.getDownsampleFactor(server, pixelSize, true));
//			PathImage<ImagePlus> pathImage = PathImagePlus.createPathImage(server, ServerTools.getDownsampleFactor(server, 0, false));
			ImageProcessor ip = pathImage.getImage().getProcessor();
			
			ImagePlus imp = new ImagePlus("Image writer", ip);
			Calibration cal = new Calibration(imp);
			
			// New :::
			imp = pathImage.getImage();
			Roi roi = ROIConverterIJ.convertToIJRoi(pathObjects.get(10).getROI(), cal, downSampleFactor);

			ImageProcessor i = imp.getStack().getProcessor(imp.getStackIndex(0,0,0));
			ImageProcessor j = imp.getStack().getProcessor(imp.getStackIndex(2,0,0));
			i.setRoi(roi);
			j.setRoi(roi);
			short [] channel1 = ((short[]) i.crop().getPixels());
			short [] channel2 = ((short[]) j.crop().getPixels());
			
			
			double [] vals = {i.crop().getHeight(), i.crop().getWidth()};
			Mat mat1 = new Mat(i.crop().getHeight(), i.crop().getWidth(), CvType.CV_16U);
			Mat mat2 = new Mat(i.crop().getHeight(), i.crop().getWidth(), CvType.CV_16U);
			mat1.put(0, 0, channel1);
			mat2.put(0, 0, channel2);
			
			// Print the matrix
			Imgcodecs.imwrite("C:\\users\\SamVa\\Desktop\\channel_1.png", mat1);
			Imgcodecs.imwrite("C:\\users\\SamVa\\Desktop\\channel_2.png", mat2);
			
			// End new :::
			
			// Now go through the list and save each object (in correct folder)
			for (PathObject p : pathObjects) {
				
				// Skip annotations which got in here by accident; we only want detections
				if (p == null || p instanceof PathAnnotationObject || p.getPathClass() == null || p.getPathClass().equals(PathClassFactory.getPathClass("Image")))
					continue;     
				
				if (saveByClass) {
					PathClass pc = p.getPathClass();
					String className = pc.getName();
					
					while (pc.isDerivedClass()) {
						className = pc.getParentClass().getName() + "_" + className;
						pc = pc.getParentClass();
					}
					// Reset the pc
					pc = p.getPathClass();
					
					File subFolder = new File (outputFolder + File.separator + className);		
					
					if (!classMap.containsKey(pc)) { // Create the subfolder
						subFolder.mkdirs();
						classMap.put(pc, 1);
					}
					else {
						classMap.put(pc, classMap.get(pc)+1);
					}
					
					// Save the file 
					saveFile(p, ip, new File (subFolder.getAbsolutePath() + File.separator + System.currentTimeMillis() + extension), ServerTools.getDownsampleFactor(server, 0, true), cal);
				}
				else {
					saveFile(p, ip, new File (outputFolder.getAbsolutePath() + File.separator + System.currentTimeMillis() + extension), ServerTools.getDownsampleFactor(server, 0, true) , cal);
				}
				
				// Increment counter
				counter++;
			}
			
			// Done: print what was done to the log
			logger.info("======= Finished saving files =======");
			logger.info("" + counter + " images saved.");
			
			// Show how many files in each subfolder
			if (saveByClass) {
				for (PathClass pc : classMap.keySet()) {
					logger.info ("" + classMap.get(pc) + " images saved of class " + pc + ".");
				}
			}
			
		}
		
		/**
		 * Writes the file to the specified file location.
		 * @param outputFile
		 */
		private void saveFile (PathObject pathObject, ImageProcessor ip, File outputFile, double downSampleFactor, Calibration cal) {
			
			// Only save what's actually inside the ROI of the object
			ROI pathROI = pathObject.getROI();
			Roi roi = ROIConverterIJ.convertToIJRoi(pathROI, cal, downSampleFactor);
			Mat write;
			ShortProcessor sp;
			
			if (cropROI) {
				// Crop to the ROI
//				ImageProcessor cp = (ImageProcessor) ip.duplicate();
//				cp.setRoi(roi);
//				cp.fillOutside(roi);
//				sp = (ShortProcessor) cp.crop();
				
				ip.setRoi(roi);
				sp = (ShortProcessor) ip.crop();

		        // Create a new roi for the ShortProcessor from the imageprocessor roi. All points have to
		        // to be translated to correspond with the new coordinate system.
		        List<Point2> pList = pathROI.getPolygonPoints();

		        // Update the points to correspond with the new processor
		        List<Point2> tempList = new ArrayList<>();
		        for (Point2 p : pList) {
		            Point2 temp = new Point2(p.getX() - pathROI.getBoundsX(), p.getY() - pathROI.getBoundsY());
		            tempList.add(temp);
		        }
		        ROI r = new PolygonROI(tempList);
		        sp.fillOutside(ROIConverterIJ.convertToIJRoi(r, cal, downSampleFactor));
		        
			}
			else {
				// Save the whole bounding box as is
				ip.setRoi(roi);
				sp = (ShortProcessor) ip.crop();
			}

			// Convert to OpenCV
			write = ImagePlusToMatConverter.toMat(sp);
			
			if (extension.equals(".jpg")) 
				write.convertTo(write, CvType.CV_8U, 0.00390625);
			Imgcodecs.imwrite(outputFile.getAbsolutePath(), write);
			
			// Release the Mat for memory reasons
			write.release();
			
		}
		
		private GridPane getPane () {
			return pane;
		}
		
		/**
		 * If ImageData changes it means another image was opened. This will affect the available objects.
		 */
		@Override
		public void imageDataChanged(ImageDataWrapper<BufferedImage> source, ImageData<BufferedImage> imageDataOld,
				ImageData<BufferedImage> imageDataNew) {
			
			this.setImageData(imageDataOld, imageDataNew);
		}
		
		public void setImageData(final ImageData<BufferedImage> imageDataOld, final ImageData<BufferedImage> imageDataNew) {
			
			if (imageDataOld == imageDataNew)
				return;
			if (imageDataOld != null)
				imageDataOld.getHierarchy().removePathObjectListener(this);
			if (imageDataNew != null)
				imageDataNew.getHierarchy().addPathObjectListener(this);
			
			this.imageData = imageDataNew;
		}
		
		/**
		 * Handle the hierarchy change
		 */
		@Override
		public void hierarchyChanged(PathObjectHierarchyEvent event) {
			this.hierarchy = event.getHierarchy();
		}
		
	}
}
