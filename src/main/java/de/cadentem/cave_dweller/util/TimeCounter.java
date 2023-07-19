package de.cadentem.cave_dweller.util;

public class TimeCounter implements ITimeCounter {
    public int counter = 0;
    public int limit;

    public TimeCounter() {
        this.rollLimit();
    }

    public void incrementCounter() {
        ++this.counter;

        if (this.counter < 0) {
            this.resetCounter();
        }
    }

    public void resetCounter() {
        this.counter = 0;
    }

    public void rollLimit() {
    }
}