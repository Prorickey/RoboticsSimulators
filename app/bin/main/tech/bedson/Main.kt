package tech.bedson

fun main(args: Array<String>) {

    val showCharts = args.size > 1 && args[1] == "showCharts"

    if (args.isNotEmpty()) {
        when(args[0]) {
            "horizontalSlide" -> {
                val simulator = HorizontalSlideSim(10.0, 0.0, 0.5, !showCharts)
                simulator.runSimulation(0.100, 0.220)
            }
            "verticalSlide" -> {
                val simulator = VerticalSlideSim()
                simulator.runSimulation(0.100, 0.220)
            }
            "autoTuneHorizontalSlide" -> {
                val tuner = AutoTuneHorizontalSlideSim()
                val pidVals = tuner.optimize(10)
                println("Best values were found: kP ${pidVals[0]} kI ${pidVals[1]} kD ${pidVals[2]}")
                val simulator = HorizontalSlideSim(pidVals[0], pidVals[1], pidVals[2], !showCharts)
                simulator.runSimulation(0.100, 0.200)
            }
            else -> sendHelpMessage()
        }
    } else sendHelpMessage()
}

fun sendHelpMessage() {
    println("This application requires at least one argument. Pick from the following")
    println("  - horizontalSlide")
    println("  - verticalSlide")
    println("  - autoTuneHorizontalSlide")
}