package com.lewis.pumpcontroller;

import com.pi4j.io.gpio.digital.DigitalStateChangeEvent;
import com.pi4j.io.gpio.digital.DigitalStateChangeListener;

public class PumpRunStateListener implements DigitalStateChangeListener {
    
    PumpController app = null;

    public PumpRunStateListener(PumpController app) {
        this.app = app;
    }

    @Override
    public void onDigitalStateChange(DigitalStateChangeEvent event) {
        if (event.state().isHigh()) { // Pump is running
            app.pumpOn();
        } else {
            app.pumpOff();         
        }
    }

}
