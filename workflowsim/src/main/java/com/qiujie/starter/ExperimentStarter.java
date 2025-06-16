package com.qiujie.starter;

import ch.qos.logback.classic.Level;
import com.qiujie.aop.ClockModifier;
import com.qiujie.enums.LevelEnum;
import com.qiujie.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import static com.qiujie.Constants.*;

public abstract class ExperimentStarter {


    public long seed;
    public final String name;
    private final Logger log;
    private static Level level;


    public ExperimentStarter() {
        this.name = getClass().getSimpleName();
        System.setProperty("startup.class", name);
        this.log = LoggerFactory.getLogger(ExperimentStarter.class);
        STARTUP = MarkerFactory.getMarker(LevelEnum.STARTUP.name());
        start();
    }


    private void start() {
        ClockModifier.modifyClockMethod();
        org.cloudbus.cloudsim.Log.disable();
        Log.setLevel(level);
        log.info(STARTUP, "{}: Starting...", name);
        this.seed = System.currentTimeMillis();
        try {
            run();
            log.info(STARTUP, String.format("%s: Running %.2fs\n", name, (System.currentTimeMillis() - seed) / 1000.0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public abstract void run() throws Exception;

    public static void startExperiment() {
        startExperiment(Level.DEBUG);
    }

    /**
     * start experiment and set log level
     *
     * @param l log level
     */
    public static void startExperiment(Level l) {
        level = l;
        try {
            String className = Thread.currentThread().getStackTrace()[2].getClassName();
            Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
