package tech.bedson

import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartPanel
import org.jfree.chart.ChartUtils
import org.jfree.chart.plot.PlotOrientation
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer
import org.jfree.data.xy.XYSeriesCollection
import java.awt.Color
import java.awt.GridLayout
import java.io.File
import java.io.IOException
import javax.swing.JFrame
import javax.swing.JPanel

fun main(args: Array<String>) {
    val showCharts = args.size > 1 && args[1] == "showCharts"

    if (args.isNotEmpty()) {
        when(args[0]) {
            "horizontalSlide" -> {
                val simulator = HorizontalSlideSim(10.0, 0.0, 0.5)
                val results = simulator.runSimulation(0.100, 0.220, true)
                chartData(results.positionDataset(), results.pidDataset(), !showCharts)
            }
            "verticalSlide" -> {
                val simulator = VerticalSlideSim(10.0, 0.0, 0.5)
                simulator.runSimulation(0.100, 0.220, true)
            }
            "autoTuneHorizontalSlide" -> {
                val tuner = AutoTuneHorizontalSlideSim()
                val pidVals = tuner.optimize(20)
                println("Best values were found: kP ${pidVals[0]} kI ${pidVals[1]} kD ${pidVals[2]}")
                val simulator = HorizontalSlideSim(pidVals[0], pidVals[1], pidVals[2])
                val results = simulator.runSimulation(0.100, 0.200, true)
                chartData(results.positionDataset(), results.pidDataset(), !showCharts)
            }
            else -> sendHelpMessage()
        }
    } else sendHelpMessage()
}

fun chartData(positionDataset: XYSeriesCollection?, pidDataset: XYSeriesCollection?, saveToFile: Boolean = true) {
    val positionChart = ChartFactory.createXYLineChart(
        "Position vs Time",
        "Time (s)",
        "Position (m)",
        positionDataset,
        PlotOrientation.VERTICAL,
        true, true, false
    )

    val pidChart = ChartFactory.createXYLineChart(
        "PID vs Time",
        "Time (s)",
        "Value",
        pidDataset,
        PlotOrientation.VERTICAL,
        true, true, false
    )

    // Set colors and hide shapes for position chart
    val posPlot = positionChart.getXYPlot()
    val posRenderer = XYLineAndShapeRenderer()
    posRenderer.setSeriesPaint(0, Color.RED)
    posRenderer.setSeriesPaint(1, Color.BLUE)
    posRenderer.setSeriesPaint(2, Color.BLACK)
    posRenderer.setSeriesShapesVisible(0, false)
    posRenderer.setSeriesShapesVisible(1, false)
    posRenderer.setSeriesShapesVisible(2, false)
    posPlot.renderer = posRenderer

    // Set colors and hide shapes for PID chart
    val pidPlot = pidChart.getXYPlot()
    val pidRenderer = XYLineAndShapeRenderer()
    pidRenderer.setSeriesPaint(0, Color.RED)
    pidRenderer.setSeriesPaint(1, Color.BLUE)
    pidRenderer.setSeriesPaint(2, Color.BLACK)
    pidRenderer.setSeriesShapesVisible(0, false)
    pidRenderer.setSeriesShapesVisible(1, false)
    pidRenderer.setSeriesShapesVisible(2, false)
    pidPlot.renderer = pidRenderer

    if (!saveToFile) {
        val frame = JFrame("MiSUMi Slide Simulator")
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)

        val panel = JPanel(GridLayout(1, 2))
        panel.add(ChartPanel(positionChart))
        panel.add(ChartPanel(pidChart))

        frame.contentPane = panel
        frame.pack()
        frame.isVisible = true
    } else {
        try {
            ChartUtils.saveChartAsPNG(File("positionChart.png"), positionChart, 800, 600)
            ChartUtils.saveChartAsPNG(File("pidChart.png"), pidChart, 800, 600)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
}

fun sendHelpMessage() {
    println("This application requires at least one argument. Pick from the following")
    println("  - horizontalSlide")
    println("  - verticalSlide")
    println("  - autoTuneHorizontalSlide")
}