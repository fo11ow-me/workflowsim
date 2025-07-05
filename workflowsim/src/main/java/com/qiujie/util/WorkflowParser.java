
package com.qiujie.util;

import com.qiujie.Constants;
import com.qiujie.entity.File;
import com.qiujie.entity.Job;
import com.qiujie.entity.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.qiujie.Constants.*;


@Slf4j
public class WorkflowParser {


    /**
     * Parse the xml file and return workflow
     *
     * @param dax file dax
     * @return workflow
     */
    public static Workflow parse(String dax) {
        // parse using builder to get DOM representation of the XML file
        java.io.File daxFile = new java.io.File(DAX_DIR + dax + ".xml");
        if (!daxFile.exists()) {
            throw new RuntimeException("Warning: dax " + daxFile.getAbsolutePath() + " not exist");
        }
        Document dom;
        try {
            dom = new SAXBuilder().build(daxFile);
        } catch (JDOMException | IOException e) {
            throw new RuntimeException(e);
        }
        Element root = dom.getRootElement();
        Map<String, Job> nodeMap = new LinkedHashMap<>();
        for (Element node : root.getChildren()) {
            switch (node.getName().toLowerCase()) {
                case "job":
                    String id = node.getAttributeValue("id");
                    long length = 0;
                    String runtime = node.getAttributeValue("runtime");
                    if (runtime != null) {
//                        length = Math.max((long) (Constants.LENGTH_FACTOR * Double.parseDouble(runtime)), 1);
                        length = (long) Math.max(Double.parseDouble(runtime), 1);
                    } else {
                        log.error("Cannot find runtime for " + id);
                    }
                    Job job = new Job(dax + "_" + id, length);
                    for (Element fileNode : node.getChildren()) {
                        if (fileNode.getName().equalsIgnoreCase("uses")) {
                            String fileName = fileNode.getAttributeValue("name"); // DAX version 3.3
                            if (fileName == null) {
                                fileName = fileNode.getAttributeValue("file"); // DAX version 3.0
                            }
                            if (fileName == null) {
                                log.error("File name not found");
                            }
                            String link = fileNode.getAttributeValue("link");
                            double size = 0.0;
                            String fileSize = fileNode.getAttributeValue("size");
                            if (fileSize != null) {
                                size = Math.max(Double.parseDouble(fileSize), 1);
                            } else {
                                log.warn("File size not found for " + fileName);
                            }
                            switch (link) {
                                case "input":
                                    job.getPredInputFileList().add(new File(fileName, size));
                                    break;
                                case "output":
                                    job.getOutputFileList().add(new File(fileName, size));
                                    break;
                                default:
                                    log.warn("Cannot identify file type");
                                    break;
                            }
                        }
                    }
                    nodeMap.put(id, job);

                    break;
                case "child":
                    String childName = node.getAttributeValue("ref");
                    if (nodeMap.containsKey(childName)) {
                        Job childJob = nodeMap.get(childName);
                        for (Element parent : node.getChildren()) {
                            String parentName = parent.getAttributeValue("ref");
                            if (nodeMap.containsKey(parentName)) {
                                Job parentJob = nodeMap.get(parentName);
                                parentJob.addChild(childJob);
                                childJob.addParent(parentJob);
                            }
                        }
                    }
                    break;
            }
        }

        setDepth(nodeMap);
        List<Job> jobList = new ArrayList<>(nodeMap.values());
        identifyLocalInputFile(jobList);
        return new Workflow(dax, jobList);
    }


    private static void setDepth(Map<String, Job> nodeMap) {
        // If a job has no parent, then it is root job.
        List<Job> rootList = new ArrayList<>();
        for (Job job : nodeMap.values()) {
            job.setDepth(0);
            if (job.getParentList().isEmpty()) {
                rootList.add(job);
            }
        }

        for (Job job : rootList) {
            setDepth(job, 0);
        }
    }


    /**
     * Set the depth of each job
     *
     * @param job
     * @param depth
     */
    private static void setDepth(Job job, int depth) {
        if (job.getDepth() < depth) {
            job.setDepth(depth);
        }
        for (Job child : job.getChildList()) {
            setDepth(child, job.getDepth() + 1);
        }
    }


    /**
     * indentify local input file
     *
     * @param jobList
     */
    private static void identifyLocalInputFile(List<Job> jobList) {
        for (Job job : jobList) {
            // avoid concurrent modification
            List<File> predInputFileListCopy = new ArrayList<>(job.getPredInputFileList());
            Set<String> parentOutputFileNameList = job.getParentList().stream().flatMap(parent -> parent.getOutputFileList().stream()).map(File::getName).collect(Collectors.toSet());
            for (File file : predInputFileListCopy) {
                if (!parentOutputFileNameList.contains(file.getName())) {
                    job.getLocalInputFileList().add(file);
                    job.getPredInputFileList().remove(file); // now remove safely
                }
            }
        }
    }
}
