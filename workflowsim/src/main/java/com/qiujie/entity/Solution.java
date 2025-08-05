package com.qiujie.entity;


import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;


import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Accessors(chain = true)
public class Solution {

    private static final AtomicInteger nextId = new AtomicInteger(0);

    @Getter
    private final int id;

    private final Map<Job, Fv> map;

    @Getter
    private final List<Job> sequence;

    @Getter
    @Setter
    private double elecCost;

    @Getter
    @Setter
    private double reliability;

    @Getter
    @Setter
    private double finishTime;


    public Solution() {
        this.id = nextId.getAndIncrement();
        map = new HashMap<>();
        sequence = new ArrayList<>();
    }

    public Map<Job, Fv> getResult() {
        return Collections.unmodifiableMap(map);
    }

    public void bindJobToFv(Job job, Fv fv) {
        map.put(job, fv);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Solution solution = (Solution) o;
        return id == solution.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        String mapStr = getResult().entrySet().stream()
                .map(e -> String.format("%d → %d (L%d)",
                        e.getKey().getCloudletId(),
                        e.getValue().getVm().getId(),
                        e.getValue().getLevel()))
                .collect(Collectors.joining(", "));

        return String.format(
                "Solution{id = %d, elecCost = %.2f, reliability = %.4f, finishTime = %.2f, sequence = %s, map = [%s]}",
                id, elecCost, reliability, finishTime, sequence.stream().map(Job::getCloudletId).toList(), mapStr
        );
    }
}
