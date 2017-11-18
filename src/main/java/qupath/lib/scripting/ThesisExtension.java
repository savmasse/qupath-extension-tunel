package qupath.lib.scripting;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension; 
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

public class ThesisExtension implements QuPathExtension {

    @Override
	public void installExtension(QuPathGUI qupath){
        // Get reference to menu
        Menu menu = qupath.getMenu("Automate>Thesis-extension", true);

        // add new item to menu
        QuPathGUI.addMenuItems(
        					menu, 
        					qupath.createPluginAction("Threshold", Thresholder.class, null, false)
        					);
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