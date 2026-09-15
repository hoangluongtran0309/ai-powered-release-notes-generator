package com.hoangluongtran0309.releaseflow.audience;

import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns release-note Markdown into HTML that is safe to insert into a page. Raw HTML
 * is escaped, link targets are limited to safe schemes, links never pass the page to
 * their target, and images become plain links so that a preview never loads anything
 * from another host.
 */
public final class MarkdownHtml {

    private static final String LINK_REL = "nofollow noopener noreferrer";
    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .escapeHtml(true)
            .sanitizeUrls(true)
            .attributeProviderFactory(context -> (node, tagName, attributes) -> {
                if (node instanceof Link) {
                    attributes.put("rel", LINK_REL);
                }
            })
            .nodeRendererFactory(ImageAsLink::new)
            .build();

    private MarkdownHtml() {
    }

    public static String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return RENDERER.render(PARSER.parse(markdown));
    }

    private static final class ImageAsLink implements NodeRenderer {

        private final HtmlNodeRendererContext context;
        private final HtmlWriter html;

        ImageAsLink(HtmlNodeRendererContext context) {
            this.context = context;
            this.html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(Image.class);
        }

        @Override
        public void render(Node node) {
            Image image = (Image) node;
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("href", context.encodeUrl(context.urlSanitizer().sanitizeLinkUrl(image.getDestination())));
            attributes.put("rel", LINK_REL);
            html.tag("a", context.extendAttributes(image, "a", attributes));
            if (image.getFirstChild() == null) {
                html.text(image.getDestination());
            }
            for (Node child = image.getFirstChild(); child != null; child = child.getNext()) {
                context.render(child);
            }
            html.tag("/a");
        }
    }
}
