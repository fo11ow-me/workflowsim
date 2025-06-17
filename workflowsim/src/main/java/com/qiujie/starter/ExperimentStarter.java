package com.qiujie.starter;

import com.qiujie.aop.ClockModifier;
import com.qiujie.enums.LevelEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public abstract class ExperimentStarter {
    public long seed;
    public final String name;
    private final Logger log;

    private final Marker BOOT;

    public ExperimentStarter() {
        this.name = getClass().getSimpleName();
        System.setProperty("startup.class", name);
        this.log = LoggerFactory.getLogger(ExperimentStarter.class);
        this.BOOT = MarkerFactory.getMarker(LevelEnum.BOOT.name());
        start();
    }

    private void start() {
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        log.info(BOOT, "{}: Starting...", name);
        this.seed = System.currentTimeMillis();
        try {
            run();
            log.info(BOOT, String.format("%s: Running %.2fs\n", name, (System.currentTimeMillis() - seed) / 1000.0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public abstract void run() throws Exception;


}
