package tech.bedson

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class AutoTuneHorizontalSlideSim {
    // Payload
    private val payloadMass = 1.0 // kg

    // Tuner Configuration
    private val startingWindow = 25.0
    private val shrinkFactor = 0.90

    private val maxKp = 100.0
    private val minKp = 0.01
    private val maxKi = 100.0
    private val minKi = 0.01
    private val maxKd = 100.0
    private val minKd = 0.01

    // MiSUMi slides configuration
    private val numSlides = 3
    private val slideLength = 0.1 // m
    private val slideStroke = 0.06 // m
    private val slideMass = 0.08 // kg

    // Motor Specifications
    private val torqueStall = 1.8338 // Nm
    private val radius = 0.0175 // m
    private val wNoLoad = 45.553093425 // rad/s

    // Simulation constants
    private val dt = 1 / 100.0 // Time step (seconds) - f=100 Hz

    // Derived Constants
    private val maxStroke = numSlides * slideStroke
    private val startingLength = slideLength - slideStroke
    private val maxLength = startingLength + maxStroke
    private val totalMass = payloadMass + (numSlides * slideMass)

    fun optimize(runs: Int): List<Double> {
        var window = startingWindow
        var currentValues = runTrials(minKp, maxKp, minKi, maxKi, minKd, maxKd, 200)

        var bestPidValues: List<Double> = listOf(0.0, 0.0, 0.0)
        for(i in 0 until runs) {
            val topFiveSets = runTrialSetReturnBest(window, currentValues)
            currentValues = topFiveSets
                .values
                .flatMap { it.entries }
                .associate { it.toPair() }

            val topPID = currentValues.keys.sorted()[0]
            bestPidValues = currentValues[topPID]!!
            window *= shrinkFactor
        }

        return bestPidValues
    }

    fun runTrialSetReturnBest(window: Double, values: Map<Double, List<Double>>): Map<Double, Map<Double, List<Double>>> {
        val setsOfValueSets = HashMap<Double, Map<Double, List<Double>>>() // {Average Cost, {cost, PID Values[P, I, D]}}
        for(cost in values.keys) {
            val pidVals = values[cost]
            val newValues = pidVals?.let {
                runTrials((it[0] - window).coerceAtLeast(minKp),
                    (it[0] + window).coerceAtMost(maxKp),
                    (it[1] - window).coerceAtLeast(minKi),
                    (it[1] + window).coerceAtMost(maxKi),
                    (it[2] - window).coerceAtLeast(minKi),
                    (it[2] + window).coerceAtMost(maxKd).coerceAtLeast(minKi),
                    200)
            }

            setsOfValueSets[newValues?.keys?.average() as Double] = newValues
        }

        val topFiveKeys = setsOfValueSets.keys.sorted().take(5)

        val topFiveMap: LinkedHashMap<Double, Map<Double, List<Double>>> = linkedMapOf()
        for (key in topFiveKeys) {
            setsOfValueSets[key]?.let { topFiveMap[key] = it }
        }

        return topFiveMap
    }

    fun runTrials(kPStart: Double, kPEnd: Double, kIStart: Double, kIEnd: Double, kDStart: Double, kDEnd: Double, trials: Int): Map<Double, List<Double>> = runBlocking {
        val deferredResults = mutableListOf<Deferred<Pair<Double, List<Double>>>>()

        for(i in 0 until trials) {
            val deferred = async(Dispatchers.Default) {
                val kP = Random.nextDouble(kPStart, kPEnd)
                val kI = Random.nextDouble(kIStart, kIEnd)
                val kD = Random.nextDouble(kDStart, kDEnd)

                val costs = ArrayList<Double>()
                for (j in 0..100) {
                    val startingPosition = Random.nextDouble(startingLength, maxLength/2)
                    val endingPosition = Random.nextDouble(maxLength/2, maxLength)

                    val cost = runSimulation(kP, kI, kD, startingPosition, endingPosition)
                    costs.add(cost)
                }

                costs.average() to listOf(kP, kI, kD)
            }

            deferredResults.add(deferred)
        }

        val results = deferredResults.awaitAll()

        val values = ConcurrentHashMap<Double, List<Double>>() // {cost, PID Values[P, I, D]}
        for((key, value) in results) {
            values[key] = value
        }

        HashMap(values)
    }

    fun runSimulation(kP: Double, kI: Double, kD: Double, startingPosition: Double, targetPosition: Double): Double {
        var totalCost = 0.0

        var stableStartTime = -1.0
        var stableReached = false

        var position = startingPosition
        var integral = 0.0
        var previousError = 0.0

        var velocity = 0.0

        val maxSteps = 1000
        for (i in 0..<maxSteps) {
            val time = i * dt

            // PID Control
            val error = targetPosition - position // m
            integral += error * dt
            val derivative = (error - previousError) / dt
            var output = (kP * error) + (kI * integral) + (kD * derivative)
            output = max(min(output, 1.0), -1.0)
            previousError = error

            // Physics stuff
            val currentW = velocity / radius * (if (output > 0) 1.0 else -1.0) // Current angular velocity of motor
            var torque = torqueStall * output * (1 - (currentW / wNoLoad)) // Motor torque from output
            torque = max(min(torque, torqueStall), -torqueStall) // Fix weird torques
            val force = torque / radius // N
            val acceleration = force / totalMass // m/s^2
            velocity += acceleration * dt // m/s
            position += velocity * dt // m

            val overshoot = if(position - targetPosition > 1) position - targetPosition else 0.0
            val settlingTime = if(stableReached) stableStartTime else time
            val costError = if(error > 1) error else 0.0
            totalCost += calculateCost(overshoot, settlingTime, costError)

            if (position > maxLength) { // Hardware limitation
                velocity = 0.0
                position = maxLength
            }

            // Check if within tolerance
            if (abs(error) < 0.01) {
                if (!stableReached) {
                    if (stableStartTime < 0) stableStartTime = time // first time in tolerance

                    if (time - stableStartTime >= 0.5) {
                        stableReached = true
                        stableStartTime = time
                    }
                }
            } else {
                // reset if error drifts out again
                stableStartTime = -1.0
            }

            // If stable reached and 2 sec passed, stop
            if (stableReached && (time - stableStartTime >= 0.5)) break
        }

        return totalCost
    }

    // Penalties
    val overshootPenalty = 50
    val settlingTimePenalty = 10
    val steadyStatePenalty = 20

    /**
     * Overshoot should be in percent from target; 0 if < 1cm
     * Steady State more than 1cm. Less than 1cm shouldn't have any affect
     */
    fun calculateCost(overshoot: Double, settlingTime: Double, error: Double): Double {
        return (overshoot * overshootPenalty) +
                (settlingTime * settlingTimePenalty) +
                (error * steadyStatePenalty)
    }
}