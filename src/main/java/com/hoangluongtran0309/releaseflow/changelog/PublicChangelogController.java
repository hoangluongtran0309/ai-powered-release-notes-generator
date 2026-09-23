package com.hoangluongtran0309.releaseflow.changelog;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * An Organization's changelog, for anybody at all: no session, no cookie, nothing but
 * what was published. It reads only public entries, which are immutable, so the pages
 * can be cached hard and a permalink can be cached for a day.
 *
 * <p>A page returns a view name rather than a {@code ResponseEntity} wrapping one:
 * wrapping it would make the model the response body and serve internal identifiers as
 * JSON. Headers belong on the response, which is where the caching contract lives.
 */
@Controller
class PublicChangelogController {

    private static final MediaType RSS = new MediaType("application", "rss+xml", StandardCharsets.UTF_8);
    private static final DateTimeFormatter RSS_DATE =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter PUBLISHED_ON =
            DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneOffset.UTC);
    private static final String LIST_CACHE = CacheControl.maxAge(Duration.ofMinutes(5))
            .cachePublic()
            .getHeaderValue();
    private static final String ENTRY_CACHE = CacheControl.maxAge(Duration.ofDays(1))
            .cachePublic()
            .immutable()
            .getHeaderValue();
    // The note is the same for everybody; the chrome around it follows the reader, so a
    // shared cache must keep one reader's language out of another's response.
    private static final String CHROME_VARIES_BY = "Accept-Language, Cookie";

    private final PublicChangelogService changelog;
    private final PublicChangelogUrls urls;

    PublicChangelogController(PublicChangelogService changelog, PublicChangelogUrls urls) {
        this.changelog = changelog;
        this.urls = urls;
    }

    @GetMapping("/changelog/{slug}")
    String index(@PathVariable String slug, Model model, HttpServletResponse response) {
        PublicChangelogService.Feed feed = changelog.findFeed(slug).orElseThrow(PublicChangelogController::notFound);
        model.addAttribute("organizationName", feed.organizationName());
        model.addAttribute("entries", feed.entries());
        model.addAttribute("canonicalUrl", urls.rootUrl(feed.slug()));
        model.addAttribute("feedUrl", urls.feedUrl(feed.slug()));
        model.addAttribute("publishedOn", PUBLISHED_ON);
        response.setHeader("Cache-Control", LIST_CACHE);
        response.setHeader("Vary", CHROME_VARIES_BY);
        return "changelog/index";
    }

    @GetMapping("/changelog/{slug}/releases/{entryId}")
    String entry(
            @PathVariable String slug,
            @PathVariable UUID entryId,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        PublicChangelogService.Entry found = changelog.findEntry(slug, entryId)
                .orElseThrow(PublicChangelogController::notFound);
        // An entry never changes, so its identifier is a complete validator.
        String etag = "\"" + found.entry().id() + "\"";
        response.setHeader("Cache-Control", ENTRY_CACHE);
        response.setHeader("Vary", CHROME_VARIES_BY);
        if (new ServletWebRequest(request, response).checkNotModified(etag)) {
            return null;
        }
        model.addAttribute("organizationName", found.organizationName());
        model.addAttribute("entry", found.entry());
        model.addAttribute("canonicalUrl", urls.entryUrl(found.slug(), found.entry().id()));
        model.addAttribute("changelogUrl", urls.rootUrl(found.slug()));
        model.addAttribute("feedUrl", urls.feedUrl(found.slug()));
        model.addAttribute("publishedOn", PUBLISHED_ON);
        return "changelog/detail";
    }

    @GetMapping("/changelog/{slug}/rss.xml")
    ResponseEntity<String> feed(@PathVariable String slug) {
        PublicChangelogService.Feed feed = changelog.findFeed(slug).orElseThrow(PublicChangelogController::notFound);
        return ResponseEntity.ok()
                .contentType(RSS)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(rss(feed));
    }

    // RSS 2.0, written with the JDK's own XML writer so every value is escaped by the
    // same code that puts it there. Its own two sentences stay English: a feed is one
    // shared, cached document, and a reader's client says nothing about who subscribed.
    private String rss(PublicChangelogService.Feed feed) {
        String root = urls.rootUrl(feed.slug());
        try {
            StringWriter output = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("rss");
            xml.writeAttribute("version", "2.0");
            xml.writeStartElement("channel");
            element(xml, "title", feed.organizationName() + " releases");
            element(xml, "link", root);
            element(xml, "description", "Published release notes from " + feed.organizationName());
            for (EntryView entry : feed.entries()) {
                xml.writeStartElement("item");
                element(xml, "title", entry.projectName() + " " + entry.releaseVersion());
                element(xml, "link", entry.url());
                xml.writeStartElement("guid");
                xml.writeAttribute("isPermaLink", "true");
                xml.writeCharacters(entry.url());
                xml.writeEndElement();
                element(xml, "pubDate", RSS_DATE.format(entry.publishedAt()));
                // Readers render a description as HTML, so raw Markdown would reach
                // subscribers as literal asterisks. It is the same safe HTML the page
                // shows, and the writer escapes it on the way into the document.
                element(xml, "description", entry.html());
            }
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
            return output.toString();
        } catch (XMLStreamException failure) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not write the feed.");
        }
    }

    private static void element(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeStartElement(name);
        xml.writeCharacters(value == null ? "" : value);
        xml.writeEndElement();
    }

    /** An address nobody answers says nothing about who exists. */
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
