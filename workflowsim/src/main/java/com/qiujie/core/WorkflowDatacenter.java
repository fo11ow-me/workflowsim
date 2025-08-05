
package com.qiujie.core;

import com.qiujie.Constants;
import com.qiujie.entity.File;
import com.qiujie.entity.Job;
import com.qiujie.util.ExperimentUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.*;


import java.util.List;
import java.util.Objects;


@Getter
@Slf4j
public class WorkflowDatacenter extends Datacenter {


    private final List<Double> elecPrice;


    public WorkflowDatacenter(DatacenterCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList, double schedulingInterval, List<Double> elecPrice) throws Exception {
        super(WorkflowDatacenter.class.getSimpleName() + "_#" + CloudSim.getEntityList().size(), characteristics, vmAllocationPolicy, storageList, schedulingInterval);
        this.elecPrice = elecPrice;
    }

    /**
     * Processes a Cloudlet submission.
     *
     * @param ev  information about the event just happened
     * @param ack indicates if the event's sender expects to receive
     *            an acknowledge message when the event finishes to be processed
     * @pre ev != null
     * @post $none
     */
    @Override
    protected void processCloudletSubmit(SimEvent ev, boolean ack) {
        updateCloudletProcessing();

        try {
            // gets the Cloudlet object
            Cloudlet cloudlet = (Cloudlet) ev.getData();

            // checks whether this Cloudlet has finished or not
            if (cloudlet.isFinished()) {
                String name = CloudSim.getEntityName(cloudlet.getUserId());
                log.warn("{}: {}: Warning - {} #{} owned by {} is already completed/finished.", CloudSim.clock(), getName(), cloudlet.getClass().getSimpleName(), cloudlet.getCloudletId(), name);
                log.info("{}: {}: Therefore, it is not being executed again", CloudSim.clock(), getName());
                // NOTE: If a Cloudlet has finished, then it won't be processed.
                // So, if ack is required, this method sends back a result.
                // If ack is not required, this method don't send back a result.
                // Hence, this might cause CloudSim to be hanged since waiting
                // for this Cloudlet back.
                if (ack) {
                    int[] data = new int[3];
                    data[0] = getId();
                    data[1] = cloudlet.getCloudletId();
                    data[2] = CloudSimTags.FALSE;

                    sendNow(cloudlet.getUserId(), CloudActionTags.CLOUDLET_SUBMIT_ACK, data);
                }

                sendNow(cloudlet.getUserId(), CloudActionTags.CLOUDLET_RETURN, cloudlet);

                return;
            }

            // process this Cloudlet to this CloudResource
            cloudlet.setResourceParameter(getId(), getCharacteristics().getCostPerSecond(), getCharacteristics().getCostPerBw());

            int userId = cloudlet.getUserId();
            int vmId = cloudlet.getGuestId();


            HostEntity host = getVmAllocationPolicy().getHost(vmId, userId);
            GuestEntity vm = host.getGuest(vmId, userId);
            CloudletScheduler scheduler = vm.getCloudletScheduler();

            // now is submission time
            Job job = (Job) cloudlet;

            // time to transfer the files
            double fileTransferTime = predictFileTransferTime(job, (Host) host);
            job.setFileTransferTime(fileTransferTime);
            double estimatedProcessTime = scheduler.cloudletSubmit(cloudlet, fileTransferTime);

            // if this cloudlet is in the exec queue
            if (estimatedProcessTime > 0.0 && !Double.isInfinite(estimatedProcessTime)) {
                send(getId(), estimatedProcessTime, CloudActionTags.VM_DATACENTER_EVENT);
            } else {
                log.trace("{} {}: {} #{} is paused because not enough free PEs on {} #{}", CloudSim.clock(), getName(), cloudlet.getClass().getSimpleName(), cloudlet.getCloudletId(), vm.getClassName(), vm.getId());
            }
            if (ack) {
                int[] data = new int[3];
                data[0] = getId();
                data[1] = cloudlet.getCloudletId();
                data[2] = CloudSimTags.TRUE;

                sendNow(cloudlet.getUserId(), CloudActionTags.CLOUDLET_SUBMIT_ACK, data);
            }
        } catch (ClassCastException c) {
            log.error("{}: {}: processCloudletSubmit(): ClassCastException error.", CloudSim.clock(), getName());
            c.printStackTrace();
        } catch (Exception e) {
            log.error("{}: {}: processCloudletSubmit(): Exception error.", CloudSim.clock(), getName());
            e.printStackTrace();
        }

        checkCloudletCompletion();
    }


    /**
     * predict the file transfer time
     *
     * @param job
     * @param host
     * @return
     */
    private double predictFileTransferTime(Job job, Host host) {
        double predDateTransferTime = 0;
        double temp;
        for (Job parentJob : job.getParentList()) {
            Host parentHost = (Host) getVmAllocationPolicy().getHost(parentJob.getGuestId(), parentJob.getUserId());
            // if parentHost == null, indicate parent job is not in this datacenter
            if (parentHost == null) {
                double dataSize = job.getPredInputFilesMap().get(parentJob).stream().mapToDouble(File::getSize).sum();
                temp = dataSize / Constants.INTER_BANDWIDTH;
            } else {
                temp = ExperimentUtil.calculatePredecessorDataTransferTime(job, host, parentJob, parentHost);
            }
            predDateTransferTime = Math.max(predDateTransferTime, temp);
        }
        double localDataTransferTime = ExperimentUtil.calculateLocalDataTransferTime(job, host);
        return predDateTransferTime + localDataTransferTime;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowDatacenter that = (WorkflowDatacenter) o;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
