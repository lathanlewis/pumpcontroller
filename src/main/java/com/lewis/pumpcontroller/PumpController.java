package com.lewis.pumpcontroller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalOutput;

/**
 * 
 *
 */
public class PumpController 
{
    private static final Logger logger = LogManager.getLogger(PumpController.class);


    private ScheduledExecutorService scheduler =  null;
    private Connection conn = null;

    DigitalInput pumpRunState = null;
    DigitalOutput pumpPowerSupply = null;

    private int maxRunTimeMins;
    private int max2HourRunTimeMins;


    // We employ a simple algorithm for detecting leaks. Each second the pump is running, we
    // increment our counter. Each second the pump is off, we decrement the counter down to a
    // floor of 0. If the counter exceeds our configured threshold it means
    private int pumpRuntimeTracker;

    private ScheduledFuture maxRunTimeTask = null;

    public PumpController(Connection conn, int maxRunTimeMins, int max2HourRunTimeMins, DigitalInput pumpRunState, DigitalOutput pumpPowerSupply) {
        this.conn = conn;
        this.maxRunTimeMins = max2HourRunTimeMins;
        this.max2HourRunTimeMins = max2HourRunTimeMins;
        this.pumpRunState = pumpRunState;
        this.pumpPowerSupply = pumpPowerSupply;

        scheduler = Executors.newScheduledThreadPool(2);

        // Add a listener to the pump run state input
        pumpRunState.addListener(new PumpRunStateListener(this));

        // init well pump to on (contactor is NC so set pin to low)
        pumpPowerSupply.low();
    }

    protected void pumpOn() {
        logger.info("Well pump is running");

        // Write event to our DB
        try {
            PreparedStatement insertEvent = conn.prepareStatement("INSERT INTO events(eventSource, eventValue) VALUES(?,?)");
            insertEvent.setObject(1, "pump",java.sql.Types.OTHER);
            insertEvent.setObject(2, true, java.sql.Types.OTHER);

            insertEvent.executeUpdate();
            insertEvent.close();
        } catch (SQLException e) {
            logger.error("Database error: " + e.getMessage());
        }

        // consider a circular linked list holding boolean values with a 1 second timer to set
        // to on/off based on pump state over 2 hour period, or just increment/decrement a counter
        // each second and compare to threshold (basically leaky bucket?)

        // Start a timer. If it expires we will disable the pump.
        // TODO: consider just a counter to incr/decr each second per above, shutdown if > threshold
        Runnable task = () -> {
            try {
                maxRunTimeThresholdReached();
            } catch (Exception e) {
                System.err.println("Error executing maxRunTimeThresholdReached: " + e.getMessage());
            }
        };

        maxRunTimeTask = scheduler.schedule(task, maxRunTimeMins, TimeUnit.MINUTES);
    }

    protected void pumpOff() {
        logger.info("Well pump is off");

        // Cancel our timer
        if (maxRunTimeTask != null) {
            maxRunTimeTask.cancel(true);
        }

        try {
            PreparedStatement insertEvent = conn.prepareStatement("INSERT INTO events(eventSource, eventValue) VALUES(?,?)");
            insertEvent.setObject(1, "pump",java.sql.Types.OTHER);
            insertEvent.setObject(2, false, java.sql.Types.OTHER);

            insertEvent.executeUpdate();
            insertEvent.close();
        } catch (SQLException e) {
            logger.error("Database error: " + e.getMessage());
        }
    }

    public void maxRunTimeThresholdReached() {
        logger.warn("Well pump has been running longer than: " + maxRunTimeMins + " mins. Locking out.");
        pumpPowerSupply.high();
    }
}
