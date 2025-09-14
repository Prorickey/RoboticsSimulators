package tech.bedson 

import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import kotlin.math.abs

class VerticalSlideSim(kP: Double, kI: Double, kD: Double) : Simulator(kP, kI, kD) {
    private val payloadMass = 1.0 // kg

    private val gravityConstant = 9.8067; // m/s^2

    // MiSUMi slides configuration
    private val numSlides = 3
    private val slideLength = 0.1 // m
    private val slideStroke = 0.06  // m
    private val slideMass = 0.08 // kg

    // Motor Specifications
    private val torqueStall = 1.8338 // Nm
    private val radius = 0.0175 // m
    private val wNoLoad = 45.553093425 // rad/s

    // Simulation constants
    private val dt = 1/100.0 // Time step (seconds) - f=100 Hz

    // Derived Constants
    private val maxStroke = numSlides * slideStroke // m
    private val startingLength = slideLength - slideStroke // m
    private val maxLength = startingLength + maxStroke // m
    private val totalMass = payloadMass + (numSlides * slideMass) // kg

    override fun runSimulation(startingPosition: Double, targetPosition: Double, debug: Boolean): SimulationResults {
        var stableStartTime = -1.0
        var stableReached = false

        var position = startingPosition
        var integral = 0.0
        var previousError = 0.0

        var velocity = 0.0

        // Position Data Series
        val positionSeries = XYSeries("Position")
        val errorSeries = XYSeries("Error")
        val targetSeries = XYSeries("Derivative")

        // PID Data Series
        val pSeries = XYSeries("Proportional")
        val iSeries = XYSeries("Integral")
        val dSeries = XYSeries("Derivative")

        val maxSteps = 1000
        for(step in 0..maxSteps) {
            val time = step * dt 

            // PID Control 
            val error = targetPosition - position 
            integral += error * dt 
            val derivative = (error - previousError) / dt 
            val output = ((kP * error) + (kI * integral) + (kD * derivative)).coerceIn(-1.0, 1.0)
            previousError = error 

            // Physics 
            val currentW = velocity / radius * if(output > 0) 1.0 else -1.0
            val torque = (torqueStall * output * (1 - (currentW / wNoLoad))).coerceIn(-torqueStall, torqueStall)
            val force = torque / radius // N
            val acceleration = (force / totalMass) - gravityConstant // m/s^2
            velocity += acceleration * dt
            position += velocity * dt

            // Hardware Limitations
            if(position > maxLength) {
                if(debug) System.err.printf("Hardstop Hit: t=%.2f s, pos=%.3f, output=%.3f, accel = %.3f m/s^2, vel=%.3f, err=%.3f\n", time, position, output, acceleration, velocity, error)
                velocity = 0.0
                position = maxLength
            } else {
                if(debug) System.out.printf("              t=%.2f s, pos=%.3f, output=%.3f, accel = %.3f m/s^2, vel=%.3f, err=%.3f\n", time, position, output, acceleration, velocity, error);
            }

            // Save data for graph
            positionSeries.add(time, position)
            errorSeries.add(time, error)
            targetSeries.add(time, targetPosition)

            pSeries.add(time, kP * error)
            iSeries.add(time, kI * integral)
            dSeries.add(time, kD * derivative)

            // Check if within tolerance
            if(abs(error) < 0.01) {
                if(!stableReached) {
                    if(stableStartTime < 0) stableStartTime = time 
                    if(time - stableStartTime >= 0.5) {
                        stableReached = true
                        stableStartTime = time
                    }
                }
            } else {
                stableStartTime = -1.0
            }

            if(stableReached && (time - stableStartTime >= 0.5)) {
                if(debug) println("Simulation complete at t=" + time + "s");
                break
            }
        }

        val positionDataset = XYSeriesCollection()
        positionDataset.addSeries(positionSeries)
        positionDataset.addSeries(errorSeries)
        positionDataset.addSeries(targetSeries)

        val pidDataset = XYSeriesCollection()
        pidDataset.addSeries(pSeries)
        pidDataset.addSeries(iSeries)
        pidDataset.addSeries(dSeries)

        return SimulationResults(0.0, positionDataset, pidDataset)
    }
}