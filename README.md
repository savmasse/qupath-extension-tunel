# Active learning extension
QuPath extension which includes active learning for nucleus and cell classification.

## Introduction
This extension includes a generic active learning approach to the classification problems in biological microscopy images. An advantage of this method is that it will work on both fluorescence and microscopy images. The method is significantly faster than using the machine learning capabilities of the standard QuPath release as much fewer samples have to be annotated to achieve the same performance.

## Installation
The process of installation for the extension is exactly the same as that of other QuPath extensions. The user should either drag the .jar file onto the main QuPath window or directly place it in the extensions folder. The source code for this project is available at "https://github.com/savmasse/qupath-extension-tunel". A jar file can be compiled from these project files.  

## Using the extension

### Segmentation
Before classification the user should perform a segmentation using the technique they would regularly use. This could be a watershed segmentation in QuPath or any segmentation method used through the ImageJ extension.

### Classification
The active learning extensions uses the standard machine learning classifier from the standard QuPath release to perform classifications. This is done so scripts may be written more easily using existing scripting functions. Please always use the default 'Random Trees' setting. More information on how to use the classification panel can be found on the QuPath wiki page (https://github.com/qupath/qupath/wiki/Classifying-objects).

#### Initial training sample selection
The initial training samples can be selected by drawing an annotation around the sample and setting a class for this annotation, or by using the "select cells" tool included in the extension. To use the latter methode, please select cells/nuclei (not annotations) by clicking them ('ALT + click' to select multiple) and pressing the "select cells" option in the extension drop-down menu. These cells are now added as an annotation, so the class can be set in the usual way from the annotation window.

![Image](images/Classsifier.PNG?raw=true "Title")

### Active learning

![Image](images/Panel.PNG?raw=true "Title")

#### Clustering
The active learning used in the implementation requires the data to clustered to work effectively. First select the amount of features you want the clustering to take into account; these are probably the same features used by the classifier. Then set the amount of clusters with the slider (recommended amount is 10), and then click the "Cluster" button. This should take only a few seconds to process. You can also view a plot of these clusters to see the data distribution and pick a good amount of clusters.

#### Sampling
After the data is clustered, the algorithm will propose a sample from each cluster. The proposed sample will be centered in the image and selected (in yellow, see image below), while the user retains control over the zoom so the surrounding context can be observed. You will probably want to use the options at the top of the active learning panel to make it so it doesn't show the detections, otherwise the interface will be quite cluttered.

The user now makes a classification decision: either the current classification can be accepted or rejected. In case of a rejection the new class can be set with the 'Choose class' drop-down menu.

It is recommended to retrain the classifier (with the classification panel) every time a sample from each cluster has been proposed. So if you choose to create 10 clusters, then retrain every 10 samples.

![Image](images/Sampling.PNG?raw=true "Title")

#### Convergence
The active learning process can be stopped when the user is satisfied with the result. The result van be checked visually or through the 'log' of the classifier. Each time a classification is performed, the last line printed in the log (opened with CTRL + SHIFT + L) will be the amount of samples which changed class because of the last classification. In the first few active learning steps this amount will be large, while later this will shrink as the result converges. If only a few samples change class anymore than the process can be stopped.

```
INFO: Number of reclassified objects: 308 of 10514
```
