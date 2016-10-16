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
 * This exception is thrown by an object when the resources passed to the
 * 	constructor are definitely of the wrong type. Factories should catch
 * 	this exception and try the next implementation.
 */
public class WrongHeaderException extends Exception {
	private static final long serialVersionUID = -5062102812893502785L;
	
	public WrongHeaderException(Class<?> err, String src) {
		super(String.format("%s (Mising or invalid value found in %s)", src, err.getSimpleName()));
	}
}
