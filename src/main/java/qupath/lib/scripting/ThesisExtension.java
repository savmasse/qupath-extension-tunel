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
        Menu menu2 = qupath.getMenu("TELIN Extension", true);
        
        // create menu item
        MenuItem item = new MenuItem("Show thesis-extension");
        MenuItem item2 = new MenuItem("Show new menu option");
        
		item.setOnAction(e -> {
			new RichScriptEditor(qupath).showNewScript();
		});

        // add new item to menu
        menu.getItems().add(item);
        menu2.getItems().add(item2);    }
	
    @Override
	public String getName(){
        return "thesis-extension";
    }
	
    @Override
	public String getDescription(){
        return "First test of qupath extension.";
    }

}