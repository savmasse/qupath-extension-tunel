package qupath.lib.scripting;

import qupath.lib.active_learning.ActiveLearningCommand;
import qupath.lib.active_learning.ActiveLearningCommand2;
import qupath.lib.algorithms.WatershedDetectionFRS;
import qupath.lib.algorithms.WatershedDetectionFRSIJ;
import qupath.lib.classification.OpenCvClassifierCommand;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.save_detections.SaveDetectionImagesCommand;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;

public class ThesisExtension implements QuPathExtension {

    @Override
	public void installExtension(QuPathGUI qupath){
        // Get reference to menu
        Menu menu = qupath.getMenu("Extensions>TUNEL extension", true);

        // Add new items to menu
        QuPathGUI.addMenuItems(
        					menu,
        					qupath.createPluginAction("Watershed (experimental)", WatershedCellDetection.class, null, false),
        					qupath.createPluginAction("Watershed (old)", WatershedCellDetection2.class, null, false),
        					qupath.createPluginAction("Thresholder (experimental)", ThresholderOpenCV.class, null, false),
        					qupath.createPluginAction("FRS (experimental)", WatershedDetectionFRSIJ.class, null, false),
        					QuPathGUI.createCommandAction(new OpenCvClassifierCommand(qupath), "Classifier (experimental)", null, new KeyCodeCombination(KeyCode.A, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN)),
        					QuPathGUI.createCommandAction(new ActiveLearningCommand(qupath), "Active learning", null, new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)),
        					QuPathGUI.createCommandAction(new SaveDetectionImagesCommand(qupath), "Save detections", null, new KeyCodeCombination(KeyCode.S, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN)),
        					QuPathGUI.createCommandAction(new ActiveLearningCommand2(qupath), "Active learning (experimental)", null, new KeyCodeCombination(KeyCode.S, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN))
        		);
        
        // Experimental Non-plugin item
        MenuItem item = new MenuItem("Create cell from annotation");
        
        item.setOnAction(e -> {
        	new CreateCellFromAnnotation(qupath).run();
		});
		
        menu.getItems().add(item);
        
        // Add selection items
        MenuItem selectionItem = new MenuItem ("Select Cells");
        selectionItem.setOnAction(e -> {
        	new CreateParentAnnotation(qupath).run();
        });
        menu.getItems().add(selectionItem);
       
    }
	
    @Override
	public String getName(){
        return "qupath-extension-tunel";
    }
	
    @Override
	public String getDescription(){
        return "Extension for TUNEL and fluorescence microscopy.";
    }

}