/*
 * Copyright 2004-2008 the original author or authors.
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
package org.codehaus.groovy.grails.web.json;

import static org.codehaus.groovy.grails.web.json.JSONWriter.Mode.*;

import java.io.IOException;
import java.io.Writer;


/**
 * A JSONWriter dedicated to create indented/pretty printed output.
 *
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class PrettyPrintJSONWriter extends JSONWriter {

    public static final String DEFAULT_INDENT_STR = "  ";

    public static final String NEWLINE;
    static {
        String nl = System.getProperty("line.separator");
        NEWLINE = nl != null ? nl : "\n";
    }

    private int indentLevel = 0;

    private final String indentStr;


    public PrettyPrintJSONWriter(Writer w) {
        this(w, DEFAULT_INDENT_STR);
    }

    public PrettyPrintJSONWriter(Writer w, String indentStr) {
        super(w);
        this.indentStr = indentStr;
    }

    private void newline() {
            try {
                writer.write(NEWLINE);
            } catch (IOException e) {
                throw new JSONException(e);
            }
    }

    private void indent() {
        try {
            for (int i = 0; i < indentLevel; i++)
                writer.write(indentStr);
        } catch (IOException e) {
            throw new JSONException(e);
        }
    }

    @Override
    protected JSONWriter append(String s) {
        if (s == null) {
            throw new JSONException("Null pointer");
        }
        if (mode == OBJECT || mode == ARRAY) {
            try {
                if (comma && mode == ARRAY) {
                    comma();
                }
                if(mode == ARRAY) {
                    newline();
                    indent();
                }
                writer.write(s);
            } catch (IOException e) {
                throw new JSONException(e);
            }
            if (mode == OBJECT) {
                mode = KEY;
            }
            comma = true;
            return this;
        }
        throw new JSONException("Value out of sequence.");
    }

    @Override
    protected JSONWriter end(Mode m, char c) {
        newline();
        indent();
        return super.end(m, c);
    }

    @Override
    protected void comma() {
        super.comma();
    }

    @Override
    public JSONWriter array() {
        super.array();
        indentLevel++;
        return this;
    }

    @Override
    public JSONWriter endArray() {
        indentLevel--;
        super.endArray();
        return this;
    }

    @Override
    public JSONWriter object() {
        super.object();
        indentLevel++;
        return this;
    }

    @Override
    public JSONWriter endObject() {
        indentLevel--;
        super.endObject();
        return this;
    }

    @Override
    public JSONWriter key(String s) {
        if (s == null) {
            throw new JSONException("Null key.");
        }
        if (mode == KEY) {
            try {
                if (comma) {
                    comma();
                }
                newline();
                indent();
                writer.write(JSONObject.quote(s));
                writer.write(": ");
                comma = false;
                mode = OBJECT;
                return this;
            } catch (IOException e) {
                throw new JSONException(e);
            }
        }
        throw new JSONException("Misplaced key.");
    }
}
