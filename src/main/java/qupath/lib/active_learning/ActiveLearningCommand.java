package qupath.lib.active_learning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;

import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;

/**
 * Command which opens the active learning panel, allowing the user to change the classes
 * of previously classified objects with a very low class probability.
 * 
 * @author Sam Vanmassenhove
 *
 */
public class ActiveLearningCommand  implements PathCommand {
	
	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(ActiveLearningCommand.class);

	private final QuPathGUI qupath;
	private Stage dialog;
	private ActiveLearningPanel panel;
	
	public ActiveLearningCommand (final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		
		if (dialog == null) {
			dialog = new Stage();
			if (qupath != null)
				dialog.initOwner(qupath.getStage());
			
			dialog.setTitle("Active learning");
			panel = new ActiveLearningPanel(qupath);
			
			BorderPane pane = new BorderPane();
			pane.setCenter(panel.getPane());
			pane.setPadding(new Insets(10, 10, 10, 10));
			
			ScrollPane scrollPane = new ScrollPane(pane);
			scrollPane.setFitToHeight(true);
			scrollPane.setFitToWidth(true);
			dialog.setScene(new Scene (scrollPane));
			
			// Closing the dialog
			dialog.setOnCloseRequest(e -> {
				resetPanel();
				return;
			});
			
			dialog.show();
			dialog.setMaxWidth(dialog.getWidth());
			
			if (dialog.getHeight() < javafx.stage.Screen.getPrimary().getVisualBounds().getHeight()) {
				dialog.setMinHeight(dialog.getHeight()/2);
			}
		}
		
	}
	
	private void resetPanel () {
		if (panel == null)
			return;
		qupath.removeImageDataChangeListener(panel);
		if (dialog != null) 
			dialog.setOnCloseRequest(null);
		dialog = null;
		panel = null;
	}

}
