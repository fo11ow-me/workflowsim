package com.qiujie.config;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HostConfig {
    private String name;
    private int pes;
    private double mips;
}
