package com.qiujie.aop;

import com.qiujie.util.ExperimentUtil;
import net.bytebuddy.asm.Advice;

public class ClockAdvice {
    @Advice.OnMethodExit
    public static void exit(@Advice.Return(readOnly = false) double returnValue) {
        returnValue = ExperimentUtil.roundToScale(returnValue, 4);
    }
}
