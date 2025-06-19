package com.qiujie.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.csv.CsvUtil;
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
import com.qiujie.starter.SimStarter;
import io.bretty.console.table.Alignment;
import io.bretty.console.table.ColumnFormatter;
import io.bretty.console.table.Precision;
import io.bretty.console.table.Table;
import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.*;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.IntStream;

import static com.qiujie.Constants.*;

@Slf4j
public class ExperimentUtil {

    private static final Marker RESULT = MarkerFactory.getMarker(LevelEnum.RESULT.name());

    private static List<HostConfig> readHostConfig() {
        String path = Objects.requireNonNull(ExperimentUtil.class.getClassLoader().getResource("config/host.json")).getPath();
        JSONArray array = JSONUtil.readJSONArray(new File(path), CharsetUtil.CHARSET_UTF_8);
        return JSONUtil.toList(array, HostConfig.class);
    }

    private static List<VmConfig> readVmConfig() {
        String path = Objects.requireNonNull(ExperimentUtil.class.getClassLoader().getResource("config/vm.json")).getPath();
        JSONArray array = JSONUtil.readJSONArray(new File(path), CharsetUtil.CHARSET_UTF_8);
        List<VmConfig> list = JSONUtil.toList(array, VmConfig.class);
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
    public static List<Vm> createVms(int userId) {
        List<Vm> list = new ArrayList<>();
        List<VmConfig> vmConfigList = readVmConfig();
        //create VMs
        for (int i = 0; i < VMS; i++) {
            VmConfig vmConfig = vmConfigList.get(i % vmConfigList.size());
            DvfsVm vm = new DvfsVm(i, userId, vmConfig.getMips(), vmConfig.getPes(), vmConfig.getFrequency(), VM_RAM, VM_BW, VM_SIZE, VMM, new DvfsCloudletSchedulerSpaceShared());
            vm.setType(vmConfig.getName());
            List<Fv> fvList = new ArrayList<>();
            List<FvConfig> fvConfigList = vmConfig.getFvConfigList();
            for (FvConfig fvConfig : fvConfigList) {
                // smaller frequency, bigger lambda (transient fault rate)
                double lambda = λ * Math.pow(10, (SR * (vmConfig.getFrequency() - fvConfig.getFrequency()) / (vmConfig.getFrequency() - fvConfigList.getLast().getFrequency())));
                double mips = vmConfig.getMips() * fvConfig.getFrequency() / vmConfig.getFrequency();
                int level = fvConfigList.indexOf(fvConfig);
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
                .addColumn("Vm_MIPS", list.stream().map(job -> job.getFv() != null ? String.format("%.2f (L%d)", job.getFv().getMips(), job.getFv().getLevel()) : job.getVm().getMips() + "").toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 15))
                .addColumn("Cloudlet_Length", list.stream().map(Cloudlet::getCloudletLength).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 20, Precision.ZERO))
                .addColumn("Length", list.stream().map(Job::getLength).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.ZERO))
//                .addColumn("Length_Check", list.stream().map(job -> job.getCloudletLength() - job.getLength() - job.getFv().getMips() * job.getFileTransferTime()).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.ZERO))
                .addColumn("Transfer_Time", list.stream().map(Job::getFileTransferTime).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.TWO))
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
                .addColumn("Count", list.stream().map(Job::getCount).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 10, Precision.ZERO))
                .addColumn("Depth", list.stream().map(Job::getDepth).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 5, Precision.ZERO));
        Table table = builder.build();
        List<Integer> dcIds = list.stream().map(Cloudlet::getResourceId).distinct().toList();
        List<Integer> vmIds = list.stream().map(Cloudlet::getGuestId).distinct().toList();
        log.info(RESULT, "\n                                                                               {} Simulation Result\nUse {} Dcs {}\nUse {} Vms {}\n{}\n", title, dcIds.size(), dcIds, vmIds.size(), vmIds, table);
    }

    public static void printExperimentResult(List<SimStarter> list) {
        printExperimentResult(list, "");
    }


    public static void printExperimentResult(List<SimStarter> list, String title) {
        List<SimStarter> sortByPlnElecCost = list.stream().sorted(Comparator.comparingDouble(SimStarter::getPlnElecCost)).toList();
        List<SimStarter> sortByPlnFinishTime = list.stream().sorted(Comparator.comparingDouble(SimStarter::getPlnFinishTime)).toList();
        List<SimStarter> sortBySimElecCost = list.stream().sorted(Comparator.comparingDouble(SimStarter::getSimElecCost)).toList();
        List<SimStarter> sortBySimFinishTime = list.stream().sorted(Comparator.comparingDouble(SimStarter::getSimFinishTime)).toList();
        List<SimStarter> sortByRetryCount = list.stream().sorted(Comparator.comparingInt(SimStarter::getRetryCount)).toList();
        List<SimStarter> sortByDcCount = list.stream().sorted(Comparator.comparingInt(SimStarter::getDcCount)).toList();
        List<SimStarter> sortByVmCount = list.stream().sorted(Comparator.comparingInt(SimStarter::getVmCount)).toList();
        List<SimStarter> sortByOverdueCount = list.stream().sorted(Comparator.comparingInt(SimStarter::getOverdueCount)).toList();
        List<SimStarter> sortByPlnRuntime = list.stream().sorted(Comparator.comparingDouble(SimStarter::getPlnRuntime)).toList();
        Table.Builder builder = new Table.Builder("idx", IntStream.rangeClosed(0, list.size() - 1).boxed().toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 6, Precision.ZERO))
                .addColumn("Name", list.stream().map(SimStarter::getName).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 80))
//                .addColumn("Pln_Elec_Cost", list.stream().map(starter -> String.format("%.2f (%d)", starter.getPlnElecCost(), sortByPlnElecCost.indexOf(starter))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 20))
                .addColumn("Sim_Elec_Cost", list.stream().map(starter -> String.format("%.2f (%d)", starter.getSimElecCost(), sortBySimElecCost.indexOf(starter))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 20))
//                .addColumn("Pln_Finish_Time", list.stream().map(starter -> String.format("%.2f (%d)", starter.getPlnFinishTime(), sortByPlnFinishTime.indexOf(starter))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 20))
                .addColumn("Sim_Finish_Time", list.stream().map(starter -> String.format("%.2f (%d)", starter.getSimFinishTime(), sortBySimFinishTime.indexOf(starter))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 20))
                .addColumn("Retry_Count", list.stream().map(starter -> String.format("%d (%d)", starter.getRetryCount(), sortByRetryCount.indexOf(starter))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 15))
                .addColumn("Dc_Count", list.stream().map(starter -> String.format("%d (%d)", starter.getDcCount(), sortByDcCount.indexOf(starter))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 15))
                .addColumn("Vm_Count", list.stream().map(starter -> String.format("%d (%d)", starter.getVmCount(), sortByVmCount.indexOf(starter))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 15))
                .addColumn("Overdue_Count", list.stream().map(starter -> String.format("%d (%d)", starter.getOverdueCount(), sortByOverdueCount.indexOf(starter))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 15))
                .addColumn("Pln_Runtime", list.stream().map(starter -> String.format("%.2f (%d)", starter.getPlnRuntime(), sortByPlnRuntime.indexOf(starter))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 15));
        Table table = builder.build();
        log.info(RESULT, "\n                                                                               {} Experiment Result\n{}\n", title, table);
    }


    /**
     * generate plan gantt data
     *
     * @param execWindowList
     */
    public static void generatePlnGanttData(List<ExecWindow> execWindowList, String str) {
        long baseMilliseconds = DateUtil.beginOfDay(new Date()).getTime();
        List<GanttTask> taskList = new ArrayList<>();
        for (ExecWindow execWindow : execWindowList) {
            Job job = execWindow.getJob();
            List<Integer> childList = job.getChildList().stream().map(Job::getCloudletId).toList();
            taskList.add(new GanttTask().setId(job.getCloudletId()).setName(job.getName()).setStartTime(DateUtil.format(new Date(baseMilliseconds + Math.round(execWindow.getStartTime() * 1000)), "yyyy-MM-dd HH:mm:ss"))
                    .setEndTime(DateUtil.format(new Date(baseMilliseconds + Math.round(execWindow.getFinishTime() * 1000)), "yyyy-MM-dd HH:mm:ss")).setVmId(execWindow.getJob().getVm().getId()).setChildList(childList).setDepth(job.getDepth()));
        }
        String jsonStr = JSONUtil.toJsonPrettyStr(taskList);
        String path = System.getProperty("user.dir") + File.separator + "data" + File.separator + "gantt" + File.separator + str + "_pln.json";
        FileUtil.writeUtf8String(jsonStr, path);
    }


    /**
     * generate sim gantt data
     *
     * @param list
     */
    public static void generateSimGanttData(List<Job> list, String str) {
        long baseMilliseconds = DateUtil.beginOfDay(new Date()).getTime();
        List<GanttTask> taskList = new ArrayList<>();
        for (Job job : list) {
            List<Integer> childList = job.getChildList().stream().map(Job::getCloudletId).toList();
            taskList.add(new GanttTask().setId(job.getCloudletId()).setName(job.getName()).setStartTime(DateUtil.format(new Date(baseMilliseconds + Math.round(job.getExecStartTime() * 1000)), "yyyy-MM-dd HH:mm:ss"))
                    .setEndTime(DateUtil.format(new Date(baseMilliseconds + Math.round(job.getExecFinishTime() * 1000)), "yyyy-MM-dd HH:mm:ss")).setVmId(job.getGuestId()).setChildList(childList).setDepth(job.getDepth()));
        }
        String jsonStr = JSONUtil.toJsonPrettyStr(taskList);
        String path = System.getProperty("user.dir") + File.separator + "data" + File.separator + "gantt" + File.separator + str + "_sim.json";
        FileUtil.writeUtf8String(jsonStr, path);
    }


    public static void generateExperimentDate(List<SimStarter> list) {
        generateExperimentDate(list, "result");
    }


    public static void generateExperimentDate(List<SimStarter> list, String str) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (SimStarter simStarter : list) {
            Map<String, Object> item = BeanUtil.beanToMap(simStarter.getParameter());
            item.put("name", simStarter.getName());
            item.put("daxPathList", simStarter.getDaxPathList());
            item.put("elecCost", simStarter.getSimElecCost());
            item.put("finishTime", simStarter.getSimFinishTime());
            item.put("retryCount", simStarter.getRetryCount());
            item.put("overdueCount", simStarter.getOverdueCount());
            item.put("runtime", simStarter.getPlnRuntime());
            data.add(item);
        }
        String jsonStr = JSONUtil.toJsonPrettyStr(data);
        String path = System.getProperty("user.dir") + File.separator + "data" + File.separator + "experiment" + File.separator + str + ".json";
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


    public List<Double> calculateRPD(List<SimStarter> list) {
        double best = list.stream().mapToDouble(SimStarter::getPlnElecCost).min().getAsDouble();
        return list.stream().mapToDouble(simStarter -> roundToScale((simStarter.getPlnElecCost() - best) / best, 3)).boxed().toList();
    }


    /**
     * @param maxValue
     * @Returns a random integer in the range [0, maxValue).
     */
    public static int getRandomValue(final int maxValue) {
        final double uniform = RANDOM.sample();
        return (int) (uniform >= 1 ? uniform % maxValue : uniform * maxValue);
    }


    public static <T> T getRandomElement(List<T> list) {
        return list.get(getRandomValue(list.size()));
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


}
