
package qupath.lib.visualization;

import qupath.lib.objects.PathObject;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.chart.*;
import javafx.scene.layout.*;

/**
 * Simple implementation of a javafx scatter plot which links objects of class T to the 
 * data points. 
 * 
 * @author Sam Vanmassenhove
 *
 * @param <T>
 */
public class ScatterPlotPanel <T>{
	
	private static final Logger logger = LoggerFactory.getLogger(ScatterPlotPanel.class);
	private GridPane pane;
	private NumberAxis xAxis, yAxis;
	private ScatterChart <Number, Number> scatterChart;
	private String xTitle, yTitle, chartTitle;
	
	public ScatterPlotPanel (String xTitle, String yTitle, String chartTitle) {
		pane = new GridPane();
		this.xTitle = xTitle;
		this.yTitle = yTitle;
		this.chartTitle = chartTitle;
		
		// Initiate the plot
		initScatterPlot(10, 10, 1, 1);
		
		// Add plot to the pane
		pane.add(scatterChart, 0, 0);
	}
	
	public ScatterPlotPanel () {
		this("x-axis", "y-axis", "Chart");
	}
	
	public void initScatterPlot (int xMax, int yMax, int xStep, int yStep) {
		// Create axis
		xAxis = new NumberAxis(0, xMax,  xStep);
		yAxis = new NumberAxis(0, yMax, yStep);
		
		// Create chart
		scatterChart = new ScatterChart<Number, Number>(xAxis, yAxis);
		scatterChart.setPrefSize(800, 800);
        scatterChart.setMinSize(500, 500);
        
        // Set the titles 
        xAxis.setLabel(xTitle);                
        yAxis.setLabel(yTitle);
        scatterChart.setTitle(chartTitle);
	}
	
	public GridPane getPane () {
		return pane;
	}
	
	/**
	 * Create a series from an array of data points
	 * @param data
	 * @param pathObjects
	 * @return
	 */
	private XYChart.Series<Number, Number> createSeries (double [][] data, List <T> objects) {
		
		XYChart.Series<Number, Number> res = new XYChart.Series<>();
		
		for (int i = 0; i < data.length; i++) {	
			XYChart.Data<Number, Number> d;
			if (objects != null)
				d = new XYChart.Data<>(data[i][0], data[i][1], objects.get(i));
			else 
				d = new XYChart.Data<>(data[i][0], data[i][1]);
			
			res.getData().add(d);
		}
		
		return res;
	}
	
	/**
	 * Add a series to the scatter plot.
	 * @param name
	 * @param data 2d array containing the x and y values for the plot
	 * @param objects Objects to link to the data points. Set to null to ignore.
	 */
	public void addSeries (String name, double [][] data, List <T> objects) {
		
		if (data == null || data[0].length < 2) {
			logger.info("No data provided - could not plot.");
			return;
		}
		if (data[0].length > 2) {
			logger.info("Provided data is not 2D - only the first two elements of the array were used in the plot.");
		}
		
		// Get the series
		XYChart.Series<Number, Number> series = createSeries(data, objects);
		series.setName(name);
		scatterChart.getData().addAll(series);
		
		// Set the axes to fit the new data
		xAxis.autoRangingProperty().set(true);
		yAxis.autoRangingProperty().set(true);
	}
	
	public void addSeries (String name, List<double[]> data, List<T>objects) {
		double [][] d = data.toArray(new double[data.size()][data.get(0).length]);
		addSeries(name, d, objects);
	}
	
	
	/**
	 * Clear the scatter plot.
	 */
	public void clearPlot () {
		scatterChart.getData().clear();
	}
	
}