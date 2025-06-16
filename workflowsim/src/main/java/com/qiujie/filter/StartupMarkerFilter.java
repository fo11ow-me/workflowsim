package com.qiujie.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import com.qiujie.Constants;
import com.qiujie.enums.LevelEnum;
import org.slf4j.Marker;

public class StartupMarkerFilter extends TurboFilter {

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (marker != null && LevelEnum.STARTUP.name().equals(marker.getName())) {
            if (Constants.ENABLE_STARTUP) {
                return FilterReply.ACCEPT;
            } else {
                return FilterReply.DENY;
            }
        }
        return FilterReply.NEUTRAL;
    }
}
