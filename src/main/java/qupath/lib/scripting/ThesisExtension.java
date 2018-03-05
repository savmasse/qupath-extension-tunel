package qupath.lib.scripting;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;

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
        					qupath.createPluginAction("Thresholder (experimental)", ThresholderOpenCV.class, null, false),
        					QuPathGUI.createCommandAction(new CreateParentAnnotation(qupath), "Cell Selector (experimental)", null, new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)),
        					QuPathGUI.createCommandAction(new CreateCellFromAnnotation(qupath), "Cell Creator (experimental)", null, new KeyCodeCombination(KeyCode.S, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN))
        		);
        
        // Experimental Non-plugin item
        MenuItem item = new MenuItem("Experimental");
        
        item.setOnAction(e -> {
        	new BrightnessContrastCommandCopy(qupath).run();
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