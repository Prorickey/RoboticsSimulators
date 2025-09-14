package tech.bedson;

import java.awt.GridLayout;
import javax.swing.JFrame;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import java.awt.Color;
import java.io.File;
import java.io.IOException;

/**
 * This is my first ever simulator. This simulates a MiSUMi slide 
 * system with a payload on the end. It takes a fair bit of information 
 * into account when simulating, but I have yet to verify how accurate 
 * it is. Nonetheless, it is a good start that I am proud of.
 * <p>
 * Specific scenario: 3 MiSUMi slides (SARC210) in series with one gobuilda 
 * yellowjacket (435 RPM) with a gobuilda pulley turning rotational motion
 * into linear motion.
 */
public class HorizontalSlideSim {
    // Payload
    private final double payloadMass = 1.0; // kg

    // PID Constants
    private final double kP;
    private final double kI;
    private final double kD;
    private final boolean saveToFile;

    public HorizontalSlideSim(double kP, double kI, double kD, boolean saveToFile) {
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
        this.saveToFile = saveToFile;
    }

    // MiSUMi slides configuration
    private final int numSlides = 3;
    private final double slideLength = 0.1; // m
    private final double slideStroke = 0.06; // m
    private final double slideMass = 0.08; // kg

    // Motor Specifications
    private final double torqueStall = 1.8338; // Nm
    private final double radius = 0.0175; // m
    private final double wNoLoad = 45.553093425; // rad/s

    // Simulation constants
    private final double dt = 1/100.0; // Time step (seconds) - f=100 Hz

    // Derived Constants
    private final double maxStroke = numSlides * slideStroke;
    private final double startingLength = slideLength - slideStroke;
    private final double maxLength = startingLength + maxStroke;
    private final double totalMass = payloadMass + (numSlides * slideMass);

    public void runSimulation(double startingPosition, double targetPosition) {
        double stableStartTime = -1;
        boolean stableReached = false;

        double position = startingPosition;
        double integral = 0;
        double previousError = 0;

        double velocity = 0;

        // Position Data Series
        XYSeries positionSeries = new XYSeries("Position");
        XYSeries errorSeries = new XYSeries("Error");
        XYSeries targetSeries = new XYSeries("Target Position");

        // PID Data Series
        XYSeries pSeries = new XYSeries("Proportional");
        XYSeries iSeries = new XYSeries("Integral");
        XYSeries dSeries = new XYSeries("Derivative");

        int maxSteps = 1000;
        for(int i = 0; i < maxSteps; i++) {
            double time = i * dt;

            // PID Control
            double error = targetPosition - position; // m
            integral += error * dt;
            double derivative = (error - previousError) / dt;
            double output = (kP * error) + (kI * integral) + (kD * derivative);
            output = Math.max(Math.min(output, 1), -1); 
            previousError = error;

            // Physics stuff
            double currentW = velocity / radius  * (output > 0 ? 1.0 : -1.0); // Current angular velocity of motor
            double torque = torqueStall * output * (1 - (currentW / wNoLoad)); // Motor torque from output
            torque = Math.max(Math.min(torque, torqueStall), -torqueStall); // Fix weird torques
            double force = torque / radius; // N
            double acceleration = force / totalMass; // m/s^2
            velocity += acceleration * dt; // m/s
            position += velocity * dt; // m

            if(position > maxLength) { // Hardware limitation
                System.err.printf("Hardstop Hit: t=%.2f s, pos=%.3f, output=%.3f, accel = %.3f m/s^2, vel=%.3f, err=%.3f\n", time, position, output, acceleration, velocity, error);
                velocity = 0;
                position = maxLength;
            } else {
                // Print state
                System.out.printf("              t=%.2f s, pos=%.3f, output=%.3f, accel = %.3f m/s^2, vel=%.3f, err=%.3f\n", time, position, output, acceleration, velocity, error);
            }

            // Save data for graph
            positionSeries.add(time, position);
            errorSeries.add(time, error);
            targetSeries.add(time, targetPosition);

            pSeries.add(time, kP * error);
            iSeries.add(time, kI * integral);
            dSeries.add(time, kD * derivative);

            // Check if within tolerance
            if (Math.abs(error) < 0.01) {
                if (!stableReached) {
                    if (stableStartTime < 0) stableStartTime = time; // first time in tolerance
                    if (time - stableStartTime >= 0.5) {
                        stableReached = true;
                        stableStartTime = time;
                    }
                } 
            } else {
                // reset if error drifts out again
                stableStartTime = -1;
            }

            // If stable reached and 2 sec passed, stop
            if (stableReached && (time - stableStartTime >= 0.5)) {
                System.out.println("Simulation complete at t=" + time + "s");
                break;
            }
        }

        XYSeriesCollection positionDataset = new XYSeriesCollection();
        positionDataset.addSeries(positionSeries);
        positionDataset.addSeries(errorSeries);
        positionDataset.addSeries(targetSeries);

        XYSeriesCollection pidDataset = new XYSeriesCollection();
        pidDataset.addSeries(pSeries);
        pidDataset.addSeries(iSeries);
        pidDataset.addSeries(dSeries);

        chartData(positionDataset, pidDataset);
    }

    public void chartData(XYSeriesCollection positionDataset, XYSeriesCollection pidDataset) {
        JFreeChart positionChart = ChartFactory.createXYLineChart(
                "Position vs Time",
                "Time (s)",
                "Position (m)",
                positionDataset,
                PlotOrientation.VERTICAL,
                true, true, false
        );

        JFreeChart pidChart = ChartFactory.createXYLineChart(
                "PID vs Time",
                "Time (s)",
                "Value",
                pidDataset,
                PlotOrientation.VERTICAL,
                true, true, false
        );

        // Set colors and hide shapes for position chart
        XYPlot posPlot = positionChart.getXYPlot();
        XYLineAndShapeRenderer posRenderer = new XYLineAndShapeRenderer();
        posRenderer.setSeriesPaint(0, Color.RED);
        posRenderer.setSeriesPaint(1, Color.BLUE);
        posRenderer.setSeriesPaint(2, Color.BLACK);
        posRenderer.setSeriesShapesVisible(0, false);
        posRenderer.setSeriesShapesVisible(1, false);
        posRenderer.setSeriesShapesVisible(2, false);
        posPlot.setRenderer(posRenderer);

        // Set colors and hide shapes for PID chart
        XYPlot pidPlot = pidChart.getXYPlot();
        XYLineAndShapeRenderer pidRenderer = new XYLineAndShapeRenderer();
        pidRenderer.setSeriesPaint(0, Color.RED);
        pidRenderer.setSeriesPaint(1, Color.BLUE);
        pidRenderer.setSeriesPaint(2, Color.BLACK);
        pidRenderer.setSeriesShapesVisible(0, false);
        pidRenderer.setSeriesShapesVisible(1, false);
        pidRenderer.setSeriesShapesVisible(2, false);
        pidPlot.setRenderer(pidRenderer);

        JFrame frame = new JFrame("MiSUMi Slide Simulator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        if(!saveToFile) {
            javax.swing.JPanel panel = new javax.swing.JPanel(new GridLayout(1, 2));
            panel.add(new ChartPanel(positionChart));
            panel.add(new ChartPanel(pidChart));

            frame.setContentPane(panel);
            frame.pack();
            frame.setVisible(true);
        } else {
            try {
                ChartUtils.saveChartAsPNG(new File("positionChart.png"), positionChart, 800, 600);
                ChartUtils.saveChartAsPNG(new File("pidChart.png"), pidChart, 800, 600);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
