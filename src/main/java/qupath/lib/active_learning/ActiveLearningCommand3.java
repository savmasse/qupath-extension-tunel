package qupath.lib.active_learning;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.stage.Stage;
import qupath.lib.classifiers.PathClassificationLabellingHelper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PanelToolsFX;
import qupath.lib.gui.helpers.dialogs.DialogHelper;
import qupath.lib.gui.helpers.dialogs.DialogHelperFX;
import qupath.lib.gui.models.HistogramDisplay;
import qupath.lib.gui.plots.ScatterPlot;
import qupath.lib.images.ImageData;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.*;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.input.*;
import javafx.scene.effect.*;


/**
 * Command which opens the active learning panel, allowing the user to change the classes
 * of previously classified objects with a very low class probability.
 * 
 * @author Sam Vanmassenhove
 *
 */
public class ActiveLearningCommand3 implements PathCommand {
	
	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(ActiveLearningCommand.class);

	private final QuPathGUI qupath;
	private Stage dialog;
	private ActiveLearningPanel3 panel;
	
	public ActiveLearningCommand3 (final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		
		// Run checks if panel should be opened
		if (!runChecks())
			return;
			
		
		if (dialog == null) {
			dialog = new Stage();
			if (qupath != null)
				dialog.initOwner(qupath.getStage());
			
			dialog.setTitle("Active learning");
			panel = new ActiveLearningPanel3(qupath);
			
			BorderPane pane = new BorderPane();
			pane.setCenter(panel.getPane());
			pane.setPadding(new Insets(10, 10, 10, 10));
			
			ScrollPane scrollPane = new ScrollPane(pane);
			scrollPane.setFitToHeight(true);
			scrollPane.setFitToWidth(true);
			
			// TODO Check if including this CSS file actually does anything...
			Scene scene = new Scene(scrollPane);
			scene.getStylesheets().add(getClass().getResource("active_learning.css").toExternalForm());
			dialog.setScene(scene);
			
			// Closing the dialog
			dialog.setOnCloseRequest(e -> {
				resetPanel();
				return;
			});
			
			dialog.show();
			dialog.setMaxWidth(Double.MAX_VALUE);
			
			if (dialog.getHeight() < javafx.stage.Screen.getPrimary().getVisualBounds().getHeight()) {
				dialog.setMinHeight(dialog.getHeight()/2);
			}
		}
		
	}
	
	/**
	 * Check if everything is OK and the panel is allowed to be opened.
	 */
	private boolean runChecks () {

		ImageData<BufferedImage> imageData = qupath.getImageData();		
		// If there is no image opened then we cannot open the panel
		if (imageData == null) {
			DisplayHelpers.showErrorMessage("No image data", "Please open an image before attempting to open this window.");;
			return false;
		}
		
		// Check if any classes have been set previously; if not then we cannot open the panel
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		Set<PathClass> representedClasses = PathClassificationLabellingHelper.getRepresentedPathClasses(hierarchy, null);
		if (representedClasses.size() == 1) {
			DisplayHelpers.showErrorMessage("No current classification", "Please perform an initial classification before opening this window.");;
			return false;
		}
		
		return true;
	}
	
	private void resetPanel () {
		if (panel == null)
			return;
		qupath.removeImageDataChangeListener(panel);
		panel.closePanel(); // Removes all listeners
		
		if (dialog != null) 
			dialog.setOnCloseRequest(null);
		dialog = null;
		panel = null;
	}

}
