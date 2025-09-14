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

    public HorizontalSlideSim(double kP, double kI, double kD) {
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
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

    // Penalties
    private final double overshootPenalty = 50;
    private final double settlingTimePenalty = 10;
    private final double steadyStatePenalty = 50;

    /**
     * Overshoot should be in percent from target; 0 if < 1cm
     * Steady State more than 1cm. Less than 1cm shouldn't have any affect
     */
    private double calculateCost(double overshoot, double settlingTime, double error) {
        return (overshoot * overshootPenalty) +
                (settlingTime * settlingTimePenalty) +
                (error * steadyStatePenalty);
    }

    public record SimulationResults(double totalCost, XYSeriesCollection positionDataset, XYSeriesCollection pidDataset) {

    }

    public SimulationResults runSimulation(double startingPosition, double targetPosition, boolean debug) {
        double totalCost = 0.0;

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

            double overshoot = position - targetPosition > 1 ? position - targetPosition : 0.0;
            double settlingTime = stableReached ? stableStartTime : time;
            double costError = error > 1 ? error : 0.0;
            totalCost += calculateCost(overshoot, settlingTime, costError);

            if(position > maxLength) { // Hardware limitation
                if(debug) System.out.printf("Hardstop Hit: t=%.2f s, pos=%.3f, output=%.3f, accel = %.3f m/s^2, vel=%.3f, err=%.3f\n", time, position, output, acceleration, velocity, error);
                velocity = 0;
                position = maxLength;
            } else {
                // Print state
                if(debug) System.out.printf("              t=%.2f s, pos=%.3f, output=%.3f, accel = %.3f m/s^2, vel=%.3f, err=%.3f\n", time, position, output, acceleration, velocity, error);
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
                if(debug) System.out.println("Simulation complete at t=" + time + "s");
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

        return new SimulationResults(totalCost, positionDataset, pidDataset);
    }
}
