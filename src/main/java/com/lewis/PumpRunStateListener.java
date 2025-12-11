package com.lewis;

import com.pi4j.io.gpio.digital.DigitalStateChangeEvent;
import com.pi4j.io.gpio.digital.DigitalStateChangeListener;

public class PumpRunStateListener implements DigitalStateChangeListener {
    
    PumpControllerApp app = null;

    public PumpRunStateListener(PumpControllerApp app) {
        this.app = app;
    }

    @Override
    public void onDigitalStateChange(DigitalStateChangeEvent event) {
        if (event.state().isHigh()) { // Pump is running
            System.out.println("Pump is running");
        } else {
            System.out.println("Pump is off");
        }
    }

}
