package com.qiujie.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.json.*;
import com.qiujie.config.FvConfig;
import com.qiujie.config.HostConfig;
import com.qiujie.config.VmConfig;
import com.qiujie.core.DvfsCloudletSchedulerSpaceShared;
import com.qiujie.core.WorkflowDatacenter;
import com.qiujie.entity.*;
import com.qiujie.entity.Job;
import com.qiujie.enums.LevelEnum;
import io.bretty.console.table.Alignment;
import io.bretty.console.table.ColumnFormatter;
import io.bretty.console.table.Precision;
import io.bretty.console.table.Table;
import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.style.Styler;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.IntStream;

import static com.qiujie.Constants.*;

@Slf4j
public class ExperimentUtil {

    private static final Marker EXPERIMENT = MarkerFactory.getMarker(LevelEnum.EXPERIMENT.name());
    private static final Marker SIM = MarkerFactory.getMarker(LevelEnum.SIM.name());

    private static List<HostConfig> readHostConfig() {
        // Get the InputStream for the resource file
        InputStream inputStream = ExperimentUtil.class.getClassLoader().getResourceAsStream("config/host.json");
        if (inputStream == null) {
            throw new IORuntimeException("Unable to find host.json file");
        }
        // Write the content of InputStream to a temporary file
        File tempFile;
        try {
            tempFile = File.createTempFile("host_config", ".json");
            FileUtil.writeFromStream(inputStream, tempFile);  // Write the InputStream to the temporary file
        } catch (IOException e) {
            throw new IORuntimeException("Unable to create temporary file", e);
        } finally {
            // Close the InputStream
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Read JSON data from the temporary file using Hutool
        JSONArray array = JSONUtil.readJSONArray(tempFile, CharsetUtil.CHARSET_UTF_8);
        return JSONUtil.toList(array, HostConfig.class);
    }

    private static List<VmConfig> readVmConfig() {
        // Get the InputStream for the resource file
        InputStream inputStream = ExperimentUtil.class.getClassLoader().getResourceAsStream("config/vm.json");
        if (inputStream == null) {
            throw new IORuntimeException("Unable to find vm.json file");
        }

        // Write the content of InputStream to a temporary file
        File tempFile;
        try {
            tempFile = File.createTempFile("vm_config", ".json");
            FileUtil.writeFromStream(inputStream, tempFile);  // Write the InputStream to the temporary file
        } catch (IOException e) {
            throw new IORuntimeException("Unable to create temporary file", e);
        } finally {
            // Close the InputStream
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Read JSON data from the temporary file using Hutool
        JSONArray array = JSONUtil.readJSONArray(tempFile, CharsetUtil.CHARSET_UTF_8);
        List<VmConfig> list = JSONUtil.toList(array, VmConfig.class);

        // Sort the FV config list by frequency in descending order
        list.forEach(vmConfig -> vmConfig.getFvConfigList().sort(Comparator.comparingDouble(FvConfig::getFrequency).reversed()));
        return list;
    }



    public static List<Datacenter> createDatacenters() {
        List<Datacenter> list = new ArrayList<>(); // Pre-allocate capacity
        List<HostConfig> hostConfigList = readHostConfig();
        int hostId = 0;
        int peId = 0;
        for (int i = 0; i < DCS; i++) {
            List<Host> hostList = new ArrayList<>();
            for (int j = 0; j < DC_HOSTS; j++) {
                HostConfig hostConfig = hostConfigList.get(j % hostConfigList.size());
                List<Pe> peList = new ArrayList<>();
                for (int k = 0; k < hostConfig.getPes(); k++) {
                    peList.add(new Pe(peId++, new PeProvisionerSimple(hostConfig.getMips())));
                }
                hostList.add(new Host(hostId++, new RamProvisionerSimple(HOST_RAM), new BwProvisionerSimple(HOST_BW), HOST_STORAGE, peList, new VmSchedulerSpaceShared(peList)));
            }
            DatacenterCharacteristics characteristics = new DatacenterCharacteristics(ARCH, OS, VMM, hostList, TIME_ZONE, COST_PER_SEC, COST_PER_MEM, COST_PER_STORAGE, COST_PER_BW);
            try {
                List<Double> elecPrice = new ArrayList<>(ELEC_PRICES.get(i % ELEC_PRICES.size()));
                list.add(new WorkflowDatacenter(characteristics, new VmAllocationPolicySimple(hostList), new LinkedList<>(), DC_SCHEDULING_INTERVAL, elecPrice));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }


    /**
     * create VMs
     *
     * @param userId the owner of the Vm
     */
    public static List<Vm> createVms(final ContinuousDistribution random, int userId) {
        List<Vm> list = new ArrayList<>();
        List<VmConfig> vmConfigList = readVmConfig();
        //create VMs
        for (int i = 0; i < VMS; i++) {
            VmConfig vmConfig = vmConfigList.get(i % vmConfigList.size());
            DvfsVm vm = new DvfsVm(i, userId, vmConfig.getMips(), vmConfig.getPes(), vmConfig.getFrequency(), VM_RAM, VM_BW, VM_SIZE, VMM, new DvfsCloudletSchedulerSpaceShared(random));
            vm.setType(vmConfig.getName());
            List<Fv> fvList = new ArrayList<>();
            List<FvConfig> fvConfigList = vmConfig.getFvConfigList();
            for (FvConfig fvConfig : fvConfigList) {
                // smaller frequency, bigger lambda (transient fault rate)
                double lambda = λ * Math.pow(10, (SR * (vmConfig.getFrequency() - fvConfig.getFrequency()) / (vmConfig.getFrequency() - fvConfigList.getLast().getFrequency())));
                double mips = vmConfig.getMips() * fvConfig.getFrequency() / vmConfig.getFrequency();
                int level = fvConfigList.size() - 1 - fvConfigList.indexOf(fvConfig);
                Fv fv = new Fv()
                        .setLevel(level)
                        .setLambda(lambda).setType(vmConfig.getName() + "_" + level)
                        .setVm(vm).setMips(mips).setFrequency(fvConfig.getFrequency()).setPower(fvConfig.getPower());
                fvList.add(fv);
            }
            vm.setFvList(fvList);
            list.add(vm);
        }
        return list;
    }


    public static void printSimResult(List<Job> list) {
        printSimResult(list, "");
    }

    /**
     * print simulation result
     *
     * @param list
     */
    public static void printSimResult(List<Job> list, String title) {
        list.sort(Comparator.comparingDouble(Cloudlet::getGuestId));
        Table.Builder builder = new Table.Builder("idx", IntStream.rangeClosed(0, list.size() - 1).boxed().toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 6, Precision.ZERO))
                .addColumn("Job_Id", list.stream().map(Cloudlet::getCloudletId).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 10, Precision.ZERO))
                .addColumn("Job_Name", list.stream().map(Job::getName).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 30))
                .addColumn("Status", list.stream().map(Cloudlet::getCloudletStatusString).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 10))
                .addColumn("Dc_Id", list.stream().map(Cloudlet::getResourceId).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 10, Precision.ZERO))
                .addColumn("Vm_Id", list.stream().map(Cloudlet::getGuestId).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 10, Precision.ZERO))
                .addColumn("Vm_MIPS", list.stream().map(job -> String.format("%.2f (L%d)", job.getFv().getMips(), job.getFv().getLevel())).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 15))
                .addColumn("Cloudlet_Length", list.stream().map(Cloudlet::getCloudletLength).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 20, Precision.ZERO))
                .addColumn("Length", list.stream().map(Job::getLength).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.ZERO))
//                .addColumn("Length_Check", list.stream().map(job -> job.getCloudletLength() - job.getLength() - job.getFv().getMips() * job.getFileTransferTime()).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.ZERO))
//                .addColumn("Transfer_Time", list.stream().map(Job::getFileTransferTime).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.TWO))
                .addColumn("Start_Time", list.stream().map(Cloudlet::getExecStartTime).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.TWO))
                .addColumn("Finish_Time", list.stream().map(Cloudlet::getExecFinishTime).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.TWO))
                .addColumn("Process_Time", list.stream().map(Cloudlet::getActualCPUTime).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.TWO))
//                .addColumn("Process_Time_Check", list.stream().map(job -> job.getActualCPUTime() - job.getCloudletLength() / job.getFv().getMips()).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 20, Precision.TWO))
                .addColumn("Elec_Cost", list.stream().map(Job::getElecCost).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.TWO))
//                .addColumn("Elec_Cost_Check", list.stream().map(job -> {
//                    WorkflowDatacenter dc = (WorkflowDatacenter) job.getFv().getVm().getDatacenter();
//                    return ExperimentUtil.calculateElecCost(dc.getElecPrice(), job.getExecStartTime(), job.getExecFinishTime(), job.getFv().getPower()) - job.getElecCost();
//                }).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 20, Precision.TWO))
                .addColumn("Retry_Count", list.stream().map(Job::getRetryCount).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.ZERO))
//                .addColumn("Count", list.stream().map(Job::getCount).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 10, Precision.ZERO))
                .addColumn("Depth", list.stream().map(Job::getDepth).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 5, Precision.ZERO));
        Table table = builder.build();
        List<Integer> dcIds = list.stream().map(Cloudlet::getResourceId).distinct().toList();
        List<Integer> vmIds = list.stream().map(Cloudlet::getGuestId).distinct().toList();
        log.info(SIM, "\n                                                                               {} Simulation Result\nUse {} Dcs {}\nUse {} Vms {}\n{}\n", title, dcIds.size(), dcIds, vmIds.size(), vmIds, table);
    }

    public static void printExperimentResult(List<Result> list) {
        printExperimentResult(list, "");
    }

    public static void printExperimentResult(List<Result> list, String title) {
        List<Result> sortByElecCost = list.stream().sorted(Comparator.comparingDouble(Result::getElecCost)).toList();
        List<Result> sortByFinishTime = list.stream().sorted(Comparator.comparingDouble(Result::getFinishTime)).toList();
        List<Result> sortByRetryCount = list.stream().sorted(Comparator.comparingInt(Result::getRetryCount)).toList();
        List<Result> sortByOverdueCount = list.stream().sorted(Comparator.comparingInt(Result::getOverdueCount)).toList();
        List<Result> sortByRuntime = list.stream().sorted(Comparator.comparingDouble(Result::getPlnRuntime)).toList();
        Table.Builder builder = new Table.Builder("idx", IntStream.rangeClosed(0, list.size() - 1).boxed().toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 6, Precision.ZERO))
                .addColumn("Name", list.stream().map(Result::getName).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 80))
                .addColumn("Elec_Cost", list.stream().map(result -> String.format("%.2f (%d)", result.getElecCost(), sortByElecCost.indexOf(result))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 20))
                .addColumn("Finish_Time", list.stream().map(result -> String.format("%.2f (%d)", result.getFinishTime(), sortByFinishTime.indexOf(result))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 20))
                .addColumn("Retry_Count", list.stream().map(result -> String.format("%d (%d)", result.getRetryCount(), sortByRetryCount.indexOf(result))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 15))
                .addColumn("Overdue_Count", list.stream().map(result -> String.format("%d (%d)", result.getOverdueCount(), sortByOverdueCount.indexOf(result))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 15))
                .addColumn("Runtime", list.stream().map(result -> String.format("%.2f (%d)", result.getPlnRuntime(), sortByRuntime.indexOf(result))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 15));
        Table table = builder.build();
        log.info(EXPERIMENT, "\n                                                                               {} Experiment Result\n{}\n", title, table);
    }



    /**
     * generate sim gantt data
     *
     * @param list
     */
    public static void generateSimData(List<Job> list, String str) {
        long baseMilliseconds = DateUtil.beginOfDay(new Date()).getTime();
        List<Task> taskList = new ArrayList<>();
        for (Job job : list) {
            List<Integer> childList = job.getChildList().stream().map(Job::getCloudletId).toList();
            taskList.add(new Task().setId(job.getCloudletId()).setName(job.getName()).setStartTime(DateUtil.format(new Date(baseMilliseconds + Math.round(job.getExecStartTime() * 1000)), "yyyy-MM-dd HH:mm:ss"))
                    .setEndTime(DateUtil.format(new Date(baseMilliseconds + Math.round(job.getExecFinishTime() * 1000)), "yyyy-MM-dd HH:mm:ss")).setVmId(job.getGuestId()).setChildList(childList).setDepth(job.getDepth()));
        }
        String jsonStr = JSONUtil.toJsonPrettyStr(taskList);
        String path = SIM_DATA_DIR + str + ".json";
        FileUtil.writeUtf8String(jsonStr, path);
    }


    public static void generateExperimentData(List<Result> list, String str) {
        String jsonStr = JSONUtil.toJsonPrettyStr(list);
        String path = EXPERIMENT_DATA_DIR + str + ".json";
        FileUtil.writeUtf8String(jsonStr, path);
    }


    public static double calculateLocalDataTransferTime(Job job, Host host) {
        return job.getLocalInputFileList().stream().mapToDouble(file -> {
            Host fileHost = file.getHost();
            if (host.getId() == fileHost.getId()) return 0;
            return host.getDatacenter().getId() == fileHost.getDatacenter().getId() ? file.getSize() / INTRA_BANDWIDTH : file.getSize() / INTER_BANDWIDTH;
        }).sum();
    }


    public static double calculatePredecessorDataTransferTime(Job job, Host host, Job parentJob, Host parentHost) {
        // No data transfer time is required if the job and its parent are on the same host.
        if (host.getId() == parentHost.getId()) {
            return 0;
        }
        List<String> parentOutputFiles = parentJob.getOutputFileList().stream().map(com.qiujie.entity.File::getName).toList();
        double dataSize = job.getPredInputFileList().stream().filter(file -> parentOutputFiles.contains(file.getName())).mapToDouble(com.qiujie.entity.File::getSize).sum();
        return host.getDatacenter().getId() == parentHost.getDatacenter().getId() ? dataSize / INTRA_BANDWIDTH : dataSize / INTER_BANDWIDTH;
    }

    public static double calculateReliability(double lambda, double duration) {
        return Math.exp(-lambda * duration);
    }

    public static double calculateElecCost(List<Double> elecPrice, double startTime, double endTime, double power) {
        double totalCost = 0.0;
        double currentTime = startTime / 3600.0;
        double remainingDuration = (endTime - startTime) / 3600.0;
        while (remainingDuration > 0) {
            int hourIndex = (int) Math.floor(currentTime) % elecPrice.size();
            double nextHourTime = Math.floor(currentTime) + 1.0;
            double availableDuration = Math.min(nextHourTime - currentTime, remainingDuration);
            double electricityUsed = power * availableDuration;
            totalCost += electricityUsed * elecPrice.get(hourIndex);
            currentTime += availableDuration;
            remainingDuration -= availableDuration;
        }
        return totalCost;
    }


    public List<Double> calculateRPD(List<Result> list) {
        double best = list.stream().mapToDouble(Result::getElecCost).min().getAsDouble();
        return list.stream().mapToDouble(result -> roundToScale((result.getElecCost() - best) / best, 3)).boxed().toList();
    }


    /**
     * @param maxValue
     * @Returns a random integer in the range [0, maxValue).
     */
    public static int getRandomValue(final ContinuousDistribution random, final int maxValue) {
        final double uniform = random.sample();
        return (int) (uniform >= 1 ? uniform % maxValue : uniform * maxValue);
    }


    public static <T> T getRandomElement(final ContinuousDistribution random, List<T> list) {
        return list.get(getRandomValue(random, list.size()));
    }


    public static void plotElecPriceChart(List<Datacenter> datacenterList) {
        XYChart chart = new XYChartBuilder().title("DC electricity price chart").xAxisTitle("hour").yAxisTitle("electricity price").theme(Styler.ChartTheme.Matlab).build();
        chart.getStyler().setCursorEnabled(true);
        for (Datacenter datacenter : datacenterList) {
            WorkflowDatacenter workflowDatacenter = (WorkflowDatacenter) datacenter;
            List<Double> elecPrice = workflowDatacenter.getElecPrice();
            chart.addSeries(datacenter.getName(), elecPrice);
        }
        new SwingWrapper<>(chart).displayChart();
    }


    /**
     * round to scale
     *
     * @param value
     * @param scale
     * @return
     */
    public static double roundToScale(double value, int scale) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(scale, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }


    public static String getPrefixFromClassName(String fullClassName) {
        String className = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
        String[] words = className.split("(?=[A-Z])");

        if (words.length <= 1) {
            return className;
        }

        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < words.length - 1; i++) {
            prefix.append(words[i]);
        }

        return prefix.toString();
    }




}
