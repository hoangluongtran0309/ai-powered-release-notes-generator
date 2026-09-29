package com.hoangluongtran0309.releaseflow.automation;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

import java.util.Locale;

/**
 * Turns release-note Markdown into Slack's own mrkdwn, which reads neither {@code #}
 * headings, {@code **bold**}, nor {@code [text](url)} links. Headings and strong text
 * become {@code *bold*}, links become {@code <url|text>}, and lists become bullets.
 *
 * <p>Every {@code &}, {@code <}, and {@code >} in the note's own text is escaped, so a
 * pull request title can never become a link, a mention, or {@code <!channel>}; the
 * only control sequences in the message are the links written here, and only for
 * http, https, and mailto targets.
 */
final class SlackMrkdwn {

    private static final Parser PARSER = Parser.builder().build();

    private SlackMrkdwn() {
    }

    static String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Writer writer = new Writer();
        PARSER.parse(markdown).accept(writer);
        return writer.result();
    }

    /** Whether the note opens with a heading, which then already names the release. */
    static boolean startsWithHeading(String markdown) {
        if (markdown == null) {
            return false;
        }
        Node first = PARSER.parse(markdown).getFirstChild();
        return first instanceof Heading;
    }

    private static final class Writer extends AbstractVisitor {

        private final StringBuilder out = new StringBuilder();
        private String linePrefix = "";
        private boolean bold;
        private boolean markerWritten;
        private int listDepth;

        String result() {
            return out.toString().strip();
        }

        @Override
        public void visit(Heading heading) {
            startBlock();
            boldly(heading);
        }

        @Override
        public void visit(Paragraph paragraph) {
            startBlock();
            visitChildren(paragraph);
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            startBlock();
            String outer = linePrefix;
            linePrefix = outer + "> ";
            out.append("> ");
            markerWritten = true;
            visitChildren(blockQuote);
            linePrefix = outer;
        }

        @Override
        public void visit(BulletList list) {
            items(list, false, 1);
        }

        @Override
        public void visit(OrderedList list) {
            Integer start = list.getMarkerStartNumber();
            items(list, true, start == null ? 1 : start);
        }

        private void items(Node list, boolean ordered, int start) {
            startBlock();
            String outer = linePrefix;
            int number = start;
            boolean first = true;
            listDepth++;
            for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
                if (!first) {
                    newLine();
                }
                first = false;
                String marker = ordered ? number++ + ". " : "• ";
                out.append(marker);
                markerWritten = true;
                linePrefix = outer + "    ";
                visitChildren(item);
                linePrefix = outer;
            }
            listDepth--;
        }

        @Override
        public void visit(FencedCodeBlock codeBlock) {
            codeBlock(codeBlock.getLiteral());
        }

        @Override
        public void visit(IndentedCodeBlock codeBlock) {
            codeBlock(codeBlock.getLiteral());
        }

        private void codeBlock(String literal) {
            startBlock();
            out.append("```");
            newLine();
            appendLines(escape(literal.stripTrailing()));
            newLine();
            out.append("```");
        }

        @Override
        public void visit(HtmlBlock htmlBlock) {
            startBlock();
            appendLines(escape(htmlBlock.getLiteral().stripTrailing()));
        }

        @Override
        public void visit(ThematicBreak thematicBreak) {
            startBlock();
            out.append("───");
        }

        @Override
        public void visit(StrongEmphasis strong) {
            boldly(strong);
        }

        @Override
        public void visit(Emphasis emphasis) {
            out.append('_');
            visitChildren(emphasis);
            out.append('_');
        }

        @Override
        public void visit(Code code) {
            out.append('`').append(escape(code.getLiteral())).append('`');
        }

        @Override
        public void visit(Link link) {
            linked(link.getDestination(), link);
        }

        @Override
        public void visit(Image image) {
            linked(image.getDestination(), image);
        }

        private void linked(String destination, Node node) {
            String url = safeUrl(destination);
            if (url == null) {
                visitChildren(node);
                return;
            }
            out.append('<').append(url);
            // An autolink's text is its own address; Slack shows that without a label.
            boolean autolink = node.getFirstChild() instanceof Text text && text.getNext() == null
                    && text.getLiteral().equals(destination);
            if (node.getFirstChild() != null && !autolink) {
                out.append('|');
                visitChildren(node);
            }
            out.append('>');
        }

        @Override
        public void visit(Text text) {
            out.append(escape(text.getLiteral()));
        }

        @Override
        public void visit(HtmlInline html) {
            out.append(escape(html.getLiteral()));
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            out.append(' ');
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            newLine();
        }

        private void boldly(Node node) {
            if (bold) {
                visitChildren(node);
                return;
            }
            bold = true;
            out.append('*');
            visitChildren(node);
            out.append('*');
            bold = false;
        }

        /**
         * Separates a block from the one before it: a blank line at the top level, a line
         * break inside a list, and nothing right after a bullet or quote marker.
         */
        private void startBlock() {
            if (markerWritten) {
                markerWritten = false;
                return;
            }
            if (out.isEmpty()) {
                return;
            }
            newLine();
            if (listDepth == 0) {
                newLine();
            }
        }

        private void newLine() {
            out.append('\n').append(linePrefix);
        }

        private void appendLines(String text) {
            String[] lines = text.split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                if (index > 0) {
                    newLine();
                }
                out.append(lines[index]);
            }
        }
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String safeUrl(String destination) {
        if (destination == null || destination.isBlank()) {
            return null;
        }
        String url = destination.strip();
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://") && !lower.startsWith("mailto:")) {
            return null;
        }
        return url.replace("|", "%7C").replace("<", "%3C").replace(">", "%3E").replace(" ", "%20");
    }
}
