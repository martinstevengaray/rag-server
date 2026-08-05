package com.mgaray.ragserver.server;

import com.mgaray.ragserver.logger.ILogger;
import com.mgaray.ragserver.logger.Logger;

import java.time.Duration;

public class Timer {

    private final ILogger logger;
    private long tick;

    public Timer() {
        this(new Logger());
    }

    public Timer(ILogger logger) {
        this.logger = logger;
        tick = System.currentTimeMillis();
    }

    public void reset() {
        tick = System.currentTimeMillis();
    }

    public void snap(String message) {
        Duration duration = Duration.ofMillis(System.currentTimeMillis() - tick);
        String elapsedTime = String.format("%02d:%02d:%02d:%03d", duration.toHours(), duration.toMinutesPart(),
                duration.toSecondsPart(), duration.toMillisPart());
        logger.log("TIMER: " + message + ": " + elapsedTime);
        tick = System.currentTimeMillis();
    }

}
