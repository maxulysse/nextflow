/*
 * Copyright (c) 2020-2021. Seqera Labs, S.L.
 *
 * All Rights reserved
 *
 */

package nextflow.cloud.google.batch

import java.nio.file.Path
import java.nio.file.Paths

import com.google.cloud.storage.contrib.nio.CloudStoragePath
import groovy.transform.CompileStatic
import nextflow.cloud.google.batch.model.TaskVolume
import nextflow.executor.BashWrapperBuilder
import nextflow.processor.TaskBean
import nextflow.processor.TaskRun
import nextflow.util.Escape
import nextflow.util.PathTrie

/**
 * Implement Nextflow task launcher script
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class GoogleBatchScriptLauncher extends BashWrapperBuilder {

    private static final String MOUNT_ROOT = '/mnt'

    private CloudStoragePath remoteWorkDir
    private Path remoteBinDir
    private Set<String> buckets = new HashSet<>()
    private PathTrie pathTrie = new PathTrie()

    GoogleBatchScriptLauncher(TaskBean bean, Path remoteBinDir) {
        super(bean)
        // keep track the google storage work dir
        this.remoteWorkDir = (CloudStoragePath) bean.workDir
        this.remoteBinDir = toContainerMount(remoteBinDir)

        // map bean work and target dirs to container mount
        // this needed to create the command launcher using container local file paths
        bean.workDir = toContainerMount(bean.workDir)
        bean.targetDir = toContainerMount(bean.targetDir)

        // remap input files to container mounted paths
        for( Map.Entry<String,Path> entry : new HashMap<>(bean.inputFiles).entrySet() ) {
            bean.inputFiles.put( entry.key, toContainerMount(entry.value, true) )
        }

        // include task script as an input to force its staging in the container work directory
        bean.inputFiles[TaskRun.CMD_SCRIPT] = bean.workDir.resolve(TaskRun.CMD_SCRIPT)
        // add the wrapper file when stats are enabled
        // NOTE: this must match the logic that uses the run script in BashWrapperBuilder
        if( isTraceRequired() ) {
            bean.inputFiles[TaskRun.CMD_RUN] = bean.workDir.resolve(TaskRun.CMD_RUN)
        }
        // include task stdin file
        if( bean.input != null ) {
            bean.inputFiles[TaskRun.CMD_INFILE] = bean.workDir.resolve(TaskRun.CMD_INFILE)
        }

        // make it change to the task work dir
        bean.headerScript = headerScript(bean)
        // enable use of local scratch dir
        if( !scratch )
            scratch = true
    }

    protected String headerScript(TaskBean bean) {
        def result = "NXF_CHDIR=${Escape.path(bean.workDir)}\n"
        if( remoteBinDir ) {
            result += "cp -r $remoteBinDir \$HOME/.nextflow-bin\n"
            result += 'chmod +x $HOME/.nextflow-bin/*\n'
            result += 'export PATH=$HOME/.nextflow-bin:$PATH\n'
        }
        return result
    }

    protected Path toContainerMount(Path path, boolean parent=false) {
        if( path instanceof CloudStoragePath ) {
            buckets.add(path.bucket())
            pathTrie.add( (parent ? "/${path.bucket()}${path.parent}" : "/${path.bucket()}${path}").toString() )
            return Paths.get("$MOUNT_ROOT/${path.bucket()}${path}")
        }
        else if( path==null )
            return null
        throw new IllegalArgumentException("Unexpected path for Google Batch task handler: ${path.toUriString()}")
    }

    List<String> getContainerMounts() {
        final result = new ArrayList(10)
        for( String it : pathTrie.longest() ) {
            result.add("$MOUNT_ROOT$it:$MOUNT_ROOT$it:rw".toString() )
        }
        return result
    }

    String getWorkDirMount() {
        return workDir.toString()
    }

    List<TaskVolume> getTaskVolumes() {
        final result = new ArrayList(10)
        final opts = ["-o rw,allow_other", "-implicit-dirs"]
        for( String it : buckets ) {
            result << new TaskVolume(gcs:[remotePath: it], mountPath: "${MOUNT_ROOT}/$it".toString(), mountOptions: opts)
        }
        return result
    }

    @Override
    protected Path targetWrapperFile() {
        return remoteWorkDir.resolve(TaskRun.CMD_RUN)
    }

    @Override
    protected Path targetScriptFile() {
        return remoteWorkDir.resolve(TaskRun.CMD_SCRIPT)
    }

    @Override
    protected Path targetInputFile() {
        return remoteWorkDir.resolve(TaskRun.CMD_INFILE)
    }

}