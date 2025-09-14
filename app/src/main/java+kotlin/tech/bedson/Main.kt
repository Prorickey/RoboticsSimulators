package tech.bedson

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        when(args[0]) {
            "horizontalSlide" -> {
                val simulator = HorizontalSlideSim()
                simulator.runSimulation(0.100, 0.220)
            }
            "verticalSlide" -> {
                val simulator = VerticalSlideSim()
                simulator.runSimulation(0.100, 0.220)
            }
            else -> sendHelpMessage()
        }
    } else sendHelpMessage()
}

fun sendHelpMessage() {
    println("This application requires at least one argument. Pick from the following")
    println("  - horizontalSlide")
    println("  - verticalSlide")
}