package com.qiujie.starter;

import ch.qos.logback.classic.Level;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.qiujie.entity.Result;
import com.qiujie.entity.SimParameter;
import com.qiujie.enums.LevelEnum;
import com.qiujie.util.ExperimentUtil;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.qiujie.Constants.*;

public abstract class ExperimentStarter {
    public final String name;
    public long seed;
    private final Logger log;
    @Setter
    private Level level;
    private final List<File> paramFileList;

    private final Marker STARTUP_EXPERIMENT;

    public ExperimentStarter() {
        this.name = getClass().getSimpleName();
        System.setProperty("startup.class", name);
        this.log = LoggerFactory.getLogger(ExperimentStarter.class);
        this.level = LEVEL;
        this.paramFileList = new ArrayList<>();
        this.STARTUP_EXPERIMENT = MarkerFactory.getMarker(LevelEnum.STARTUP_EXPERIMENT.name());
        start();
    }

    private void start() {
        log.info(STARTUP_EXPERIMENT, "{}: Starting...", name);
        this.seed = System.currentTimeMillis();
        try {
            run();
            log.info(STARTUP_EXPERIMENT, String.format("%s: Finished in %.2fs", name, (System.currentTimeMillis() - seed) / 1000.0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void run() throws Exception {
        createDirs();
        init();
        // Start subprocesses to run simulations
        List<Process> processList = startSimProcesses();
        // Wait for all subprocesses to complete
        for (Process process : processList) {
            process.waitFor();
        }
        // Collect and save results
        List<Result> results = collectResults();
        ExperimentUtil.printExperimentResult(results, name);
        ExperimentUtil.generateExperimentData(results, name);
        // Delete temporary parameter and result files
        deleteFiles(paramFileList, "parameter");
        deleteFiles(List.of(Objects.requireNonNull(new File(RESULT_DIR).listFiles(
                (dir, fileName) -> fileName.startsWith(name) && fileName.endsWith(".json")
        ))), "result");
    }

    private void createDirs() {
        FileUtil.mkdir(PARAM_DIR);
        FileUtil.mkdir(RESULT_DIR);
        FileUtil.mkdir(SIM_DATA_DIR);
        FileUtil.mkdir(EXPERIMENT_DATA_DIR);
    }

    protected abstract void init();

    protected void addParam(SimParameter simParameter) {
        String id = String.format(name + "_%06d", paramFileList.size());
        simParameter.setId(id);
        String json = JSONUtil.toJsonPrettyStr(simParameter);
        File file = new File(PARAM_DIR + id + ".json");
        FileUtil.writeUtf8String(json, file);
        if (!file.exists() || file.length() == 0) {
            throw new RuntimeException("❌ Parameter file creation failed or is empty: " + file.getAbsolutePath());
        }
        paramFileList.add(file);
    }

    private List<Process> startSimProcesses() throws Exception {
        List<Process> processList = new ArrayList<>();
        for (File paramFile : paramFileList) {
            ProcessBuilder pb = new ProcessBuilder(
                    "java",
                    "-Dstartup.class=" + name,
                    "-cp", System.getProperty("java.class.path"),
                    SimStarter.class.getName(),
                    String.valueOf(level.levelInt),
                    paramFile.getAbsolutePath()
            );
            pb.inheritIO(); // Inherit output for real-time logging
            Process process = pb.start();
            processList.add(process);
        }
        return processList;
    }

    private List<Result> collectResults() {
        List<Result> resultList = new ArrayList<>();
        File[] resultFileList = new File(RESULT_DIR).listFiles(
                (dir, fileName) -> fileName.startsWith(name) && fileName.endsWith(".json")
        );

        if (resultFileList == null || resultFileList.length == 0) {
            log.error("❌ No result files found!");
            return resultList;
        }

        for (File file : resultFileList) {
            try {
                String json = FileUtil.readUtf8String(file);
                Result result = JSONUtil.toBean(json, Result.class);
                resultList.add(result);
            } catch (Exception e) {
                log.warn("⚠️ Failed to read result file: {}", file.getName(), e);
            }
        }

        return resultList;
    }

    private void deleteFiles(List<File> list, String type) {
        if (list == null) return;
        for (File file : list) {
            if (!file.delete()) {
                log.warn("⚠️ Failed to delete {} file: {}", type, file.getName());
            }
        }
    }
}
