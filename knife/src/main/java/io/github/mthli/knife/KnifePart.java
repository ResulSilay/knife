/*
 * Copyright (C) 2015 Matthew Lee
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.mthli.knife;

public class KnifePart {
    private final int start;
    private final int end;
    private int value;
    private String valueString;
    private float valueFloat;

    public KnifePart(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public KnifePart(int value, int start, int end) {
        this.value = value;
        this.start = start;
        this.end = end;
    }

    public KnifePart(String value, int start, int end) {
        this.valueString = value;
        this.start = start;
        this.end = end;
    }

    public KnifePart(float value, int start, int end) {
        this.valueFloat = value;
        this.start = start;
        this.end = end;
    }

    public int getValue() {
        return value;
    }

    public float getValueFloat() {
        return valueFloat;
    }

    public String getValueString() {
        return valueString;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public boolean isValid() {
        return start < end;
    }
}
