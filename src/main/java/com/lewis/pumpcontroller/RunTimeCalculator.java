package com.lewis.pumpcontroller;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates total energy usage for a given time period based on start and stop timestamps.
 */
public class RunTimeCalculator {
    
    /**
     * Represents a single usage period with start and stop timestamps
     */
    public static class UsagePeriod {
        private final Instant start;
        private final Instant stop;
        
        public UsagePeriod(Instant start, Instant stop) {
            if (start.isAfter(stop)) {
                throw new IllegalArgumentException("Start time must be before or equal to stop time");
            }
            this.start = start;
            this.stop = stop;
        }
        
        public Instant getStart() {
            return start;
        }
        
        public Instant getStop() {
            return stop;
        }
        
        public Duration getDuration() {
            return Duration.between(start, stop);
        }
    }
    
    /**
     * Calculates total energy usage in kilowatt-hours (kWh)
     * 
     * @param periods List of usage periods with start/stop timestamps
     * @param powerRatingKW Power rating of the device in kilowatts
     * @return Total energy usage in kWh
     */
    public static double calculateTotalEnergyUsage(List<UsagePeriod> periods, double powerRatingKW) {
        if (powerRatingKW < 0) {
            throw new IllegalArgumentException("Power rating must be non-negative");
        }
        
        double totalHours = 0.0;
        
        for (UsagePeriod period : periods) {
            Duration duration = period.getDuration();
            double hours = duration.toMillis() / 1000.0 / 3600.0; // Convert milliseconds to hours
            totalHours += hours;
        }
        
        return totalHours * powerRatingKW;
    }
    
    /**
     * Calculates total runtime duration across all periods
     * 
     * @param periods List of usage periods
     * @return Total duration as a Duration object
     */
    public static Duration calculateTotalDuration(List<UsagePeriod> periods) {
        Duration total = Duration.ZERO;
        
        for (UsagePeriod period : periods) {
            total = total.plus(period.getDuration());
        }
        
        return total;
    }
    
    /**
     * Formats duration as a human-readable string
     */
    public static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        
        return String.format("%d hours, %d minutes, %d seconds", hours, minutes, seconds);
    }
    
    // Example usage
    public static void main(String[] args) {
        // Create a list of usage periods
        List<UsagePeriod> periods = new ArrayList<>();
        
        // Example: Device was on from 8 AM to 10 AM
        Instant start1 = Instant.parse("2024-02-13T08:00:00Z");
        Instant stop1 = Instant.parse("2024-02-13T10:00:00Z");
        periods.add(new UsagePeriod(start1, stop1));
        
        // Device was on again from 2 PM to 6 PM
        Instant start2 = Instant.parse("2024-02-13T14:00:00Z");
        Instant stop2 = Instant.parse("2024-02-13T18:00:00Z");
        periods.add(new UsagePeriod(start2, stop2));
        
        // Device was on from 8 PM to 10:30 PM
        Instant start3 = Instant.parse("2024-02-13T20:00:00Z");
        Instant stop3 = Instant.parse("2024-02-13T22:30:00Z");
        periods.add(new UsagePeriod(start3, stop3));
        
        // Calculate energy usage for a 1.5 kW device
        double powerRatingKW = 1.5;
        double totalEnergyKWh = calculateTotalEnergyUsage(periods, powerRatingKW);
        Duration totalDuration = calculateTotalDuration(periods);
        
        // Display results
        System.out.println("=== Energy Usage Report ===");
        System.out.println("Number of usage periods: " + periods.size());
        System.out.println("Power rating: " + powerRatingKW + " kW");
        System.out.println("Total runtime: " + formatDuration(totalDuration));
        System.out.println("Total energy consumed: " + String.format("%.2f", totalEnergyKWh) + " kWh");
        System.out.println();
        
        // Show individual periods
        System.out.println("Individual periods:");
        for (int i = 0; i < periods.size(); i++) {
            UsagePeriod period = periods.get(i);
            double periodEnergyKWh = period.getDuration().toMillis() / 1000.0 / 3600.0 * powerRatingKW;
            System.out.printf("Period %d: %s to %s (%.2f kWh)%n", 
                i + 1, 
                period.getStart(), 
                period.getStop(), 
                periodEnergyKWh);
        }
    }
}