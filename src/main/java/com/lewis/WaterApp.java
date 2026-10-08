package com.lewis;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.lewis.pumpcontroller.PumpController;
import com.lewis.troughcontroller.TroughController;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.io.gpio.digital.PullResistance;

/**
 * 
 *
 */
public class WaterApp 
{
    private static final Logger logger = LogManager.getLogger(WaterApp.class);

    private static final String FIREBASE_TOPIC = "pump-controller-updates";

    private static final String PG_URL = "jdbc:postgresql://localhost:5432/pumpdb";
    private static final String PG_USER = "postgres";
    private static       String PG_PWD  = null;

    // Pump monitoring and control
    // Pin positions relative to board with pin bank on top right corner
    private static final int PIN_PUMP_POWER_SUPPLY  = 27; // GPIO Pin 27 (Physical Pin 13, Col 1, 7 down)
    private static final int PIN_PUMP_RUN_STATE     = 22; // GPIO Pin 22 (Physical Pin 15, Col 1, 8 down))

    // Water trough monitoring and control
    private static final String TROUGH_1_NAME = "upper";
    private static final int PIN_TROUGH1_FILL_STATE = 23; // GPIO Pin 23 (Physical Pin 16, Col 2, 8 down)
    private static final int PIN_TROUGH1_VALVE      = 25; // GPIO Pin 25 (Physical Pin 22, Col 2, 11 down)

    private static final String TROUGH_2_NAME = "lower";
    private static final int PIN_TROUGH2_FILL_STATE = 24; // GPIO Pin 24 (Physical Pin 18, Col 2, 9 down)
    private static final int PIN_TROUGH2_VALVE      = 16; // GPIO Pin 16 (Physical Pin 36, col 2, 3rd from bottom)


    // Config read from DB
    private int maxRunTimeMins;
    private int max2HourRunTimeMins;
    private int maxFillTimeMins;
    private int minBetweenFillsMins;

    private Connection conn = null;

    private DigitalOutput pumpPowerSupply = null;
    private DigitalInput pumpRunState = null;
    private DigitalInput trough1FillState = null;
    private DigitalOutput trough1Valve = null;
    private DigitalInput trough2FillState = null;
    private DigitalOutput trough2Valve = null;


    // We employ a simple algorithm for detecting leaks. Each second the pump is running, we
    // increment our counter. Each second the pump is off, we decrement the counter down to a
    // floor of 0. If the counter exceeds our configured threshold it means
    private int pumpRuntimeTracker;

    private ScheduledExecutorService scheduler =  null;
    private ScheduledFuture maxRunTimeTask = null;

    static {
        PG_PWD = System.getenv("PG_PWD");
    }

    public WaterApp() {
        // initialize logging level for pi4j
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "INFO");

        scheduler = Executors.newScheduledThreadPool(2);


        // init conn to local postgres DB
        try  {
           conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PWD);
        } catch (SQLException e) {
           logger.error("Database connection error: " + e.getMessage());
        }

        readConfig();

        Context pi4j = Pi4J.newAutoContext();

        // Create our Pump inputs and outputs
        pumpPowerSupply = pi4j.create(DigitalOutput.newConfigBuilder(pi4j)
                .id("pumpPowerSupply")
                .address(PIN_PUMP_POWER_SUPPLY) 
                .provider("gpiod-digital-output")
                .build());

        pumpRunState = pi4j.create(DigitalInput.newConfigBuilder(pi4j)
                .id("pumpRunState")
                .address(PIN_PUMP_RUN_STATE) 
                .pull(PullResistance.PULL_DOWN) // Use internal pull-down resistor
                .debounce(Long.valueOf(500000)) // 500ms
                .provider("gpiod-digital-input")
                .build());

        // Create our Water Trough inputs and outputs
        trough1FillState = pi4j.create(DigitalInput.newConfigBuilder(pi4j)
                .id("trough1FillState")
                .address(PIN_TROUGH1_FILL_STATE) 
                .pull(PullResistance.PULL_DOWN) // No signal means trough is full
                .debounce(Long.valueOf(500000)) // 500ms
                .provider("gpiod-digital-input")
                .build());

        trough1Valve = pi4j.create(DigitalOutput.newConfigBuilder(pi4j)
                .id("trough1Valve")
                .address(PIN_TROUGH1_VALVE) 
                .provider("gpiod-digital-output")
                .initial(DigitalState.HIGH) // High means the relay is off
                .build());

        trough2FillState = pi4j.create(DigitalInput.newConfigBuilder(pi4j)
                .id("trough2FillState")
                .address(PIN_TROUGH2_FILL_STATE) 
                .pull(PullResistance.PULL_DOWN) // No signal means trough is full
                .debounce(Long.valueOf(500000)) // 500ms
                .provider("gpiod-digital-input")
                .build());

        trough2Valve = pi4j.create(DigitalOutput.newConfigBuilder(pi4j)
                .id("trough2Valve")
                .address(PIN_TROUGH2_VALVE) 
                .provider("gpiod-digital-output")
                .initial(DigitalState.HIGH) // High means the relay is off
                .build());

        //Init firebase messaging to send messages to android app
        try (InputStream serviceAccount = getClass().getResourceAsStream("/pumpcontroller-android-firebase-adminsdk-fbsvc-30b066edbc.json")) {
            if (serviceAccount == null) {
                throw new IllegalArgumentException("Resource file not found!");
            }
            
            FirebaseOptions options = new FirebaseOptions.Builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build();

            FirebaseApp.initializeApp(options);
        } catch (IOException e) {
            e.printStackTrace();
        }


        new PumpController(conn, maxRunTimeMins, max2HourRunTimeMins, pumpRunState, pumpPowerSupply);
        new TroughController(TROUGH_1_NAME, maxFillTimeMins, minBetweenFillsMins, conn, trough1FillState, trough1Valve);
        new TroughController(TROUGH_2_NAME, maxFillTimeMins, minBetweenFillsMins, conn, trough2FillState, trough2Valve);
        

         // Keep us running indefinitely
        try {
            while (true) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {}

        scheduler.shutdown();
        pi4j.shutdown();
    }

    public static void sendPushNotification(String name, String state) {
        // Step 1: Build the notification visual content
        Notification notification = Notification.builder()
                .setTitle(name)
                .setBody(state)
                .build();

        // Step 2: Build the core message payload
        Message message = Message.builder()
                .setTopic(FIREBASE_TOPIC)
                .setNotification(notification)
                // .putData("click_action", "FLUTTER_NOTIFICATION_CLICK") // Optional custom data
                // .putData("customKey", "customValue")                     // Optional custom data
                //.setToken("cSxj3qcSTpu1XrBHNTQj29:APA91bG6a9vi6ivURJpCsiFumJrYlcRfsz-EIaXGauddH8QCQI5uPj-bCTwhNeICDRrHP_kOdFJBbF9w9YIdaSFCH50kLiEp7efq-BCx6yLvWFHA0Uuo35Y") // Target device registration token
                .build();

        try {
            // Step 3: Send the message via Firebase Messaging
            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("Successfully sent message: " + response);
        } catch (FirebaseMessagingException e) {
            System.err.println("Error sending FCM message: " + e.getMessage());
        }
    }


    private void readConfig() {
        String sqlQuery = "SELECT value FROM config WHERE param = ?";

        try {
            PreparedStatement pstmt = conn.prepareStatement(sqlQuery);

        
            pstmt.setString(1, "MAX_RUN_TIME_MINS");
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                maxRunTimeMins = Integer.parseInt(rs.getString("value")); 

                logger.warn("MAX_RUN_TIME_MINS: " + maxRunTimeMins);
            } else {
                logger.warn("No MAX_RUN_TIME_MINS");
            }

            pstmt.setString(1, "MAX_2_HOUR_RUN_TIME_MINS");
            rs = pstmt.executeQuery();

            if (rs.next()) {
                max2HourRunTimeMins = Integer.parseInt(rs.getString("value")); 

                logger.warn("MAX_2_HOUR_RUN_TIME_MINS: " + max2HourRunTimeMins);
            } else {
                logger.warn("No MAX_2_HOUR_RUN_TIME_MINS");
            }

            pstmt.setString(1, "MAX_FILL_TIME_MINS");
            rs = pstmt.executeQuery();

            if (rs.next()) {
                maxFillTimeMins = Integer.parseInt(rs.getString("value")); 

                logger.warn("MAX_FILL_TIME_MINS: " + maxFillTimeMins);
            } else {
                logger.warn("No MAX_FILL_TIME_MINS");
            }

            pstmt.setString(1, "MIN_BETWEEN_FILLS_MINS");
            rs = pstmt.executeQuery();

            if (rs.next()) {
                minBetweenFillsMins = Integer.parseInt(rs.getString("value")); 

                logger.warn("MIN_BETWEEN_FILLS_MINS: " + minBetweenFillsMins);
            } else {
                logger.warn("No MIN_BETWEEN_FILLS_MINS");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public int getMaxRunTimeMins() {
        return maxRunTimeMins;
    }

    public int getMax2HourRunTimeMins() {
        return max2HourRunTimeMins;
    }


    public static void main( String[] args )
    {
        new WaterApp();
    }
}
