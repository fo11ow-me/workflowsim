package com.qiujie.entity;

import lombok.Data;

import java.util.List;


@Data
public class Cpu {
    private String name;
    private int pes;
    private double mips;
    private double frequency;
    private List<Fv> fvList;

    @Data
    public static class Fv {
        private double frequency;
        private double mips;
        private double power;
        private double lambda;
        private int level;
        private String type;
    }
}
