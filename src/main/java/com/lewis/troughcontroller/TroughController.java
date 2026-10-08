package com.lewis.troughcontroller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lewis.WaterApp;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalOutput;

/**
 * 
 *
 */
public class TroughController 
{
    private static final Logger logger = LogManager.getLogger(TroughController.class);

    private Connection conn = null;
    private DigitalInput troughFillState = null;
    private DigitalOutput troughValve = null;
    private LocalDateTime lastValveOpenTS = null;
    private String name = null;
    private int maxFillTimeMins = 0;
    private int minBetweenFillsMins = 0;
    private boolean valveLockout = false;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private ScheduledFuture maxFillTimeTimer = null;
    private ScheduledFuture delayFillTimer = null;

    public TroughController(String name, int maxFillTimeMins, int minBetweenFillsMins, Connection conn, DigitalInput troughFillState, DigitalOutput troughValve) {
        this.name = name;
        this.maxFillTimeMins = maxFillTimeMins;
        this.minBetweenFillsMins = minBetweenFillsMins;
        this.conn = conn;
        this.troughFillState = troughFillState;
        this.troughValve = troughValve;

        // Add listeners to our trough fill state inputs
        this.troughFillState.addListener(new TroughFillStateListener(this));

        // If the trough is empty, go ahead and fill it up. Otherwise default our valve to off.
        if (this.troughFillState.isHigh()) {
            troughEmpty();
        } else {
                // high low is reversed on this relay
            logger.info("Trough " + name + " is full on initialization. Defaulting valve to off.");
            troughValve.high();
        }

    }

    protected void troughEmpty() {
        // Make sure we haven't locked out the valve due to an error condition first.
        if (! valveLockout) {
            logger.info("Trough " + name + " is empty.");

            // We check to see how long it has been since we last filled the trough. We enforce a delay period so that we don't
            // try to keep refilling it in case there is a problem with the float switch
            LocalDateTime currentTime = LocalDateTime.now();

            long elapsedMinutes = Long.MAX_VALUE;
            if (lastValveOpenTS != null) {
                elapsedMinutes = ChronoUnit.MINUTES.between(lastValveOpenTS, currentTime);
            }

            if (elapsedMinutes >= minBetweenFillsMins) {
                logger.info("Turning on valve for trough: " + name);

                // high low is reversed on this relay
                troughValve.low();

                lastValveOpenTS = LocalDateTime.now();

                // Tell the android app
                WaterApp.sendPushNotification("trough " + name, "on");

                // Set a timer to turn off the valve if we run too long (i.e. float switch damaged)
                Runnable task = () -> {
                    try {
                        // We've set the max fill time based on worst case filling of the trough plus a buffer. If we've
                        // exceeded it then we can be pretty sure the float switch is messed up. We therefore lock
                        // out the fill valve and send an alert so someone can fix it.
                        logger.warn("Trough has been filling for too long. Locking out trough: " + name);
                        valveLockout = true;

                        // high low is reversed on this relay
                        troughValve.high();

                        WaterApp.sendPushNotification("trough " + name, "off");

                        WaterApp.sendPushNotification("error" + name, "Trough " + name + " filling too long. Locked out");
                    } catch (Exception e) {
                        logger.error("Error executing task: " + e.getMessage());
                    }
                };
                maxFillTimeTimer = scheduler.schedule(task, maxFillTimeMins, TimeUnit.MINUTES);


                // Log the event
                try {
                    PreparedStatement insertEvent = conn.prepareStatement("INSERT INTO events(eventSource, eventValue) VALUES(?,?)");
                    insertEvent.setObject(1, "trough-" + name, java.sql.Types.OTHER);
                    insertEvent.setObject(2, false, java.sql.Types.OTHER);

                    insertEvent.executeUpdate();
                    insertEvent.close();
                } catch (SQLException e) {
                    logger.error("Database error: " + e.getMessage());
                }
            } else {
                // Shouldn't need to refill again so soon so expect an error case. Notify of the issue but go ahead
                // and set a timer to turn back on once the minimum time has passed in case it is not an error. 
                Runnable task = () -> {
                    try {
                        if (troughFillState.isHigh()) {
                            logger.info("Opening fill valve based on delayed fill timer: " + name);

                            // high low is reversed on this relay
                            troughValve.low();

                            WaterApp.sendPushNotification("trough " + name, "on");
                        }
                    } catch (Exception e) {
                        logger.error("Error executing task: " + e.getMessage());
                    }
                };
                long delayTime = minBetweenFillsMins - elapsedMinutes;
                delayFillTimer = scheduler.schedule(task, delayTime, TimeUnit.MINUTES);

                logger.warn("Trough " + name + " reporting empty but we just refilled recently. Delaying " + delayTime + " mins.");
                WaterApp.sendPushNotification("error" + name, "Trough " + name + " reporting empty but we just refilled recently. Delaying " + delayTime + " mins.");

            }
        }
    }

    protected void troughFull() {
        logger.info("Trough " + name + " is full. Turning off valve.");

        // high low is reversed on this relay
        troughValve.high();

        WaterApp.sendPushNotification("trough " + name, "off");


        // Cancel any fill timers we have running
        if (maxFillTimeTimer != null) {
            maxFillTimeTimer.cancel(true);
        }
        if (delayFillTimer != null) {
            delayFillTimer.cancel(true);
        }


        try {
            PreparedStatement insertEvent = conn.prepareStatement("INSERT INTO events(eventSource, eventValue) VALUES(?,?)");
            insertEvent.setObject(1, "trough-" + name, java.sql.Types.OTHER);
            insertEvent.setObject(2, true, java.sql.Types.OTHER);

            insertEvent.executeUpdate();
            insertEvent.close();
        } catch (SQLException e) {
            logger.error("Database error: " + e.getMessage());
        }
    }

}
