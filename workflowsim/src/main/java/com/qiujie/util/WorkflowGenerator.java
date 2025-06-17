package com.qiujie.util;

import com.qiujie.Constants;
import generator.RunAll;
import generator.app.*;

import java.io.File;
import java.util.List;


public class WorkflowGenerator {


    public static void main(String[] args) throws Exception {

        String basePath = "data/dax";

//        generateWorkflow(basePath, CyberShake.class, 800);
//        generateWorkflow(basePath, Montage.class, 800);

//        generateWorkflows(basePath, Montage.class, Constants.JOB_NUM_LIST, Constants.INSTANCE_NUM_LIST);
//        generateWorkflows(basePath, CyberShake.class, Constants.JOB_NUM_LIST, Constants.INSTANCE_NUM_LIST);

        List<Class<? extends Application>> classList = List.of(Montage.class, CyberShake.class, Genome.class,  LIGO.class, SIPHT.class);
        for (Class<? extends Application> clazz : classList) {
            for (Integer jobNum : Constants.JOB_NUM_LIST) {
                generateWorkflow(basePath, clazz, jobNum);
            }
        }

//        for (Class<? extends Application> clazz : Constants.APP_LIST) {
//            generateWorkflows(basePath, clazz, Constants.JOB_NUM_LIST);
//        }
    }


    /**
     * Generates a workflow with the specified number of jobs
     *
     * @param basePath
     * @param clazz
     * @param jobNum
     * @throws Exception
     */
    public static void generateWorkflow(String basePath, Class<? extends Application> clazz, Integer jobNum) throws Exception {
        File dir = new File(basePath);
        if (!dir.exists()) {
            dir.mkdir();
        }
        Application app = clazz.getDeclaredConstructor().newInstance();
        RunAll.run(app, new File(dir.getAbsolutePath() + "\\" + app.getClass().getSimpleName() + "_" + jobNum + ".xml"), "-n", jobNum + "");
    }


    /**
     * Generates multiple workflow instances with specified job num and instance num
     *
     * @param basePath
     * @param clazz
     * @param jobNumList
     * @param instanceNumList
     * @throws Exception
     */
    public static void generateWorkflows(String basePath, Class<? extends Application> clazz, List<Integer> jobNumList, List<Integer> instanceNumList) throws Exception {
        for (Integer jobNum : jobNumList) {
            for (Integer instanceNum : instanceNumList) {
                File dir = new File(basePath + "\\" + clazz.getSimpleName() + "\\" + jobNum + "\\" + instanceNum);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                for (int i = 0; i < instanceNum; i++) {
                    Application app = clazz.getDeclaredConstructor().newInstance();
                    RunAll.run(app, new File(dir.getAbsolutePath() + "\\" + app.getClass().getSimpleName() + "_" + jobNum + "_" + instanceNum + "_" + i + ".xml"), "-n", jobNum + "");
                }
            }
        }
    }


    public static void generateWorkflows(String basePath, Class<? extends Application> clazz, List<Integer> jobNumList) throws Exception {
        for (Integer jobNum : jobNumList) {
            File dir = new File(basePath + "\\" + clazz.getSimpleName() + "\\" + jobNum);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            Application app = clazz.getDeclaredConstructor().newInstance();
            RunAll.run(app, new File(dir.getAbsolutePath() + "\\" + app.getClass().getSimpleName() + "_" + jobNum + ".xml"), "-n", jobNum + "");
        }
    }
}
