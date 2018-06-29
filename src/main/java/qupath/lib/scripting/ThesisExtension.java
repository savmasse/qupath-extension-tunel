package qupath.lib.scripting;

import qupath.lib.active_learning.ActiveLearningCommand2;
import qupath.lib.active_learning.ActiveLearningCommand3;
import qupath.lib.algorithms.WatershedDetectionFRSIJ;
import qupath.lib.classification.ClassifierExampleCommand;
import qupath.lib.classification.OpenCvClassifierCommand;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.save_detections.SaveDetectionImagesCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        // Create a list of classes for the classifier; also create the classes needed for TUNEL
        // and add them to the UI.
        List <PathClass> classList = new ArrayList<>();
        classList.add(PathClassFactory.getPathClass("Tumor"));
        classList.add(PathClassFactory.getPathClass("Stroma"));

        // Create a list of relevant features for the classifier
        List <String> featureList = Arrays.asList("Nucleus: Area", "Nucleus: Perimeter", "Nucleus: Circularity");
        
        // Add new items to menu
        QuPathGUI.addMenuItems(
        					menu,
        					qupath.createPluginAction("Thresholder (experimental)", ThresholderOpenCV.class, null, false),
//        					qupath.createPluginAction("Experimental plugin", TunelDetectionPlugin.class, null, false),

//        					QuPathGUI.createCommandAction(new TunelDetectionPlugin(qupath), "Run plugin"),
        					QuPathGUI.createCommandAction(new ClassifierExampleCommand(qupath, classList, featureList), "Classifier (experimental)", null, new KeyCodeCombination(KeyCode.A, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN)),
        					QuPathGUI.createCommandAction(new OpenCvClassifierCommand(qupath), "Updated classifier"),
        					QuPathGUI.createCommandAction(new SaveDetectionImagesCommand(qupath), "Save detections", null, new KeyCodeCombination(KeyCode.S, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN)),
        					QuPathGUI.createCommandAction(new ActiveLearningCommand2(qupath), "Active learning (experimental)", null, new KeyCodeCombination(KeyCode.S, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN)),
        					QuPathGUI.createCommandAction(new ActiveLearningCommand3(qupath), "Active learning (experimental)", null, null)
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