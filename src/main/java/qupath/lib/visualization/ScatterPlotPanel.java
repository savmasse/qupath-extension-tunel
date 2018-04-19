
package qupath.lib.visualization;

import qupath.lib.objects.PathObject;

import java.util.List;

import javafx.scene.chart.*;
import javafx.scene.layout.*;

public class ScatterPlotPanel {
	
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
	private XYChart.Series<Number, Number> createSeries (double [][] data, List <PathObject> pathObjects) {
		
		XYChart.Series<Number, Number> res = new XYChart.Series<>();
		
		for (int i = 0; i < data.length; i++) {	
			XYChart.Data<Number, Number> d = new XYChart.Data<>(data[0][0], data[0][1], pathObjects.get(i));
			res.getData().add(d);
		}
		
		return res;
	}
	
	/**
	 * Add a series to the scatter plot.
	 * @param name
	 * @param data 2d array containing the x and y values for the plot
	 */
	public void addSeries (String name, double [][] data, List <PathObject> pathObjects) {
		
		// Get the series
		XYChart.Series<Number, Number> series = createSeries(data, pathObjects);
		series.setName(name);
		scatterChart.getData().addAll(series);
		
		// Set the axes to fit the new data
		xAxis.autoRangingProperty().set(true);
		yAxis.autoRangingProperty().set(true);
	}
	
	/**
	 * Clear the scatter plot.
	 */
	public void clearPlot () {
		scatterChart.getData().clear();
	}
	
}