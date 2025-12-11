package com.lewis;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.PullResistance;

/**
 * 
 *
 */
public class PumpControllerApp 
{
    private static final int PIN_PUMP_POWER_SUPPLY = 24; // BCM Pin 22 (Physical Pin 15)
    private static final int PIN_PUMP_RUN_STATE = 22; // BCM Pin 27 (Physical Pin 13)

    DigitalOutput pumpPowerSupply = null;
    DigitalInput pumpRunState = null;

    public PumpControllerApp() {
        // initialize logging level for pi4j
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "INFO");

        Context pi4j = Pi4J.newAutoContext();

        // Create our inputs and outputs
        pumpPowerSupply = pi4j.create(DigitalOutput.newConfigBuilder(pi4j)
                .id("pumpPowerSupply")
                .address(PIN_PUMP_POWER_SUPPLY) 
                .build());

        pumpRunState = pi4j.create(DigitalInput.newConfigBuilder(pi4j)
                .id("pumpRunState")
                .address(PIN_PUMP_RUN_STATE) 
                .pull(PullResistance.PULL_DOWN) // Use internal pull-down resistor
                .build());

        // Add a listener to the pump run state input
        pumpRunState.addListener(new PumpRunStateListener(this));

        // init well pump to on
        pumpPowerSupply.high();

         // Keep us running indefinitely
        try {
            while (true) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {}

        pi4j.shutdown();
    }

    public void runTimeThresholdReached() {

    }


    public static void main( String[] args )
    {
        new PumpControllerApp();
    }
}
