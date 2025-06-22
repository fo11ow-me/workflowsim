package com.qiujie.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;


@Accessors(chain = true)
@Data
public class Task implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    private int id;
    private String name;
    private String startTime;
    private String endTime;
    private int vmId;
    private List<Integer> childList;
    private int depth;
}
