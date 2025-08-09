package com.qiujie.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.IoUtil;
import cn.hutool.json.*;
import com.qiujie.core.WorkflowBroker;
import com.qiujie.entity.Freq2Power;
import com.qiujie.entity.Cpu;
import com.qiujie.core.DvfsCloudletSchedulerSpaceShared;
import com.qiujie.core.WorkflowDatacenter;
import com.qiujie.entity.*;
import com.qiujie.entity.Job;
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

    public static List<Cpu> getCpuList() {
        try (InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream("cpu.json")) {
            if (inputStream == null) {
                throw new IORuntimeException("Unable to find cpu.json file");
            }
            String jsonStr = IoUtil.readUtf8(inputStream);
            JSONArray array = JSONUtil.parseArray(jsonStr);
            List<Cpu> list = JSONUtil.toList(array, Cpu.class);
            list.forEach(cpu ->
                    cpu.getFreq2PowerList().sort(Comparator.comparingDouble(Freq2Power::getFrequency).reversed())
            );
            return list;
        } catch (IOException e) {
            throw new IORuntimeException("Failed to read cpu.json", e);
        }
    }


    public static List<Datacenter> createDatacenters() throws Exception {
        List<Datacenter> list = new ArrayList<>();
        int hostId = 0;
        int peId = 0;
        for (int i = 0; i < DCS; i++) {
            List<Host> hostList = new ArrayList<>();
            for (int j = 0; j < DC_HOSTS; j++) {
                List<Pe> peList = new ArrayList<>();
                for (int k = 0; k < HOST_PES; k++) {
                    peList.add(new Pe(peId++, new PeProvisionerSimple(HOST_MIPS)));
                }
                hostList.add(new Host(hostId++, new RamProvisionerSimple(HOST_RAM), new BwProvisionerSimple(HOST_BW), HOST_STORAGE, peList, new VmSchedulerSpaceShared(peList)));
            }
            DatacenterCharacteristics characteristics = new DatacenterCharacteristics(ARCH, OS, VMM, hostList, TIME_ZONE, COST_PER_SEC, COST_PER_MEM, COST_PER_STORAGE, COST_PER_BW);
            List<Double> elecPrice = new ArrayList<>(ELEC_PRICES.get(i % ELEC_PRICES.size()));
            list.add(new WorkflowDatacenter(characteristics, new VmAllocationPolicySimple(hostList), new LinkedList<>(), DC_SCHEDULING_INTERVAL, elecPrice));
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
        List<Cpu> cpuList = getCpuList();
        //create VMs
        for (int i = 0; i < VMS; i++) {
            Cpu cpu = getRandomElement(random, cpuList);
            DvfsVm vm = new DvfsVm(i, userId, cpu.getMips(), cpu.getPes(), cpu.getFrequency(), VM_RAM, VM_BW, VM_SIZE, VMM, new DvfsCloudletSchedulerSpaceShared(random));
            vm.setCpu(cpu.getName());
            List<Fv> fvList = new ArrayList<>();
            List<Freq2Power> freq2PowerList = cpu.getFreq2PowerList();
            for (Freq2Power freq2Power : freq2PowerList) {
                // smaller frequency, bigger lambda (transient fault rate)
                double lambda = λ * Math.pow(10, (SR * (cpu.getFrequency() - freq2Power.getFrequency()) / (cpu.getFrequency() - freq2PowerList.getLast().getFrequency())));
                double mips = cpu.getMips() * freq2Power.getFrequency() / cpu.getFrequency();
                int level = freq2PowerList.size() - 1 - freq2PowerList.indexOf(freq2Power);
                Fv fv = new Fv()
                        .setLevel(level)
                        .setLambda(lambda).setType(cpu.getName() + " (L" + level + ")")
                        .setVm(vm).setMips(mips).setFrequency(freq2Power.getFrequency()).setPower(freq2Power.getPower());
                fvList.add(fv);
            }
            vm.setFvList(fvList);
            list.add(vm);
        }
        return list;
    }


    public static Workflow createWorkflow(String name) {
        return WorkflowParser.parse(name);
    }

    public static List<Workflow> createWorkflow(List<String> list) {
        List<Workflow> workflowList = new ArrayList<>();
        for (String name : list) {
            Workflow workflow = createWorkflow(name);
            workflowList.add(workflow);
        }
        return workflowList;
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
        List<Job> jobList = list.stream().sorted(Comparator.comparingDouble(Cloudlet::getGuestId)).toList();
        Table.Builder builder = new Table.Builder("Idx", IntStream.rangeClosed(0, jobList.size() - 1).boxed().toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 6, Precision.ZERO))
                .addColumn("Job_Id", jobList.stream().map(Cloudlet::getCloudletId).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 10, Precision.ZERO))
                .addColumn("Job_Name", jobList.stream().map(Job::getName).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 30))
                .addColumn("Status", jobList.stream().map(Cloudlet::getCloudletStatusString).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 10))
                .addColumn("Dc_Id", jobList.stream().map(Cloudlet::getResourceId).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 10, Precision.ZERO))
                .addColumn("Vm_Id", jobList.stream().map(Cloudlet::getGuestId).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 10, Precision.ZERO))
                .addColumn("MIPS", jobList.stream().map(job -> String.format("%.2f (L%d)", job.getFv().getMips(), job.getFv().getLevel())).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 15))
                .addColumn("Cloudlet_Length", jobList.stream().map(Cloudlet::getCloudletLength).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 20, Precision.ZERO))
                .addColumn("Length", jobList.stream().map(Job::getLength).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.ZERO))
                .addColumn("Retry_Count", jobList.stream().map(Job::getRetryCount).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.ZERO))
                .addColumn("Transfer_Time", jobList.stream().map(Job::getFileTransferTime).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.TWO))
                .addColumn("Start_Time", jobList.stream().map(Cloudlet::getExecStartTime).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.TWO))
                .addColumn("Finish_Time", jobList.stream().map(Cloudlet::getExecFinishTime).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.TWO))
                .addColumn("Process_Time", jobList.stream().map(Cloudlet::getActualCPUTime).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.TWO))
                .addColumn("Elec_Cost", jobList.stream().map(Job::getElecCost).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 15, Precision.SIX))
                .addColumn("Depth", jobList.stream().map(Job::getDepth).toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 5, Precision.ZERO));
        Table table = builder.build();
        List<Integer> dcIds = jobList.stream().map(Cloudlet::getResourceId).distinct().toList();
        List<Integer> vmIds = jobList.stream().map(Cloudlet::getGuestId).distinct().toList();
        log.info("\n                                                                               {} Simulation Result\nUse {} Dcs {}\nUse {} Vms {}\n{}\n", title, dcIds.size(), dcIds, vmIds.size(), vmIds, table);
    }

    public static void printExperimentResult(List<Result> list) {
        printExperimentResult(list, "");
    }

    public static void printExperimentResult(List<Result> list, String title) {
        List<Result> sortByElecCost = list.stream().sorted(Comparator.comparingDouble(Result::getElecCost)).toList();
        List<Result> sortByFinishTime = list.stream().sorted(Comparator.comparingDouble(Result::getFinishTime)).toList();
        List<Result> sortByRetryCount = list.stream().sorted(Comparator.comparingInt(Result::getRetryCount)).toList();
        List<Result> sortByOverdueCount = list.stream().sorted(Comparator.comparingInt(Result::getOverdueCount)).toList();
        List<Result> sortByPlnRuntime = list.stream().sorted(Comparator.comparingDouble(Result::getPlnRuntime)).toList();
        List<Result> sortByRuntime = list.stream().sorted(Comparator.comparingDouble(Result::getRuntime)).toList();
        Table.Builder builder = new Table.Builder("Idx", IntStream.rangeClosed(0, list.size() - 1).boxed().toArray(Number[]::new), ColumnFormatter.number(Alignment.CENTER, 6, Precision.ZERO))
                .addColumn("Name", list.stream().map(Result::getName).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 80))
                .addColumn("Elec_Cost", list.stream().map(result -> String.format("%.6f (%d)", result.getElecCost(), sortByElecCost.indexOf(result))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 20))
                .addColumn("Finish_Time", list.stream().map(result -> String.format("%.2f (%d)", result.getFinishTime(), sortByFinishTime.indexOf(result))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 20))
                .addColumn("Retry_Count", list.stream().map(result -> String.format("%d (%d)", result.getRetryCount(), sortByRetryCount.indexOf(result))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 20))
                .addColumn("Overdue_Count", list.stream().map(result -> String.format("%d (%d)", result.getOverdueCount(), sortByOverdueCount.indexOf(result))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 20))
                .addColumn("Pln_Runtime", list.stream().map(result -> String.format("%.4f (%d)", result.getPlnRuntime(), sortByPlnRuntime.indexOf(result))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 20))
                .addColumn("Runtime", list.stream().map(result -> String.format("%.4f (%d)", result.getPlnRuntime(), sortByRuntime.indexOf(result))).toArray(String[]::new), ColumnFormatter.text(Alignment.CENTER, 20));
        Table table = builder.build();
        log.info("\n                                                                               {} Experiment Result\n{}\n", title, table);
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
        String path = SIM_DIR + str + ".json";
        FileUtil.writeUtf8String(jsonStr, path);
    }


    public static void generateExperimentData(List<Result> list, String str) {
        String jsonStr = JSONUtil.toJsonPrettyStr(list);
        String path = EXPERIMENT_DIR + str + ".json";
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
        if (host.getId() == parentHost.getId()) return 0;
        double dataSize = job.getPredInputFilesMap().get(parentJob).stream().mapToDouble(com.qiujie.entity.File::getSize).sum();
        return host.getDatacenter().getId() == parentHost.getDatacenter().getId() ? dataSize / INTRA_BANDWIDTH : dataSize / INTER_BANDWIDTH;
    }

    /**
     * Calculate reliability based on failure rate (lambda) and execution time using exponential decay model
     *
     * @param lambda
     * @param duration
     * @return
     */
    public static double calculateReliability(double lambda, double duration) {
        return Math.exp(-lambda * duration);
    }


    /**
     * Calculate the electricity cost
     *
     * @param elecPrice $/kWh
     * @param startTime s
     * @param endTime   s
     * @param power     watt
     */
    public static double calculateElecCost(List<Double> elecPrice, double startTime, double endTime, double power) {
        double totalCost = 0.0;
        double currentTime = startTime / 3600.0;
        if (startTime > endTime) {
            throw new IllegalStateException(String.format("startTime > endTime: %s > %s", startTime, endTime));
        }
        double remainingDuration = (endTime - startTime) / 3600.0;
        while (remainingDuration > 0) {
            int hourIndex = (int) (Math.floor(currentTime) % elecPrice.size());
            double nextHourTime = Math.floor(currentTime) + 1.0;
            double availableDuration = Math.min(nextHourTime - currentTime, remainingDuration);
            double electricityUsed = (power * availableDuration) / 1000.0; // kWh
            totalCost += electricityUsed * elecPrice.get(hourIndex);
            currentTime += availableDuration;
            remainingDuration -= availableDuration;
        }
        return totalCost;
    }

    /**
     * calculate avg elec price
     *
     * @param elecPrice $/kWh
     * @param startTime s
     * @param endTime   s
     */
    public static double calculateAvgElecPrice(List<Double> elecPrice, double startTime, double endTime) {
        double totalCost = 0.0;
        double currentTime = startTime / 3600.0;
        if (startTime >= endTime) {
            int hourIndex = (int) (Math.floor(currentTime) % elecPrice.size());
            return elecPrice.get(hourIndex);
        }
        double duration = (endTime - startTime) / 3600.0;
        double remainingDuration = duration;
        while (remainingDuration > 0) {
            int hourIndex = (int) (Math.floor(currentTime) % elecPrice.size());
            double nextHourTime = Math.floor(currentTime) + 1.0;
            double availableDuration = Math.min(nextHourTime - currentTime, remainingDuration);
            totalCost += availableDuration * elecPrice.get(hourIndex);
            currentTime += availableDuration;
            remainingDuration -= availableDuration;
        }
        return totalCost / duration;
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


    public static String getFilenameNoExt(File file) {
        String filename = file.getName();
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return filename;
        }
        return filename.substring(0, lastDotIndex);
    }
}
