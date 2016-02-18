/*
 * This file is part of SpongeAPI, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.api.text.serializer;

import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializer;
import org.spongepowered.api.text.LiteralText;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.TextTemplate;
import org.spongepowered.api.text.format.TextFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a {@link TypeSerializer} for {@link TextTemplate}s. TextTemplates
 * are serialized in two parts.
 *
 * <p>First, the template's arguments as defined by
 * {@link TextTemplate#getArguments()} are serialized to the "arguments" node.
 * This is where the argument definitions are kept.</p>
 *
 * <p>Second, the template's text representation as defined by
 * {@link TextTemplate#toText()} is serialized to the "content" node.</p>
 *
 * <p>Deserialization is a bit more complicated. We start by loading the
 * "content" Text and check the root Text element as well as it's children. If
 * a {@link LiteralText} value is found that is wrapped in curly braces we
 * check to see if the value inside the braces is defined as an argument in the
 * "arguments" nodes. If so, we use the name and format from the original
 * LiteralText and obtain whether the argument is optional from the definition.
 * This is repeated until there are no more Text children to check and we
 * return a TextTemplate of the elements we have collected.</p>
 */
public class TextTemplateConfigSerializer implements TypeSerializer<TextTemplate> {

    private static final String ARG_OPEN = "{";
    private static final String ARG_CLOSE = "}";

    private static final String NODE_CONTENT = "content";
    private static final String NODE_ARGS = "arguments";
    private static final String NODE_OPT = "optional";

    @Override
    public TextTemplate deserialize(TypeToken<?> type, ConfigurationNode value) throws ObjectMappingException {
        Text content = value.getNode(NODE_CONTENT).getValue(TypeToken.of(Text.class));
        List<Object> elements = new ArrayList<>();
        elements.add(content.getFormat());
        if (isArg(content, value)) {
            elements.add(parseArg((LiteralText) content, value));
        }

        for (Text child : content.getChildren()) {
            if (isArg(child, value)) {
                elements.add(parseArg((LiteralText) child, value));
            } else {
                elements.add(child);
            }
        }

        return TextTemplate.of(elements.toArray(new Object[elements.size()]));
    }

    @Override
    public void serialize(TypeToken<?> type, TextTemplate obj, ConfigurationNode value) throws ObjectMappingException {
        value.getNode(NODE_ARGS).setValue(new TypeToken<Map<String, TextTemplate.Arg>>() {}, obj.getArguments());
        value.getNode(NODE_CONTENT).setValue(TypeToken.of(Text.class), obj.toText());
    }

    private static boolean isArg(Text element, ConfigurationNode root) {
        // Returns true if the element is enclosed by curly braces and the parameter is defined
        if (!(element instanceof LiteralText)) {
            return false;
        }
        String literal = ((LiteralText) element).getContent();
        return literal.startsWith(ARG_OPEN) && literal.endsWith(ARG_CLOSE)
                && isArgDefined(unwrap(literal), root);
    }

    private static TextTemplate.Arg.Builder parseArg(LiteralText source, ConfigurationNode root) {
        // Creates a new Arg from Text source
        // Assumption: isArg(source, root) returns true
        String name = unwrap(source.getContent());
        boolean optional = root.getNode(NODE_ARGS, name, NODE_OPT).getBoolean();
        TextFormat format = source.getFormat();
        return TextTemplate.arg(name).format(format).optional(optional);
    }

    private static String unwrap(String str) {
        // Unwraps an argument from curly braces
        return str.substring(1, str.length() - 1);
    }


    private static boolean isArgDefined(String argName, ConfigurationNode root) {
        // Returns true if the arg is defined in the "arguments" node
        return !root.getNode(NODE_ARGS, argName).isVirtual();
    }
}
