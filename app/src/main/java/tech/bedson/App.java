package tech.bedson;

import javax.swing.JFrame;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class App {
    // Payload
    private double payloadMass = 1.0; // kg

    // MiSUMi slides configuration
    private int numSlides = 3;
    private double slideLength = 0.1; // m
    private double slideStroke = 0.06; // m
    private double slideMass = 0.08; // kg

    // Motor Specifications
    private double torqueStall = 1.8338; // Nm 
    private double radius = 0.0175; // m
    private double wNoLoad = 45.553093425; // rad/s

    // Simulation constants
    private double dt = 1/100.0;        // Time step (seconds) - f=100 Hz

    // Derived Constants
    private double maxStroke = numSlides * slideStroke;
    private double startingLength = slideLength - slideStroke;
    private double maxLength = startingLength + maxStroke;
    private double totalMass = payloadMass + (numSlides * slideMass);

    public XYSeriesCollection runSimulation(double kP, double kI, double kD, double startingPosition, double targetPosition) {
        double stableStartTime = -1;
        boolean stableReached = false;

        double position = startingPosition;
        double integral = 0;
        double previousError = 0;

        double velocity = 0;

        // Create data series
        XYSeries positionSeries = new XYSeries("Position");
        XYSeries errorSeries = new XYSeries("Error");
        XYSeries targetSeries = new XYSeries("Target Position");

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

            // Print state
            System.out.printf("t=%.2f s, pos=%.3f, output=%.3f, accel = %.3f m/s^2, vel=%.3f, err=%.3f\n", time, position, output, acceleration, velocity, error);

            // Save data for graph
            positionSeries.add(time, position);
            errorSeries.add(time, error);
            targetSeries.add(time, targetPosition);

            // --- Check if within tolerance ---
            if (Math.abs(error) < 0.01) {
                if (!stableReached) {
                    if (stableStartTime < 0) stableStartTime = time; // first time in tolerance
                    if (time - stableStartTime >= 1.0) {
                        stableReached = true;
                        stableStartTime = time;
                    }
                } 
            } else {
                // reset if error drifts out again
                stableStartTime = -1;
            }

            // --- If stable reached and 2 sec passed, stop ---
            if (stableReached && (time - stableStartTime >= 1.0)) {
                System.out.println("Simulation complete at t=" + time + "s");
                break;
            }
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(positionSeries);
        dataset.addSeries(errorSeries);
        dataset.addSeries(targetSeries);

        return dataset;
    }

    public void chartData(XYSeriesCollection dataset) {
        // Create chart
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Position vs Time",
                "Time (s)",
                "Position (m)",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false
        );

        // Display in Swing
        JFrame frame = new JFrame("MiSUMi Slide Simulator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new ChartPanel(chart));
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        App simulator = new App();
        XYSeriesCollection dataset = simulator.runSimulation(10, 0, 0.5, simulator.startingLength, 0.3);
        simulator.chartData(dataset);
    }
}
