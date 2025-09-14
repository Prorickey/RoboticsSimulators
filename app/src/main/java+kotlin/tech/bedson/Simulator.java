package tech.bedson;

import org.jfree.data.xy.XYSeriesCollection;

public abstract class Simulator {

    protected double kP;
    protected double kI;
    protected double kD;

    public Simulator(double kP, double kI, double kD) {
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
    }

    public record SimulationResults(double totalCost, XYSeriesCollection positionDataset, XYSeriesCollection pidDataset) { }

    public abstract SimulationResults runSimulation(double startingPosition, double targetPosition, boolean debug);
}
