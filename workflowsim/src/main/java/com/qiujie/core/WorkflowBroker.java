
package com.qiujie.core;

import com.qiujie.entity.File;
import com.qiujie.entity.Job;
import com.qiujie.entity.Parameter;
import com.qiujie.entity.Workflow;
import com.qiujie.planner.WorkflowPlannerAbstract;
import com.qiujie.util.ExperimentUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.*;
import org.cloudbus.cloudsim.lists.VmList;

import java.util.*;

/**
 * WorkflowBroker
 *
 * @author qiujie
 */
@Slf4j
public class WorkflowBroker extends DatacenterBroker {

    @Getter
    private final List<Workflow> workflowList;

    private final WorkflowPlannerAbstract planner;

    public WorkflowBroker(Class<? extends WorkflowPlannerAbstract> clazz, Parameter parameter) throws Exception {
        super(WorkflowBroker.class.getSimpleName() + "_#" + CloudSim.getEntityList().size());
        this.planner = clazz.getDeclaredConstructor().newInstance();
        this.planner.setParameter(parameter);
        this.workflowList = new ArrayList<>();
    }

    public WorkflowBroker(Class<? extends WorkflowPlannerAbstract> clazz) throws Exception {
        this(clazz, new Parameter());
    }

    public double getPlnElecCost() {
        return planner.getElecCost();
    }

    public double getPlnFinishTime() {
        return planner.getFinishTime();
    }

    public double getPlnRuntime() {
        return planner.getRuntime();
    }

    public void submitWorkflowList(List<Workflow> workflowList) {
        this.workflowList.addAll(workflowList);
    }

    public void submitWorkflow(Workflow workflow) {
        workflowList.add(workflow);
    }

    /**
     * Process the ack received due to a request for VM creation.
     *
     * @param ev a SimEvent object
     * @pre ev != null
     * @post $none
     */
    @Override
    protected void processVmCreateAck(SimEvent ev) {
        int[] data = (int[]) ev.getData();
        int datacenterId = data[0];
        int vmId = data[1];
        int result = data[2];
        GuestEntity guest = VmList.getById(getGuestList(), vmId);
        if (result == CloudSimTags.TRUE) {
            getVmsToDatacentersMap().put(vmId, datacenterId);
            getGuestsCreatedList().add(guest);
            log.info("{}: {}: {} #{} has been created in Datacenter #{}, {} #{}", CloudSim.clock(), getName(), guest.getClassName(), vmId, datacenterId, guest.getHost().getClassName(), guest.getHost().getId());
        } else {
            log.trace("{}: {}: Creation of {} #{} failed in Datacenter #{}", CloudSim.clock(), getName(), guest.getClassName(), vmId, datacenterId);
        }

        incrementVmsAcks();

        // all the requested VMs have been created
        if (getGuestsCreatedList().size() == getGuestList().size() - getVmsDestroyed()) {
            processPlanning();
        } else {
            // all the acks received, but some VMs were not created
            if (getVmsRequested() == getVmsAcks()) {
                // find id of the next datacenter that has not been tried
                for (int nextDatacenterId : getDatacenterIdsList()) {
                    if (!getDatacenterRequestedIdsList().contains(nextDatacenterId)) {
                        createVmsInDatacenter(nextDatacenterId);
                        return;
                    }
                }

                // all datacenters already queried
                if (!getGuestsCreatedList().isEmpty()) { // if some vm were created
                    processPlanning();
                } else { // no vms created. abort
                    log.info("{}: {}: none of the required VMs could be created. Aborting", CloudSim.clock(), getName());
                    finishExecution();
                }
            }
        }
    }

    /**
     * Process a cloudlet return event.
     *
     * @param ev a SimEvent object
     * @pre ev != $null
     * @post $none
     */
    @Override
    protected void processCloudletReturn(SimEvent ev) {
        Cloudlet cloudlet = (Cloudlet) ev.getData();
        Job job = (Job) cloudlet;
        getCloudletReceivedList().add(cloudlet);
        log.info("{}: {}: {} #{} {} return received, the number of finished Cloudlets is {}", CloudSim.clock(), getName(), cloudlet.getClass().getSimpleName(), cloudlet.getCloudletId(), job.getName(), getCloudletReceivedList().size());
        cloudletsSubmitted--;
        if (getCloudletList().isEmpty() && cloudletsSubmitted == 0) { // all cloudlets executed
            log.info("{}: {}: All Cloudlets executed. Finishing...", CloudSim.clock(), getName());
//            clearDatacenters();
            finishExecution();
        } else {
            submitCloudlets();
        }
    }

    /**
     * Submit cloudlets to the created VMs.
     *
     * @pre $none
     * @post $none
     * @see #submitCloudletList(java.util.List)
     */
    @Override
    protected void submitCloudlets() {
        List<Cloudlet> successfullySubmitted = new ArrayList<>();
        for (Cloudlet cloudlet : getCloudletList()) {
            Job job = (Job) cloudlet;
            // if its parents have not been finished, skip it
            if (!new HashSet<>(getCloudletReceivedList()).containsAll(job.getParentList())) {
                continue;
            }
            GuestEntity vm;
            // if user didn't bind this cloudlet and it has not been executed yet
            if (cloudlet.getGuestId() == -1) {
                // randomly select a VM
                vm = ExperimentUtil.getRandomElement(getGuestsCreatedList());
            } else { // submit to the specific vm
                vm = VmList.getById(getGuestsCreatedList(), cloudlet.getGuestId());
                if (vm == null) { // vm was not created
                    vm = VmList.getById(getGuestList(), cloudlet.getGuestId()); // check if exists in the submitted list
                    if (vm != null) {
                        log.info("{}: {}: Postponing execution of cloudlet #{}: bount {} #{} not available", CloudSim.clock(), getName(), cloudlet.getCloudletId(), vm.getClassName(), vm.getId());
                    } else {
                        log.info("{}: {}: Postponing execution of cloudlet #{}: bount guest entity of id {} doesn't exist", CloudSim.clock(), getName(), cloudlet.getCloudletId(), cloudlet.getGuestId());
                    }
                    continue;
                }
            }
            log.info("{}: {}: Sending {} #{} {} to {} #{}", CloudSim.clock(), getName(), cloudlet.getClass().getSimpleName(), cloudlet.getCloudletId(), ((Job) cloudlet).getName(), vm.getClassName(), vm.getId());
            cloudlet.setGuestId(vm.getId());
            sendNow(getVmsToDatacentersMap().get(vm.getId()), CloudActionTags.CLOUDLET_SUBMIT, cloudlet);
            cloudletsSubmitted++;
            getCloudletSubmittedList().add(cloudlet);
            successfullySubmitted.add(cloudlet);
        }
        // remove submitted cloudlets from waiting list
        getCloudletList().removeAll(successfullySubmitted);
    }


    /**
     * Process a request for the characteristics of a Datacenter.
     *
     * @param ev a SimEvent object
     * @pre ev != $null
     * @post $none
     */
    @Override
    protected void processResourceCharacteristicsRequest(SimEvent ev) {
        setDatacenterIdsList(CloudSim.getCloudResourceList());
        setDatacenterCharacteristicsList(new HashMap<>());
        log.info("{}: {}: Cloud Resource List received with {} datacenter(s)", CloudSim.clock(), getName(), getDatacenterIdsList().size());
        for (Integer datacenterId : getDatacenterIdsList()) {
            sendNow(datacenterId, CloudActionTags.RESOURCE_CHARACTERISTICS, getId());
        }
    }


    /**
     * run planning algorithm and pre-assign job to vm
     */
    private void processPlanning() {
        selectHostForLocalInputFile();
        planner.setWorkflowList(new ArrayList<>(workflowList));
        planner.setVmList(new ArrayList<>(getGuestsCreatedList()));
        log.debug("{}: {}: Create {} Vms {}", CloudSim.clock(), getName(), getGuestsCreatedList().size(), getGuestsCreatedList().stream().map(GuestEntity::getId).sorted().toList());
        log.info("{}: {}: Starting planning...", CloudSim.clock(), getName());
        planner.start();
        log.debug("{}: {}: Job schedule sequence {}", CloudSim.clock(), getName(), planner.getSequence().stream().map(Cloudlet::getCloudletId).toList());
        log.info("{}: {}: Starting submitting...", CloudSim.clock(), getName());
        submitCloudletList(planner.getSequence());
        submitCloudlets();
    }


    /**
     * select host for local input file
     */
    private void selectHostForLocalInputFile() {
        for (Workflow workflow : workflowList) {
            for (Job job : workflow.getJobList()) {
                job.setUserId(getId());
                for (File file : job.getLocalInputFileList()) {
                    file.setHost(ExperimentUtil.getRandomElement(getGuestsCreatedList()).getHost());
                }
            }
        }
    }
}
