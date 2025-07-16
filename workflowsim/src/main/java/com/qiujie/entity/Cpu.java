package com.qiujie.config;

import lombok.Data;

import java.util.List;


@Data
public class Cpu {
    private String name;
    private int pes;
    private double mips;
    private double frequency;
    private List<Freq2Power> freq2PowerList;
}
