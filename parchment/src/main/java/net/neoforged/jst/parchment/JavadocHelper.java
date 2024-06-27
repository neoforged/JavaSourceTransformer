package net.neoforged.jst.parchment;

import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocToken;
import net.neoforged.jst.api.PsiHelper;
import net.neoforged.jst.api.Replacement;
import net.neoforged.jst.api.Replacements;
import org.jetbrains.annotations.Nullable;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class JavadocHelper {

    private static final List<String> TAGS_ORDER = List.of(
            "author",
            "version",
            "param",
            "return",
            "throws",
            "exception",
            "see",
            "since",
            "serial",
            "deprecated"
    );

    private static final Comparator<String> TAGS_COMPARATOR = Comparator.comparingInt(text -> {
        var idx = TAGS_ORDER.indexOf(text);
        return idx != -1 ? idx : Integer.MAX_VALUE;
    });

    private JavadocHelper() {
    }

    public static void enrichJavadoc(PsiJavaDocumentedElement psiElement,
                                     List<String> javadoc,
                                     Replacements replacements) {
        enrichJavadoc(psiElement, javadoc, Map.of(), Map.of(), List.of(), replacements);
    }

    public static void enrichJavadoc(PsiJavaDocumentedElement psiElement,
                                     List<String> javadoc,
                                     Map<String, String> parameters,
                                     Map<String, String> renamedParameters,
                                     List<String> parameterOrder,
                                     Replacements replacements) {

        var existingDocComment = psiElement.getDocComment();
        if (existingDocComment != null) {

            // If no parameter documentation or javadoc is given, and no parameters were renamed, don't bother
            if (javadoc.isEmpty() && parameters.isEmpty() && renamedParameters.isEmpty()) {
                return;
            }

            // Merge the existing body + new lines
            var bodyLines = getMergedJavadocBody(existingDocComment, javadoc);

            // Collect tags
            List<JavadocTag> tags = new ArrayList<>();
            var parameterDocs = new HashMap<String, String>();
            for (var tag : existingDocComment.getTags()) {
                var name = tag.getName();
                if ("param".equalsIgnoreCase(name) && tag.getValueElement() != null) {
                    var paramName = tag.getValueElement().getText().trim();
                    var paramDocText = assembleTagText(tag.getValueElement().getNextSibling());

                    // Consider references to renamed parameters
                    paramName = renamedParameters.getOrDefault(paramName, paramName);

                    parameterDocs.put(paramName, paramDocText);
                    continue;
                }

                var text = assembleTagText(tag.getNameElement().getNextSibling());
                tags.add(new JavadocTag(tag.getName(), null, text));
            }

            // Add new parameter docs
            parameterDocs.putAll(parameters);

            var indent = JavadocHelper.getIndent(existingDocComment);
            replacements.replace(
                    existingDocComment,
                    JavadocHelper.formatJavadoc(indent, bodyLines, tags, parameterDocs, parameterOrder)
            );
        } else {

            // If no parameter documentation or javadoc is given
            if (javadoc.isEmpty() && parameters.isEmpty()) {
                return;
            }

            int indent = 0;
            // If the element is preceded by whitespace, use the last line of that whitespace as the indent
            if (psiElement.getPrevSibling() instanceof PsiWhiteSpace psiWhiteSpace) {
                indent = PsiHelper.getLastLineLength(psiWhiteSpace);
            }
            replacements.insertBefore(
                    psiElement,
                    JavadocHelper.formatJavadoc(indent, javadoc, List.of(), parameters, parameterOrder)
                            // We have to make an indent part of the replacement since it will now be
                            // in front of our comment, making the original element unindented
                            + "\n" + " ".repeat(indent)
            );
        }
    }

    private static String assembleTagText(PsiElement startingElement) {
        var paramDocBuilder = new StringBuilder();
        for (var child = startingElement; child != null; child = child.getNextSibling()) {
            if (!(child instanceof PsiDocToken docToken) || docToken.getTokenType() != JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
                var lineText = child.getText();
                paramDocBuilder.append(lineText);
            }
        }
        // Whitespace collapse and trim
        Pattern p = Pattern.compile("\\s+");
        Matcher m = p.matcher(paramDocBuilder);
        return m.replaceAll(" ").trim();
    }

    private static List<String> getMergedJavadocBody(PsiDocComment existingDocComment,
                                                     List<String> newLines) {
        var bodyLines = new ArrayList<String>();
        for (var el : existingDocComment.getDescriptionElements()) {
            bodyLines.add(trimJavadocBodyLine(el.getText()));
        }
        removeLeadingAndTrailingEmptyLines(bodyLines);
        if (!bodyLines.isEmpty() && !newLines.isEmpty()) {
            bodyLines.add("<p>");
        }
        bodyLines.addAll(newLines);
        return bodyLines;
    }

    private static String trimJavadocBodyLine(String text) {
        var line = new StringBuilder(text);
        // Remove a single leading space, since this is the space between the asterisk and actual content
        // I.e.: '/* Text'
        if (!line.isEmpty() && line.charAt(0) == ' ') {
            line.deleteCharAt(0);
        }
        // Remove trailing newline
        if (!line.isEmpty() && line.charAt(line.length() - 1) == '\n') {
            line.deleteCharAt(line.length() - 1);
        }
        if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
            line.deleteCharAt(line.length() - 1);
        }
        return line.toString();
    }

    private static void removeLeadingAndTrailingEmptyLines(List<String> bodyLines) {
        while (!bodyLines.isEmpty() && bodyLines.get(0).isBlank()) {
            bodyLines.remove(0);
        }
        while (!bodyLines.isEmpty() && bodyLines.get(bodyLines.size() - 1).isBlank()) {
            bodyLines.remove(bodyLines.size() - 1);
        }
    }

    public static int getIndent(PsiDocCommentBase comment) {
        int indentLength = 0;
        for (var child = comment.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PsiDocToken token && child.getPrevSibling() instanceof PsiWhiteSpace precedingWhitespace) {
                var type = token.getTokenType();
                if (type == JavaDocTokenType.DOC_COMMENT_START) {
                    indentLength = Math.max(indentLength, PsiHelper.getLastLineLength(precedingWhitespace));

                } else if (type == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS || type == JavaDocTokenType.DOC_COMMENT_END) {
                    // Strip one space since it includes the inner-javadoc indent
                    indentLength = Math.max(indentLength, PsiHelper.getLastLineLength(precedingWhitespace) - 1);
                }
            }
        }
        return indentLength;
    }

    private static String formatJavadoc(int indent, List<String> descriptionLines, List<JavadocTag> tags, Map<String, String> parameterDocs, List<String> parameterOrder) {

        // Defensive copy, we are going to sort/modify them
        tags = new ArrayList<>(tags);

        // Order param tags by the order of the method parameters
        parameterDocs.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> {
                    var idx = parameterOrder.indexOf(entry.getKey());
                    return idx == -1 ? Integer.MAX_VALUE : idx; // sort unknown params last
                }))
                .map(e -> new JavadocTag("param", e.getKey(), e.getValue()))
                .forEach(tags::add);

        // Order tags. Sort is stable so pre-ordered param tags stay in order
        tags.sort(Comparator.comparing(JavadocTag::tagName, TAGS_COMPARATOR));

        String indentText = " ".repeat(indent);
        StringBuilder result = new StringBuilder();
        result.append("/**\n");
        for (String line : descriptionLines) {
            result.append(indentText).append(" * ").append(line);
            endLine(result);
        }

        // The empty line between description and tags
        if (!descriptionLines.isEmpty() && !tags.isEmpty()) {
            result.append(indentText).append(" *\n");
        }

        formatTags(tags, indentText, result);

        result.append(indentText).append(" */");
        return result.toString();
    }

    private static void formatTags(List<JavadocTag> tags, String indentText, StringBuilder result) {
        // Used for breaking overly long lines
        var boundary = BreakIterator.getWordInstance(Locale.ENGLISH);

        tags.stream().collect(Collectors.groupingBy(JavadocTag::tagName)).forEach((tagName, javadocTags) -> {
            // For certain tags (throws, param), try to align columnar, for any other tag-type,
            // simple align tag-by-tag. Example:
            // @param short        description line 1
            //                     description line 2
            // @param longlonglong description line 1
            //                     description line 2
            // @see ABC XYZ
            // @see A B C
            boolean groupedTag = tagName.equals("param") || tagName.equals("throws") || tagName.equals("exception");

            int firstColWidth = 0;
            if (groupedTag) {
                firstColWidth = javadocTags.stream()
                        .mapToInt(t -> t.refersTo != null ? t.refersTo.length() : 0)
                        .max().orElse(0);
            }

            for (var tag : javadocTags) {
                result.append(indentText).append(" *");
                int startOfLineContent = result.length(); // Used to compute continuation indent
                result.append(" @").append(tag.tagName);

                if (tag.refersTo != null) {
                    result.append(' ').append(tag.refersTo);
                    var padding = firstColWidth - tag.refersTo.length();
                    for (int j = 0; j < padding; j++) {
                        result.append(' ');
                    }
                }

                result.append(' '); // separator

                // Compute the text to be prepended to each wrapped line
                int continuationIndent = result.length() - startOfLineContent;
                String continuationIndentText = indentText + " *" + " ".repeat(continuationIndent);

                // Target 80, but allocate always at least 40
                var maxLineLength = Math.max(40, 80 - continuationIndent);

                // Break the text
                boundary.setText(tag.text);
                int start = boundary.first();

                int currentLineLength = 0;
                for (int end = boundary.next(); end != BreakIterator.DONE; start = end, end = boundary.next()) {
                    var wordLength = end - start;
                    if (currentLineLength + wordLength > maxLineLength) {
                        endLine(result).append(continuationIndentText);
                        currentLineLength = 0;
                        // After a line-break, trim leading whitespace
                        while (start < end && tag.text.charAt(start) == ' ') {
                            start++;
                        }
                    }
                    result.append(tag.text, start, end);
                    currentLineLength += wordLength;
                }

                endLine(result);
            }
        });
    }

    private static StringBuilder endLine(StringBuilder sb) {
        // Avoid end-of-line trailing whitespace
        while (!sb.isEmpty() && sb.charAt(sb.length() - 1) == ' ') {
            sb.setLength(sb.length() - 1);
        }
        return sb.append('\n');
    }

    /**
     * @param refersTo In case of param or throws, this is the parameter name or exception that the tag refers to.
     */
    private record JavadocTag(String tagName, @Nullable String refersTo, String text) {
    }

}
