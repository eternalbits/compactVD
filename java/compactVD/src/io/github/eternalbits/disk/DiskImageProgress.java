/*
 * Copyright 2016 Rui Baptista
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

package io.github.eternalbits.disk;

/**
 * A read-only progress of a {@link DiskImage} task. All fields are public and final.
 * <p>
 */
public class DiskImageProgress {
	
	/** This is not a progress value */
	public static final int NO_TASK = 0;
	/** This is the progress value of {@link DiskImage#optimize(int)}. */
	public static final int OPTIMIZE = 1;
	/** This is the progress value of {@link DiskImage#compact()}. */
	public static final int COMPACT = 2;
	/** This is the progress value of {@link DiskImage#copy(DiskImage)}. */
	public static final int COPY = 3;

	/** The running or completed task. Can be one of:
	 * <ul>
	 * <li>{@link #OPTIMIZE}
	 * <li>{@link #COMPACT}
	 * <li>{@link #COPY}
	 * </ul>
	*/
	public final int task;
	
	/** A long value with the starting time of the {@link #task} in milliseconds. */
	public final long start;

	/** A boolean value indicating if the {@link #task} is complete. */
	public final boolean done;

	/** A float value between 0 and 1 with the progress of the {@link #task}. */
	public final float value;

	/**
	 * Initializes a new {@code DiskImageProgress} that represents a task in progress.
	 * 
	 * @param task		The running {@link #task}.
	 * @param start		Starting time of the {@code #task}.
	 * @param value		A float value between 0 and 1.
	 */
	DiskImageProgress(int task, long start, float value) {
		this.task 	= task;
		this.start	= start;
		this.value 	= value;
		this.done	= false;
	}

	/**
	 * Initializes a new {@code DiskImageProgress} that represents a completed task.
	 * 
	 * @param task		The completed {@link #task}.
	 * @param start		Starting time of the {@code #task}.
	 */
	DiskImageProgress(int task, long start) {
		this.task 	= task;
		this.start	= start;
		this.value 	= 1F;
		this.done	= true;
	}

	/**
	 * Initializes a new {@code DiskImageProgress} that doesn't represent a task.
	 */
	public DiskImageProgress() {
		this.task 	= NO_TASK;
		this.start	= 0L;
		this.value 	= 1F;
		this.done	= true;
	}
}
