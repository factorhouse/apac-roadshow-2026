package com.ververica.composable_job.flink.inventory;

import org.apache.flink.api.common.io.FileInputFormat;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.FileInputSplit;
import org.apache.flink.core.fs.Path;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Input format that reads an entire file as a single String record.
 */
public class WholeFileInputFormat extends FileInputFormat<String> {
    private static final long serialVersionUID = 1L;
    private transient String fileContent;
    private transient boolean hasRead;

    @Override
    public void configure(Configuration parameters) {
        super.configure(parameters);
    }

    @Override
    public void open(FileInputSplit split) throws IOException {
        super.open(split);
        Path filePath = split.getPath();
        
        // Read entire file content
        java.nio.file.Path nioPath = Paths.get(filePath.toUri());
        byte[] bytes = Files.readAllBytes(nioPath);
        fileContent = new String(bytes, StandardCharsets.UTF_8);
        hasRead = false;
    }

    @Override
    public boolean reachedEnd() throws IOException {
        return hasRead;
    }

    @Override
    public String nextRecord(String reuse) throws IOException {
        if (!hasRead) {
            hasRead = true;
            return fileContent;
        }
        return null;
    }
}