package com.qiujie.util;

import com.qiujie.entity.Job;
import com.qiujie.entity.Workflow;
import com.qiujie.entity.Dax;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DaxParser {

    public static Dax parse(java.io.File daxFile) {
        Document dom;
        try {
            dom = new SAXBuilder().build(daxFile);
        } catch (JDOMException | IOException e) {
            throw new RuntimeException(e);
        }
        Element root = dom.getRootElement();
        Map<String, Dax.Job> JobMap = new LinkedHashMap<>();
        Map<String, List<Dax.File>> jobInputFileListMap = new HashMap<>();
        String name = ExperimentUtil.getFilenameNoExt(daxFile);
        for (Element node : root.getChildren()) {
            switch (node.getName().toLowerCase()) {
                case "job":
                    String id = node.getAttributeValue("id");
                    double runtime = Math.max(Double.parseDouble(node.getAttributeValue("runtime")), 0);
                    Dax.Job job = new Dax.Job().setName(name + "_" + id).setLength((long) runtime);
                    List<Dax.File> inputFileList = new ArrayList<>();
                    Set<String> fileNameSet = new HashSet<>(); // avoid duplicate file
                    for (Element fileNode : node.getChildren()) {
                        if (fileNode.getName().equalsIgnoreCase("uses")) {
                            String file = fileNode.getAttributeValue("file");
                            if (file == null) {
                                throw new IllegalStateException("File name not found");
                            }
                            String link = fileNode.getAttributeValue("link");
                            double size = Math.max(Double.parseDouble(fileNode.getAttributeValue("size")), 0);
                            switch (link) {
                                case "input":
                                    if (!fileNameSet.contains(file)) {
                                        inputFileList.add(new Dax.File().setName(file).setSize(size));
                                        fileNameSet.add(file);
                                    }
                                    break;
                                case "output":
                                    if (!fileNameSet.contains(file)) {
                                        job.getOutputFileList().add(new Dax.File().setName(file).setSize(size));
                                        fileNameSet.add(file);
                                    }
                                    break;
                                default:
                                    throw new IllegalStateException("Cannot identify file type");
                            }
                        }
                    }
                    JobMap.put(job.getName(), job);
                    jobInputFileListMap.put(job.getName(), inputFileList);
                    break;
                case "child":
                    String childName = name + "_" + node.getAttributeValue("ref");
                    if (JobMap.containsKey(childName)) {
                        Dax.Job childJob = JobMap.get(childName);
                        for (Element parent : node.getChildren()) {
                            String parentName = name + "_" + parent.getAttributeValue("ref");
                            if (JobMap.containsKey(parentName)) {
                                Dax.Job parentJob = JobMap.get(parentName);
                                parentJob.addChild(childName);
                                childJob.addParent(parentName);
                            }
                        }
                    }
                    break;
            }
        }
        setDepth(JobMap);
        identifyInputFile(jobInputFileListMap, JobMap);
        return new Dax(name, new ArrayList<>(JobMap.values()));
    }

    /**
     * Set the depth of each job
     */
    private static void setDepth(Map<String, Dax.Job> jobMap) {

        Map<String, Integer> inDegree = new HashMap<>();
        for (Dax.Job job : jobMap.values()) {
            inDegree.put(job.getName(), job.getParentList().size());
            job.setDepth(0);
        }

        Queue<Dax.Job> queue = new LinkedList<>();
        for (Dax.Job job : jobMap.values()) {
            if (inDegree.get(job.getName()) == 0) {
                queue.add(job);
            }
        }

        // topological sort
        while (!queue.isEmpty()) {
            Dax.Job job = queue.poll();
            int currentDepth = job.getDepth();

            for (String childName : job.getChildList()) {
                Dax.Job child = jobMap.get(childName);

                // update child's depth
                if (child.getDepth() < currentDepth + 1) {
                    child.setDepth(currentDepth + 1);
                }

                // decrease in-degree
                inDegree.put(childName, inDegree.get(childName) - 1);
                if (inDegree.get(childName) == 0) {
                    queue.add(child);
                }
            }
        }
    }


    /**
     * identify local or pred input file
     */
    private static void identifyInputFile(Map<String, List<Dax.File>> jobInputFileListMap, Map<String, Dax.Job> jobMap) {
        for (Dax.Job job : jobMap.values()) {
            Set<String> parentsOutputFileNameSet = job.getParentList().stream().flatMap(parent -> jobMap.get(parent).getOutputFileList().stream()).map(Dax.File::getName).collect(Collectors.toSet());
            List<Dax.File> inputFileList = jobInputFileListMap.get(job.getName());
            List<Dax.File> predInputFileList = new ArrayList<>();
            for (Dax.File file : inputFileList) {
                if (parentsOutputFileNameSet.contains(file.getName())) {
                    predInputFileList.add(file);
                } else {
                    job.getLocalInputFileList().add(file);
                }
            }
            for (String id : job.getParentList()) {
                Dax.Job parent = jobMap.get(id);
                Set<String> parentOutputFileNameSet = parent.getOutputFileList().stream().map(Dax.File::getName).collect(Collectors.toSet());
                List<Dax.File> fileList = predInputFileList.stream().filter(file -> parentOutputFileNameSet.contains(file.getName())).toList();
                job.getPredInputFilesMap().put(parent.getName(), fileList);
            }
        }
    }
}
