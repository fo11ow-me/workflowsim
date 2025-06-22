package com.qiujie.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import com.qiujie.Constants;
import com.qiujie.enums.LevelEnum;
import org.slf4j.Marker;

public class LogMarkerFilter extends TurboFilter {

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level,
                              String format, Object[] params, Throwable t) {
        if (marker == null) {
            return FilterReply.NEUTRAL;
        }

        String markerName = marker.getName();

        if (LevelEnum.STARTUP_SIM.name().equals(markerName)) {
            return Constants.ENABLE_STARTUP_SIM ? FilterReply.ACCEPT : FilterReply.DENY;
        }

        if (LevelEnum.RESULT_SIM.name().equals(markerName)) {
            return Constants.ENABLE_RESULT_SIM ? FilterReply.ACCEPT : FilterReply.DENY;
        }

        if (LevelEnum.STARTUP_EXPERIMENT.name().equals(markerName) || LevelEnum.RESULT_EXPERIMENT.name().equals(markerName)) {
            return FilterReply.ACCEPT;
        }

        return FilterReply.NEUTRAL;
    }
}

