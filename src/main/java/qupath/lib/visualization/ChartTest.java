package qupath.lib.visualization;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.jzy3d.chart.AWTChart;
import org.jzy3d.chart.Chart;
import org.jzy3d.chart.ChartLauncher;
import org.jzy3d.chart.factories.AWTChartComponentFactory;
import org.jzy3d.colors.Color;
import org.jzy3d.colors.ColorMapper;
import org.jzy3d.colors.colormaps.ColorMapRainbow;
import org.jzy3d.javafx.JavaFXChartFactory;
import org.jzy3d.javafx.JavaFXRenderer3d;
import org.jzy3d.javafx.controllers.mouse.JavaFXCameraMouseController;
import org.jzy3d.maths.Coord3d;
import org.jzy3d.maths.Range;
import org.jzy3d.plot3d.builder.Builder;
import org.jzy3d.plot3d.builder.Mapper;
import org.jzy3d.plot3d.primitives.Scatter;
import org.jzy3d.plot3d.primitives.Shape;
import org.jzy3d.plot3d.rendering.canvas.Quality;

/**
 * Showing how to pipe an offscreen Jzy3d chart image to a JavaFX ImageView.
 * 
 * {@link JavaFXChartFactory} delivers dedicated  {@link JavaFXCameraMouseController}
 * and {@link JavaFXRenderer3d}
 * 
 * Support 
 * Rotation control with left mouse button hold+drag
 * Scaling scene using mouse wheel 
 * Animation (camera rotation with thread) 
 * 
 * TODO : 
 * Mouse right click shift
 * Keyboard support (rotate/shift, etc)
 * 
 * @author Martin Pernollet
 */
public class ChartTest extends Application {
	
    public static void main(String[] args) {
        Application.launch(args);
    }
    
    public void start(Stage stage) {
        stage.setTitle("");
        
        // Jzy3d
        JavaFXChartFactory factory = new JavaFXChartFactory();
        
        AWTChart chart  = getDemoChart(factory, "offscreen");
        ImageView imageView = factory.bindImageView( chart );
        
        // JavaFX
        StackPane pane = new StackPane();
        Scene scene = new Scene(pane);
        stage.setScene(scene);
        stage.show();
        pane.getChildren().add(imageView);

        factory.addSceneSizeChangedListener(chart, scene);
        
        stage.setWidth(500);
        stage.setHeight(500);
    }

    private AWTChart getDemoChart(JavaFXChartFactory factory, String toolkit) {
        // -------------------------------
        // Define a function to plot
//        Mapper mapper = new Mapper() {
//            @Override
//            public double f(double x, double y) {
//                return x * Math.sin(x * y);
//            }
//        };
//
//        // Define range and precision for the function to plot
//        Range range = new Range(-3, 3);
//        int steps = 80;
//
//        // Create the object to represent the function over the given range.
//        final Shape surface = Builder.buildOrthonormal(mapper, range, steps);
//        surface.setColorMapper(new ColorMapper(new ColorMapRainbow(), surface.getBounds().getZmin(), surface.getBounds().getZmax(), new Color(1, 1, 1, .5f)));
//        surface.setFaceDisplayed(true);
//        surface.setWireframeDisplayed(false);
//
//        // -------------------------------
//        // Create a chart
//        Quality quality = Quality.Advanced;
//        //quality.setSmoothPolygon(true);
//        //quality.setAnimated(true);
//        
//        // let factory bind mouse and keyboard controllers to JavaFX node
//        AWTChart chart = (AWTChart) factory.newChart(quality, toolkit);
//        chart.getScene().getGraph().add(surface);
//        return chart;
    	
    	int size = 100000;
    	float x;
    	float y;
    	float z;
    	Coord3d[] points = new Coord3d[size];

    	// Create scatter points
    	for(int i=0; i<size; i++){
    	    x = (float)Math.random() - 0.5f;
    	    y = (float)Math.random() - 0.5f;
    	    z = (float)Math.random() - 0.5f;
    	    points[i] = new Coord3d(x, y, z);
    	}       

    	// Create a drawable scatter with a colormap
    	Scatter scatter = new Scatter( points );

    	// Create a chart and add scatter
    	Quality quality = Quality.Advanced;
    	AWTChart chart = (AWTChart) factory.newChart(quality, toolkit);
    	chart.getAxeLayout().setMainColor(Color.RED);
    	chart.getView().setBackgroundColor(Color.WHITE);
    	chart.getScene().add( scatter );

    	return chart;
    }
}
