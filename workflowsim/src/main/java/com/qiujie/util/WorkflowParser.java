
package com.qiujie.util;

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
        java.io.File daxFile = new java.io.File(DAX_DIR + dax + ".xml");
        if (!daxFile.exists()) {
            throw new IllegalStateException("DAX file not found: " + daxFile.getAbsolutePath());
        }
        Document dom;
        try {
            dom = new SAXBuilder().build(daxFile);
        } catch (JDOMException | IOException e) {
            throw new RuntimeException(e);
        }
        Element root = dom.getRootElement();
        Map<String, Job> idJobMap = new LinkedHashMap<>();
        Map<Job, List<File>> jobInputFileListMap = new HashMap<>();
        for (Element node : root.getChildren()) {
            switch (node.getName().toLowerCase()) {
                case "job":
                    String id = node.getAttributeValue("id");
                    double runtime = Math.max(Double.parseDouble(node.getAttributeValue("runtime")), 0);
                    Job job = new Job(dax + "_" + id, (long) runtime);
                    List<File> inputFileList = new ArrayList<>();
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
                                        inputFileList.add(new File(file, size));
                                        fileNameSet.add(file);
                                    }
                                    break;
                                case "output":
                                    if (!fileNameSet.contains(file)) {
                                        job.getOutputFileList().add(new File(file, size));
                                        fileNameSet.add(file);
                                    }
                                    break;
                                default:
                                    throw new IllegalStateException("Cannot identify file type");
                            }
                        }
                    }
                    idJobMap.put(id, job);
                    jobInputFileListMap.put(job, inputFileList);
                    break;
                case "child":
                    String childId = node.getAttributeValue("ref");
                    if (idJobMap.containsKey(childId)) {
                        Job childJob = idJobMap.get(childId);
                        for (Element parent : node.getChildren()) {
                            String parentId = parent.getAttributeValue("ref");
                            if (idJobMap.containsKey(parentId)) {
                                Job parentJob = idJobMap.get(parentId);
                                parentJob.addChild(childJob);
                                childJob.addParent(parentJob);
                            }
                        }
                    }
                    break;
            }
        }
        List<Job> jobList = new ArrayList<>(idJobMap.values());
        settingDepth(jobList);
        identifyInputFile(jobList, jobInputFileListMap);
        return new Workflow(dax, jobList);
    }

    /**
     * Set the depth of each job
     */
    private static void settingDepth(List<Job> jobList) {
        for (Job job : jobList) {
            job.setDepth(-1); // -1 means unset
        }
        for (Job job : jobList) {
            if (job.getParentList().isEmpty()) {
                updateDepth(job, 0);
            }
        }
    }

    private static void updateDepth(Job job, int depth) {
        if (depth > job.getDepth()) {
            job.setDepth(depth);
            for (Job child : job.getChildList()) {
                updateDepth(child, depth + 1);
            }
        }
    }

    /**
     * identify local or pred input file
     */
    private static void identifyInputFile(List<Job> jobList, Map<Job, List<File>> jobInputFileListMap) {
        for (Job job : jobList) {
            List<File> inputFileList = jobInputFileListMap.get(job);
            Set<String> parentsOutputFileNameSet = job.getParentList().stream().flatMap(parent -> parent.getOutputFileList().stream()).map(File::getName).collect(Collectors.toSet());
            List<File> predInputFileList = new ArrayList<>();
            for (File file : inputFileList) {
                if (parentsOutputFileNameSet.contains(file.getName())) {
                    predInputFileList.add(file);
                } else {
                    job.getLocalInputFileList().add(file);
                }
            }
            for (Job parent : job.getParentList()) {
                Set<String> parentOutputFileNameSet = parent.getOutputFileList().stream().map(File::getName).collect(Collectors.toSet());
                List<File> fileList = predInputFileList.stream().filter(file -> parentOutputFileNameSet.contains(file.getName())).toList();
                job.getPredInputFilesMap().put(parent, fileList);
            }
        }
    }

}
