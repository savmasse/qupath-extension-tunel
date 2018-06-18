package qupath.lib.active_learning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.PanelToolsFX;
import qupath.lib.gui.models.HistogramDisplay;
import qupath.lib.gui.plots.ScatterPlot;
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
public class ActiveLearningCommand2 implements PathCommand {
	
	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(ActiveLearningCommand.class);

	private final QuPathGUI qupath;
	private Stage dialog;
	private ActiveLearningPanel2 panel;
	
	public ActiveLearningCommand2 (final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		
		if (dialog == null) {
			dialog = new Stage();
			if (qupath != null)
				dialog.initOwner(qupath.getStage());
			
			dialog.setTitle("Active learning");
			panel = new ActiveLearningPanel2(qupath);
			
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
