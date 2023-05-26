/*
 * Copyright 2013-2023, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.executor

import java.nio.file.Path

import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun

/**
 * Interface for executors that support array jobs.
 *
 * @author Ben Sherman <bentshermann@gmail.com>
 */
interface TaskArrayAware {

    String getName()

    Path getWorkDir()

    void submit( TaskRun task )

    TaskHandler createTaskHandler(TaskRun task)

    boolean isFusionEnabled()

    /**
     * Get the environment variable name that provides the array index of a task.
     */
    default String getArrayIndexName() {
        throw new UnsupportedOperationException("Executor '${getName()}' does not support array jobs")
    }

}
