package com.lewis.troughcontroller;

import com.pi4j.io.gpio.digital.DigitalStateChangeEvent;
import com.pi4j.io.gpio.digital.DigitalStateChangeListener;

public class TroughFillStateListener implements DigitalStateChangeListener {
    
    private TroughController app = null;


    public TroughFillStateListener(TroughController app) {
        this.app = app;
    }

    @Override
    public void onDigitalStateChange(DigitalStateChangeEvent event) {
        if (event.state().isHigh()) { // trough is empty
            app.troughEmpty();
        } else {
            app.troughFull();         
        }
    }

}
