package tech.bedson

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class AutoTuneHorizontalSlideSim {
    // Tuner Configuration
    private val startingWindow = 25.0
    private val shrinkFactor = 0.90
    private val simsPerTrial = 500

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

    // Derived Constants
    private val maxStroke = numSlides * slideStroke
    private val startingLength = slideLength - slideStroke
    private val maxLength = startingLength + maxStroke

    private var totalTrialSets = (5*simsPerTrial)
    private var trialSetNum = 0
    private val barLength = 50

    fun incrementTrialSetCount() {
        trialSetNum++
        val percent = trialSetNum.toDouble() / totalTrialSets
        val filled = (percent * barLength).toInt()
        val bar = "=".repeat(filled) + " ".repeat(barLength - filled)
        print("\r[$bar] ${trialSetNum}/${totalTrialSets} ${(percent * 100).toInt()}%")
    }

    fun optimize(runs: Int): List<Double> {
        totalTrialSets *= runs
        totalTrialSets++
        var window = startingWindow
        var currentValues = runTrials(minKp, maxKp, minKi, maxKi, minKd, maxKd, simsPerTrial)
        incrementTrialSetCount()

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
                    simsPerTrial)
            }

            setsOfValueSets[newValues?.keys?.average() as Double] = newValues
            incrementTrialSetCount()
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

                    val simulator = HorizontalSlideSim(kP, kI, kD)
                    val cost = simulator.runSimulation(startingPosition, endingPosition, false).totalCost()
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
}