/**
 * Copyright 2012-2013 University Of Southern California
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.qiujie.entity;

import lombok.Getter;
import lombok.Setter;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Vm;

import java.util.List;
import java.util.Objects;

/**
 * a vm of  supporting dvfs
 */

@Setter
@Getter
public class DvfsVm extends Vm {

    private double frequency;

    private String cpu;

    private List<Fv> fvList;

    /**
     * Create a vm.
     *
     * @param id                ID of the VM
     * @param userId            ID of the user that owns this VM
     * @param mips              the mips
     * @param numberOfPes       amount of CPUs
     * @param ram               amount of ram
     * @param bw                amount of bandwidth
     * @param size              amount of storage
     * @param vmm               virtual machine monitor
     * @param cloudletScheduler cloudletScheduler policy for cloudlets
     * @pre id >= 0
     * @pre userId >= 0
     * @pre size > 0
     * @pre ram > 0
     * @pre bw > 0
     * @pre cpus > 0
     * @pre priority >= 0
     * @pre cloudletScheduler != null
     * @post $none
     */
    public DvfsVm(int id, int userId, double mips, int numberOfPes, double frequency, int ram, long bw, long size, String vmm, CloudletScheduler cloudletScheduler) {
        super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);
        setFrequency(frequency);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DvfsVm dvfsVm = (DvfsVm) o;
        return getId() == dvfsVm.getId();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
