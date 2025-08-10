package com.qiujie.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.*;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class Dax {

    private String name;
    private int jobNum;
    private long length;
    private int depth;
    private List<Job> jobList;

    public Dax(String name, List<Job> jobList) {
        this.name = name;
        this.jobList = jobList;
        this.jobNum = jobList.size();
        this.length = jobList.stream().mapToLong(Dax.Job::getLength).sum();
        this.depth = jobList.stream().mapToInt(Dax.Job::getDepth).max().getAsInt();
    }


    @Data
    @Accessors(chain = true)
    public static class Job {
        private String name;
        private long length;
        private int depth;
        private List<String> parentList;
        private List<String> childList;
        private Map<String, List<File>> predInputFilesMap;
        private List<File> localInputFileList;
        private List<File> outputFileList;

        public Job() {
            this.parentList = new ArrayList<>();
            this.childList = new ArrayList<>();
            this.predInputFilesMap = new HashMap<>();
            this.localInputFileList = new ArrayList<>();
            this.outputFileList = new ArrayList<>();
        }


        public void addChild(String name) {
            if (!this.childList.contains(name)) {
                this.childList.add(name);
            }
        }

        public void addParent(String name) {
            if (!this.parentList.contains(name)) {
                this.parentList.add(name);
            }
        }


    }


    @Data
    @Accessors(chain = true)
    public static class File {
        private String name;
        private double size;
    }


}
