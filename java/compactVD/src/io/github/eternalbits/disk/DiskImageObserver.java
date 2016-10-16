/*
 * Copyright 2016 Rui Baptista
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.eternalbits.disk;

/**
 * Specific interface like {@link java.util.Observer} that allows observers
 *  to declare what type of changes they are interested in being notified.
 *
 * @see	DiskImage#addObserver(DiskImageObserver, boolean)
 */
public interface DiskImageObserver {
	/**
	 * This method is called whenever there is a progress in a task executed 
	 *  by the disk image, or the disk image has changes when executing a task.
	 *
	 * @param	image	The observable disk image.
	 * @param	arg		An argument passed to the {@code notifyProgress}
	 * 						or the {@code notifyChange} method.
	 */
	void update(DiskImage image, Object arg);
}
