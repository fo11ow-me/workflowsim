package com.qiujie.util;

import generator.RunAll;
import generator.app.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.qiujie.Constants.*;


public class WorkflowGenerator {


    public static void main(String[] args) throws Exception {

        Map<Class<? extends Application>, Integer> map = new HashMap<>();
        map.put(Montage.class, 100000);
        map.put(Genome.class, 100);

        File dir = new File(DAX_DIR);
        if (!dir.exists()) {
            dir.mkdir();
        }

        for (Class<? extends Application> clazz : APP_LIST) {
            for (Integer jobNum : JOB_NUM_LIST) {
                for (int i = 0; i < INSTANCE_NUM; i++) {
                    Application app = clazz.getDeclaredConstructor().newInstance();
                    RunAll.run(app, new File(dir.getAbsolutePath() + "\\" + app.getClass().getSimpleName() + "_" + jobNum + "_" + i + ".xml"), "-n", jobNum + "", "-f", map.get(clazz) + "");
                }
            }
        }


        for (Class<? extends Application> clazz : APP_LIST) {
            for (Integer jobNum : JOB_NUM_LIST) {
                Application app = clazz.getDeclaredConstructor().newInstance();
                RunAll.run(app, new File(dir.getAbsolutePath() + "\\" + app.getClass().getSimpleName() + "_" + jobNum + ".xml"), "-n", jobNum + "", "-f", map.get(clazz) + "");
            }
        }
    }
}
